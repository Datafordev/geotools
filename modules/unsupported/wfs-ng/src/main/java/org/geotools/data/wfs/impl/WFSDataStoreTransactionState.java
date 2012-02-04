package org.geotools.data.wfs.impl;

import static org.geotools.data.wfs.internal.Loggers.info;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.geotools.data.Diff;
import org.geotools.data.Transaction;
import org.geotools.data.Transaction.State;
import org.geotools.data.TransactionStateDiff;
import org.geotools.data.wfs.internal.TransactionRequest;
import org.geotools.data.wfs.internal.TransactionRequest.Delete;
import org.geotools.data.wfs.internal.TransactionRequest.Insert;
import org.geotools.data.wfs.internal.TransactionResponse;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.Identifier;

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

    public void putDiff(Name typeName, Diff diff) {
        diffs.put(typeName, diff);
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
    public synchronized void commit() throws IOException {
        if (this.diffs.isEmpty()) {
            return;
        }
        // If the commit fails state is reset
        Map<Name, Diff> diffs = this.diffs;
        this.diffs = new HashMap<Name, Diff>();

        WFSClient wfs = dataStore.getWfsClient();
        TransactionRequest transactionRequest = wfs.createTransaction();

        for (Name typeName : diffs.keySet()) {
            Diff diff = diffs.get(typeName);
            applyDiff(typeName, diff, transactionRequest);
        }

        TransactionResponse transactionResponse = wfs.issueTransaction(transactionRequest);
        List<FeatureId> insertedFids = transactionResponse.getInsertedFids();
        int deleteCount = transactionResponse.getDeleteCount();
        int updatedCount = transactionResponse.getUpdatedCount();
        info(getClass().getSimpleName(), "::commit(): Updated: ", updatedCount, ", Deleted: ",
                deleteCount, ", Inserted: ", insertedFids);
        // TODO: update generated fids? issue events?
    }

    private void applyDiff(final Name localTypeName, Diff diff,
            TransactionRequest transactionRequest) throws IOException {

        final QName remoteTypeName = dataStore.getRemoteTypeName(localTypeName);

        final SimpleFeatureType remoteType = dataStore.getRemoteSimpleFeatureType(remoteTypeName);

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(remoteType);

        // Create a single insert element with all the inserts for this type
        final Map<String, SimpleFeature> added = diff.getAdded();
        if (added.size() > 0) {
            Insert insert = transactionRequest.createInsert(remoteTypeName);

            for (String fid : diff.getAddedOrder()) {
                SimpleFeature localFeature = added.get(fid);

                SimpleFeature remoteFeature = SimpleFeatureBuilder.retype(localFeature, builder);

                insert.add(remoteFeature);
            }
            transactionRequest.add(insert);
        }

        final Map<String, SimpleFeature> modified = diff.getModified();

        // Create a single delete element with all the deletes for this type
        Set<Identifier> ids = new LinkedHashSet<Identifier>();
        for (Map.Entry<String, SimpleFeature> entry : modified.entrySet()) {
            if (!(TransactionStateDiff.NULL == entry.getValue())) {
                continue;// not a delete
            }
            String rid = entry.getKey();
            Identifier featureId = featureId(rid);
            ids.add(featureId);
        }
        if (!ids.isEmpty()) {
            Filter deleteFilter = dataStore.getFilterFactory().id(ids);
            Delete delete = transactionRequest.createDelete(remoteTypeName, deleteFilter);
            transactionRequest.add(delete);
        }

        // Create a single update element with all the updates for this type
        for (Map.Entry<String, SimpleFeature> entry : modified.entrySet()) {
            if (TransactionStateDiff.NULL == entry.getValue()) {
                continue;// not an update
            }
        }
    }

    private Identifier featureId(final String rid) {
        final FilterFactory ff = dataStore.getFilterFactory();
        String fid = rid;
        String featureVersion = null;
        int versionSeparatorIdx = rid.indexOf(FeatureId.VERSION_SEPARATOR);
        if (-1 != versionSeparatorIdx) {
            fid = rid.substring(0, versionSeparatorIdx);
            featureVersion = rid.substring(versionSeparatorIdx + 1);
        }
        FeatureId featureId = ff.featureId(fid, featureVersion);
        return featureId;
    }
}
