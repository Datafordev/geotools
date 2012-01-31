package org.geotools.data.wfs.internal;

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
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.data.wfs.internal.v1_0.StrictWFS_1_0_Strategy;
import org.geotools.data.wfs.internal.v1_1.IonicStrategy;
import org.geotools.data.wfs.internal.v1_1.StrictWFS_1_1_Strategy;
import org.geotools.ows.ServiceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WFSClientTest {

    WFSConfig config;

    @Before
    public void setUp() throws Exception {
        Loggers.RESPONSES.setLevel(Level.FINEST);
        config = new WFSConfig();
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

    private WFSClient testInit(String resource, String expectedVersion) throws IOException,
            ServiceException {

        URL capabilitiesURL = url(resource);
        HTTPClient httpClient = new SimpleHttpClient();

        WFSClient client = new WFSClient(capabilitiesURL, httpClient, config);
        WFSGetCapabilities capabilities = client.getCapabilities();

        Assert.assertEquals(expectedVersion, capabilities.getVersion());
        return client;
    }

    private WFSClient checkStrategy(String resource, String expectedVersion,
            Class<? extends WFSStrategy> expectedStrategy) throws IOException, ServiceException {

        WFSClient client = testInit(resource, expectedVersion);

        WFSStrategy strategy = client.getStrategy();

        assertEquals(expectedStrategy, strategy.getClass());

        return client;
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
    public void testInit_2_0() throws Exception {
        testInit("GeoServer_2.2.x/2.0.0/GetCapabilities.xml", "2.0.0");
        testInit("Degree_3.0/2.0.0/GetCapabilities.xml", "2.0.0");
    }

    @Test
    public void testAutoDetermineStrategy() throws Exception {
        Class<StrictWFS_1_0_Strategy> strict10 = StrictWFS_1_0_Strategy.class;

        checkStrategy("GeoServer_2.2.x/1.0.0/GetCapabilities.xml", "1.0.0", strict10);
        checkStrategy("PCIGeoMatics_unknown/1.0.0/GetCapabilities.xml", "1.0.0", strict10);
        checkStrategy("MapServer_5.6.5/1.0.0/GetCapabilities.xml", "1.0.0", strict10);
        checkStrategy("Ionic_unknown/1.0.0/GetCapabilities.xml", "1.0.0", IonicStrategy.class);
        checkStrategy("Galdos_unknown/1.0.0/GetCapabilities.xml", "1.0.0", strict10);
        checkStrategy("CubeWerx_4.12.6/1.0.0/GetCapabilities.xml", "1.0.0", strict10);

        Class<StrictWFS_1_1_Strategy> strict11 = StrictWFS_1_1_Strategy.class;
        checkStrategy("GeoServer_1.7.x/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("GeoServer_2.0/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("CubeWerx_4.12.6/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("CubeWerx_4.7.5/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("CubeWerx_5.6.3/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("Deegree_unknown/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("Ionic_unknown/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("MapServer_5.6.5/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
        checkStrategy("CubeWerx_nsdi/1.1.0/GetCapabilities.xml", "1.1.0", strict11);
    }
}
