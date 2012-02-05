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

import org.geotools.data.Diff;
import org.geotools.data.store.DiffTransactionState;

/**
 * Transaction state responsible for holding an in memory {@link Diff} of any modifications.
 */
class WFSDiffTransactionState extends DiffTransactionState {

    /**
     * Transaction state responsible for holding an in memory {@link Diff}.
     * 
     * @param state
     *            ContentState for the transaction
     */
    public WFSDiffTransactionState(WFSContentState state) {
        super(state);
    }

    @Override
    public synchronized void commit() throws IOException {
       System.err.println("Commit " + state.getFeatureType());
        
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