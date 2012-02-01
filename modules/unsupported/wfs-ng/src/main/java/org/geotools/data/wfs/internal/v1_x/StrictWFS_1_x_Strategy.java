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
package org.geotools.data.wfs.internal.v1_x;

import static org.geotools.data.wfs.internal.GetFeatureRequest.ResultType.RESULTS;
import static org.geotools.data.wfs.internal.HttpMethod.GET;
import static org.geotools.data.wfs.internal.WFSOperationType.GET_CAPABILITIES;
import static org.geotools.data.wfs.internal.WFSOperationType.GET_FEATURE;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.xml.namespace.QName;

import net.opengis.ows10.DCPType;
import net.opengis.ows10.DomainType;
import net.opengis.ows10.OperationType;
import net.opengis.ows10.OperationsMetadataType;
import net.opengis.ows10.RequestMethodType;
import net.opengis.wfs.DescribeFeatureTypeType;
import net.opengis.wfs.FeatureTypeListType;
import net.opengis.wfs.FeatureTypeType;
import net.opengis.wfs.GetFeatureType;
import net.opengis.wfs.QueryType;
import net.opengis.wfs.ResultTypeType;
import net.opengis.wfs.WFSCapabilitiesType;
import net.opengis.wfs.WfsFactory;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.geotools.data.wfs.impl.WFSServiceInfo;
import org.geotools.data.wfs.internal.AbstractWFSStrategy;
import org.geotools.data.wfs.internal.FeatureTypeInfo;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.data.wfs.internal.DescribeFeatureTypeRequest;
import org.geotools.data.wfs.internal.HttpMethod;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSExtensions;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.data.wfs.internal.WFSResponseFactory;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.factory.GeoTools;
import org.geotools.util.Version;
import org.geotools.wfs.v1_0.WFS;
import org.geotools.xml.Configuration;
import org.opengis.filter.Filter;
import org.opengis.filter.capability.FilterCapabilities;
import org.opengis.filter.sort.SortBy;

/**
 * 
 */
public class StrictWFS_1_x_Strategy extends AbstractWFSStrategy {

    private static final List<String> PREFFERRED_GETFEATURE_FORMATS = Collections
            .unmodifiableList(Arrays.asList("text/xml; subtype=gml/3.1.1",
                    "text/xml; subtype=gml/3.1.1/profiles/gmlsf/0", "GML3"));

    /**
     * The WFS GetCapabilities document. Final by now, as we're not handling updatesequence, so will
     * not ask the server for an updated capabilities during the life-time of this datastore.
     */
    protected net.opengis.wfs.WFSCapabilitiesType capabilities;

    private final Map<QName, FeatureTypeType> typeInfos;

    private Version serviceVersion;

    public StrictWFS_1_x_Strategy() {
        // default to 1.0, override at setCapabilities if needed
        this(Versions.v1_0_0);
    }

    public StrictWFS_1_x_Strategy(Version defaultVersion) {
        super();
        typeInfos = new HashMap<QName, FeatureTypeType>();
        serviceVersion = defaultVersion;
    }

    /*---------------------------------------------------------------------
     * AbstractWFSStrategy methods
     * ---------------------------------------------------------------------*/

    @Override
    protected QName getOperationName(WFSOperationType operation) {
        return new QName(WFS.NAMESPACE, operation.getName());
    }

    /**
     * @see org.geotools.data.wfs.internal.AbstractWFSStrategy#createGetFeatureRequestPost(org.geotools.data.wfs.internal.GetFeatureRequest)
     */
    @SuppressWarnings("unchecked")
    @Override
    protected GetFeatureType createGetFeatureRequestPost(GetFeatureRequest query)
            throws IOException {

        final QName typeName = query.getTypeName();
        final FeatureTypeInfo featureTypeInfo = getFeatureTypeInfo(typeName);

        final WfsFactory factory = WfsFactory.eINSTANCE;

        GetFeatureType getFeature = factory.createGetFeatureType();
        getFeature.setService("WFS");
        getFeature.setVersion(getVersion());
        
        String outputFormat = query.getOutputFormat();
        getFeature.setOutputFormat(outputFormat);

        getFeature
                .setHandle("GeoTools " + GeoTools.getVersion() + " WFS DataStore " + getVersion());
        Integer maxFeatures = query.getMaxFeatures();
        if (maxFeatures != null) {
            getFeature.setMaxFeatures(BigInteger.valueOf(maxFeatures.intValue()));
        }

        ResultType resultType = query.getResultType();
        getFeature.setResultType(RESULTS == resultType ? ResultTypeType.RESULTS_LITERAL
                : ResultTypeType.HITS_LITERAL);

        QueryType wfsQuery = factory.createQueryType();
        wfsQuery.setTypeName(Collections.singletonList(typeName));

        final Filter supportedFilter;
        final Filter unsupportedFilter;
        {
            final Filter filter = query.getFilter();
            Filter[] splitFilters = splitFilters(typeName, filter);
            supportedFilter = splitFilters[0];
            unsupportedFilter = splitFilters[1];
        }

        query.setUnsupportedFilter(unsupportedFilter);

        if (!Filter.INCLUDE.equals(supportedFilter)) {
            wfsQuery.setFilter(supportedFilter);
        }

        String srsName = query.getSrsName();
        if (null == srsName) {
            srsName = featureTypeInfo.getDefaultSRS();
        }
        try {
            wfsQuery.setSrsName(new URI(srsName));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Can't create a URI from the query CRS: " + srsName, e);
        }
        String[] propertyNames = query.getPropertyNames();
        boolean retrieveAllProperties = propertyNames == null;
        if (!retrieveAllProperties) {
            List<String> propertyName = wfsQuery.getPropertyName();
            for (String propName : propertyNames) {
                propertyName.add(propName);
            }
        }
        SortBy[] sortByList = query.getSortBy();
        if (sortByList != null) {
            for (SortBy sortBy : sortByList) {
                wfsQuery.getSortBy().add(sortBy);
            }
        }

        getFeature.getQuery().add(wfsQuery);

        return getFeature;
    }

