/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2004-2008, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.data.wfs.internal.v1_0_0;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.geotools.data.ows.HTTPClient;
import org.geotools.data.ows.SimpleHttpClient;
import org.geotools.data.wfs.impl.WFSDataStoreFactory;
import org.geotools.data.wfs.internal.GetFeature;
import org.geotools.data.wfs.internal.HttpMethod;
import org.geotools.data.wfs.internal.TransactionRequest;
import org.geotools.data.wfs.internal.TransactionResult;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.data.wfs.internal.WFSResponse;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.Version;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.capability.FilterCapabilities;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.w3c.dom.Document;

/**
 * 
 
 */
public class StrictWFS_1_0_0_Strategy implements WFSStrategy {

    private static final Logger LOGGER = Logging.getLogger(StrictWFS_1_0_0_Strategy.class);

    private Capabilities capabilities;

    private HTTPClient httpClient;

    private WFSConfig config;

    public StrictWFS_1_0_0_Strategy() {
        this.config = new WFSConfig();
        this.httpClient = new SimpleHttpClient();
    }

    @Override
    public void setConfig(WFSConfig config) {
        this.config = config;
        if (null != config.getGetCapabilities()) {
            this.capabilities = new Capabilities(config.getGetCapabilities());
        }
    }

    @Override
    public WFSConfig getConfig() {
        return config;
    }

    @Override
    public void setHttpClient(HTTPClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void initFromCapabilities(InputStream capabilitiesContents) throws IOException {
        Document capsDoc = WFSDataStoreFactory.parseCapabilities(capabilitiesContents);
        this.capabilities = new Capabilities(capsDoc);
    }

    @Override
    public Version getServiceVersion() {
        return Versions.v1_0_0;
    }

    @Override
    public String getServiceTitle() {
        return capabilities.getServiceTitle();
    }

    @Override
    public String getServiceAbstract() {
        return capabilities.getServiceAbstract();
    }

    @Override
    public Set<String> getServiceKeywords() {
        return capabilities.getServiceKeywords();
    }

    @Override
    public URI getServiceProviderUri() {
        return capabilities.getServiceProviderUri();
    }

    @Override
    public Set<String> getSupportedGetFeatureOutputFormats() {
        return capabilities.getSupportedGetFeatureOutputFormats();
    }

    @Override
    public Set<String> getSupportedOutputFormats(String typeName) {
        QName remoteName = getFeatureTypeName(typeName);
        return capabilities.getSupportedOutputFormats(remoteName);
    }

    @Override
    public Set<QName> getFeatureTypeNames() {
        return capabilities.getFeatureTypeNames();
    }

    @Override
    public QName getFeatureTypeName(String localTypeName) {
        return capabilities.getFeatureTypeName(localTypeName);
    }

    @Override
    public String getSimpleTypeName(QName qname) {
        return capabilities.getSimpleTypeName(qname);
    }

    @Override
    public FilterCapabilities getFilterCapabilities() {
        return capabilities.getFilterCapabilities();
    }

    @Override
    public boolean supportsOperation(final WFSOperationType operation, boolean post) {
        try {
            getOperationURL(operation, post);
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    @Override
    public URL getOperationURL(WFSOperationType operation, boolean post) {
        return capabilities.getOperationURL(operation, post ? HttpMethod.POST : HttpMethod.GET);
    }

    @Override
    public String getFeatureTypeTitle(String typeName) {
        return capabilities.getFeatureTypeTitle(getFeatureTypeName(typeName));
    }

    @Override
    public String getFeatureTypeAbstract(String typeName) {
        return capabilities.getFeatureTypeAbstract(getFeatureTypeName(typeName));
    }

    @Override
    public ReferencedEnvelope getFeatureTypeWGS84Bounds(String typeName) {
        return capabilities.getFeatureTypeWGS84Bounds(getFeatureTypeName(typeName));
    }

    @Override
    public String getDefaultCRS(String typeName) {
        return capabilities.getDefaultCRS(getFeatureTypeName(typeName));
    }

    @Override
    public Set<String> getSupportedCRSIdentifiers(String typeName) {
        return capabilities.getSupportedCRSIdentifiers(getFeatureTypeName(typeName));
    }

    @Override
    public String getDefaultOutputFormat(WFSOperationType operation) {
        if (WFSOperationType.GET_FEATURE.equals(operation)) {
            return "GML2";
        }
        throw new UnsupportedOperationException(
                "Don't know how to obtain default output format for " + operation);
    }

    @Override
    public Set<String> getFeatureTypeKeywords(String typeName) {
        return capabilities.getFeatureTypeKeywords(getFeatureTypeName(typeName));
    }

    @Override
    public WFSResponse describeFeatureTypeGET(String typeName, String outputFormat)
            throws IOException, UnsupportedOperationException {
        throw new UnsupportedOperationException("implement");
    }

    @Override
    public WFSResponse describeFeatureTypePOST(String typeName, String outputFormat)
            throws IOException, UnsupportedOperationException {
        throw new UnsupportedOperationException("implement");
    }

    @Override
    public WFSResponse issueGetFeatureGET(GetFeature request) throws IOException,
            UnsupportedOperationException {
        throw new UnsupportedOperationException("implement");
    }

    @Override
    public WFSResponse issueGetFeaturePOST(GetFeature request) throws IOException,
            UnsupportedOperationException {
        throw new UnsupportedOperationException("implement");
    }

    @Override
    public void dispose() {
        //
    }

    @Override
    public Filter[] splitFilters(Filter filter) {
        throw new UnsupportedOperationException("implement");
    }

    @Override
    public SimpleFeatureType issueDescribeFeatureTypeGET(String prefixedTypeName,
            CoordinateReferenceSystem crs) throws IOException {
        throw new UnsupportedOperationException("implement");
    }

    @Override
    public TransactionResult issueTransaction(TransactionRequest transaction) throws IOException {
        throw new UnsupportedOperationException("implement");
    }

    @Override
    public TransactionRequest createTransaction() {
        throw new UnsupportedOperationException("implement");
    }
}
