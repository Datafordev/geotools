/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 * 
 *    (C) 2003-2008, Open Source Geospatial Foundation (OSGeo)
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.geotools.data.Diff;
import org.geotools.data.DiffFeatureWriter;
import org.geotools.data.FeatureEvent;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.Transaction.State;
import org.geotools.data.wfs.internal.TransactionRequest;
import org.geotools.data.wfs.internal.TransactionRequest.Insert;
import org.geotools.data.wfs.internal.TransactionResponse;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;

import com.vividsolutions.jts.geom.Geometry;

/**
 * A Transaction.State that keeps a difference table for use with WFSDataStore.
 * 
 * @author Jody Garnett, Refractions Research
 * 
 * 
 * @source $URL$
 */
class TransactionStateDiff implements State {
    /**
     * DataStore used to commit() results of this transaction.
     * 
     * @see TransactionStateDiff.commit();
     */
    WFSClient wfs;

    /** Tranasction this State is opperating against. */
    Transaction transaction;

    /**
     * Map of differences by typeName.
     * 
     * <p>
     * Differences are stored as a Map of Feature by fid, and are reset during a commit() or
     * rollback().
     * </p>
     */
    Map<String, Diff> typeNameDiff = new HashMap<String, Diff>();

    public TransactionStateDiff(WFSClient wfsClient) {
        wfs = wfsClient;
    }

    public synchronized void setTransaction(Transaction transaction) {
        if (transaction != null) {
            // configure
            this.transaction = transaction;
        } else {
            this.transaction = null;

            if (typeNameDiff != null) {
                for (Iterator<Diff> i = typeNameDiff.values().iterator(); i.hasNext();) {
                    Diff diff = (Diff) i.next();
                    diff.clear();
                }

                typeNameDiff.clear();
            }

            wfs = null;
        }
    }

    public synchronized Diff diff(final String typeName) throws IOException {
        if (!exists(typeName)) {
            throw new IOException(typeName + " not defined");
        }

        if (typeNameDiff.containsKey(typeName)) {
            return (Diff) typeNameDiff.get(typeName);
        } else {
            Diff diff = new Diff();
            typeNameDiff.put(typeName, diff);

            return diff;
        }
    }

