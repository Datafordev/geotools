package org.geotools.data.wfs.integration;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.ows.Request;
import org.geotools.data.ows.Response;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.impl.TestHttpResponse;
import org.geotools.data.wfs.internal.AbstractWFSStrategy;
import org.geotools.data.wfs.internal.DescribeFeatureTypeRequest;
import org.geotools.data.wfs.internal.DescribeFeatureTypeResponse;
import org.geotools.data.wfs.internal.GetCapabilitiesRequest;
import org.geotools.data.wfs.internal.GetCapabilitiesResponse;
import org.geotools.data.wfs.internal.GetFeatureParser;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.data.wfs.internal.GetFeatureResponse;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSResponse;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.ows.ServiceException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;
import com.vividsolutions.jts.geom.GeometryFactory;

public class IntegrationTestWFSClient extends WFSClient {

    private URL baseDirectory;

    public IntegrationTestWFSClient(String baseDirectory, WFSConfig config)
            throws ServiceException, IOException {

        super(url(baseDirectory + "/GetCapabilities.xml"), new SimpleHttpClient(), config);

        this.baseDirectory = url(baseDirectory);
    }

    private static URL url(String resource) {

        String absoluteResouce = "/org/geotools/data/wfs/integration/test-data/" + resource;

        URL url = IntegrationTestWFSClient.class.getResource(absoluteResouce);

        return url;
    }

    @Override
    protected Response internalIssueRequest(Request request) throws IOException {
        try {
            if (request instanceof GetCapabilitiesRequest) {
                return mockCapabilities();
            }
            if (request instanceof DescribeFeatureTypeRequest) {
                return mockDFT((DescribeFeatureTypeRequest) request);
            }
            if (request instanceof GetFeatureRequest) {
                return mockGetFeature((GetFeatureRequest) request);
            }
        } catch (ServiceException e) {
            throw new IOException(e.getCause());
        }

        throw new IllegalArgumentException("Unknown request : " + request);
    }

    private Response mockCapabilities() throws IOException, ServiceException {
        HTTPResponse httpResp = new TestHttpResponse("text/xml", "UTF-8", super.serverURL);

        return new GetCapabilitiesResponse(httpResp);
    }

    private Response mockDFT(DescribeFeatureTypeRequest request) throws ServiceException,
            IOException {

        QName typeName = request.getTypeName();
        String simpleName = typeName.getPrefix() + "_" + typeName.getLocalPart();

        String resource = "DescribeFeatureType_" + simpleName + ".xsd";
        URL contentUrl = new URL(baseDirectory, resource);

        String outputFormat = request.getOutputFormat();

        HTTPResponse response = new TestHttpResponse(outputFormat, "UTF-8", contentUrl);
        return new DescribeFeatureTypeResponse(request, response);
    }

    private Response mockGetFeature(GetFeatureRequest request) throws IOException {

        QName typeName = request.getTypeName();
        String simpleName = typeName.getPrefix() + "_" + typeName.getLocalPart();

        String resource = "GetFeature_" + simpleName + ".xml";
        URL contentUrl = new URL(baseDirectory, resource);

        String outputFormat = request.getOutputFormat();

        HTTPResponse httpResponse = new TestHttpResponse(outputFormat, "UTF-8", contentUrl);

        WFSResponse response = request.createResponse(httpResponse);

        if (!(response instanceof GetFeatureResponse)) {
            return response;
        }

        final GetFeatureResponse gfr = (GetFeatureResponse) response;
        WFSStrategy strategy = getStrategy();

        Filter filter = request.getFilter();
        Filter[] split = ((AbstractWFSStrategy) strategy).splitFilters(typeName, filter);
        final Filter serverFiler = split[0];

        final GetFeatureParser allFeatures = gfr.getFeatures();
        final List<SimpleFeature> serverFiltered = new ArrayList<SimpleFeature>();
        {
            SimpleFeature feature;
            while ((feature = allFeatures.parse()) != null) {
                if (serverFiler.evaluate(feature)) {
                    serverFiltered.add(feature);
                }
            }
        }
        final GetFeatureParser filteredParser = new GetFeatureParser() {

            private Iterator<SimpleFeature> it = serverFiltered.iterator();

            @Override
            public void setGeometryFactory(GeometryFactory geometryFactory) {
                // TODO Auto-generated method stub
            }

            @Override
            public SimpleFeature parse() throws IOException {
                if (!it.hasNext()) {
                    return null;
                }
                return it.next();
            }

            @Override
            public int getNumberOfFeatures() {
                if (-1 != allFeatures.getNumberOfFeatures()) {
                    // only if the original response included number of features (i.e. the server
                    // does advertise it)
                    return serverFiltered.size();
                }
                return -1;
            }

            @Override
            public FeatureType getFeatureType() {
                return allFeatures.getFeatureType();
            }

            @Override
            public void close() throws IOException {
                //
            }
        };

        try {
            return new GetFeatureResponse(request, httpResponse, filteredParser);
        } catch (ServiceException e) {
            throw new IOException(e);
        }
    }
}
