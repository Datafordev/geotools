package org.geotools.data.wfs.v1_1_0;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import net.opengis.wfs.InsertElementType;
import net.opengis.wfs.TransactionType;
import net.opengis.wfs.WfsFactory;

import org.geotools.data.wfs.protocol.TransactionRequest;
import org.geotools.data.wfs.protocol.TransactionRequest.Delete;
import org.geotools.data.wfs.protocol.TransactionRequest.Insert;
import org.geotools.data.wfs.protocol.TransactionRequest.TransactionElement;
import org.geotools.data.wfs.protocol.TransactionRequest.Update;
import org.geotools.data.wfs.protocol.WFSProtocol;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

class WFS_1_1_0_TransactionRequest implements TransactionRequest {

    private final List<TransactionElement> transactionElements;

    private final TransactionType transaction;

    private final WFSProtocol wfsImpl;

    public WFS_1_1_0_TransactionRequest(final WFSProtocol wfsImpl) {
        this.wfsImpl = wfsImpl;
        transaction = WfsFactory.eINSTANCE.createTransactionType();
        transactionElements = new ArrayList<TransactionRequest.TransactionElement>(2);
    }

    TransactionType getTransaction() {
        return transaction;
    }

    @Override
    public List<TransactionElement> getTransactionElements() {
        return new ArrayList<TransactionRequest.TransactionElement>(transactionElements);
    }

    @Override
    public void add(final TransactionElement txElem) {
        if (txElem instanceof InsertWfs_1_1_0) {
            transaction.getInsert().add(((InsertWfs_1_1_0) txElem).insertElementType);
        }
        transactionElements.add(txElem);
    }

    @Override
    public Insert createInsert(final SimpleFeatureType localType) {
        InsertElementType insertElementType = WfsFactory.eINSTANCE.createInsertElementType();
        return new InsertWfs_1_1_0(localType, wfsImpl, insertElementType);
    }

    @Override
    public Update createUpdate(String localTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Delete createDelete(String localTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    private static abstract class AbstractTransactionElement_1_1_0 implements TransactionElement {

        protected final String localTypeName;

        protected final WFSProtocol wfsImpl;

        protected final QName remoteTypeName;

        private final SimpleFeatureType localType;

        public AbstractTransactionElement_1_1_0(final SimpleFeatureType localType,
                WFSProtocol wfsImpl) {
            this.localType = localType;
            this.localTypeName = localType.getTypeName();
            this.wfsImpl = wfsImpl;
            this.remoteTypeName = wfsImpl.getFeatureTypeName(localTypeName);
        }

        @Override
        public String getLocalTypeName() {
            return localTypeName;
        }

    }

    private static class InsertWfs_1_1_0 extends AbstractTransactionElement_1_1_0 implements Insert {

        private final InsertElementType insertElementType;

        private SimpleFeatureBuilder builder;

        public InsertWfs_1_1_0(final SimpleFeatureType localType, final WFSProtocol wfsImpl,
                final InsertElementType insertElementType) {
            super(localType, wfsImpl);
            this.insertElementType = insertElementType;

            SimpleFeatureTypeBuilder remoteTypeBuilder = new SimpleFeatureTypeBuilder();
            remoteTypeBuilder.setName(new NameImpl(remoteTypeName));
            remoteTypeBuilder.addAll(localType.getAttributeDescriptors());

            SimpleFeatureType remoteType = remoteTypeBuilder.buildFeatureType();
            builder = new SimpleFeatureBuilder(remoteType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void add(final SimpleFeature localFeature) {
            builder.reset();
            builder.addAll(localFeature.getAttributes());
            SimpleFeature remoteFeature = builder.buildFeature(localFeature.getID());

            this.insertElementType.getFeature().add(remoteFeature);
        }

    }
}
