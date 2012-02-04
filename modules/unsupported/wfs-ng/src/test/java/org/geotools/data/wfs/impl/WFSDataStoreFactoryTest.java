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
package org.geotools.data.wfs.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.ows.HTTPClient;
import org.geotools.data.wfs.internal.URIs;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.data.wfs.internal.v1_x.CubeWerxStrategy;
import org.geotools.data.wfs.internal.v1_x.GeoServerPre200Strategy;
import org.geotools.data.wfs.internal.v1_x.IonicStrategy;
import org.geotools.test.TestData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

/**
 * 
 * 
 * @source $URL$
 */
public class WFSDataStoreFactoryTest {

    private WFSDataStoreFactory dsf;

    private Map<String, Serializable> params;

    @Before
    public void setUp() throws Exception {
        dsf = new WFSDataStoreFactory();
        params = new HashMap<String, Serializable>();
    }

    @After
    public void tearDown() throws Exception {
        dsf = null;
        params = null;
    }

    @Test
    public void testCanProcess() {
        // URL not set
        assertFalse(dsf.canProcess(params));

        params.put(WFSDataStoreFactory.URL.key,
                "http://someserver.example.org/wfs?request=GetCapabilities");
        assertTrue(dsf.canProcess(params));

        params.put(WFSDataStoreFactory.USERNAME.key, "groldan");
        assertFalse(dsf.canProcess(params));

        params.put(WFSDataStoreFactory.PASSWORD.key, "secret");
        assertTrue(dsf.canProcess(params));
    }

//    @SuppressWarnings("nls")
//    @Test
//    public void testDetermineWFS1_1_0_Strategy() throws IOException {
//        URL url;
//        InputStream in;
//        Document capabilitiesDoc;
//        WFSStrategy strategy;
//
//        WFSConfig config = mock(WFSConfig.class);
//        when(config.getWfsStrategy()).thenReturn("geoserver");
//
//        url = TestData.url(this, "geoserver_capabilities_1_1_0.xml");
//        in = url.openStream();
//        capabilitiesDoc = WFSDataStoreFactory.parseDocument(in);
//        strategy = WFSDataStoreFactory.determineCorrectStrategy(Versions.v1_1_0, config,
//                capabilitiesDoc);
//        assertNotNull(strategy);
//        assertEquals(GeoServerPre200Strategy.class, strategy.getClass());
//
//        // try override
//        url = TestData.url(this, "geoserver_capabilities_1_1_0.xml");
//        in = url.openStream();
//        capabilitiesDoc = WFSDataStoreFactory.parseDocument(in);
//        when(config.getWfsStrategy()).thenReturn("cubewerx");
//        strategy = WFSDataStoreFactory.determineCorrectStrategy(Versions.v1_1_0, config,
//                capabilitiesDoc);
//        assertNotNull(strategy);
//        assertEquals(CubeWerxStrategy.class, strategy.getClass());
//
//        url = TestData.url(this, "cubewerx_capabilities_1_1_0.xml");
//        in = url.openStream();
//        capabilitiesDoc = WFSDataStoreFactory.parseDocument(in);
//        when(config.getWfsStrategy()).thenReturn(null);
//        strategy = WFSDataStoreFactory.determineCorrectStrategy(Versions.v1_1_0, config,
//                capabilitiesDoc);
//        assertNotNull(strategy);
//        assertEquals(CubeWerxStrategy.class, strategy.getClass());
//
//        url = TestData.url(this, "ionic_capabilities_1_1_0.xml");
//        in = url.openStream();
//        capabilitiesDoc = WFSDataStoreFactory.parseDocument(in);
//        when(config.getWfsStrategy()).thenReturn(null);
//        strategy = WFSDataStoreFactory.determineCorrectStrategy(Versions.v1_1_0, config,
//                capabilitiesDoc);
//        assertNotNull(strategy);
//        assertEquals(IonicStrategy.class, strategy.getClass());
//    }

    @Test
    public void testCreateDataStoreWFS_1_1_0() throws IOException {
        String capabilitiesFile;
        capabilitiesFile = "geoserver_capabilities_1_1_0.xml";
        testCreateDataStore_WFS_1_1_0(capabilitiesFile);

        capabilitiesFile = "deegree_capabilities_1_1_0.xml";
        testCreateDataStore_WFS_1_1_0(capabilitiesFile);
    }

    private void testCreateDataStore_WFS_1_1_0(final String capabilitiesFile) throws IOException {

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        final URL capabilitiesUrl = TestData.getResource(this, capabilitiesFile);
        if (capabilitiesUrl == null) {
            throw new IllegalArgumentException(capabilitiesFile + " not found");
        }
        params.put(WFSDataStoreFactory.URL.key, capabilitiesUrl);

        WFSContentDataStore dataStore = dsf.createDataStore(params);
        assertTrue(dataStore instanceof WFSContentDataStore);
    }

}
