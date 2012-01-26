package org.geotools.data.wfs.internal.v1_0_0;

import java.net.URI;
import java.net.URL;
import java.util.Set;

import javax.xml.namespace.QName;

import org.geotools.data.ows.OperationType;
import org.geotools.data.wfs.internal.HttpMethod;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.filter.capability.FilterCapabilities;
import org.w3c.dom.Document;

public class Capabilities {

    public Capabilities(Document getCapabilities) {
         throw new UnsupportedOperationException("Not yet implemented");
    }

    public String getServiceTitle() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getServiceAbstract() {
        // TODO Auto-generated method stub
        return null;
    }

    public Set<String> getServiceKeywords() {
        // TODO Auto-generated method stub
        return null;
    }

    public Set<String> getSupportedGetFeatureOutputFormats() {
        // TODO Auto-generated method stub
        return null;
    }

    public URI getServiceProviderUri() {
        // TODO Auto-generated method stub
        return null;
    }

    public Set<String> getSupportedOutputFormats(QName remoteName) {
        // TODO Auto-generated method stub
        return null;
    }

    public Set<QName> getFeatureTypeNames() {
        // TODO Auto-generated method stub
        return null;
    }

    public QName getFeatureTypeName(String localTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    public String getSimpleTypeName(QName qname) {
        // TODO Auto-generated method stub
        return null;
    }

    public FilterCapabilities getFilterCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    public URL getOperationURL(WFSOperationType operation, HttpMethod httpMethod) {
        OperationType operationType;
        switch (operation) {
        case DESCRIBE_FEATURETYPE:
            operationType = capabilities.getDescribeFeatureType();
            break;
        case GET_CAPABILITIES:
            operationType = capabilities.getGetCapabilities();
            break;
        case GET_FEATURE:
            operationType = capabilities.getGetFeature();
            break;
        case GET_FEATURE_WITH_LOCK:
            operationType = capabilities.getGetFeatureWithLock();
            break;
        case LOCK_FEATURE:
            operationType = capabilities.getLockFeature();
            break;
        case TRANSACTION:
            operationType = capabilities.getTransaction();
            break;
        default:
            throw new IllegalArgumentException("Unknown operation type " + operation);
        }
        if (operationType == null) {
            throw new UnsupportedOperationException(operation + " not supported by the server");
        }
        URL url;
        if (post) {
            url = operationType.getPost();
        } else {
            url = operationType.getGet();
        }
        if (url == null) {
            throw new UnsupportedOperationException("Method " + (post ? "POST" : "GET") + " for "
                    + operation + " is not supported by the server");
        }
        return url;
    }

    public String getFeatureTypeTitle(QName featureTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    public String getFeatureTypeAbstract(QName featureTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    public ReferencedEnvelope getFeatureTypeWGS84Bounds(QName featureTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    public String getDefaultCRS(QName featureTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    public Set<String> getSupportedCRSIdentifiers(QName featureTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

    public Set<String> getFeatureTypeKeywords(QName featureTypeName) {
        // TODO Auto-generated method stub
        return null;
    }

}
