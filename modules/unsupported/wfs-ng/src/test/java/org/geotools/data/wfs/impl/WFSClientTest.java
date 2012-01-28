package org.geotools.data.wfs.impl;

import java.net.URL;
import java.util.logging.Level;

import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.internal.Loggers;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
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

    @Test
    public void testInit_1_0_0() throws Exception {
        URL capabilitiesURL = new URL(
                "http://localhost:8080/geoserver/ows?service=wfs&request=GetCapabilities&version=1.0.0");

        HTTPClient httpClient = new SimpleHttpClient();
        WFSConfig config = new WFSConfig();

        WFSClient client = new WFSClient(capabilitiesURL, httpClient, config);
        WFSGetCapabilities capabilities = client.getCapabilities();
        Assert.assertEquals("1.0.0", capabilities.getVersion());
    }

    @Test
    public void testInit_1_1_0() throws Exception {
        URL capabilitiesURL = new URL(
                "http://localhost:8080/geoserver/ows?service=wfs&request=GetCapabilities&version=1.1.0");

        HTTPClient httpClient = new SimpleHttpClient();
        WFSConfig config = new WFSConfig();

        WFSClient client = new WFSClient(capabilitiesURL, httpClient, config);
        WFSGetCapabilities capabilities = client.getCapabilities();
        Assert.assertEquals("1.1.0", capabilities.getVersion());
    }


    @Test
    public void testInit_2_0_0() throws Exception {
        URL capabilitiesURL = new URL(
                "http://localhost:8080/geoserver/ows?service=wfs&request=GetCapabilities&version=2.0.0");

        HTTPClient httpClient = new SimpleHttpClient();
        WFSConfig config = new WFSConfig();

        WFSClient client = new WFSClient(capabilitiesURL, httpClient, config);
        WFSGetCapabilities capabilities = client.getCapabilities();
        Assert.assertEquals("2.0.0", capabilities.getVersion());
    }

}