    @Override
    protected DescribeFeatureTypeType createDescribeFeatureTypeRequestPost(
            DescribeFeatureTypeRequest request) {

        final WfsFactory factory = WfsFactory.eINSTANCE;

        DescribeFeatureTypeType dft = factory.createDescribeFeatureTypeType();

        Version version = getServiceVersion();
        dft.setService("WFS");
        dft.setVersion(version.toString());
        dft.setHandle("GeoTools " + GeoTools.getVersion() + " WFS DataStore " + getVersion());

        if (Versions.v1_0_0.equals(version)) {
            dft.setOutputFormat(null);
        }

        QName typeName = request.getTypeName();
        @SuppressWarnings("unchecked")
        List<QName> typeNames = dft.getTypeName();
        typeNames.add(typeName);

        return dft;
    }

    /*---------------------------------------------------------------------
     * WFSStrategy methods
     * ---------------------------------------------------------------------*/

    @Override
    public Configuration getFilterConfiguration() {
        return Versions.v1_0_0.equals(getServiceVersion()) ? FILTER_1_0_CONFIGURATION
                : FILTER_1_1_CONFIGURATION;
    }

    @Override
    public Configuration getWfsConfiguration() {
        return Versions.v1_0_0.equals(getServiceVersion()) ? WFS_1_0_CONFIGURATION
                : WFS_1_1_CONFIGURATION;
    }

    @Override
    public void setCapabilities(WFSGetCapabilities capabilities) {
        WFSCapabilitiesType caps = (WFSCapabilitiesType) capabilities.getParsedCapabilities();
        this.capabilities = caps;
        String version = caps.getVersion();
        try {
            this.serviceVersion = Versions.find(version);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Capabilities document didn't advertise a supported version (" + version
                    + "). Defaulting to " + this.serviceVersion);
        }

        typeInfos.clear();
        FeatureTypeListType featureTypeList = this.capabilities.getFeatureTypeList();

        @SuppressWarnings("unchecked")
        List<FeatureTypeType> featureTypes = featureTypeList.getFeatureType();

