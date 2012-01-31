package org.geotools.data.wfs.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;

import javax.xml.namespace.QName;

import org.geotools.data.ows.GetCapabilitiesRequest;
import org.geotools.data.wfs.impl.WFSServiceInfo;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.util.Version;
import org.opengis.filter.Filter;
import org.opengis.filter.capability.FilterCapabilities;

public class WFSStrategyAdapter extends WFSStrategy {

    private final WFSStrategy adaptee;

    public WFSStrategyAdapter(WFSStrategy adaptee) {
        this.adaptee = adaptee;
    }

    @Override
    public void setCapabilities(WFSGetCapabilities capabilities) {
        adaptee.setCapabilities(capabilities);
    }

    @Override
    public void setConfig(WFSConfig config) {
        adaptee.setConfig(config);
    }

    @Override
    public FeatureTypeInfo getFeatureTypeInfo(QName typeName) {
        return adaptee.getFeatureTypeInfo(typeName);
    }

    @Override
    public WFSConfig getConfig() {
        return adaptee.getConfig();
    }

    @Override
    public Version getServiceVersion() {
        return getServiceVersion();
    }

    @Override
    public Set<String> getSupportedOutputFormats(QName typeName, WFSOperationType operation) {
        return adaptee.getSupportedOutputFormats(typeName, operation);
    }

    @Override
    public Set<QName> getFeatureTypeNames() {
        return adaptee.getFeatureTypeNames();
    }

    @Override
    public FilterCapabilities getFilterCapabilities() {
        return adaptee.getFilterCapabilities();
    }

    @Override
    public boolean supportsOperation(WFSOperationType operation, boolean post) {
        return adaptee.supportsOperation(operation, post);
    }

    @Override
    public URL getOperationURL(WFSOperationType operation, boolean post) {
        return adaptee.getOperationURL(operation, post);
    }

    @Override
    public Set<String> getSupportedCRSIdentifiers(QName typeName) {
        return adaptee.getSupportedCRSIdentifiers(typeName);
    }

    @Override
    public void dispose() {
        adaptee.dispose();
    }

    @Override
    public String getDefaultOutputFormat(WFSOperationType operation) {
        return adaptee.getDefaultOutputFormat(operation);
    }

    @Override
    public Filter[] splitFilters(Filter filter) {
        return adaptee.splitFilters(filter);
    }

    @Override
    public boolean supports(ResultType resultType) {
        return adaptee.supports(resultType);
    }

    @Override
    public URL buildUrlGET(WFSRequest request) {
        return adaptee.buildUrlGET(request);
    }

    @Override
    public String getPostContentType(WFSRequest wfsRequest) {
        return adaptee.getPostContentType(wfsRequest);
    }

    @Override
    public InputStream getPostContents(WFSRequest wfsRequest) throws IOException {
        return adaptee.getPostContents(wfsRequest);
    }

    @Override
    public WFSServiceInfo getServiceInfo() {
        return adaptee.getServiceInfo();
    }

    @Override
    public String getVersion() {
        return adaptee.getVersion();
    }

    @Override
    public GetCapabilitiesRequest createGetCapabilitiesRequest(URL server) {
        return adaptee.createGetCapabilitiesRequest(server);
    }

}
