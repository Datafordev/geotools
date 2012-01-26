/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.data.wfs.internal.v1_1_0;

import static net.opengis.wfs.ResultTypeType.HITS_LITERAL;
import static net.opengis.wfs.ResultTypeType.RESULTS_LITERAL;
import static org.geotools.data.wfs.internal.GetFeature.ResultType.RESULTS;
import static org.geotools.data.wfs.internal.HttpMethod.GET;
import static org.geotools.data.wfs.internal.HttpMethod.POST;
import static org.geotools.data.wfs.internal.WFSOperationType.DESCRIBE_FEATURETYPE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import net.opengis.ows10.DCPType;
import net.opengis.ows10.DomainType;
import net.opengis.ows10.KeywordsType;
import net.opengis.ows10.OnlineResourceType;
import net.opengis.ows10.OperationType;
import net.opengis.ows10.OperationsMetadataType;
import net.opengis.ows10.Ows10Factory;
import net.opengis.ows10.RequestMethodType;
import net.opengis.ows10.ServiceIdentificationType;
import net.opengis.ows10.ServiceProviderType;
import net.opengis.ows10.WGS84BoundingBoxType;
import net.opengis.wfs.DescribeFeatureTypeType;
import net.opengis.wfs.FeatureTypeType;
import net.opengis.wfs.GetCapabilitiesType;
import net.opengis.wfs.GetFeatureType;
import net.opengis.wfs.GetGmlObjectType;
import net.opengis.wfs.LockFeatureType;
import net.opengis.wfs.OutputFormatListType;
import net.opengis.wfs.QueryType;
import net.opengis.wfs.TransactionType;
import net.opengis.wfs.WFSCapabilitiesType;
import net.opengis.wfs.WfsFactory;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.ecore.EObject;
import org.geotools.data.DataSourceException;
import org.geotools.data.Query;
import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.internal.GetFeature;
import org.geotools.data.wfs.internal.GetFeature.ResultType;
import org.geotools.data.wfs.internal.HttpMethod;
import org.geotools.data.wfs.internal.RequestComponents;
import org.geotools.data.wfs.internal.TransactionRequest;
import org.geotools.data.wfs.internal.TransactionRequest.TransactionElement;
import org.geotools.data.wfs.internal.TransactionResult;
import org.geotools.data.wfs.internal.URIs;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.data.wfs.internal.WFSResponse;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.data.wfs.internal.parsers.EmfAppSchemaParser;
import org.geotools.factory.GeoTools;
import org.geotools.filter.Capabilities;
import org.geotools.filter.v1_1.OGC;
import org.geotools.filter.v1_1.OGCConfiguration;
import org.geotools.filter.visitor.CapabilitiesFilterSplitter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.geotools.wfs.WFS;
import org.geotools.wfs.v1_1.WFSConfiguration;
import org.geotools.xml.Configuration;
import org.geotools.xml.Encoder;
import org.geotools.xml.Parser;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.Id;
import org.opengis.filter.capability.FilterCapabilities;
import org.opengis.filter.identity.Identifier;
import org.opengis.filter.sort.SortBy;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.xml.sax.SAXException;

/**
 * {@link WFSStrategy} implementation to talk to a WFS 1.1.0 server leveraging the GeoTools
 * {@code xml-xsd} subsystem for schema assisted parsing and encoding of WFS requests and responses.
 * <p>
 * Additional extension hooks:
 * <ul>
 * <li> {@link #supportsGet()}
 * <li> {@link #supportsPost()}
 * <li> {@link #createGetFeatureRequest(GetFeature)}
 * <li> {@link #buildGetFeatureParametersForGet(GetFeatureType)}
 * <li> {@link #encodeGetFeatureGetFilter(Filter)}
 * </ul>
 * </p>
 * 
 * @author groldan
 */
public class Strict_1_1_0_Strategy implements WFSStrategy {

    private static final Logger LOGGER = Logging.getLogger(Strict_1_1_0_Strategy.class);

    protected static final String DEFAULT_OUTPUT_FORMAT = "text/xml; subtype=gml/3.1.1";

    private static final Configuration filter_1_1_0_Configuration = new OGCConfiguration();

    private static final Configuration wfs_1_1_0_Configuration = new WFSConfiguration();

    /**
     * The WFS GetCapabilities document. Final by now, as we're not handling updatesequence, so will
     * not ask the server for an updated capabilities during the life-time of this datastore.
     */
    WFSCapabilitiesType capabilities;

    /**
     * Per featuretype name Map of capabilities feature type information. Not to be used directly
     * but through {@link #getFeatureTypeInfo(String)}
     */
    private final Map<String, FeatureTypeType> typeInfos;

    private HTTPClient http;

    private WFSConfig config;

