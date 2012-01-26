package org.geotools.data.wfs.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.wfs.internal.CapabilitiesServiceInfo;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.feature.NameImpl;
import org.opengis.feature.type.Name;

public class WFSContentDataStore extends ContentDataStore {

    private final WFSStrategy wfs;

    public WFSContentDataStore(final WFSStrategy wfsImpl) {
        this.wfs = wfsImpl;
    }

    /**
     * @see org.geotools.data.store.ContentDataStore#createTypeNames()
     */
    @Override
    protected List<Name> createTypeNames() throws IOException {
        Set<QName> featureTypeNames = wfs.getFeatureTypeNames();
        List<Name> names = new ArrayList<Name>(featureTypeNames.size());
        for (QName qname : featureTypeNames) {
            final String prefixedName = wfs.getSimpleTypeName(qname);
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

        source = new WFSContentFeatureSource(entry, wfs);

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
        return new CapabilitiesServiceInfo(wfs);
    }

}
