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
package org.geotools.data.wfs.internal;

import static org.geotools.data.wfs.internal.HttpMethod.GET;
import static org.geotools.data.wfs.internal.HttpMethod.POST;
import static org.geotools.data.wfs.internal.Loggers.requestTrace;
import static org.geotools.data.wfs.internal.WFSOperationType.DESCRIBE_FEATURETYPE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import org.eclipse.emf.ecore.EObject;
import org.geotools.filter.Capabilities;
import org.geotools.filter.visitor.CapabilitiesFilterSplitter;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.util.Version;
import org.geotools.xml.Configuration;
import org.geotools.xml.Encoder;
import org.geotools.xml.XMLHandlerHints;
import org.geotools.xml.filter.FilterCompliancePreProcessor;
import org.opengis.filter.Filter;
import org.opengis.filter.Id;
import org.opengis.filter.capability.FilterCapabilities;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;

/**
 * {@link WFSStrategy} implementation to talk to a WFS 1.1.0 server leveraging the GeoTools
 * {@code xml-xsd} subsystem for schema assisted parsing and encoding of WFS requests and responses.
 * <p>
 * Additional extension hooks:
 * <ul>
 * <li> {@link #supportsGet()}
 * <li> {@link #supportsPost()}
 * <li> {@link #buildGetFeatureRequest}
 * <li> {@link #buildGetFeatureParametersForGet}
 * <li> {@link #encodeGetFeatureGetFilter}
 * </ul>
 * </p>
 * 
 * @author groldan
 */
public abstract class AbstractWFSStrategy extends WFSStrategy {

    protected static final Logger LOGGER = Loggers.MODULE;

    public static final Configuration FILTER_1_0_CONFIGURATION = new org.geotools.filter.v1_0.OGCConfiguration();

    public static final Configuration WFS_1_0_CAPABILITIES_CONFIGURATION = new org.geotools.wfs.v1_0.WFSCapabilitiesConfiguration();

    public static final Configuration WFS_1_0_CONFIGURATION = new org.geotools.wfs.v1_0.WFSConfiguration();

    public static final Configuration FILTER_1_1_CONFIGURATION = new org.geotools.filter.v1_1.OGCConfiguration();

    public static final Configuration WFS_1_1_CONFIGURATION = new org.geotools.wfs.v1_1.WFSConfiguration();

    public static final Configuration FILTER_2_0_CONFIGURATION = new org.geotools.filter.v2_0.FESConfiguration();

    public static final Configuration WFS_2_0_CONFIGURATION = new org.geotools.wfs.v2_0.WFSConfiguration();

    protected WFSConfig config;

    public AbstractWFSStrategy() {
        this.config = new WFSConfig();
    }

    /*
     * org.geotools.data.ows.Specification methods
     */

    @Override
    public String getVersion() {
        return getServiceVersion().toString();
    }

    /**
     * Factory method to create GetCapabilities Request
     * 
     * @param server
     *            the URL that points to the server's getCapabilities document
     * @return a configured GetCapabilitiesRequest that can be used to access the Document
     */
    @Override
    public GetCapabilitiesRequest createGetCapabilitiesRequest(URL server) {
        return new GetCapabilitiesRequest(server);
    }

    /*
     * This class' extension points
     */

    protected abstract QName getOperationName(WFSOperationType operation);

    protected abstract EObject createGetFeatureRequestPost(GetFeatureRequest query)
            throws IOException;

    protected abstract EObject createDescribeFeatureTypeRequestPost(
            DescribeFeatureTypeRequest request);

    /*
     * WFSStrategy methods
     */
    @Override
    public void setConfig(WFSConfig config) {
        this.config = config;
    }

