/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2008, Open Source Geospatial Foundation (OSGeo)
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.xml.namespace.QName;

import org.apache.commons.io.IOUtils;
import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.test.TestData;

@SuppressWarnings("nls")
/**
 * 
 *
 * @source $URL$
 */
public class DataTestSupport {

    /**
     * A class holding the type name and test data location for a feature type
     */
    public static class TestDataType {
        /**
         * Location of a test data capabilities file
         */
        final String CAPABILITIES;

        /**
         * Location of a test DescribeFeatureType response for the given feature type
         */
        final String SCHEMA;

        /**
         * The type name, including namespace
         */
        final QName TYPENAME;

        /**
         * The type name as referred in the capabilities (ej, "topp:states")
         */
        final String FEATURETYPENAME;

        /**
         * The FeatureType CRS as declared in the capabilities
         */
        final String CRS;

        /**
         * Location of a sample GetFeature response for this feature type
         */
        final String DATA;

        /**
         * @param folder
         *            the folder name under {@code test-data} where the test files for this feature
         *            type are stored
         * @param qName
         *            the qualified type name (ns + local name)
         * @param featureTypeName
         *            the name as stated in the capabilities
         * @param crs
         *            the default feature type CRS as stated in the capabilities
         */
        TestDataType(final String folder, final QName qName, final String featureTypeName,
                final String crs) {
            TYPENAME = qName;
            FEATURETYPENAME = featureTypeName;
            CRS = crs;
            CAPABILITIES = folder + "/GetCapabilities_1_1_0.xml";
            SCHEMA = folder + "/DescribeFeatureType_" + qName.getLocalPart() + ".xsd";
            DATA = folder + "/GetFeature_" + qName.getLocalPart() + ".xml";

            checkResource(CAPABILITIES);
            checkResource(SCHEMA);
            checkResource(DATA);
        }

        private void checkResource(String resource) {
            try {
                TestData.url(this, resource);
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

    }

    public static final TestDataType GEOS_ARCHSITES = new TestDataType("geoserver", new QName(
            "http://www.openplans.org/spearfish", "archsites"), "sf:archsites", "EPSG:26713");

    public static final TestDataType GEOS_POI = new TestDataType("geoserver", new QName(
            "http://www.census.gov", "poi"), "tiger:poi", "EPSG:4326");

    public static final TestDataType GEOS_ROADS = new TestDataType("geoserver", new QName(
            "http://www.openplans.org/spearfish", "roads"), "sf:roads", "EPSG:26713");

    public static final TestDataType GEOS_STATES = new TestDataType("geoserver", new QName(
            "http://www.openplans.org/topp", "states"), "topp:states", "EPSG:4326");

    public static final TestDataType GEOS_TASMANIA_CITIES = new TestDataType("geoserver",
            new QName("http://www.openplans.org/topp", "tasmania_cities"), "topp:tasmania_cities",
            "EPSG:4326");

    public static final TestDataType GEOS_TIGER_ROADS = new TestDataType("geoserver", new QName(
            "http://www.census.gov", "tiger_roads"), "tiger:tiger_roads", "EPSG:4326");

    public static final TestDataType CUBEWERX_GOVUNITCE = new TestDataType("CubeWerx_nsdi",
            new QName("http://www.fgdc.gov/framework/073004/gubs", "GovernmentalUnitCE"),
            "gubs:GovernmentalUnitCE", "EPSG:4269");

    public static final TestDataType CUBEWERX_ROADSEG = new TestDataType("CubeWerx_nsdi",
            new QName("http://www.fgdc.gov/framework/073004/transportation", "RoadSeg"),
            "trans:RoadSeg", "EPSG:4269");

    public static final TestDataType IONIC_STATISTICAL_UNIT = new TestDataType("Ionic", new QName(
            "http://www.fgdc.gov/fgdc/gubs", "StatisticalUnit"), "gubs:StatisticalUnit",
            "EPSG:4269");

    /**
     * Creates the test {@link #wfs} with a default connection factory that parses the capabilities
     * object from the test xml file pointed out by {@code capabilitiesFileName}
     * <p>
     * Tests methods call this one to set up a protocolHandler to test
     * </p>
     * 
     * @param capabilitiesFileName
     *            the relative path under {@code test-data} for the file containing the
     *            WFS_Capabilities document.
     * @throws IOException
     */
    public <T extends WFSStrategy> T createTestProtocol(String capabilitiesFileName, T real)
            throws IOException {
        HTTPClient http = new SimpleHttpClient();
        return createTestProtocol(capabilitiesFileName, http, real);
    }

    /**
     * Creates the test {@link #wfs} with the provided connection factory that parses the
     * capabilities object from the test xml file pointed out by {@code capabilitiesFileName}
     * <p>
     * Tests methods call this one to set up a protocolHandler to test
     * </p>
     * 
     * @param capabilitiesFileName
     *            the relative path under {@code test-data} for the file containing the
     *            WFS_Capabilities document.
     * @throws IOException
     */
    public <T extends WFSStrategy> T createTestProtocol(String capabilitiesFileName,
            HTTPClient http, T real) throws IOException {

        InputStream stream = TestData.openStream(DataTestSupport.class, capabilitiesFileName);
        real.setHttpClient(http);
        real.initFromCapabilities(stream);
        return real;
    }

    public static class TestHTTPClient extends SimpleHttpClient {

        private HTTPResponse mockResponse;

        public URL targetUrl;

        public String postCallbackContentType;

        public long postCallbackContentLength = -1;

        public ByteArrayOutputStream postCallbackEncodedRequestBody;

        public TestHTTPClient(HTTPResponse mockResponse) {
            this.mockResponse = mockResponse;
        }

        @Override
        public HTTPResponse get(final URL baseUrl) throws IOException {
            this.targetUrl = baseUrl;
            return mockResponse;
        }

        @Override
        public HTTPResponse post(final URL url, final InputStream postContent,
                final String postContentType) throws IOException {
            this.targetUrl = url;
            this.postCallbackContentType = postContentType;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IOUtils.copy(postContent, out);
            this.postCallbackEncodedRequestBody = out;
            this.postCallbackContentLength = out.size();
            return mockResponse;
        }
    }

    public static class TestHttpResponse implements HTTPResponse {

        private String contentType;

        private String charset;

        private String bodyContent;

        public TestHttpResponse(String contentType, String charset, String bodyContent) {
            this.contentType = contentType;
            this.charset = charset;
            this.bodyContent = bodyContent;
        }

        public TestHttpResponse(String contentType, String charset, InputStream contentInputStream) {
            this.contentType = contentType;
            this.charset = charset;
            BufferedReader reader = new BufferedReader(new InputStreamReader(contentInputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append('\n');
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.bodyContent = sb.toString();
        }

        @Override
        public void dispose() {
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getResponseHeader(String headerName) {
            if ("charset".equalsIgnoreCase(headerName)) {
                return charset;
            }
            return null;
        }

        @Override
        public InputStream getResponseStream() throws IOException {
            return new ByteArrayInputStream(this.bodyContent.getBytes());
        }
    }

}
