package org.geotools.data.wfs.impl;

import java.io.IOException;

import org.geotools.data.Diff;
import org.geotools.data.FeatureReader;
import org.geotools.data.store.DiffContentFeatureWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;

public class WFSAutoCommitFeatureWriter extends DiffContentFeatureWriter {

    final WFSDataStoreTransactionState committingState;

    public WFSAutoCommitFeatureWriter(WFSContentFeatureStore store,
            FeatureReader<SimpleFeatureType, SimpleFeature> reader) {

        super(store, new Diff(), reader);

        WFSContentDataStore dataStore = (WFSContentDataStore) store.getDataStore();
        committingState = new WFSDataStoreTransactionState(dataStore);
        Name typeName = store.getName();
        committingState.putDiff(typeName, diff);
    }

    @Override
    public void write() throws IOException {
        super.write();
        committingState.commit();
    }

    @Override
    public void remove() throws IOException {
        super.remove();
        committingState.commit();
    }

    @Override
    public boolean hasNext() throws IOException {
        checkClosed();
        return super.hasNext();
    }

    @Override
    public SimpleFeature next() throws IOException {
        checkClosed();
        return super.next();
    }

    private void checkClosed() throws IOException {
        if (reader == null) {
            throw new IOException("FeatureWriter is closed");
        }
    }

}