        for (FeatureTypeType typeInfo : featureTypes) {
            QName name = typeInfo.getName();
            typeInfos.put(name, typeInfo);
        }
    }

    @Override
    public WFSServiceInfo getServiceInfo() {

        final String schemaLocation;
        if (Versions.v1_1_0.equals(getServiceVersion())) {
            schemaLocation = "http://schemas.opengis.net/wfs/1.0.0/WFS-transaction.xsd";
        } else {
            schemaLocation = "http://schemas.opengis.net/wfs/1.1.0/wfs.xsd";
        }

        URL getCapsUrl = getOperationURL(GET_CAPABILITIES, GET);
        return new CapabilitiesServiceInfo(schemaLocation, getCapsUrl, capabilities);
    }

    @Override
    public boolean supports(ResultType resultType) {
        switch (resultType) {
        case RESULTS:
            return true;
        case HITS:
            return Versions.v1_0_0.equals(getServiceVersion()) ? false : true;
        default:
            return false;
        }
    }

    @Override
    public Version getServiceVersion() {
        return this.serviceVersion;
    }

    /**
     * @see WFSStrategy#getFeatureTypeNames()
     */
    @Override
    public Set<QName> getFeatureTypeNames() {
        return new HashSet<QName>(typeInfos.keySet());
    }

    /**
     * @see org.geotools.data.wfs.internal.WFSStrategy#getFeatureTypeInfo(javax.xml.namespace.QName)
     */
    @Override
    public FeatureTypeInfo getFeatureTypeInfo(QName typeName) {
        FeatureTypeType eType = typeInfos.get(typeName);
        if (null == eType) {
            throw new IllegalArgumentException("Type name not found: " + typeName);
        }
        return new FeatureTypeInfoImpl(eType);
    }

    /**
     * @see WFSStrategy#getFilterCapabilities()
     */
    @Override
    public FilterCapabilities getFilterCapabilities() {
        FilterCapabilities wfsFilterCapabilities;
        wfsFilterCapabilities = capabilities.getFilterCapabilities();
        return wfsFilterCapabilities;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String getOperationURI(WFSOperationType operation, HttpMethod method) {

        List<OperationType> operations = capabilities.getOperationsMetadata().getOperation();
        for (OperationType op : operations) {
            if (!operation.getName().equals(op.getName())) {
                continue;
            }
            List<DCPType> dcpTypes = op.getDCP();
            if (null == dcpTypes) {
                continue;
            }
            for (DCPType d : dcpTypes) {
                List<RequestMethodType> methods;
                if (HttpMethod.GET.equals(method)) {
                    methods = d.getHTTP().getGet();
                } else {
                    methods = d.getHTTP().getPost();
                }
                if (null == methods || methods.isEmpty()) {
                    continue;
                }

                return methods.get(0).getHref();
            }
        }

        return null;
    }

    /**
     * @see WFSStrategy#getServerSupportedOutputFormats(QName, WFSOperationType)
     */
    @Override
    public Set<String> getServerSupportedOutputFormats(QName typeName, WFSOperationType operation) {

        Set<String> ftypeFormats = new HashSet<String>();

        final Set<String> serviceOutputFormats = getServerSupportedOutputFormats(operation);
        ftypeFormats.addAll(serviceOutputFormats);

        if (GET_FEATURE.equals(operation)) {
            final FeatureTypeInfo typeInfo = getFeatureTypeInfo(typeName);

            final Set<String> typeAdvertisedFormats = typeInfo.getOutputFormats();

            ftypeFormats.addAll(typeAdvertisedFormats);
        }
        return ftypeFormats;
    }

    /**
     * @see #getDefaultOutputFormat
     */
    @Override
    public Set<String> getServerSupportedOutputFormats(final WFSOperationType operation) {
        final OperationType operationMetadata = getOperationMetadata(operation);

        String parameterName;

        final Version serviceVersion = getServiceVersion();
        final boolean wfs1_0 = Versions.v1_0_0.equals(serviceVersion);
        switch (operation) {
        case GET_FEATURE:
            parameterName = wfs1_0 ? "ResultFormat" : "outputFormat";
            break;
        case DESCRIBE_FEATURETYPE:
            parameterName = wfs1_0 ? "SchemaDescriptionLanguage" : "outputFormat";
            break;
        case GET_FEATURE_WITH_LOCK:
            parameterName = wfs1_0 ? "ResultFormat" : "outputFormat";
            break;
        case TRANSACTION:
            parameterName = wfs1_0 ? "" : "inputFormat";
        default:
            throw new UnsupportedOperationException("not yet implemented for " + operation);
        }

        Set<String> serverSupportedFormats;
        serverSupportedFormats = findParameters(operationMetadata, parameterName);
        return serverSupportedFormats;
    }

    @SuppressWarnings("unchecked")
    protected Set<String> findParameters(final OperationType operationMetadata,
            final String parameterName) {
        Set<String> outputFormats = new HashSet<String>();

        List<DomainType> parameters = operationMetadata.getParameter();
        for (DomainType param : parameters) {

            String paramName = param.getName();

            if (parameterName.equals(paramName)) {

                List<String> value = param.getValue();
                outputFormats.addAll(value);
            }
        }
        return outputFormats;
    }

    @Override
    public List<String> getClientSupportedOutputFormats(WFSOperationType operation) {

        List<WFSResponseFactory> operationResponseFactories;
        operationResponseFactories = WFSExtensions.findResponseFactories(operation);

        List<String> outputFormats = new LinkedList<String>();
        for (WFSResponseFactory factory : operationResponseFactories) {
            List<String> factoryFormats = factory.getSupportedOutputFormats();
            outputFormats.addAll(factoryFormats);
        }

        if (GET_FEATURE.equals(operation)) {
            for (String preferred : PREFFERRED_GETFEATURE_FORMATS) {
                boolean hasFormat = outputFormats.remove(preferred);
                if (hasFormat) {
                    outputFormats.add(0, preferred);
                    break;
                }
            }
        }

        return outputFormats;
    }

    /**
     * @return the operation metadata advertised in the capabilities for the given operation
     * @see #getServerSupportedOutputFormats(WFSOperationType)
     */
    protected OperationType getOperationMetadata(final WFSOperationType operation) {
        final OperationsMetadataType operationsMetadata = capabilities.getOperationsMetadata();
        @SuppressWarnings("unchecked")
        final List<OperationType> operations = operationsMetadata.getOperation();
        final String expectedOperationName = operation.getName();
        for (OperationType operationType : operations) {
            String operationName = operationType.getName();
            if (expectedOperationName.equalsIgnoreCase(operationName)) {
                return operationType;
            }
        }
        throw new NoSuchElementException("Operation metadata not found for "
                + expectedOperationName + " in the capabilities document");
    }
}