    boolean exists(final String typeName) {
        try {
            wfs.getFeatureTypeName(typeName);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    /**
     * @see org.geotools.data.Transaction.State#addAuthorization(java.lang.String)
     */
    public synchronized void addAuthorization(String AuthID) throws IOException {
        // not required for TransactionStateDiff
    }

    /**
     * Will apply differences to store.
     * 
     * @see org.geotools.data.Transaction.State#commit()
     */
    public synchronized void commit() throws IOException {
        Map.Entry<String, Diff> entry;

        TransactionRequest transactionRequest = wfs.createTransaction();

        for (Iterator<Entry<String, Diff>> i = typeNameDiff.entrySet().iterator(); i.hasNext();) {
            entry = i.next();

            String localTypeName = entry.getKey();
            Diff diff = entry.getValue();
            applyDiff(localTypeName, diff, transactionRequest);
        }

        TransactionResponse transactionResult = wfs.issueTransaction(transactionRequest);

        // TODO: update fids.
    }

    private void applyDiff(final String localTypeName, Diff diff,
            TransactionRequest transactionRequest) {

        Map<String, SimpleFeature> added = diff.getAdded();
        if (added.size() > 0) {
            SimpleFeatureType localType = added.values().iterator().next().getFeatureType();
            Insert insert = transactionRequest.createInsert(localType);
            for (Map.Entry<String, SimpleFeature> entry : added.entrySet()) {
                SimpleFeature feature = entry.getValue();
                insert.add(feature);
            }
            transactionRequest.add(insert);
        }
    }

    /**
     * @see org.geotools.data.Transaction.State#rollback()
     */
    public synchronized void rollback() throws IOException {
        Entry<String, Diff> entry;

        for (Iterator<Entry<String, Diff>> i = typeNameDiff.entrySet().iterator(); i.hasNext();) {
            entry = i.next();

            String typeName = (String) entry.getKey();
            Diff diff = (Diff) entry.getValue();

            diff.clear(); // rollback differences
            // store.listenerManager.fireChanged(typeName, transaction, false);
        }
    }

    /**
     * Convience Method for a Transaction based FeatureWriter
     * 
     * <p>
     * Constructs a DiffFeatureWriter that works against this Transaction.
     * </p>
     * 
     * @param reader2
     *            Type Name to record differences against
     * @param filter
     * 
     * @return A FeatureWriter that records Differences against a FeatureReader
     * 
     * @throws IOException
     *             If a FeatureRader could not be constucted to record differences against
     */
    public synchronized FeatureWriter<SimpleFeatureType, SimpleFeature> writer(
            final String typeName, final FeatureReader<SimpleFeatureType, SimpleFeature> reader,
            Filter filter) throws IOException {

        Diff diff = diff(typeName);

        return new DiffFeatureWriter(reader, diff, filter) {
            public void fireNotification(int eventType, ReferencedEnvelope bounds) {
                switch (eventType) {
                case FeatureEvent.FEATURES_ADDED:
                    // store.listenerManager.fireFeaturesAdded(reader, transaction, bounds, false);

                    break;

                case FeatureEvent.FEATURES_CHANGED:
                    // store.listenerManager.fireFeaturesChanged(reader, transaction, bounds,
                    // false);

                    break;

                case FeatureEvent.FEATURES_REMOVED:
                    // store.listenerManager.fireFeaturesRemoved(reader, transaction, bounds,
                    // false);

                    break;
                }
            }

            public String toString() {
                return "<DiffFeatureWriter>(" + reader.toString() + ")";
            }
        };
    }

    /**
     * A NullObject used to represent the absence of a SimpleFeature.
     * <p>
     * This class is used by TransactionStateDiff as a placeholder to represent features that have
     * been removed. The concept is generally useful and may wish to be taken out as a separate
     * class (used for example to represent deleted rows in a shapefile).
     */
    public static final SimpleFeature NULL = new SimpleFeature() {
        public Object getAttribute(String path) {
            return null;
        }

        public Object getAttribute(int index) {
            return null;
        }

        // public Object[] getAttributes(Object[] attributes) {
        // return null;
        // }

        public ReferencedEnvelope getBounds() {
            return null;
        }

        public Geometry getDefaultGeometry() {
            return null;
        }

        public SimpleFeatureType getFeatureType() {
            return null;
        }

        public String getID() {
            return null;
        }

        public FeatureId getIdentifier() {
            return null;
        }

        // public int getNumberOfAttributes() {
        // return 0;
        // }

        public void setAttribute(int position, Object val) {
        }

        public void setAttribute(String path, Object attribute) throws IllegalAttributeException {
        }

        // public void setDefaultGeometry(Geometry geometry)
        // throws IllegalAttributeException {
        // }

        public Object getAttribute(Name name) {
            return null;
        }

        public int getAttributeCount() {
            return 0;
        }

        public List<Object> getAttributes() {
            return null;
        }

        public SimpleFeatureType getType() {
            return null;
        }

        public void setAttribute(Name name, Object value) {
        }

        public void setAttributes(List<Object> values) {
        }

        public void setAttributes(Object[] values) {
        }

        public void setDefaultGeometry(Object geometry) {
        }

        public GeometryAttribute getDefaultGeometryProperty() {
            return null;
        }

        public void setDefaultGeometryProperty(GeometryAttribute geometryAttribute) {
        }

        public Collection<Property> getProperties(Name name) {
            return null;
        }

        public Collection<Property> getProperties() {
            return null;
        }

        public Collection<Property> getProperties(String name) {
            return null;
        }

        public Property getProperty(Name name) {
            return null;
        }

        public Property getProperty(String name) {
            return null;
        }

        public Collection<? extends Property> getValue() {
            return null;
        }

        public void setValue(Collection<Property> values) {
        }

        public AttributeDescriptor getDescriptor() {
            return null;
        }

        public Name getName() {
            return null;
        }

        public Map<Object, Object> getUserData() {
            return null;
        }

        public boolean isNillable() {
            return false;
        }

        public void setValue(Object newValue) {
        }

        public String toString() {
            return "<NullFeature>";
        }

        public int hashCode() {
            return 0;
        }

        public boolean equals(Object arg0) {
            return arg0 == this;
        }

        public void validate() {
        }
    };

}
