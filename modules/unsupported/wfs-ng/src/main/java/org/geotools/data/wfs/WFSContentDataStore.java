package org.geotools.data.wfs;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.xml.namespace.QName;

import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.wfs.protocol.WFSOperationType;
import org.geotools.data.wfs.protocol.WFSProtocol;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.opengis.feature.type.Name;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class WFSContentDataStore extends ContentDataStore implements WFSDataStore {

    private final WFSProtocol wfsImpl;

    private Integer maxFeatures;

    private boolean preferPostOverGet;

    public WFSContentDataStore(final WFSProtocol wfsImpl) {
        this.wfsImpl = wfsImpl;
    }

    /**
     * @see org.geotools.data.store.ContentDataStore#createTypeNames()
     */
    @Override
    protected List<Name> createTypeNames() throws IOException {
        Set<QName> featureTypeNames = wfsImpl.getFeatureTypeNames();
        List<Name> names = new ArrayList<Name>(featureTypeNames.size());
        for (QName qname : featureTypeNames) {
            final String prefixedName = wfsImpl.getSimpleTypeName(qname);
            names.add(new NameImpl(getNamespaceURI(), prefixedName));
        }
        return names;
    }

    /**
     * @see org.geotools.data.store.ContentDataStore#createFeatureSource(org.geotools.data.store.ContentEntry)
     */
    @Override
    protected ContentFeatureSource createFeatureSource(final ContentEntry entry) throws IOException {
        ContentFeatureSource source;

        source = new WFSContentFeatureSource(entry, wfsImpl, maxFeatures, preferPostOverGet);

        // Do not return a FeatureStore until it's implementation is finished
        // if (wfsImpl.supportsOperation(WFSOperationType.TRANSACTION, true)) {
        // source = new WFSContentFeatureStore((WFSContentFeatureSource) source);
        // }

        return source;
    }

    /**
     * @see WFSDataStore#getInfo()
     */
    @Override
    public WFSServiceInfo getInfo() {
        return new CapabilitiesServiceInfo(this);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getCapabilitiesURL()
     */
    @Override
    public URL getCapabilitiesURL() {
        return wfsImpl.getOperationURL(WFSOperationType.GET_CAPABILITIES, false);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getServiceTitle()
     */
    @Override
    public String getServiceTitle() {
        return wfsImpl.getServiceTitle();
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getServiceVersion()
     */
    @Override
    public String getServiceVersion() {
        return wfsImpl.getServiceVersion().toString();
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getServiceAbstract()
     */
    @Override
    public String getServiceAbstract() {
        return wfsImpl.getServiceAbstract();
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getServiceKeywords()
     */
    @Override
    public Set<String> getServiceKeywords() {
        return wfsImpl.getServiceKeywords();
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getServiceProviderUri()
     */
    @Override
    public URI getServiceProviderUri() {
        return wfsImpl.getServiceProviderUri();
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getFeatureTypeTitle(java.lang.String)
     */
    @Override
    public String getFeatureTypeTitle(String typeName) {
        return wfsImpl.getFeatureTypeTitle(typeName);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getFeatureTypeName(java.lang.String)
     */
    @Override
    public QName getFeatureTypeName(String typeName) {
        return wfsImpl.getFeatureTypeName(typeName);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getFeatureTypeAbstract(java.lang.String)
     */
    @Override
    public String getFeatureTypeAbstract(String typeName) {
        return wfsImpl.getFeatureTypeAbstract(typeName);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getFeatureTypeWGS84Bounds(java.lang.String)
     */
    @Override
    public ReferencedEnvelope getFeatureTypeWGS84Bounds(String typeName) {
        return wfsImpl.getFeatureTypeWGS84Bounds(typeName);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getFeatureTypeBounds(java.lang.String)
     */
    @Override
    public ReferencedEnvelope getFeatureTypeBounds(String typeName) {
        throw new UnsupportedOperationException();
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getFeatureTypeCRS(java.lang.String)
     */
    @Override
    public CoordinateReferenceSystem getFeatureTypeCRS(String localTypeName) {
        final String defaultCRS = wfsImpl.getDefaultCRS(localTypeName);
        CoordinateReferenceSystem crs = null;
        try {
            crs = CRS.decode(defaultCRS);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "can't decode CRS '" + defaultCRS + "' for " + localTypeName
                    + ". Assigning DefaultEngineeringCRS.GENERIC_2D.", e);
            crs = DefaultEngineeringCRS.GENERIC_2D;
        }
        return crs;
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getFeatureTypeKeywords(java.lang.String)
     */
    @Override
    public Set<String> getFeatureTypeKeywords(String typeName) {
        return wfsImpl.getFeatureTypeKeywords(typeName);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getDescribeFeatureTypeURL(java.lang.String)
     */
    @Override
    public URL getDescribeFeatureTypeURL(String typeName) {
        return wfsImpl.getDescribeFeatureTypeURLGet(typeName);
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#setMaxFeatures(java.lang.Integer)
     */
    @Override
    public void setMaxFeatures(Integer maxFeatures) {
        this.maxFeatures = maxFeatures;
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#getMaxFeatures()
     */
    @Override
    public Integer getMaxFeatures() {
        return maxFeatures;
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#setPreferPostOverGet(java.lang.Boolean)
     */
    @Override
    public void setPreferPostOverGet(Boolean post) {
        this.preferPostOverGet = post == null ? false : post;
    }

    /**
     * @see org.geotools.data.wfs.WFSDataStore#isPreferPostOverGet()
     */
    @Override
    public boolean isPreferPostOverGet() {
        return preferPostOverGet;
    }
}