    @Override
    public WFSConfig getConfig() {
        return config;
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

    protected Map<String, String> buildGetFeatureParametersForGet(GetFeatureRequest request) {

        Map<String, String> map = new HashMap<String, String>();
        map.put("SERVICE", "WFS");
        Version serviceVersion = getServiceVersion();
        map.put("VERSION", serviceVersion.toString());
        map.put("REQUEST", "GetFeature");
        String outputFormat = request.getOutputFormat();
        if (outputFormat == null) {
            outputFormat = getDefaultOutputFormat(WFSOperationType.GET_FEATURE);
        }
        map.put("OUTPUTFORMAT", outputFormat);

        if (request.getMaxFeatures() != null) {
            map.put("MAXFEATURES", String.valueOf(request.getMaxFeatures()));
        }

        QName typeName = request.getTypeName();
        String queryTypeName = getPrefixedTypeName(typeName);
        map.put("TYPENAME", queryTypeName);

        if (request.getPropertyNames() != null && request.getPropertyNames().length > 0) {
            List<String> propertyNames = Arrays.asList(request.getPropertyNames());
            StringBuilder pnames = new StringBuilder();
            for (Iterator<String> it = propertyNames.iterator(); it.hasNext();) {
                pnames.append(it.next());
                if (it.hasNext()) {
                    pnames.append(',');
                }
            }
            map.put("PROPERTYNAME", pnames.toString());
        }

        final String srsName = request.getSrsName();
        if (srsName != null) {
            final Set<String> supportedCRSIdentifiers = getSupportedCRSIdentifiers(typeName);
            if (supportedCRSIdentifiers.contains(srsName)) {
                map.put("SRSNAME", srsName.toString());
            }
        }

        final Filter supportedFilter;
        final Filter unsupportedFilter;
        {
            final Filter filter = request.getFilter();
            Filter[] splitFilters = splitFilters(typeName, filter);
            supportedFilter = splitFilters[0];
            unsupportedFilter = splitFilters[1];
        }

        request.setUnsupportedFilter(unsupportedFilter);

        if (supportedFilter instanceof Id) {
            final Set<Identifier> identifiers = ((Id) supportedFilter).getIdentifiers();
            StringBuffer idValues = new StringBuffer();
            for (Iterator<Identifier> it = identifiers.iterator(); it.hasNext();) {
                Object id = it.next().getID();
                if (id instanceof FeatureId) {
                    idValues.append(((FeatureId) id).getRid());
                } else {
                    idValues.append(String.valueOf(id));
                }
                if (it.hasNext()) {
                    idValues.append(",");
                }
            }
            map.put("FEATUREID", idValues.toString());
        } else if (Filter.INCLUDE != supportedFilter) {
            String xmlEncodedFilter;
            try {
                xmlEncodedFilter = encodeGetFeatureGetFilter(supportedFilter);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            map.put("FILTER", xmlEncodedFilter);
        }

        return map;
    }

    /**
     * Returns a single-line string containing the xml representation of the given filter, as
     * appropriate for the {@code FILTER} parameter in a GetFeature request.
     */
    protected String encodeGetFeatureGetFilter(final Filter filter) throws IOException {

        final Configuration filterConfig = getFilterConfiguration();
        final QName encName;

        if (filterConfig instanceof org.geotools.filter.v1_0.OGCConfiguration
                || filterConfig instanceof org.geotools.filter.v1_1.OGCConfiguration) {
            encName = org.geotools.filter.v1_0.OGC.Filter;
        } else {
            encName = org.geotools.filter.v2_0.FES.Filter;
        }

        Encoder encoder = new Encoder(filterConfig);
        // do not write the xml declaration
        encoder.setOmitXMLDeclaration(true);
        encoder.setEncoding(Charset.forName("UTF-8"));

        String encoded = encoder.encodeAsString(filter, encName);

        encoded = encoded.replaceAll("\n", "");
        return encoded;
    }

    /*---------------------------------------------------------------------
     * WFSStrategy methods
     * ---------------------------------------------------------------------*/

    /**
     * @see WFSStrategy#getServiceVersion()
     */
    @Override
    public abstract Version getServiceVersion();

    protected String getPrefixedTypeName(QName qname) {
        String prefix = qname.getPrefix();
        String simpleName;
        if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
            simpleName = qname.getLocalPart();
        } else {
            simpleName = prefix + ":" + qname.getLocalPart();
        }
        return simpleName;
    }

    /**
     * @see WFSStrategy#supportsOperation
     */
    @Override
    public boolean supportsOperation(WFSOperationType operation, HttpMethod method) {
        if (POST == method && !supportsPost()) {
            return false;
        }
        if (GET == method && !supportsGet()) {
            return false;
        }

        return null != getOperationURI(operation, method);
    }

    /**
     * @see WFSStrategy#getOperationURL
     */
    @Override
    public URL getOperationURL(WFSOperationType operation, HttpMethod method) {
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

    protected abstract String getOperationURI(WFSOperationType operation, HttpMethod method);

    @Override
    public String getDefaultOutputFormat(WFSOperationType operation) {
        List<String> clientSupportedFormats = getClientSupportedOutputFormats(operation);
        Set<String> supportedOutputFormats = getServerSupportedOutputFormats(operation);
        for (String clientSupported : clientSupportedFormats) {
            if (supportedOutputFormats.contains(clientSupported)) {
                return clientSupported;
            }
        }

        throw new IllegalArgumentException(
                "Client does not support any of the server supported output formats for "
                        + operation);

    }

    // /**
    // * @see WFSStrategy#getFeaturePOST(Query, String)
    // */
    // @Override
    // public WFSResponse issueGetFeaturePOST(final GetFeatureRequest request) throws IOException {
    // if (!supportsOperation(WFSOperationType.GET_FEATURE, true)) {
    // throw new UnsupportedOperationException(
    // "The server does not support GetFeature for HTTP method POST");
    // }
    // URL url = getOperationURL(WFSOperationType.GET_FEATURE, true);
    //
    // RequestComponents reqParts = createGetFeatureRequest(request);
    // GetFeatureType serverRequest = reqParts.getServerRequest();
    //
    // Encoder encoder = new Encoder(getWfsConfiguration());
    //
    // // If the typeName is of the form prefix:typeName we better declare the namespace since we
    // // don't know how picky the server parser will be
    // String typeName = reqParts.getKvpParameters().get("TYPENAME");
    // QName fullName = getFeatureTypeName(typeName);
    // String prefix = fullName.getPrefix();
    // String namespace = fullName.getNamespaceURI();
    // if (!XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
    // encoder.getNamespaces().declarePrefix(prefix, namespace);
    // }
    // WFSResponse response = issuePostRequest(serverRequest, url, encoder);
    //
    // return response;
    // }

    /**
     * @see WFSStrategy#dispose()
     */
    @Override
    public void dispose() {
        // do nothing
    }

    protected Map<String, String> buildDescribeFeatureTypeURLGet(
            final DescribeFeatureTypeRequest request) {

        final QName typeName = request.getTypeName();

        // final String outputFormat = getDefaultOutputFormat(DESCRIBE_FEATURETYPE);
        try {
            getFeatureTypeInfo(typeName);
        } catch (RuntimeException e) {
            throw e;
        }

        Map<String, String> kvp = new HashMap<String, String>();
        kvp.put("SERVICE", "WFS");
        kvp.put("VERSION", getServiceVersion().toString());
        kvp.put("REQUEST", "DescribeFeatureType");
        String prefixedTypeName = getPrefixedTypeName(typeName);
        kvp.put("TYPENAME", prefixedTypeName);

        if (!XMLConstants.DEFAULT_NS_PREFIX.equals(typeName.getPrefix())) {
            String nsUri = typeName.getNamespaceURI();
            kvp.put("NAMESPACE", "xmlns(" + typeName.getPrefix() + "=" + nsUri + ")");
        }

        // ommit output format by now, server should just return xml shcema
        // kvp.put("OUTPUTFORMAT", outputFormat);

        return kvp;
    }

    // private WFSResponse issueGetRequest(EObject request, URL baseUrl, Map<String, String> kvp)
    // throws IOException {
    // WFSResponse response;
    // URL url = URIs.buildURL(baseUrl, kvp);
    // HTTPResponse httpResponse = http.get(url);
    //
    // String responseCharset = httpResponse.getResponseHeader("charset");
    // Charset charset = responseCharset == null ? null : Charset.forName(responseCharset);
    // String contentType = httpResponse.getContentType();
    // InputStream responseStream = httpResponse.getResponseStream();
    // String target = url.toExternalForm();
    // response = new WFSResponse(target, request, charset, contentType, responseStream);
    // return response;
    // }

    // private WFSResponse issuePostRequest(final EObject request, final URL url, final Encoder
    // encoder)
    // throws IOException {
    //
    // final Charset requestCharset = config.getDefaultEncoding();
    // encoder.setEncoding(requestCharset);
    // ByteArrayOutputStream out = new ByteArrayOutputStream();
    // if (LOGGER.isLoggable(Level.FINEST)) {
    // LOGGER.finest("Sending POST request: ");
    // LOGGER.finest(out.toString(requestCharset.name()));
    // }
    // AbstractWFSStrategy.encode(request, encoder, out);
    // InputStream postContent = new ByteArrayInputStream(out.toByteArray());
    // HTTPResponse httpResponse = http.post(url, postContent, "text/xml");
    //
    // String responseCharset = httpResponse.getResponseHeader("charset");
    // Charset charset = responseCharset == null ? null : Charset.forName(responseCharset);
    // String contentType = httpResponse.getContentType();
    // InputStream responseStream = httpResponse.getResponseStream();
    // String target = url.toExternalForm();
    // WFSResponse response = new WFSResponse(target, request, charset, contentType,
    // responseStream);
    // return response;
    // }

    // /**
    // * Returns the operation URI for the given operation/http method as a String to avoid creating
    // a
    // * URL instance when not needed
    // */
    // @SuppressWarnings("unchecked")
    // private String getOperationURI(WFSOperationType operation, HttpMethod method) {
    // final OperationType operationType = getOperationMetadata(operation);
    // final List<DCPType> dcps = operationType.getDCP();
    // for (DCPType dcp : dcps) {
    // List<RequestMethodType> requests;
    // if (GET == method) {
    // requests = dcp.getHTTP().getGet();
    // } else {
    // requests = dcp.getHTTP().getPost();
    // }
    // for (RequestMethodType req : requests) {
    // String href = req.getHref();
    // return href;
    // }
    // }
    // return null;
    // }

    /**
     * Encodes a WFS request into {@code out}
     * 
     * @throws IOException
     */
    public void encode(final WFSRequest request, final EObject requestObject, final OutputStream out)
            throws IOException {

        final Configuration configuration = getWfsConfiguration();
        Charset charset = getConfig().getDefaultEncoding();
        if (null == charset) {
            charset = Charset.forName("UTF-8");
        }
        Encoder encoder = new Encoder(configuration);
        encoder.setEncoding(charset);
        encoder.setIndentSize(1);

        final QName opName = getOperationName(request.getOperation());

        QName typeName = request.getTypeName();
        if (typeName != null && !XMLConstants.NULL_NS_URI.equals(typeName.getNamespaceURI())) {
            String prefix = typeName.getPrefix();
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
                prefix = "type_ns";
            }
            String namespaceURI = typeName.getNamespaceURI();
            encoder.getNamespaces().declarePrefix(prefix, namespaceURI);
        }

        encoder.encode(requestObject, opName, out);
    }

    //
    // private static QName getElementName(EObject originatingRequest) {
    // QName encodeElementName;
    // if (originatingRequest instanceof GetCapabilitiesType) {
    // encodeElementName = WFS.GetCapabilities;
    // } else if (originatingRequest instanceof GetFeatureType) {
    // encodeElementName = WFS.GetFeature;
    // } else if (originatingRequest instanceof DescribeFeatureTypeType) {
    // encodeElementName = WFS.DescribeFeatureType;
    // } else if (originatingRequest instanceof GetCapabilitiesType) {
    // encodeElementName = WFS.GetCapabilities;
    // } else if (originatingRequest instanceof GetGmlObjectType) {
    // encodeElementName = WFS.GetGmlObject;
    // } else if (originatingRequest instanceof LockFeatureType) {
    // encodeElementName = WFS.LockFeature;
    // } else if (originatingRequest instanceof TransactionType) {
    // encodeElementName = WFS.Transaction;
    // } else {
    // throw new IllegalArgumentException("Unkown xml element name for " + originatingRequest);
    // }
    // return encodeElementName;
    // }

    /**
     * Splits the filter provided by the geotools query into the server supported and unsupported
     * ones.
     * 
     * @param typeName
     * 
     * @return a two-element array where the first element is the supported filter and the second
     *         the one to post-process
     * @see org.geotools.data.wfs.internal.WFSStrategy#splitFilters(org.opengis.filter.Filter)
     */
    protected Filter[] splitFilters(QName typeName, Filter filter) {

        final Set<String> supportedCRSIdentifiers = getSupportedCRSIdentifiers(typeName);

        FilterCapabilities filterCapabilities = getFilterCapabilities();
        Capabilities filterCaps = new Capabilities();
        if (filterCapabilities != null) {
            filterCaps.addAll(filterCapabilities);
        }
        filter = simplify(filter);

        Filter server;
        Filter post;

        Integer complianceLevel = getConfig().getFilterCompliance();
        if (null == complianceLevel) {
            complianceLevel = XMLHandlerHints.VALUE_FILTER_COMPLIANCE_HIGH;
        }
        // TODO: modify FilterCompliancePreProcessor so that it preservers original FeatureID
        // instead re creating them from the FeautreId.getID()
        FilterCompliancePreProcessor compliancePreProcessor = new FilterCompliancePreProcessor(
                complianceLevel);
        filter.accept(compliancePreProcessor, null);

        filter = compliancePreProcessor.getFilter();
        Id fidFilter = compliancePreProcessor.getFidFilter();
        if (!fidFilter.getIdentifiers().isEmpty()) {
            server = fidFilter;
            post = filter;
        } else {

            CapabilitiesFilterSplitter splitter = new CapabilitiesFilterSplitter(filterCaps, null,
                    null);

            filter.accept(splitter, null);
            server = splitter.getFilterPre();
            post = splitter.getFilterPost();

        }
        return new Filter[] { server, post };
    }

    protected Filter simplify(Filter filter) {
        if (Filter.INCLUDE.equals(filter) || Filter.EXCLUDE.equals(filter)) {
            return filter;
        }

        SimplifyingFilterVisitor simplifier = new SimplifyingFilterVisitor();
        filter = (Filter) filter.accept(simplifier, null);
        return filter;
    }

    // /**
    // * @see
    // org.geotools.data.wfs.internal.WFSStrategy#issueDescribeFeatureTypeGET(java.lang.String,
    // * org.opengis.referencing.crs.CoordinateReferenceSystem)
    // */
    // @Override
    // public SimpleFeatureType issueDescribeFeatureTypeGET(final String prefixedTypeName,
    // CoordinateReferenceSystem crs) throws IOException {
    //
    // File tmpFile = null;
    // final URL describeUrl;
    // {
    // final boolean isAuthenticating = http.getUser() != null;
    // if (isAuthenticating) {
    // WFSResponse wfsResponse = describeFeatureTypeGET(prefixedTypeName);
    // tmpFile = File.createTempFile("describeft", ".xsd");
    // OutputStream output = new FileOutputStream(tmpFile);
    // InputStream response = wfsResponse.getInputStream();
    // try {
    // IOUtils.copy(response, output);
    // } finally {
    // output.flush();
    // output.close();
    // response.close();
    // }
    // describeUrl = tmpFile.toURI().toURL();
    // } else {
    // describeUrl = buildDescribeFeatureTypeURLGet(prefixedTypeName);
    // }
    // }
    //
    // final Configuration wfsConfiguration = getWfsConfiguration();
    // final QName featureDescriptorName = getFeatureTypeName(prefixedTypeName);
    //
    // SimpleFeatureType featureType;
    // try {
    // featureType = EmfAppSchemaParser.parseSimpleFeatureType(wfsConfiguration,
    // featureDescriptorName, describeUrl, crs);
    // } finally {
    // if (tmpFile != null) {
    // tmpFile.delete();
    // }
    // }
    // return featureType;
    // }

    // @Override
    // public TransactionResponse issueTransaction(final TransactionRequest transactionRequest)
    // throws IOException {
    //
    // if (!supportsOperation(WFSOperationType.TRANSACTION, true)) {
    // throw new IOException("WFS does not support transactions");
    // }
    //
    // TransactionType transaction = unwrapTransaction(transactionRequest);
    //
    // // used to declare prefixes on the encoder later
    // Set<QName> affectedTypes = new HashSet<QName>();
    // for (TransactionElement e : transactionRequest.getTransactionElements()) {
    // String localTypeName = e.getLocalTypeName();
    // QName remoteTypeName = getFeatureTypeName(localTypeName);
    // affectedTypes.add(remoteTypeName);
    // }
    //
    // final Encoder encoder = new Encoder(getWfsConfiguration());
    // // declare prefixes
    // for (QName remoteType : affectedTypes) {
    // if (XMLConstants.DEFAULT_NS_PREFIX.equals(remoteType.getPrefix())) {
    // continue;
    // }
    // encoder.getNamespaces().declarePrefix(remoteType.getPrefix(),
    // remoteType.getNamespaceURI());
    // }
    // encoder.setIndenting(true);
    // encoder.setIndentSize(2);
    //
    // final URL operationURL = getOperationURL(WFSOperationType.TRANSACTION, true);
    // final WFSResponse wfsResponse = issuePostRequest(transaction, operationURL, encoder);
    // return toTransactionResult(wfsResponse);
    // }
    //
    // private TransactionResponse toTransactionResult(WFSResponse wfsResponse) {
    // return null;
    // }

    @Override
    public URL buildUrlGET(WFSRequest request) {
        final WFSOperationType operation = request.getOperation();

        Map<String, String> requestParams;

        switch (operation) {
        case GET_FEATURE:
            requestParams = buildGetFeatureParametersForGet((GetFeatureRequest) request);
            break;
        case DESCRIBE_FEATURETYPE:
            requestParams = buildDescribeFeatureTypeURLGet((DescribeFeatureTypeRequest) request);
            break;
        default:
            throw new UnsupportedOperationException();
        }

        URL baseUrl = getOperationURL(operation, GET);

        URL finalURL = URIs.buildURL(baseUrl, requestParams);

        return finalURL;
    }

    @Override
    public String getPostContentType(WFSRequest wfsRequest) {

        final WFSOperationType operation = wfsRequest.getOperation();

        if (DESCRIBE_FEATURETYPE.equals(operation)) {
            return "text/xml";
        }

        String defaultOutputFormat = getDefaultOutputFormat(operation);

        return defaultOutputFormat;
    }

    @Override
    public InputStream getPostContents(WFSRequest request) throws IOException {
        EObject requestObject;

        switch (request.getOperation()) {
        case GET_FEATURE:
            requestObject = createGetFeatureRequestPost((GetFeatureRequest) request);
            break;
        case DESCRIBE_FEATURETYPE:
            requestObject = createDescribeFeatureTypeRequestPost((DescribeFeatureTypeRequest) request);
            break;
        default:
            throw new UnsupportedOperationException("not yet implemented for "
                    + request.getOperation());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        encode(request, requestObject, out);
        requestTrace("Encoded ", request.getOperation(), " request: ", out);

        return new ByteArrayInputStream(out.toByteArray());

    }
}
