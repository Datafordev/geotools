package org.geotools.data.wfs.impl;

import java.io.IOException;

import org.geotools.data.Diff;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.ResourceInfo;
import org.geotools.data.Transaction;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureStore;
import org.geotools.data.store.ContentState;
import org.geotools.data.store.DiffContentFeatureWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;

class WFSContentFeatureStore extends ContentFeatureStore {

    private WFSContentFeatureSource delegate;

    public WFSContentFeatureStore(WFSContentFeatureSource source) {
        super(source.getEntry(), null);
        this.delegate = source;
    }

    @Override
    public boolean canReproject() {
        return delegate.canReproject();
    }

    @Override
    public boolean canLock() {
        return false; // not yet
    }

    @Override
    protected boolean canEvent() {
        return false; // let the super classe take over this work
    }

    @Override
    public WFSContentDataStore getDataStore() {
        return delegate.getDataStore();
    }

    @Override
    public ContentEntry getEntry() {
        return delegate.getEntry();
    }

    @Override
    public ResourceInfo getInfo() {
        return delegate.getInfo();
    }

    @Override
    public Name getName() {
        return delegate.getName();
    }

    @Override
    public QueryCapabilities getQueryCapabilities() {
        return delegate.getQueryCapabilities();
    }

    @Override
    public ContentState getState() {
        return delegate.getState();
    }

    @Override
    public Transaction getTransaction() {
        return delegate.getTransaction();
    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {
        return delegate.buildFeatureType();
    }

    @Override
    protected int getCountInternal(Query query) throws IOException {
        return delegate.getCount(query);
    }

    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        return delegate.getBoundsInternal(query);
    }

    @Override
    protected boolean canFilter() {
        return delegate.canFilter();
    }

    @Override
    protected boolean canSort() {
        return delegate.canSort();
    }

    @Override
    protected boolean canRetype() {
        return delegate.canRetype();
    }

    @Override
    protected boolean canLimit() {
        return delegate.canLimit();
    }

    @Override
    protected boolean canOffset() {
        return delegate.canOffset();
    }

    /**
     * @return {@code false}, so that we rely on all the in-memory transaction state diff machinery
     *         set up by {@link ContentDataStore}
     * @see org.geotools.data.store.ContentFeatureSource#canTransact()
     */
    @Override
    protected boolean canTransact() {
        return false;
    }

    @Override
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(Query query)
            throws IOException {
        return delegate.getReaderInternal(query);
    }

    @Override
    protected boolean handleVisitor(Query query, FeatureVisitor visitor) throws IOException {
        return delegate.handleVisitor(query, visitor);
    }

    @Override
    public void setTransaction(Transaction transaction) {
        // JD: note, we need to set both super and delegate transactions.
        super.setTransaction(transaction);

        // JD: this guard ensures that a recursive loop will not form
        if (delegate.getTransaction() != transaction) {
            delegate.setTransaction(transaction);
        }
    }

    @Override
    protected FeatureWriter<SimpleFeatureType, SimpleFeature> getWriterInternal(Query query,
            final int flags) throws IOException {
        query = joinQuery(query);
        query = resolvePropertyNames(query);

        WFSContentEntryState state = (WFSContentEntryState) getTransaction().getState(getEntry());

        FeatureReader<SimpleFeatureType, SimpleFeature> reader = getReader(query);

        Diff diff = state.getDiff();

        FeatureWriter<SimpleFeatureType, SimpleFeature> writer;
        writer = new DiffContentFeatureWriter(this, diff, reader);

        return writer;
    }

}
