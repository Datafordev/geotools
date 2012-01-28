package org.geotools.data.wfs.internal;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import net.opengis.wfs.InsertElementType;
import net.opengis.wfs.TransactionType;
import net.opengis.wfs.WfsFactory;

import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class DefaultTransactionRequest implements TransactionRequest {

    private final List<TransactionElement> transactionElements;

    private final TransactionType transaction;

    private final WFSStrategy wfsImpl;

    public DefaultTransactionRequest(final WFSStrategy wfsImpl) {
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
        if (txElem instanceof InsertWfs) {
            transaction.getInsert().add(((InsertWfs) txElem).insertElementType);
        }
        transactionElements.add(txElem);
    }

    @Override
    public Insert createInsert(final SimpleFeatureType localType) {
        InsertElementType insertElementType = WfsFactory.eINSTANCE.createInsertElementType();
        return new InsertWfs(localType, wfsImpl, insertElementType);
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

    private static abstract class AbstractTransactionElement implements TransactionElement {

        protected final String localTypeName;

        protected final WFSStrategy wfsImpl;

        protected final QName remoteTypeName;

        private final SimpleFeatureType localType;

        public AbstractTransactionElement(final SimpleFeatureType localType, WFSStrategy wfsImpl) {
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

    private static class InsertWfs extends AbstractTransactionElement implements Insert {

        private final InsertElementType insertElementType;

        private SimpleFeatureBuilder builder;

        public InsertWfs(final SimpleFeatureType localType, final WFSStrategy wfsImpl,
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
