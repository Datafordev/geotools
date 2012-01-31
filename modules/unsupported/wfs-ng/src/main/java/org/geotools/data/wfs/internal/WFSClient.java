package org.geotools.data.wfs.internal;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.geotools.data.ows.AbstractOpenWebService;
import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.Request;
import org.geotools.data.ows.Response;
import org.geotools.data.ows.Specification;
import org.geotools.data.wfs.impl.WFSServiceInfo;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.data.wfs.internal.v1_0.StrictWFS_1_0_Strategy;
import org.geotools.data.wfs.internal.v1_1.ArcGISServerStrategy;
import org.geotools.data.wfs.internal.v1_1.CubeWerxStrategy;
import org.geotools.data.wfs.internal.v1_1.GeoServerPre200Strategy;
import org.geotools.data.wfs.internal.v1_1.IonicStrategy;
import org.geotools.data.wfs.internal.v1_1.StrictWFS_1_1_Strategy;
import org.geotools.data.wfs.internal.v2_0.StrictWFS_2_0_Strategy;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.ows.ServiceException;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WFSClient extends AbstractOpenWebService<WFSGetCapabilities, QName> {

    private static final Logger LOGGER = Logging.getLogger(WFSClient.class);

    private final WFSConfig config;

    public WFSClient(URL capabilitiesURL, HTTPClient httpClient, WFSConfig config)
            throws IOException, ServiceException {
        this(capabilitiesURL, httpClient, config, (WFSGetCapabilities) null);
    }

    public WFSClient(URL capabilitiesURL, HTTPClient httpClient, WFSConfig config,
            WFSGetCapabilities capabilities) throws IOException, ServiceException {

        super(capabilitiesURL, httpClient, capabilities);
        this.config = config;
        super.specification = determineCorrectStrategy();
        ((WFSStrategy) specification).setCapabilities(super.capabilities);
    }

    WFSStrategy getStrategy() {
        return (WFSStrategy) super.specification;
    }

    @Override
    public WFSGetCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public WFSServiceInfo getInfo() {
        return (WFSServiceInfo) super.getInfo();
    }

    @Override
    protected WFSServiceInfo createInfo() {
        return getStrategy().getServiceInfo();
    }

    @Override
    protected FeatureTypeInfo createInfo(QName typeName) {
        return getStrategy().getFeatureTypeInfo(typeName);
    }

    @Override
    protected void setupSpecifications() {
        specs = new Specification[3];
        WFSStrategy strictWFS_1_0_0_Strategy = new StrictWFS_1_0_Strategy();
        WFSStrategy strictWFS_1_1_0_Strategy = new StrictWFS_1_1_Strategy();
        WFSStrategy strictWFS_2_0_0_Strategy = new StrictWFS_2_0_Strategy();

        strictWFS_1_0_0_Strategy.setConfig(config);
        strictWFS_1_1_0_Strategy.setConfig(config);
        strictWFS_2_0_0_Strategy.setConfig(config);

        specs[0] = strictWFS_1_0_0_Strategy;
        specs[1] = strictWFS_1_1_0_Strategy;
        specs[2] = strictWFS_2_0_0_Strategy;

    }

    /**
     * Determine correct WFSStrategy based on capabilities document.
     * 
     * @param getCapabilitiesRequest
     * @param capabilitiesDoc
     * @param override
     *            optional override provided by user
     * @return WFSStrategy to use
     */
    private WFSStrategy determineCorrectStrategy() {

        Version version = new Version(capabilities.getVersion());
        Document capabilitiesDoc = capabilities.getRawDocument();

        final String override = config.getWfsStrategy();

        WFSStrategy strategy = null;
        // override
        if (override != null) {
            if (override.equalsIgnoreCase("geoserver")) {
                strategy = new GeoServerPre200Strategy();
            } else if (override.equalsIgnoreCase("arcgis")) {
                strategy = new ArcGISServerStrategy();
            } else if (override.equalsIgnoreCase("cubewerx")) {
                strategy = new CubeWerxStrategy();
            } else if (override.equalsIgnoreCase("ionic")) {
                strategy = new IonicStrategy();
            } else {
                LOGGER.warning("Could not handle wfs strategy override " + override
                        + " proceeding with autodetection");
            }
        }

        // auto detection
        if (strategy == null) {
            // look in comments for indication of CubeWerx server
            NodeList childNodes = capabilitiesDoc.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (child.getNodeType() == Node.COMMENT_NODE) {
                    String nodeValue = child.getNodeValue();
                    nodeValue = nodeValue.toLowerCase();
                    if (nodeValue.contains("cubewerx")) {
                        strategy = new CubeWerxStrategy();
                        break;
                    }
                }
            }
        }

        if (strategy == null) {
            // Ionic declares its own namespace so that's our hook
            Element root = capabilitiesDoc.getDocumentElement();
            String ionicNs = root.getAttribute("xmlns:ionic");
            if (ionicNs != null) {
                if (ionicNs.equals("http://www.ionicsoft.com/versions/4")) {
                    strategy = new IonicStrategy();
                } else if (ionicNs.startsWith("http://www.ionicsoft.com/versions")) {
                    LOGGER.warning("Found a Ionic server but the version may not match the strategy "
                            + "we have (v.4). Ionic namespace url: " + ionicNs);
                    strategy = new IonicStrategy();
                }
            }
        }

        if (strategy == null) {
            java.net.URL capabilitiesURL = super.serverURL;
            // guess server implementation from capabilities URI
            String uri = capabilitiesURL.toExternalForm();
            if (uri.contains("geoserver")) {
                strategy = new GeoServerPre200Strategy();
            } else if (uri.contains("/ArcGIS/services/")) {
                strategy = new ArcGISServerStrategy();
            }
        }

        if (strategy == null) {
            // use fallback strategy
            if (Versions.v1_0_0.equals(version)) {
                strategy = new StrictWFS_1_0_Strategy();
            } else if (Versions.v1_1_0.equals(version)) {
                strategy = new StrictWFS_1_1_Strategy();
            } else {
                throw new IllegalArgumentException("Unsupported version: " + version);
            }
        }
        LOGGER.info("Using WFS Strategy: " + strategy.getClass().getName());
        return strategy;
    }

    public Set<QName> getRemoteTypeNames() {
        Set<QName> featureTypeNames = getStrategy().getFeatureTypeNames();
        return featureTypeNames;
    }

    public boolean supportsTransaction(String simpleTypeName) {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean canLimit() {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean canFilter() {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean canRetype() {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean canSort() {
        // TODO Auto-generated method stub
        return false;
    }

    public ReferencedEnvelope getBounds(QName typeName, CoordinateReferenceSystem targetCrs) {

        WFSStrategy strategy = getStrategy();
        final FeatureTypeInfo typeInfo = strategy.getFeatureTypeInfo(typeName);
        ReferencedEnvelope nativeBounds = typeInfo.getBounds();

        ReferencedEnvelope bounds;
        try {
            bounds = nativeBounds.transform(targetCrs, true);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Can't transform native bounds of " + typeName + ": " + e.getMessage());
            try {
                bounds = typeInfo.getWGS84BoundingBox().transform(targetCrs, true);
            } catch (Exception ew) {
                LOGGER.log(Level.WARNING,
                        "Can't transform wgs84 bounds of " + typeName + ": " + e.getMessage());
                bounds = new ReferencedEnvelope(targetCrs);
            }
        }
        return bounds;
    }

    public boolean canCount() {
        return getStrategy().supports(ResultType.HITS);
    }

    public GetFeatureRequest createGetFeatureRequest() {
        WFSStrategy strategy = getStrategy();
        return new GetFeatureRequest(config, strategy);
    }

    // public int getCount(QName typeName, Filter filter, Integer maxFeatures) {
    // if (!canCount()) {
    // throw new IllegalStateException("can't count, should check the canCount() method first");
    // }
    //
    // final WFSStrategy strategy = getStrategy();
    // final Filter[] filters = strategy.splitFilters(filter);
    // final Filter postFilter = filters[1];
    // if (!Filter.INCLUDE.equals(postFilter)) {
    // // Filter not fully supported, can't know without a full scan of the results
    // return -1;
    // }
    //
    // final FeatureType contentType = getFeatureType(typeName);
    //
    // WFSResponse response = executeGetFeatures(query, Transaction.AUTO_COMMIT, ResultType.HITS);
    // response.setQueryType(contentType);
    // response.setRemoteTypeName(strategy.getFeatureTypeName(getEntry().getTypeName()));
    //
    // Object process = WFSExtensions.process(response);
    // if (!(process instanceof GetFeatureParser)) {
    // LOGGER.info("GetFeature with resultType=hits resulted in " + process);
    // }
    // int hits = ((GetFeatureParser) process).getNumberOfFeatures();
    // int maxFeatures = getMaxFeatures(query);
    // if (hits != -1 && maxFeatures > 0) {
    // hits = Math.min(hits, maxFeatures);
    // }
    // return hits;
    // }

    // public FeatureType getFeatureType(QName remoteTypeName) {
    //
    // final SimpleFeatureType featureType;
    // CoordinateReferenceSystem crs = getFeatureTypeCRS(remoteTypeName);
    // featureType = client.issueDescribeFeatureTypeGET(remoteTypeName, crs);
    //
    // // adapt the feature type name
    // SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
    // builder.init(featureType);
    // builder.setName(remoteTypeName);
    // String namespaceOverride = entry.getName().getNamespaceURI();
    // if (namespaceOverride != null) {
    // builder.setNamespaceURI(namespaceOverride);
    // }
    // GeometryDescriptor defaultGeometry = featureType.getGeometryDescriptor();
    // if (defaultGeometry != null) {
    // builder.setDefaultGeometry(defaultGeometry.getLocalName());
    // builder.setCRS(defaultGeometry.getCoordinateReferenceSystem());
    // }
    // final SimpleFeatureType adaptedFeatureType = builder.buildFeatureType();
    // return adaptedFeatureType;
    // }

    @Override
    protected Response internalIssueRequest(Request request) throws IOException {
        Response response;
        try {
            response = super.internalIssueRequest(request);
        } catch (ServiceException e) {
            throw new IOException(e);
        }

        if (response instanceof ServiceExceptionReport) {
            ServiceExceptionReport serviceException = (ServiceExceptionReport) response;
            throw new IOException(serviceException.getExceptionMessage());
        }

        return response;
    }

    public TransactionRequest createTransaction() {
        // TODO Auto-generated method stub
        return null;
    }

    public TransactionResponse issueTransaction(TransactionRequest transactionRequest) {
        // TODO Auto-generated method stub
        return null;
    }

    public GetFeatureResponse issueRequest(GetFeatureRequest request) throws IOException {
        Response response = internalIssueRequest(request);
        return (GetFeatureResponse) response;
    }

    public DescribeFeatureTypeRequest createDescribeFeatureTypeRequest() {
        return new DescribeFeatureTypeRequest(config, getStrategy());
    }

    public DescribeFeatureTypeResponse issueRequest(DescribeFeatureTypeRequest request)
            throws IOException {
        Response response = internalIssueRequest(request);
        return (DescribeFeatureTypeResponse) response;
    }

    public CoordinateReferenceSystem getDefaultCRS(QName typeName) {
        final WFSStrategy strategy = getStrategy();
        FeatureTypeInfo typeInfo = strategy.getFeatureTypeInfo(typeName);
        CoordinateReferenceSystem crs = typeInfo.getCRS();
        return crs;
    }
}
