package org.geotools.data.wfs.impl;

import java.io.IOException;

import org.geotools.data.Diff;
import org.geotools.data.DiffFeatureReader;
import org.geotools.data.EmptyFeatureReader;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.Transaction.State;
import org.geotools.data.store.ContentFeatureStore;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class WFSContentFeatureStore extends ContentFeatureStore {

    private WFSContentFeatureSource source;

    public WFSContentFeatureStore(WFSContentFeatureSource source) {
        super(source.getEntry(), null);
        this.source = source;
    }

    private TransactionStateDiff getStateDiff() throws IOException {

        final Transaction transaction = getTransaction();

        if (Transaction.AUTO_COMMIT.equals(transaction)) {
            throw new IOException("Transaction AUTO_COMMIT is not supported");
        }

        State state;
        synchronized (TransactionStateDiff.class) {
            state = transaction.getState(TransactionStateDiff.class);
            if (state == null) {
                state = new TransactionStateDiff(source.getWfs());
                transaction.putState(TransactionStateDiff.class, state);
            }
        }
        return (TransactionStateDiff) state;
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        return source.getBoundsInternal(query);
    }

    @Override
    protected int getCountInternal(Query query) throws IOException {
        return source.getCountInternal(query);
    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {
        return source.buildFeatureType();
    }

    @Override
    protected FeatureWriter<SimpleFeatureType, SimpleFeature> getWriterInternal(Query query,
            final int flags) throws IOException {

        if (flags == 0) {
            throw new IllegalArgumentException("no write flags set");
        }

        final boolean appendOnly = (flags | WRITER_ADD) == WRITER_ADD;

        final TransactionStateDiff state = getStateDiff();
        final String typeName = getEntry().getTypeName();

        FeatureReader<SimpleFeatureType, SimpleFeature> reader;
        if (appendOnly) {
            reader = new EmptyFeatureReader<SimpleFeatureType, SimpleFeature>(getSchema());
        } else {
            reader = getReaderInternal(query);
        }

        FeatureWriter<SimpleFeatureType, SimpleFeature> writer;
        writer = state.writer(typeName, reader, query.getFilter());
        return writer;
    }

    @Override
    protected synchronized FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(
            Query query) throws IOException {

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader;
        featureReader = source.getReaderInternal(query);

        Transaction transaction = getTransaction();
        if (Transaction.AUTO_COMMIT.equals(transaction)) {
            return featureReader;
        }

        TransactionStateDiff stateDiff = getStateDiff();
        Diff diff = stateDiff.diff(getEntry().getTypeName());
        featureReader = new DiffFeatureReader<SimpleFeatureType, SimpleFeature>(featureReader, diff);
        return featureReader;
    }

}
