package org.geotools.data.wfs.integration;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureStore;

public class MadTest extends AbstractDataStoreTest {

    public MadTest() {
        super("MadWFSDataStoreTest");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public DataStore createDataStore() throws Exception {
        DataStore data = new MemoryDataStore();
        data.createSchema(roadType);
        data.createSchema(riverType);

        SimpleFeatureStore roads;
        roads = ((SimpleFeatureStore) data.getFeatureSource(roadType.getTypeName()));

        roads.addFeatures(DataUtilities.collection(roadFeatures));

        SimpleFeatureStore rivers;
        rivers = ((SimpleFeatureStore) data.getFeatureSource(riverType.getTypeName()));

        rivers.addFeatures(DataUtilities.collection(riverFeatures));
        return data;
    }

    @Override
    public DataStore tearDownDataStore(DataStore data) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected String getNameAttribute() {
        return "name";
    }

    @Override
    protected String getRoadTypeName() {
        return "road";
    }

    @Override
    protected String getRiverTypeName() {
        return "river";
    }

}
