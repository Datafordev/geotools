package org.geotools.data.wfs.impl;

import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentState;

public class WFSContentState extends ContentState {

    public WFSContentState(ContentEntry entry) {
        super(entry);
        super.transactionState = new WFSDiffTransactionState(this);
    }

    WFSContentState(WFSContentState wfsContentState) {
        super(wfsContentState);
        super.transactionState = new WFSDiffTransactionState(this);
    }

    @Override
    public WFSContentState copy() {
        return new WFSContentState(this);
    }
}
