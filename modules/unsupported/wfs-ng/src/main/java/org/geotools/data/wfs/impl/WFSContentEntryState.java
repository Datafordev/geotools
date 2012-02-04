/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 * 
 *    (C) 2011, Open Source Geospatial Foundation (OSGeo)
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataSourceException;
import org.geotools.data.Diff;
import org.geotools.data.FeatureWriter;
import org.geotools.data.TransactionStateDiff;
import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.store.ContentFeatureStore;
import org.geotools.data.store.ContentState;
import org.geotools.data.store.DiffTransactionState;
import org.geotools.data.wfs.internal.TransactionRequest;
import org.geotools.data.wfs.internal.TransactionRequest.Insert;
import org.geotools.factory.Hints;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;

/**
 * Transaction state responsible for holding an in memory {@link Diff} of any modifications.
 */
class WFSContentEntryState extends DiffTransactionState {

    /**
     * Transaction state responsible for holding an in memory {@link Diff}.
     * 
     * @param state
     *            ContentState for the transaction
     */
    public WFSContentEntryState(ContentState state) {
        super(state);
    }

    @Override
    public synchronized void commit() throws IOException {
//        if (diff.isEmpty()) {
//            return; // nothing to do
//        }
//        FeatureWriter<SimpleFeatureType, SimpleFeature> writer;
//        ContentFeatureStore store;
//        ContentEntry entry = state.getEntry();
//        Name name = entry.getName();
//        ContentDataStore dataStore = entry.getDataStore();
//        ContentFeatureSource source = (ContentFeatureSource) dataStore.getFeatureSource(name);
//        if (source instanceof ContentFeatureStore) {
//            store = (ContentFeatureStore) dataStore.getFeatureSource(name);
//            writer = store.getWriter(Filter.INCLUDE);
//        } else {
//            throw new UnsupportedOperationException("not writable");
//        }
//        SimpleFeature feature;
//        SimpleFeature update;
//
//        Throwable cause = null;
//        try {
//            while (writer.hasNext()) {
//                feature = (SimpleFeature) writer.next();
//                String fid = feature.getID();
//
//                if (diff.getModified().containsKey(fid)) {
//                    update = (SimpleFeature) diff.getModified().get(fid);
//
//                    if (update == TransactionStateDiff.NULL) {
//                        writer.remove();
//
//                        // notify
//                        state.fireFeatureRemoved(store, feature);
//                    } else {
//                        try {
//                            feature.setAttributes(update.getAttributes());
//                            writer.write();
//
//                            // notify
//                            ReferencedEnvelope bounds = ReferencedEnvelope.reference(feature
//                                    .getBounds());
//                            state.fireFeatureUpdated(store, update, bounds);
//                        } catch (IllegalAttributeException e) {
//                            throw new DataSourceException("Could update " + fid, e);
//                        }
//                    }
//                }
//            }
//
//            SimpleFeature addedFeature;
//            SimpleFeature nextFeature;
//
//            synchronized (diff) {
//                for (String fid : diff.getAddedOrder()) {
//                    addedFeature = diff.getAdded().get(fid);
//
//                    nextFeature = (SimpleFeature) writer.next();
//
//                    if (nextFeature == null) {
//                        throw new DataSourceException("Could not add " + fid);
//                    } else {
//                        try {
//                            nextFeature.setAttributes(addedFeature.getAttributes());
//                            // if( Boolean.TRUE.equals(
//                            // addedFeature.getUserData().get(Hints.USE_PROVIDED_FID)) ){
//                            nextFeature.getUserData().put(Hints.USE_PROVIDED_FID, true);
//                            if (addedFeature.getUserData().containsKey(Hints.PROVIDED_FID)) {
//                                String providedFid = (String) addedFeature.getUserData().get(
//                                        Hints.PROVIDED_FID);
//                                nextFeature.getUserData().put(Hints.PROVIDED_FID, providedFid);
//                            } else {
//                                nextFeature.getUserData().put(Hints.PROVIDED_FID,
//                                        addedFeature.getID());
//                            }
//                            // }
//                            writer.write();
//
//                            // notify
//                            state.fireFeatureAdded(store, nextFeature);
//                        } catch (IllegalAttributeException e) {
//                            throw new DataSourceException("Could update " + fid, e);
//                        }
//                    }
//                }
//            }
//        } catch (IOException e) {
//            cause = e;
//            throw e;
//        } catch (RuntimeException e) {
//            cause = e;
//            throw e;
//        } finally {
//            try {
//                writer.close();
//                state.fireBatchFeatureEvent(true);
//                diff.clear();
//            } catch (IOException e) {
//                if (cause != null) {
//                    e.initCause(cause);
//                }
//                throw e;
//            } catch (RuntimeException e) {
//                if (cause != null) {
//                    e.initCause(cause);
//                }
//                throw e;
//            }
//        }
    }
}