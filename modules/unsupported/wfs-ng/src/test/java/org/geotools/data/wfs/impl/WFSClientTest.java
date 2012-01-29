package org.geotools.data.wfs.impl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;

import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.internal.Loggers;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.geotools.ows.ServiceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WFSClientTest {

    @Before
    public void setUp() throws Exception {
        Loggers.RESPONSES.setLevel(Level.FINEST);
    }

    @After
    public void tearDown() throws Exception {
    }

    private URL url(String resource) {

        String absoluteResouce = "/org/geotools/data/wfs/impl/test-data/" + resource;

        URL capabilitiesURL = getClass().getResource(absoluteResouce);

        assertNotNull("resource not found: " + absoluteResouce, capabilitiesURL);
        return capabilitiesURL;
    }

    private void testInit(String resource, String expectedVersion) throws IOException,
            ServiceException {
        URL capabilitiesURL = url(resource);
        HTTPClient httpClient = new SimpleHttpClient();
        WFSConfig config = new WFSConfig();

        WFSClient client = new WFSClient(capabilitiesURL, httpClient, config);
        WFSGetCapabilities capabilities = client.getCapabilities();
        Assert.assertEquals(expectedVersion, capabilities.getVersion());
    }

    @Test
    public void testInit_1_0() throws Exception {
        testInit("GeoServer_2.2.x/1.0.0/GetCapabilities.xml", "1.0.0");
        testInit("PCIGeoMatics_unknown/1.0.0/GetCapabilities.xml", "1.0.0");
        testInit("MapServer_5.6.5/1.0.0/GetCapabilities.xml", "1.0.0");
        testInit("Ionic_unknown/1.0.0/GetCapabilities.xml", "1.0.0");
        testInit("Galdos_unknown/1.0.0/GetCapabilities.xml", "1.0.0");
        testInit("CubeWerx_4.12.6/1.0.0/GetCapabilities.xml", "1.0.0");
    }

    @Test
    public void testInit_1_1() throws Exception {
        testInit("GeoServer_1.7.x/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("GeoServer_2.0/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("CubeWerx_4.12.6/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("CubeWerx_4.7.5/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("CubeWerx_5.6.3/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("Deegree_unknown/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("Ionic_unknown/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("MapServer_5.6.5/1.1.0/GetCapabilities.xml", "1.1.0");
        testInit("CubeWerx_nsdi/1.1.0/GetCapabilities.xml", "1.1.0");
    }

    @Test
    public void testInit_2_0_0() throws Exception {
        testInit("GeoServer_2.2.x/1.1.0/GetCapabilities.xml", "2.0.0");
        testInit("Degree_3.0/1.1.0/GetCapabilities.xml", "2.0.0");
    }

}
