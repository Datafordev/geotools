package org.geotools.data.wfs.impl;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import org.geotools.data.ResourceInfo;
import org.geotools.data.ServiceInfo;
import org.geotools.data.ows.AbstractOpenWebService;
import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.Specification;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.data.wfs.internal.v1_0_0.StrictWFS_1_0_0_Strategy;
import org.geotools.data.wfs.internal.v1_1_0.ArcGISServerStrategy;
import org.geotools.data.wfs.internal.v1_1_0.CubeWerxStrategy;
import org.geotools.data.wfs.internal.v1_1_0.GeoServerPre200Strategy;
import org.geotools.data.wfs.internal.v1_1_0.IonicStrategy;
import org.geotools.data.wfs.internal.v1_1_0.StrictWFS_1_1_0_Strategy;
import org.geotools.data.wfs.internal.v2_0_0.StrictWFS_2_0_0_Strategy;
import org.geotools.ows.ServiceException;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WFSClient extends AbstractOpenWebService<WFSGetCapabilities, ResourceInfo> {

    private static final Logger LOGGER = Logging.getLogger(WFSClient.class);

    private final WFSStrategy strategy;

    private final WFSConfig config;

    public WFSClient(URL capabilitiesURL, HTTPClient httpClient, WFSConfig config)
            throws ServiceException, IOException {
        this(capabilitiesURL, httpClient, config, (WFSGetCapabilities) null);
    }

    public WFSClient(URL capabilitiesURL, HTTPClient httpClient, WFSConfig config,
            WFSGetCapabilities capabilities) throws ServiceException, IOException {

        super(capabilitiesURL, httpClient, capabilities);
        this.config = config;
        this.strategy = determineCorrectStrategy();
    }

    @Override
    public WFSGetCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    protected ServiceInfo createInfo() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceInfo createInfo(ResourceInfo resource) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void setupSpecifications() {
        specs = new Specification[3];
        WFSStrategy strictWFS_1_0_0_Strategy = new StrictWFS_1_0_0_Strategy();
        WFSStrategy strictWFS_1_1_0_Strategy = new StrictWFS_1_1_0_Strategy();
        WFSStrategy strictWFS_2_0_0_Strategy = new StrictWFS_2_0_0_Strategy();

        HTTPClient httpClient = super.getHTTPClient();

        strictWFS_1_0_0_Strategy.setConfig(config);
        strictWFS_1_0_0_Strategy.setHttpClient(httpClient);
        strictWFS_1_1_0_Strategy.setConfig(config);
        strictWFS_1_1_0_Strategy.setHttpClient(httpClient);
        strictWFS_2_0_0_Strategy.setConfig(config);
        strictWFS_2_0_0_Strategy.setHttpClient(httpClient);

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
                strategy = new StrictWFS_1_1_0_Strategy();
            } else if (Versions.v1_1_0.equals(version)) {
                strategy = new StrictWFS_1_1_0_Strategy();
            } else {
                throw new IllegalArgumentException("Unsupported version: " + version);
            }
        }
        LOGGER.info("Using WFS Strategy: " + strategy.getClass().getName());
        return strategy;
    }
}