    public Strict_1_1_0_Strategy() {
        this.config = new WFSConfig();
        this.http = new SimpleHttpClient();
        this.typeInfos = new HashMap<String, FeatureTypeType>();
    }

    @Override
    public void setConfig(WFSConfig config) {
        this.config = config;
    }

    @Override
    public WFSConfig getConfig() {
        return config;
    }

    @Override
    public void setHttpClient(HTTPClient httpClient) {
        this.http = httpClient;
    }

    @Override
    public void initFromCapabilities(final InputStream capabilitiesContents) throws IOException {
        this.capabilities = parseCapabilities(capabilitiesContents);
        this.typeInfos.clear();

        @SuppressWarnings("unchecked")
        final List<FeatureTypeType> ftypes = capabilities.getFeatureTypeList().getFeatureType();
        QName typeName;
        for (FeatureTypeType ftype : ftypes) {
            typeName = ftype.getName();
            assert !("".equals(typeName.getPrefix()));
            String prefixedTypeName = typeName.getPrefix() + ":" + typeName.getLocalPart();
            typeInfos.put(prefixedTypeName, ftype);
        }
    }

    /**
     * @return {@code true}, override if the WFS doesn't support GET at all
     */
    protected boolean supportsGet() {
        return true;
    }

    /**
     * @return {@code true}, override if the WFS doesn't support POST at all
     */
    protected boolean supportsPost() {
        return true;
    }

    /**
     * Creates the mapping {@link GetFeatureType GetFeature} request for the given {@link Query} and
     * {@code outputFormat}, and post-processing filter based on the server's stated filter
     * capabilities.
     * 
     */
    @SuppressWarnings("unchecked")
    protected RequestComponents createGetFeatureRequest(GetFeature query) throws IOException {
        final WfsFactory factory = WfsFactory.eINSTANCE;

        GetFeatureType getFeature = factory.createGetFeatureType();
        getFeature.setService("WFS");
        getFeature.setVersion(getServiceVersion().toString());
        getFeature.setOutputFormat(query.getOutputFormat());

        getFeature.setHandle("GeoTools " + GeoTools.getVersion() + " WFS DataStore");
        Integer maxFeatures = query.getMaxFeatures();
        if (maxFeatures != null) {
            getFeature.setMaxFeatures(BigInteger.valueOf(maxFeatures.intValue()));
        }

        ResultType resultType = query.getResultType();
        getFeature.setResultType(RESULTS == resultType ? RESULTS_LITERAL : HITS_LITERAL);

        QueryType wfsQuery = factory.createQueryType();
        wfsQuery.setTypeName(Collections.singletonList(query.getTypeName()));

        Filter serverFilter = query.getFilter();
        if (!Filter.INCLUDE.equals(serverFilter)) {
            wfsQuery.setFilter(serverFilter);
        }
        String srsName = query.getSrsName();
        try {
            wfsQuery.setSrsName(new URI(srsName));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Can't create a URI from the query CRS: " + srsName, e);
        }
        String[] propertyNames = query.getPropertyNames();
        boolean retrieveAllProperties = propertyNames == null;
        if (!retrieveAllProperties) {
            List<String> propertyName = wfsQuery.getPropertyName();
            for (String propName : propertyNames) {
                propertyName.add(propName);
            }
        }
        SortBy[] sortByList = query.getSortBy();
        if (sortByList != null) {
            for (SortBy sortBy : sortByList) {
                wfsQuery.getSortBy().add(sortBy);
            }
        }

        getFeature.getQuery().add(wfsQuery);

        RequestComponents reqParts = new RequestComponents();
        reqParts.setServerRequest(getFeature);

        Map<String, String> parametersForGet = buildGetFeatureParametersForGet(getFeature);
        reqParts.setKvpParameters(parametersForGet);

        return reqParts;
    }

    protected Map<String, String> buildGetFeatureParametersForGet(GetFeatureType request)
            throws IOException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("SERVICE", "WFS");
        map.put("VERSION", "1.1.0");
        map.put("REQUEST", "GetFeature");
        map.put("OUTPUTFORMAT", request.getOutputFormat());

        if (request.getMaxFeatures() != null) {
            map.put("MAXFEATURES", String.valueOf(request.getMaxFeatures()));
        }

        final QueryType query = (QueryType) request.getQuery().get(0);
        final String typeName = (String) query.getTypeName().get(0);
        map.put("TYPENAME", typeName);

        if (query.getPropertyName().size() > 0) {
            @SuppressWarnings("unchecked")
            List<String> propertyNames = query.getPropertyName();
            StringBuilder pnames = new StringBuilder();
            for (Iterator<String> it = propertyNames.iterator(); it.hasNext();) {
                pnames.append(it.next());
                if (it.hasNext()) {
                    pnames.append(',');
                }
            }
            map.put("PROPERTYNAME", pnames.toString());
        }

