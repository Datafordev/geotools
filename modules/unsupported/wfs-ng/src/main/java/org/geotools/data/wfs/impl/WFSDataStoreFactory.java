/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2004-2008, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.data.wfs.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.geotools.data.AbstractDataStoreFactory;
import org.geotools.data.DataSourceException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.Parameter;
import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.internal.URIs;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.ows.ServiceException;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.geotools.xml.XMLHandlerHints;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * A {@link DataStoreFactorySpi} to connect to a Web Feature Service.
 * <p>
 * Produces a {@link WFSDataStore} is the correct set of connection parameters are provided. For
 * instance, the only mandatory one is {@link #URL}.
 * </p>
 * <p>
 * As with all the DataStoreFactorySpi implementations, this one is not intended to be used directly
 * but through the {@link DataStoreFinder} mechanism, so client application should not have strong
 * dependencies over this module.
 * </p>
 * <p>
 * Upon a valid URL to a WFS GetCapabilities document, this factory will perform version negotiation
 * between the server supported protocol versions and this plugin supported ones, and will return a
 * {@link DataStore} capable of communicating with the server using the agreed WFS protocol version.
 * </p>
 * <p>
 * In the case the provided GetCapabilities URL explicitly contains a VERSION parameter and both the
 * server and client support that version, that version will be used.
 * </p>
 * <p>
 * That said, for the time being, the current default version is {@code 1.0.0} instead of
 * {@code 1.1.0}, since the former is the one that supports transactions. When further development
 * provides transaction support for the WFS 1.1.0 version, propper version negotiation capabilities
 * will be added.
 * </p>
 * <p>
 * Among feeding the wfs datastore with a {@link WFSStrategy} that can handle the WFS version agreed
 * upong the server and this client, this factory will try to provide the datastore with a
 * {@link WFSStrategy} appropriate for the WFS implementation, if that could be somehow guessed.
 * That is so the datastore itself nor the protocol need to worry about any implementation specific
 * limitation or deviation from the standard the actual server may have.
 * </p>
 * 
 * @author dzwiers
 * @author Gabriel Roldan (TOPP)
 * 
 * 
 * 
 * @source $URL$
 *         http://svn.geotools.org/geotools/trunk/gt/modules/plugin/wfs/src/main/java/org/geotools
 *         /data/wfs/WFSDataStoreFactory.java $
 * @see WFSDataStore
 * @see WFSStrategy
 */
@SuppressWarnings({ "unchecked", "nls" })
public class WFSDataStoreFactory extends AbstractDataStoreFactory {
    private static final Logger logger = Logging.getLogger("org.geotools.data.wfs");

    /**
     * A {@link Param} subclass that allows to provide a default value to the lookUp method.
     * 
     * @author Gabriel Roldan
     * @version $Id$
     * @since 2.5.x
     * @source $URL:
     *         http://svn.geotools.org/geotools/trunk/gt/modules/plugin/wfs/src/main/java/org/geotools
     *         /data/wfs/WFSDataStoreFactory.java $
     */
    public static class WFSFactoryParam<T> extends Param {
        private T defaultValue;

        /**
         * Creates a required parameter
         * 
         * @param key
         * @param type
         * @param description
         */
        public WFSFactoryParam(String key, Class<T> type, String description) {
            super(key, type, description, true);
        }

        /**
         * Creates an optional parameter with the supplied default value
         * 
         * @param key
         * @param type
         * @param description
         * @param required
         */
        public WFSFactoryParam(String key, Class<T> type, String description, T defaultValue) {
            super(key, type, description, false, defaultValue);
            this.defaultValue = defaultValue;
        }

        public WFSFactoryParam(String key, Class<T> type, String description, T defaultValue,
                Object... metadata) {
            super(key, type, description, false, defaultValue, metadata);
            this.defaultValue = defaultValue;
        }

        public T lookUp(final Map params) throws IOException {
            T parameter = (T) super.lookUp(params);
            return parameter == null ? defaultValue : parameter;
        }
    }

    /** Access with {@link WFSDataStoreFactory#getParametersInfo()  */
    private static final WFSFactoryParam<?>[] parametersInfo = new WFSFactoryParam[13];

    /**
     * Mandatory DataStore parameter indicating the URL for the WFS GetCapabilities document.
     */
    public static final WFSFactoryParam<URL> URL;
    static {
        String name = "WFSDataStoreFactory:GET_CAPABILITIES_URL";
        String description = "Represents a URL to the getCapabilities document or a server instance.";
        parametersInfo[0] = URL = new WFSFactoryParam<URL>(name, URL.class, description);
    }

    /**
     * Optional {@code Boolean} DataStore parameter acting as a hint for the HTTP protocol to use
     * preferably against the WFS instance, with the following semantics:
     * <ul>
     * <li>{@code null} (not supplied): use "AUTO", let the DataStore decide.
     * <li>{@code Boolean.TRUE} use HTTP POST preferably.
     * <li {@code Boolean.FALSE} use HTTP GET preferably.
     * </ul>
     */
    public static final WFSFactoryParam<Boolean> PROTOCOL;
    static {
        String name = "WFSDataStoreFactory:PROTOCOL";
        String description = "Sets a preference for the HTTP protocol to use when requesting "
                + "WFS functionality. Set this value to Boolean.TRUE for POST, Boolean.FALSE "
                + "for GET or NULL for AUTO";
        parametersInfo[1] = PROTOCOL = new WFSFactoryParam<Boolean>(name, Boolean.class,
                description, null);
    }

    /**
     * Optional {@code String} DataStore parameter supplying the user name to use when the server
     * requires HTTP authentication
     * <p>
     * Shall be used together with {@link #PASSWORD} or not used at all.
     * </p>
     * 
     * @see Authenticator
     */
    public static final WFSFactoryParam<String> USERNAME;
    static {
        String name = "WFSDataStoreFactory:USERNAME";
        String description = "This allows the user to specify a username. This param should not "
                + "be used without the PASSWORD param.";
        parametersInfo[2] = USERNAME = new WFSFactoryParam<String>(name, String.class, description,
                null);
    }

    /**
     * Optional {@code String} DataStore parameter supplying the password to use when the server
     * requires HTTP authentication
     * <p>
     * Shall be used together with {@link #USERNAME} or not used at all.
     * </p>
     * 
     * @see Authenticator
     */
    public static final WFSFactoryParam<String> PASSWORD;
    static {
        String name = "WFSDataStoreFactory:PASSWORD";
        String description = "This allows the user to specify a username. This param should not"
                + " be used without the USERNAME param.";
        parametersInfo[3] = PASSWORD = new WFSFactoryParam<String>(name, String.class, description,
                null, Param.IS_PASSWORD, true);
    }

    /**
     * Optional {@code String} DataStore parameter supplying a JVM supported {@link Charset charset}
     * name to use as the character encoding for XML requests sent to the server.
     */
    public static final WFSFactoryParam<String> ENCODING;
    static {

        String name = "WFSDataStoreFactory:ENCODING";
        String description = "This allows the user to specify the character encoding of the "
                + "XML-Requests sent to the Server. Defaults to UTF-8";

        String defaultValue = "UTF-8";
        List<String> options = new ArrayList<String>(Charset.availableCharsets().keySet());
        Collections.sort(options);
        parametersInfo[4] = ENCODING = new WFSFactoryParam<String>(name, String.class, description,
                defaultValue, Parameter.OPTIONS, options);
    }

    /**
     * Optional {@code Integer} DataStore parameter indicating a timeout in milliseconds for the
     * HTTP connections. <>p>
     * 
     * @TODO: specify if its just a connection timeout or also a read timeout
     */
    public static final WFSFactoryParam<Integer> TIMEOUT;
    static {
        String name = "WFSDataStoreFactory:TIMEOUT";
        String description = "This allows the user to specify a timeout in milliseconds. This param"
                + " has a default value of 3000ms.";
        parametersInfo[5] = TIMEOUT = new WFSFactoryParam<Integer>(name, Integer.class,
                description, 3000);
    }

    /**
     * Optional {@code Integer} parameter stating how many Feature instances to buffer at once. Only
     * implemented for WFS 1.0.0 support.
     */
    public static final WFSFactoryParam<Integer> BUFFER_SIZE;
    static {
        String name = "WFSDataStoreFactory:BUFFER_SIZE";
        String description = "This allows the user to specify a buffer size in features. This param "
                + "has a default value of 10 features.";
        parametersInfo[6] = BUFFER_SIZE = new WFSFactoryParam<Integer>(name, Integer.class,
                description, 10);
    }

    /**
     * Optional {@code Boolean} data store parameter indicating whether to set the accept GZip
     * encoding on the HTTP request headers sent to the server
     */
    public static final WFSFactoryParam<Boolean> TRY_GZIP;
    static {
        String name = "WFSDataStoreFactory:TRY_GZIP";
        String description = "Indicates that datastore should use gzip to transfer data if the server "
                + "supports it. Default is true";
        parametersInfo[7] = TRY_GZIP = new WFSFactoryParam<Boolean>(name, Boolean.class,
                description, Boolean.TRUE);
    }

    /**
     * Optional {@code Boolean} DataStore parameter indicating whether to be lenient about parsing
     * bad data
     */
    public static final WFSFactoryParam<Boolean> LENIENT;
    static {

        String name = "WFSDataStoreFactory:LENIENT";
        String description = "Indicates that datastore should do its best to create features from the "
                + "provided data even if it does not accurately match the schema.  Errors will "
                + "be logged but the parsing will continue if this is true.  Default is false";
        parametersInfo[8] = LENIENT = new WFSFactoryParam<Boolean>(name, Boolean.class,
                description, false);
    }

    /**
     * Optional positive {@code Integer} used as a hard limit for the amount of Features to retrieve
     * for each FeatureType. A value of zero or not providing this parameter means no limit.
     */
    public static final WFSFactoryParam<Integer> MAXFEATURES;
    static {
        String name = "WFSDataStoreFactory:MAXFEATURES";
        String description = "Positive integer used as a hard limit for the amount of Features to retrieve"
                + " for each FeatureType. A value of zero or not providing this parameter means no limit.";
        parametersInfo[9] = MAXFEATURES = new WFSFactoryParam<Integer>(name, Integer.class,
                description, 0);
    }

    /**
     * Optional {@code Integer} DataStore parameter indicating level of compliance to WFS
     * specification
     * <ul>
     * <li>{@link XMLHandlerHints#VALUE_FILTER_COMPLIANCE_LOW}</li>
     * <li>{@link XMLHandlerHints#VALUE_FILTER_COMPLIANCE_MEDIUM}</li>
     * <li>{@link XMLHandlerHints#VALUE_FILTER_COMPLIANCE_HIGH}</li>
     * </ul>
     */
    public static final WFSFactoryParam<Integer> FILTER_COMPLIANCE;;
    static {

        String name = "WFSDataStoreFactory:FILTER_COMPLIANCE";
        String description = "Level of compliance to WFS specification (0-low,1-medium,2-high)";
        List<Integer> options = Arrays.asList(new Integer[] { 0, 1, 2 });

        parametersInfo[10] = FILTER_COMPLIANCE = new WFSFactoryParam<Integer>(name, Integer.class,
                description, null, Parameter.OPTIONS, options);
    }

    /**
     * Optional {@code String} DataStore parameter indicating either "mapserver", "geoserver",
     * "strict" or "nonstrict" strategy
     */
    public static final WFSFactoryParam<String> WFS_STRATEGY;
    static {
        String name = "WFSDataStoreFactory:WFS_STRATEGY";
        String description = "Override wfs stragegy with either arcgis, cubwerx, ionic, mapserver"
                + ", geoserver, strict or nonstrict strategy.";
        List<String> options = Arrays.asList(new String[] { "strict", "nonstrict", "mapserver",
                "geoserver", "arcgis", "cubewerx", "ionix" });
        parametersInfo[11] = WFS_STRATEGY = new WFSFactoryParam<String>(name, String.class,
                description, null, Parameter.OPTIONS, options);
    }

    /**
     * Optional {@code String} namespace URI to override the originial namespaces
     */
    public static final WFSFactoryParam<String> NAMESPACE;
    static {
        String name = "namespace";
        String description = "Override the original WFS type name namespaces";
        parametersInfo[12] = NAMESPACE = new WFSFactoryParam<String>(name, String.class,
                description, null);
    }

    /**
     * Requests the WFS Capabilities document from the {@link WFSDataStoreFactory#URL url} parameter
     * in {@code params} and returns a {@link WFSDataStore} according to the version of the
     * GetCapabilities document returned.
     * <p>
     * Note the {@code URL} provided as parameter must refer to the actual {@code GetCapabilities}
     * request. If you need to specify a preferred version or want the GetCapabilities request to be
     * generated from a base URL build the URL with the {@link #createGetCapabilitiesRequest} first.
     * </p>
     * 
     * @see org.geotools.data.DataStoreFactorySpi#createDataStore(java.util.Map)
     */
    @Override
    public DataStore createDataStore(final Map<String, Serializable> params) throws IOException {

        final WFSConfig config = WFSConfig.fromParams(params);

        {
            String user = config.getUser();
            String password = config.getPassword();
            if (((user == null) && (password != null))
                    || ((config.getPassword() == null) && (config.getUser() != null))) {
                throw new IOException(
                        "Cannot define only one of USERNAME or PASSWORD, must define both or neither");
            }
        }

        final HTTPClient http = new SimpleHttpClient();
        // TODO: let HTTPClient be configured for gzip
        // http.setTryGzip(tryGZIP);
        http.setUser(config.getUser());
        http.setPassword(config.getPassword());
        http.setConnectTimeout(config.getTimeoutMillis() / 1000);

        final URL capabilitiesURL = URL.lookUp(params);

        // WFSClient performs version negotiation and selects the correct strategy
        WFSClient wfsClient;
        try {
            wfsClient = new WFSClient(capabilitiesURL, http, config);
        } catch (ServiceException e) {
            throw new IOException(e);
        }

        WFSContentDataStore dataStore = new WFSContentDataStore(wfsClient);

        return dataStore;
    }

    /**
     * Unsupported operation, can't create a WFS service.
     * 
     * @throws UnsupportedOperationException
     *             always, as this operation is not applicable to WFS.
     * @see org.geotools.data.DataStoreFactorySpi#createNewDataStore(java.util.Map)
     */
    public DataStore createNewDataStore(final Map<String, Serializable> params) throws IOException {
        throw new UnsupportedOperationException("Operation not applicable to a WFS service");
    }

    /**
     * @see org.geotools.data.DataStoreFactorySpi#getDescription()
     */
    public String getDescription() {
        return "The WFSDataStore represents a connection to a Web Feature Server. This connection provides access to the Features published by the server, and the ability to perform transactions on the server (when supported / allowed).";
    }

    /**
     * Returns the set of parameter descriptors needed to connect to a WFS.
     * 
     * @see org.geotools.data.DataStoreFactorySpi#getParametersInfo()
     * @see #URL
     * @see #PROTOCOL
     * @see #USERNAME
     * @see #PASSWORD
     * @see #TIMEOUT
     * @see #BUFFER_SIZE
     * @see #TRY_GZIP
     * @see #LENIENT
     * @see #ENCODING
     */
    public Param[] getParametersInfo() {
        int length = parametersInfo.length;
        Param[] params = new Param[length];
        System.arraycopy(parametersInfo, 0, params, 0, length);
        return params;
    }

    /**
     * Checks whether {@code params} contains a valid set of parameters to connecto to a WFS.
     * <p>
     * Rules are:
     * <ul>
     * <li>the mandatory {@link #URL} is provided.
     * <li>whether both {@link #USERNAME} and {@link #PASSWORD} are provided, or none.
     * </ul>
     * Availability of the other optional parameters is not checked for existence.
     * </p>
     * 
     * @param params
     *            non null map of datastore parameters.
     * @see org.geotools.data.DataStoreFactorySpi#canProcess(java.util.Map)
     */
    public boolean canProcess(@SuppressWarnings("rawtypes") final Map params) {
        if (params == null) {
            return false; // throw new NullPointerException("params");
        }
        try {
            URL url = (URL) URL.lookUp(params);
            if (!"http".equalsIgnoreCase(url.getProtocol())
                    && !"https".equalsIgnoreCase(url.getProtocol())) {
                return false; // must be http or https since we use SimpleHTTPClient class
            }
        } catch (Exception e) {
            return false;
        }

        // check password / username
        if (params.containsKey(USERNAME.key)) {
            if (!params.containsKey(PASSWORD.key)) {
                return false; // must have both
            }
        } else {
            if (params.containsKey(PASSWORD.key)) {
                return false; // must have both
            }
        }
        return true;
    }

    /**
     * @see org.geotools.data.DataStoreFactorySpi#getDisplayName()
     */
    public String getDisplayName() {
        return "Web Feature Server (NG)";
    }

    /**
     * @return {@code true}, no extra or external requisites for datastore availability.
     * @see org.geotools.data.DataStoreFactorySpi#isAvailable()
     */
    public boolean isAvailable() {
        return true;
    }

    /**
     * Creates a HTTP GET Method based WFS {@code GetCapabilities} request for the given protocol
     * version.
     * <p>
     * If the query string in the {@code host} URL already contains a VERSION number, that version
     * is <b>discarded</b>.
     * </p>
     * 
     * @param host
     *            non null URL from which to construct the WFS {@code GetCapabilities} request by
     *            discarding the query string, if any, and appending the propper query string.
     * @return
     */
    public static URL createGetCapabilitiesRequest(URL host, Version version) {
        if (host == null) {
            throw new NullPointerException("null url");
        }
        if (version == null) {
            throw new NullPointerException("version");
        }

        Map<String, String> getCapsKvp = new HashMap<String, String>();
        getCapsKvp.put("SERVICE", "WFS");
        getCapsKvp.put("REQUEST", "GetCapabilities");
        getCapsKvp.put("VERSION", version.toString());
        String getcapsUrl;
        try {
            getcapsUrl = URIs.buildURL(host.toExternalForm(), getCapsKvp);
            return new URL(getcapsUrl);
        } catch (MalformedURLException e) {
            logger.log(Level.WARNING, "Can't create GetCapabilities request from " + host, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a HTTP GET Method based WFS {@code GetCapabilities} request.
     * <p>
     * If the query string in the {@code host} URL already contains a VERSION number, that version
     * is used, otherwise the queried version will be 1.0.0.
     * </p>
     * <p>
     * <b>NOTE</b> the default version will be 1.0.0 until the support for 1.1.0 gets stable enough
     * for general use. If you want to use a 1.1.0 WFS you'll have to explicitly provide the
     * VERSION=1.1.0 parameter in the GetCapabilities request meanwhile.
     * </p>
     * 
     * @param host
     *            non null URL pointing either to a base WFS service access point, or to a full
     *            {@code GetCapabilities} request.
     * @return
     */
    public static URL createGetCapabilitiesRequest(final URL host) {
        if (host == null) {
            throw new NullPointerException("url");
        }

        // final Version defaultVersion = Version.highest();

        // We cannot use the highest vesion as the default yet
        // since v1_1_0 does not implement a read/write datastore
        // and is still having trouble with requests from
        // different projections etc...
        //
        // this is a result of the udig code sprint QA run
        final Version defaultVersion = Versions.v1_0_0;
        // which version to use
        Version requestVersion = defaultVersion;

        final Map<String, String> params = URIs.parseQueryString(host.getQuery());

        String request = params.get("REQUEST");
        if ("GETCAPABILITIES".equals(request)) {
            String version = params.get("VERSION");
            if (version != null) {
                requestVersion = Versions.find(version);
                if (requestVersion == null) {
                    requestVersion = defaultVersion;
                }
            }
        }

        return createGetCapabilitiesRequest(host, requestVersion);
    }

    /**
     * Package visible to be overridden by unit test.
     * 
     * @param capabilitiesUrl
     * @param tryGZIP
     * @param auth
     * @return
     * @throws IOException
     */
    byte[] loadCapabilities(final URL capabilitiesUrl, HTTPClient http) throws IOException {
        byte[] wfsCapabilitiesRawData;

        HTTPResponse httpResponse = http.get(capabilitiesUrl);
        InputStream inputStream = httpResponse.getResponseStream();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        int readCount;
        while ((readCount = inputStream.read(buff)) != -1) {
            out.write(buff, 0, readCount);
        }
        wfsCapabilitiesRawData = out.toByteArray();
        return wfsCapabilitiesRawData;
    }

    static Document parseDocument(InputStream inputStream) throws IOException, DataSourceException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        Document document;
        try {
            document = documentBuilder.parse(inputStream);
        } catch (SAXException e) {
            throw new DataSourceException("Error parsing capabilities document", e);
        }
        return document;
    }
}
