package org.geotools.data.wfs.integration;

import java.io.IOException;
import java.net.URL;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.wfs.impl.WFSContentDataStore;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.referencing.CRS;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class GeoServerIntegrationTest extends AbstractDataStoreTest {

    protected WFSClient wfs;

    public GeoServerIntegrationTest() {
        super("MadWFSDataStoreTest");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        boolean longitudeFirst = true;
        CoordinateReferenceSystem wgs84LonLat = CRS.decode("EPSG:4326", longitudeFirst);

        roadType = DataUtilities.createSubType(roadType, null, wgs84LonLat);
        riverType = DataUtilities.createSubType(riverType, null, wgs84LonLat);
    }

    @Override
    public DataStore createDataStore() throws Exception {

        wfs = mockUpWfsClient();

        WFSContentDataStore wfsds = new WFSContentDataStore(wfs);
        return wfsds;
    }

    private WFSClient mockUpWfsClient() throws Exception {
        WFSConfig config = new WFSConfig();
        String baseDirectory = "GeoServer_2.2.x/1.1.0/";

        return new IntegrationTestWFSClient(baseDirectory, config);
    }

    @Override
    public DataStore tearDownDataStore(DataStore data) throws Exception {
        data.dispose();
        return data;
    }

    @Override
    protected String getNameAttribute() {
        return "name";
    }

    @Override
    protected String getRoadTypeName() {
        return "topp_road";
    }

    @Override
    protected String getRiverTypeName() {
        return "sf_river";
    }

    @Test
    public void testGetFeatureTypes() {
        super.testGetFeatureTypes();
    }

    @Test
    public void testGetSchema() throws IOException {
        SimpleFeatureType road = data.getSchema(getRoadTypeName());
        SimpleFeatureType river = data.getSchema(getRiverTypeName());

        assertNotNull(road);
        assertNotNull(river);

        assertEquals(getRoadTypeName(), road.getTypeName());
        assertEquals(getRiverTypeName(), river.getTypeName());

        assertEquals(roadType.getAttributeCount(), road.getAttributeCount());
        assertEquals(riverType.getAttributeCount(), river.getAttributeCount());

        for (int i = 0; i < roadType.getAttributeCount(); i++) {
            AttributeDescriptor expected = roadType.getDescriptor(i);
            AttributeDescriptor actual = road.getDescriptor(i);
            assertEquals(expected, actual);
        }
    }

}