        // SRSNAME parameter. Let the server reproject.
        // TODO: should check if the server supports the required crs
        URI srsName = query.getSrsName();
        if (srsName != null) {
            map.put("SRSNAME", srsName.toString());
        }
        final Filter filter = query.getFilter();

        if (filter != null && Filter.INCLUDE != filter) {
            if (filter instanceof Id) {
                final Set<Identifier> identifiers = ((Id) filter).getIdentifiers();
                StringBuffer idValues = new StringBuffer();
                for (Iterator<Identifier> it = identifiers.iterator(); it.hasNext();) {
                    Object id = it.next().getID();
                    // REVISIT: should URL encode the id?
                    idValues.append(String.valueOf(id));
                    if (it.hasNext()) {
                        idValues.append(",");
                    }
                }
                map.put("FEATUREID", idValues.toString());
            } else {
                String xmlEncodedFilter = encodeGetFeatureGetFilter(filter);
                map.put("FILTER", xmlEncodedFilter);
            }
        }

        return map;
    }

    /**
     * Returns a single-line string containing the xml representation of the given filter, as
     * appropriate for the {@code FILTER} parameter in a GetFeature request.
     */
    protected String encodeGetFeatureGetFilter(final Filter filter) throws IOException {
        Configuration filterConfig = getFilterConfiguration();
        Encoder encoder = new Encoder(filterConfig);
        // do not write the xml declaration
        encoder.setOmitXMLDeclaration(true);
        encoder.setEncoding(Charset.forName("UTF-8"));

        OutputStream out = new ByteArrayOutputStream();
        encoder.encode(filter, OGC.Filter, out);
        String encoded = out.toString();
        encoded = encoded.replaceAll("\n", "");
        return encoded;
    }

    /*---------------------------------------------------------------------
     * WFSStrategy methods
     * ---------------------------------------------------------------------*/

    /**
     * @return {@link Versions#v1_1_0}
     * @see WFSStrategy#getServiceVersion()
     */
    @Override
    public Version getServiceVersion() {
        return Versions.v1_1_0;
    }

    /**
     * @see WFSStrategy#getServiceTitle()
     */
    public String getServiceTitle() {
        return getServiceIdentification().getTitle();
    }

    /**
     * @see WFSStrategy#getServiceAbstract()
     */
    @Override
    public String getServiceAbstract() {
        return getServiceIdentification().getAbstract();
    }

    /**
     * @see WFSStrategy#getServiceKeywords()
     */
    @Override
    public Set<String> getServiceKeywords() {
        @SuppressWarnings("unchecked")
        List<KeywordsType> capsKeywords = getServiceIdentification().getKeywords();
        return extractKeywords(capsKeywords);
    }

    private ServiceIdentificationType getServiceIdentification() {
        ServiceIdentificationType serviceId = capabilities.getServiceIdentification();
        if (serviceId == null) {
            LOGGER.info("Capabilities did not provide a ServiceIdentification section");
            serviceId = Ows10Factory.eINSTANCE.createServiceIdentificationType();
            capabilities.setServiceIdentification(serviceId);
        }
        return serviceId;
    }

    /**
     * @see WFSStrategy#getServiceProviderUri()
     */
    @Override
    public URI getServiceProviderUri() {
        ServiceProviderType serviceProvider = capabilities.getServiceProvider();
        if (serviceProvider == null) {
            return null;
        }
        OnlineResourceType providerSite = serviceProvider.getProviderSite();
        if (providerSite == null) {
            return null;
        }
        String href = providerSite.getHref();
        if (href == null) {
            return null;
        }
        try {
            return new URI(href);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * @see WFSStrategy#getSupportedGetFeatureOutputFormats()
     */
    @Override
    public Set<String> getSupportedGetFeatureOutputFormats() {
        OperationType operationMetadata = getOperationMetadata(WFSOperationType.GET_FEATURE);
        @SuppressWarnings("unchecked")
        List<DomainType> parameters = operationMetadata.getParameter();
        @SuppressWarnings("unchecked")
        List<FeatureTypeType> featuretypes = capabilities.getFeatureTypeList().getFeatureType();

        List<String> supportedByAllFeatureTypes = null;
        for (int i = 0; i < featuretypes.size(); i++) {
            net.opengis.wfs.FeatureTypeType ft = (FeatureTypeType) featuretypes.get(i);
            if (ft.getOutputFormats() != null) {
                @SuppressWarnings("unchecked")
                List<String> value = ft.getOutputFormats().getFormat();
                if (supportedByAllFeatureTypes == null) {
                    supportedByAllFeatureTypes = value;
                } else {
                    List<String> removeOutputFormats = new ArrayList<String>();
                    for (String o : supportedByAllFeatureTypes) {
                        if (!value.contains(o)) {
                            removeOutputFormats.add(o);
                        }
                    }
                    for (Object o : removeOutputFormats) {
                        supportedByAllFeatureTypes.remove(o);
                    }
                    if (supportedByAllFeatureTypes.size() == 0) {
                        break;
                    }
                }
            }
        }

        Set<String> outputFormats = new HashSet<String>();
        for (DomainType param : parameters) {
            String paramName = param.getName();
            if ("outputFormat".equals(paramName)) {
                @SuppressWarnings("unchecked")
                List<String> value = param.getValue();
                outputFormats.addAll(value);
            }
        }
        if (supportedByAllFeatureTypes != null)
            outputFormats.addAll(supportedByAllFeatureTypes);
        return outputFormats;
    }

    /**
     * @see WFSStrategy#getSupportedOutputFormats(String)
     */
    @Override
    public Set<String> getSupportedOutputFormats(String typeName) {
        final Set<String> serviceOutputFormats = getSupportedGetFeatureOutputFormats();
        final FeatureTypeType typeInfo = getFeatureTypeInfo(typeName);
        final OutputFormatListType outputFormats = typeInfo.getOutputFormats();

        Set<String> ftypeFormats = new HashSet<String>();
        if (outputFormats != null) {
            @SuppressWarnings("unchecked")
            List<String> ftypeDeclaredFormats = outputFormats.getFormat();
            ftypeFormats.addAll(ftypeDeclaredFormats);
        }

        ftypeFormats.addAll(serviceOutputFormats);
        return ftypeFormats;
    }

    /**
     * @see WFSStrategy#getFeatureTypeNames()
     */
    @Override
    public Set<QName> getFeatureTypeNames() {
        Set<QName> typeNames = new HashSet<QName>();
        for (FeatureTypeType typeInfo : typeInfos.values()) {
            QName name = typeInfo.getName();
            typeNames.add(name);
        }
        return typeNames;
    }

    /**
     * @see WFSStrategy#getFeatureTypeName(String)
     */
    @Override
    public QName getFeatureTypeName(String typeName) {
        FeatureTypeType featureTypeInfo = getFeatureTypeInfo(typeName);
        QName name = featureTypeInfo.getName();
        return name;
    }

    /**
     * @see org.geotools.data.wfs.internal.WFSStrategy#getSimpleTypeName(javax.xml.namespace.QName)
     */
    @Override
    public String getSimpleTypeName(QName qname) {
        String prefix = qname.getPrefix();
        String simpleName;
        if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
            simpleName = qname.getLocalPart();
        } else {
            simpleName = prefix + ":" + qname.getLocalPart();
        }
        return simpleName;
    }

    protected Configuration getFilterConfiguration() {
        return filter_1_1_0_Configuration;
    }

    protected Configuration getWfsConfiguration() {
        return wfs_1_1_0_Configuration;
    }

    /**
     * @see WFSStrategy#getFilterCapabilities()
     */
    @Override
    public FilterCapabilities getFilterCapabilities() {
        FilterCapabilities wfsFilterCapabilities;
        wfsFilterCapabilities = capabilities.getFilterCapabilities();
        return wfsFilterCapabilities;
    }

    /**
     * @see WFSStrategy#supportsOperation(WFSOperationType, boolean)
     */
    @Override
    public boolean supportsOperation(WFSOperationType operation, boolean post) {
        if (post && !supportsPost()) {
            return false;
        }
        if (!post && !supportsGet()) {
            return false;
        }

        HttpMethod method = post ? POST : GET;
        return null != getOperationURI(operation, method);
    }

    /**
     * @see WFSStrategy#getOperationURL(WFSOperationType, boolean)
     */
    @Override
    public URL getOperationURL(WFSOperationType operation, boolean post) {
        HttpMethod method = post ? POST : GET;
        String href = getOperationURI(operation, method);
        if (href != null) {
            try {
                return new URL(href);
            } catch (MalformedURLException e) {
                // Log error and let the search continue
                LOGGER.log(Level.INFO, "Malformed " + method + " URL for " + operation, e);
            }
        }
        return null;
    }

    /**
     * @see WFSStrategy#getFeatureTypeTitle(String)
     */
    @Override
    public String getFeatureTypeTitle(String typeName) {
        FeatureTypeType featureTypeInfo = getFeatureTypeInfo(typeName);
        return featureTypeInfo.getTitle();
    }

    /**
     * @see WFSStrategy#getFeatureTypeAbstract(String)
     */
    @Override
    public String getFeatureTypeAbstract(String typeName) {
        FeatureTypeType featureTypeInfo = getFeatureTypeInfo(typeName);
        return featureTypeInfo.getAbstract();
    }

    /**
     * @see WFSStrategy#getFeatureTypeWGS84Bounds(String)
     */
    @Override
    public ReferencedEnvelope getFeatureTypeWGS84Bounds(String typeName) {
        final FeatureTypeType featureTypeInfo = getFeatureTypeInfo(typeName);
        @SuppressWarnings("unchecked")
        List<WGS84BoundingBoxType> bboxList = featureTypeInfo.getWGS84BoundingBox();
        if (bboxList != null && bboxList.size() > 0) {
            WGS84BoundingBoxType bboxType = bboxList.get(0);
            @SuppressWarnings("unchecked")
            List<Double> lowerCorner = bboxType.getLowerCorner();
            @SuppressWarnings("unchecked")
            List<Double> upperCorner = bboxType.getUpperCorner();
            double minLon = (Double) lowerCorner.get(0);
            double minLat = (Double) lowerCorner.get(1);
            double maxLon = (Double) upperCorner.get(0);
            double maxLat = (Double) upperCorner.get(1);

            ReferencedEnvelope latLonBounds = new ReferencedEnvelope(minLon, maxLon, minLat,
                    maxLat, DefaultGeographicCRS.WGS84);

            return latLonBounds;
        }
        throw new IllegalStateException(
                "The capabilities document does not supply the ows:WGS84BoundingBox element");
    }

    /**
     * @see WFSStrategy#getDefaultCRS(String)
     */
    @Override
    public String getDefaultCRS(String typeName) {
        FeatureTypeType featureTypeInfo = getFeatureTypeInfo(typeName);
        String defaultSRS = featureTypeInfo.getDefaultSRS();
        return defaultSRS;
    }

    /**
     * @see WFSStrategy#getSupportedCRSIdentifiers(String)
     */
    @Override
    public Set<String> getSupportedCRSIdentifiers(String typeName) {
        FeatureTypeType featureTypeInfo = getFeatureTypeInfo(typeName);
        // TODO: another wrong emf mapping: getOtherSRS():String? should be a list
        String defaultSRS = featureTypeInfo.getDefaultSRS();
        @SuppressWarnings("unchecked")
        List<String> otherSRS = featureTypeInfo.getOtherSRS();

        Set<String> ftypeCrss = new HashSet<String>();
        ftypeCrss.add(defaultSRS);
        ftypeCrss.addAll(otherSRS);
        return ftypeCrss;
    }

    /**
     * @see WFSStrategy#getFeatureTypeKeywords(String)
     */
    @Override
    public Set<String> getFeatureTypeKeywords(String typeName) {
        FeatureTypeType featureTypeInfo = getFeatureTypeInfo(typeName);
        @SuppressWarnings("unchecked")
        List<KeywordsType> ftKeywords = featureTypeInfo.getKeywords();
        Set<String> ftypeKeywords = extractKeywords(ftKeywords);
        return ftypeKeywords;
    }

    public URL buildDescribeFeatureTypeURLGet(String typeName) {
        final String outputFormat = "text/xml; subtype=gml/3.1.1";
        return getDescribeFeatureTypeURLGet(typeName, outputFormat);
    }

    /**
     * @throws IOException
     * @see WFSStrategy#describeFeatureTypeGET(String, String)
     */
    @Override
    public WFSResponse describeFeatureTypeGET(String typeName, String outputFormat)
            throws IOException {
        if (!supportsOperation(DESCRIBE_FEATURETYPE, false)) {
            throw new UnsupportedOperationException(
                    "The server does not support DescribeFeatureType for HTTP method GET");
        }

        URL url = getDescribeFeatureTypeURLGet(typeName, outputFormat);
        @SuppressWarnings("unchecked")
        WFSResponse response = issueGetRequest(null, url, Collections.EMPTY_MAP);
        return response;
    }

    /**
     * @throws IOException
     * @see WFSStrategy#describeFeatureTypePOST(String, String)
     */
    @Override
    public WFSResponse describeFeatureTypePOST(String typeName, String outputFormat)
            throws IOException {
        throw new UnsupportedOperationException("POST not implemented yet for DescribeFeatureType");

    }

    /**
     * @see WFSStrategy#issueGetFeatureGET(GetFeatureType, Map)
     */
    @Override
    public WFSResponse issueGetFeatureGET(final GetFeature request) throws IOException {
        if (!supportsOperation(WFSOperationType.GET_FEATURE, false)) {
            throw new UnsupportedOperationException(
                    "The server does not support GetFeature for HTTP method GET");
        }
        URL url = getOperationURL(WFSOperationType.GET_FEATURE, false);

        RequestComponents reqParts = createGetFeatureRequest(request);
        Map<String, String> getFeatureKvp = reqParts.getKvpParameters();
        GetFeatureType requestType = reqParts.getServerRequest();

        System.out.println(" > getFeatureGET: Request url: " + url + ". Parameters: "
                + getFeatureKvp);
        WFSResponse response = issueGetRequest(requestType, url, getFeatureKvp);

        return response;
    }

    /**
     * @see WFSStrategy#getFeaturePOST(Query, String)
     */
    @Override
    public WFSResponse issueGetFeaturePOST(final GetFeature request) throws IOException {
        if (!supportsOperation(WFSOperationType.GET_FEATURE, true)) {
            throw new UnsupportedOperationException(
                    "The server does not support GetFeature for HTTP method POST");
        }
        URL url = getOperationURL(WFSOperationType.GET_FEATURE, true);

        RequestComponents reqParts = createGetFeatureRequest(request);
        GetFeatureType serverRequest = reqParts.getServerRequest();

        Encoder encoder = new Encoder(getWfsConfiguration());

        // If the typeName is of the form prefix:typeName we better declare the namespace since we
        // don't know how picky the server parser will be
        String typeName = reqParts.getKvpParameters().get("TYPENAME");
        QName fullName = getFeatureTypeName(typeName);
        String prefix = fullName.getPrefix();
        String namespace = fullName.getNamespaceURI();
        if (!XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
            encoder.getNamespaces().declarePrefix(prefix, namespace);
        }
        WFSResponse response = issuePostRequest(serverRequest, url, encoder);

        return response;
    }

    /**
     * @see WFSStrategy#dispose()
     */
    @Override
    public void dispose() {
        // do nothing
    }

    /**
     * Returns the feature type metadata object parsed from the capabilities document for the given
     * {@code typeName}
     * <p>
     * NOTE: this method is package protected just to be also accessed by unit test.
     * </p>
     * 
     * @param typeName
     *            the typeName as stated in the capabilities {@code FeatureTypeList} to get the info
     *            for
     * @return the WFS capabilities metadata {@link FeatureTypeType metadata} for {@code typeName}
     * @throws IllegalArgumentException
     *             if {@code typeName} is not the name of a FeatureType stated in the capabilities
     *             document.
     */
    private FeatureTypeType getFeatureTypeInfo(final String typeName) {
        if (!typeInfos.containsKey(typeName)) {
            throw new IllegalArgumentException("Type name not found: " + typeName);
        }
        return typeInfos.get(typeName);
    }

    private WFSCapabilitiesType parseCapabilities(InputStream capabilitiesReader)
            throws IOException {
        final Configuration wfsConfig = getWfsConfiguration();
        final Parser parser = new Parser(wfsConfig);
        final Object parsed;
        try {
            parsed = parser.parse(capabilitiesReader);
        } catch (SAXException e) {
            throw new DataSourceException("Exception parsing WFS 1.1.0 capabilities", e);
        } catch (ParserConfigurationException e) {
            throw new DataSourceException("WFS 1.1.0 parsing configuration error", e);
        }
        if (parsed == null) {
            throw new DataSourceException("WFS 1.1.0 capabilities was not parsed");
        }
        if (!(parsed instanceof WFSCapabilitiesType)) {
            throw new DataSourceException("Expected WFS Capabilities, got " + parsed);
        }
        return (WFSCapabilitiesType) parsed;
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractKeywords(List<KeywordsType> keywordsList) {
        Set<String> keywords = new HashSet<String>();
        for (KeywordsType keys : keywordsList) {
            keywords.addAll(keys.getKeyword());
        }
        return keywords;
    }

    private OperationType getOperationMetadata(WFSOperationType operation) {
        final OperationsMetadataType operationsMetadata = capabilities.getOperationsMetadata();
        @SuppressWarnings("unchecked")
        final List<OperationType> operations = operationsMetadata.getOperation();
        final String expectedOperationName = operation.getName();
        for (OperationType operationType : operations) {
            String operationName = operationType.getName();
            if (expectedOperationName.equalsIgnoreCase(operationName)) {
                return operationType;
            }
        }
        throw new NoSuchElementException("Operation metadata not found for "
                + expectedOperationName + " in the capabilities document");
    }

    private URL getDescribeFeatureTypeURLGet(String typeName, String outputFormat) {
        final FeatureTypeType typeInfo = getFeatureTypeInfo(typeName);

        final URL describeFeatureTypeUrl = getOperationURL(DESCRIBE_FEATURETYPE, false);

        Map<String, String> kvp = new HashMap<String, String>();
        kvp.put("SERVICE", "WFS");
        kvp.put("VERSION", getServiceVersion().toString());
        kvp.put("REQUEST", "DescribeFeatureType");
        kvp.put("TYPENAME", typeName);

        QName name = typeInfo.getName();
        if (!XMLConstants.DEFAULT_NS_PREFIX.equals(name.getPrefix())) {
            String nsUri = name.getNamespaceURI();
            kvp.put("NAMESPACE", "xmlns(" + name.getPrefix() + "=" + nsUri + ")");
        }

        // ommit output format by now, server should just return xml shcema
        // kvp.put("OUTPUTFORMAT", outputFormat);

        URL url = URIs.buildURL(describeFeatureTypeUrl, kvp);
        return url;
    }

    private WFSResponse issueGetRequest(EObject request, URL baseUrl, Map<String, String> kvp)
            throws IOException {
        WFSResponse response;
        URL url = URIs.buildURL(baseUrl, kvp);
        HTTPResponse httpResponse = http.get(url);

        String responseCharset = httpResponse.getResponseHeader("charset");
        Charset charset = responseCharset == null ? null : Charset.forName(responseCharset);
        String contentType = httpResponse.getContentType();
        InputStream responseStream = httpResponse.getResponseStream();
        String target = url.toExternalForm();
        response = new WFSResponse(target, request, charset, contentType, responseStream);
        return response;
    }

    private WFSResponse issuePostRequest(final EObject request, final URL url, final Encoder encoder)
            throws IOException {

        final Charset requestCharset = config.getDefaultEncoding();
        encoder.setEncoding(requestCharset);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Sending POST request: ");
            LOGGER.finest(out.toString(requestCharset.name()));
        }
        Strict_1_1_0_Strategy.encode(request, encoder, out);
        InputStream postContent = new ByteArrayInputStream(out.toByteArray());
        HTTPResponse httpResponse = http.post(url, postContent, "text/xml");

        String responseCharset = httpResponse.getResponseHeader("charset");
        Charset charset = responseCharset == null ? null : Charset.forName(responseCharset);
        String contentType = httpResponse.getContentType();
        InputStream responseStream = httpResponse.getResponseStream();
        String target = url.toExternalForm();
        WFSResponse response = new WFSResponse(target, request, charset, contentType,
                responseStream);
        return response;
    }

    /**
     * Returns the operation URI for the given operation/http method as a String to avoid creating a
     * URL instance when not needed
     */
    @SuppressWarnings("unchecked")
    private String getOperationURI(WFSOperationType operation, HttpMethod method) {
        final OperationType operationType = getOperationMetadata(operation);
        final List<DCPType> dcps = operationType.getDCP();
        for (DCPType dcp : dcps) {
            List<RequestMethodType> requests;
            if (GET == method) {
                requests = dcp.getHTTP().getGet();
            } else {
                requests = dcp.getHTTP().getPost();
            }
            for (RequestMethodType req : requests) {
                String href = req.getHref();
                return href;
            }
        }
        return null;
    }

    /**
     * Encodes a WFS request into {@code out}
     * 
     * @param request
     *            one of {@link GetCapabilitiesType}, {@link GetFeatureType}, etc
     * @param configuration
     *            the wfs configuration to use for encoding the request into the output stream
     * @param out
     *            the output stream where to encode the request into
     * @param charset
     *            the charset to use to encode the request in
     * @throws IOException
     */
    public static void encode(final EObject request, final Configuration configuration,
            final OutputStream out, final Charset charset) throws IOException {
        Encoder encoder = new Encoder(configuration);
        encoder.setEncoding(charset);
        encode(request, encoder, out);
    }

    private static void encode(EObject request, Encoder encoder, OutputStream out)
            throws IOException {
        encoder.setIndentSize(1);
        QName encodeElementName = getElementName(request);
        encoder.encode(request, encodeElementName, out);
    }

    private static QName getElementName(EObject originatingRequest) {
        QName encodeElementName;
        if (originatingRequest instanceof GetCapabilitiesType) {
            encodeElementName = WFS.GetCapabilities;
        } else if (originatingRequest instanceof GetFeatureType) {
            encodeElementName = WFS.GetFeature;
        } else if (originatingRequest instanceof DescribeFeatureTypeType) {
            encodeElementName = WFS.DescribeFeatureType;
        } else if (originatingRequest instanceof GetCapabilitiesType) {
            encodeElementName = WFS.GetCapabilities;
        } else if (originatingRequest instanceof GetGmlObjectType) {
            encodeElementName = WFS.GetGmlObject;
        } else if (originatingRequest instanceof LockFeatureType) {
            encodeElementName = WFS.LockFeature;
        } else if (originatingRequest instanceof TransactionType) {
            encodeElementName = WFS.Transaction;
        } else {
            throw new IllegalArgumentException("Unkown xml element name for " + originatingRequest);
        }
        return encodeElementName;
    }

    @Override
    public String getDefaultOutputFormat(WFSOperationType operation) {
        if (WFSOperationType.GET_FEATURE != operation) {
            throw new UnsupportedOperationException(
                    "Not implemented for other than GET_FEATURE yet");
        }

        Set<String> supportedOutputFormats = getSupportedGetFeatureOutputFormats();
        if (supportedOutputFormats.contains(DEFAULT_OUTPUT_FORMAT)) {
            return DEFAULT_OUTPUT_FORMAT;
        }
        throw new IllegalArgumentException("Server does not support '" + DEFAULT_OUTPUT_FORMAT
                + "' output format: " + supportedOutputFormats);
    }

    /**
     * Splits the filter provided by the geotools query into the server supported and unsupported
     * ones.
     * 
     * @return a two-element array where the first element is the supported filter and the second
     *         the one to post-process
     */
    @Override
    public Filter[] splitFilters(final Filter filter) {
        FilterCapabilities filterCapabilities = getFilterCapabilities();
        Capabilities filterCaps = new Capabilities();
        if (filterCapabilities != null) {
            filterCaps.addAll(filterCapabilities);
        }
        CapabilitiesFilterSplitter splitter = new CapabilitiesFilterSplitter(filterCaps, null, null);

        filter.accept(splitter, null);

        Filter server = splitter.getFilterPre();
        Filter post = splitter.getFilterPost();

        return new Filter[] { server, post };
    }

    /**
     * @see org.geotools.data.wfs.internal.WFSStrategy#issueDescribeFeatureTypeGET(java.lang.String,
     *      org.opengis.referencing.crs.CoordinateReferenceSystem)
     */
    @Override
    public SimpleFeatureType issueDescribeFeatureTypeGET(final String prefixedTypeName,
            CoordinateReferenceSystem crs) throws IOException {

        File tmpFile = null;
        final URL describeUrl;
        {
            final boolean isAuthenticating = http.getUser() != null;
            if (isAuthenticating) {
                WFSResponse wfsResponse = describeFeatureTypeGET(prefixedTypeName, null);
                tmpFile = File.createTempFile("describeft", ".xsd");
                OutputStream output = new FileOutputStream(tmpFile);
                InputStream response = wfsResponse.getInputStream();
                try {
                    IOUtils.copy(response, output);
                } finally {
                    output.flush();
                    output.close();
                    response.close();
                }
                describeUrl = tmpFile.toURI().toURL();
            } else {
                describeUrl = buildDescribeFeatureTypeURLGet(prefixedTypeName);
            }
        }

        final Configuration wfsConfiguration = getWfsConfiguration();
        final QName featureDescriptorName = getFeatureTypeName(prefixedTypeName);

        SimpleFeatureType featureType;
        try {
            featureType = EmfAppSchemaParser.parseSimpleFeatureType(wfsConfiguration,
                    featureDescriptorName, describeUrl, crs);
        } finally {
            if (tmpFile != null) {
                tmpFile.delete();
            }
        }
        return featureType;
    }

    @Override
    public TransactionResult issueTransaction(final TransactionRequest transactionRequest)
            throws IOException {

        if (!supportsOperation(WFSOperationType.TRANSACTION, true)) {
            throw new IOException("WFS does not support transactions");
        }

        WFS_1_1_0_TransactionRequest req = (WFS_1_1_0_TransactionRequest) transactionRequest;
        TransactionType transaction = req.getTransaction();

        // used to declare prefixes on the encoder later
        Set<QName> affectedTypes = new HashSet<QName>();
        for (TransactionElement e : transactionRequest.getTransactionElements()) {
            String localTypeName = e.getLocalTypeName();
            QName remoteTypeName = getFeatureTypeName(localTypeName);
            affectedTypes.add(remoteTypeName);
        }

        final Encoder encoder = new Encoder(getWfsConfiguration());
        // declare prefixes
        for (QName remoteType : affectedTypes) {
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(remoteType.getPrefix())) {
                continue;
            }
            encoder.getNamespaces().declarePrefix(remoteType.getPrefix(),
                    remoteType.getNamespaceURI());
        }
        encoder.setIndenting(true);
        encoder.setIndentSize(2);

        final URL operationURL = getOperationURL(WFSOperationType.TRANSACTION, true);
        final WFSResponse wfsResponse = issuePostRequest(transaction, operationURL, encoder);
        return toTransactionResult(wfsResponse);
    }

    private TransactionResult toTransactionResult(WFSResponse wfsResponse) {
        return null;
    }

    @Override
    public TransactionRequest createTransaction() {
        return new WFS_1_1_0_TransactionRequest(this);
    }
}
