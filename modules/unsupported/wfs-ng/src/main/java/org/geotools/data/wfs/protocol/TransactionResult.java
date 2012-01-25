package org.geotools.data.wfs.protocol;

import java.util.List;

import org.opengis.filter.identity.FeatureId;

public interface TransactionResult {

    public List<FeatureId> getInsertedFids();

    public int getUpdatedCount();

    public int getDeleteCount();
}
