package org.geotools.data.wfs.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.Diff;
import org.geotools.data.Transaction;
import org.geotools.data.Transaction.State;
import org.opengis.feature.type.Name;

class WFSDataStoreTransactionState implements State {

    private final WFSContentDataStore dataStore;

    private Transaction transaction;

    private Map<Name, Diff> diffs;

    public WFSDataStoreTransactionState(WFSContentDataStore dataStore) {
        this.dataStore = dataStore;
        this.diffs = new HashMap<Name, Diff>();
    }

    public synchronized Diff getDiff(final Name typeName) {
        Diff diff = diffs.get(typeName);
        if (diff == null) {

            diff = new Diff();
            diffs.put(typeName, diff);

        }
        return diff;
    }

    @Override
    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public void rollback() throws IOException {
        diffs.clear(); // rollback differences
        // state.fireBatchFeatureEvent(false);
    }

    @Override
    public void addAuthorization(String AuthID) throws IOException {
        // not needed?
    }

    @Override
    public void commit() throws IOException {
        // TODO Auto-generated method stub

    }
}
