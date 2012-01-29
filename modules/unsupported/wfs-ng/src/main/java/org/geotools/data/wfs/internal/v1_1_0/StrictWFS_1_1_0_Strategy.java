/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2008, Open Source Geospatial Foundation (OSGeo)
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
package org.geotools.data.wfs.internal.v1_1_0;

import static org.geotools.data.wfs.internal.GetFeatureRequest.ResultType.RESULTS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import net.opengis.ows10.DCPType;
import net.opengis.ows10.DomainType;
import net.opengis.ows10.OperationType;
import net.opengis.ows10.RequestMethodType;
import net.opengis.wfs.FeatureTypeListType;
import net.opengis.wfs.FeatureTypeType;
import net.opengis.wfs.GetFeatureType;
import net.opengis.wfs.OutputFormatListType;
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
import org.geotools.data.wfs.internal.HttpMethod;
import org.geotools.data.wfs.internal.RequestComponents;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.data.wfs.internal.v1_0_0.CapabilitiesServiceInfo;
import org.geotools.data.wfs.internal.v1_0_0.FeatureTypeInfoImpl;
import org.geotools.factory.GeoTools;
import org.geotools.util.Version;
import org.geotools.xml.Configuration;
import org.opengis.filter.Filter;
import org.opengis.filter.capability.FilterCapabilities;
import org.opengis.filter.sort.SortBy;

/**
 * {@link WFSStrategy} implementation to talk to a WFS 1.1.0 server leveraging the GeoTools
 * {@code xml-xsd} subsystem for schema assisted parsing and encoding of WFS requests and responses.
 * <p>
 * Additional extension hooks:
 * <ul>
 * <li> {@link #supportsGet()}
 * <li> {@link #supportsPost()}
 * <li> {@link #buildGetFeatureRequest(GetFeatureRequest)}
 * <li> {@link #buildGetFeatureParametersForGet(GetFeatureType)}
 * <li> {@link #encodeGetFeatureGetFilter(Filter)}
 * </ul>
 * </p>
 * 
 * @author groldan
 */
public class StrictWFS_1_1_0_Strategy extends AbstractWFSStrategy {

    protected static final String DEFAULT_OUTPUT_FORMAT = "text/xml; subtype=gml/3.1.1";

    /**
     * The WFS GetCapabilities document. Final by now, as we're not handling updatesequence, so will
     * not ask the server for an updated capabilities during the life-time of this datastore.
     */
    protected net.opengis.wfs.WFSCapabilitiesType capabilities;

    private final Map<QName, FeatureTypeType> typeInfos;

    public StrictWFS_1_1_0_Strategy() {
        super();
        typeInfos = new HashMap<QName, FeatureTypeType>();
    }

    /*---------------------------------------------------------------------
     * AbstractWFSStrategy methods
     * ---------------------------------------------------------------------*/
    @Override
    protected Configuration getFilterConfiguration() {
        return FILTER_1_1_0_CONFIGURATION;
    }

    @Override
    protected Configuration getWfsConfiguration() {
        return WFS_1_1_0_CONFIGURATION;
    }

    /*---------------------------------------------------------------------
     * WFSStrategy methods
     * ---------------------------------------------------------------------*/

    @Override
    public void setCapabilities(WFSGetCapabilities capabilities) {
        WFSCapabilitiesType caps = (WFSCapabilitiesType) capabilities.getParsedCapabilities();
        this.capabilities = caps;

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
        URL getCapsUrl = getOperationURL(WFSOperationType.GET_CAPABILITIES, false);
        return new CapabilitiesServiceInfo("http://schemas.opengis.net/wfs/1.1.0/wfs.xsd",
                getCapsUrl, capabilities);
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

    @Override
    public boolean supports(ResultType resultType) {
        switch (resultType) {
        case RESULTS:
        case HITS:
            return true;
        default:
            return false;
        }
    }

    /**
     * @return {@link Versions#v1_1_0}
     * @see WFSStrategy#getServiceVersion()
     */
    @Override
    public Version getServiceVersion() {
        return Versions.v1_1_0;
    }

    @Override
    public String getDefaultOutputFormat(WFSOperationType operation) {
        switch (operation) {
        case GET_FEATURE:
            Set<String> supportedOutputFormats = getSupportedGetFeatureOutputFormats();
            if (supportedOutputFormats.contains(DEFAULT_OUTPUT_FORMAT)) {
                return DEFAULT_OUTPUT_FORMAT;
            } else {

                throw new IllegalArgumentException("Server does not support '"
                        + DEFAULT_OUTPUT_FORMAT + "' output format: " + supportedOutputFormats);

            }

        default:
            throw new UnsupportedOperationException(
                    "Not implemented for other than GET_FEATURE yet");
        }
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

    /**
     * @see WFSStrategy#getSupportedOutputFormats(QName, WFSOperationType)
     */
    @Override
    public Set<String> getSupportedOutputFormats(QName typeName, WFSOperationType operation) {
        final Set<String> serviceOutputFormats = getSupportedGetFeatureOutputFormats();
        final FeatureTypeInfo typeInfo = getFeatureTypeInfo(typeName);
        final OutputFormatListType outputFormats = typeInfo.getOutputFormats();

        Set<String> ftypeFormats = new HashSet<String>();
        if (outputFormats != null) {
            @SuppressWarnings("unchecked")
            List<String> ftypeDeclaredFormats = outputFormats.getFormat();
            ftypeFormats.addAll(ftypeDeclaredFormats);
        }

        ftypeFormats.addAll(serviceOutputFormats);
        return ftypeFormats;
    }

    private Set<String> getSupportedGetFeatureOutputFormats() {
        OperationType operationMetadata = getOperationMetadata(WFSOperationType.GET_FEATURE);
        @SuppressWarnings("unchecked")
        List<DomainType> parameters = operationMetadata.getParameter();
        @SuppressWarnings("unchecked")
        List<FeatureTypeType> featuretypes = capabilities.getFeatureTypeList().getFeatureType();

        List<String> supportedByAllFeatureTypes = null;
        for (int i = 0; i < featuretypes.size(); i++) {
            net.opengis.wfs.FeatureTypeType ft = (FeatureTypeType) featuretypes.get(i);
            if (ft.getOutputFormats() != null) {
                @SuppressWarnings("unchecked")
                List<String> value = ft.getOutputFormats().getFormat();
                if (supportedByAllFeatureTypes == null) {
                    supportedByAllFeatureTypes = value;
                } else {
                    List<String> removeOutputFormats = new ArrayList<String>();
                    for (String o : supportedByAllFeatureTypes) {
                        if (!value.contains(o)) {
                            removeOutputFormats.add(o);
                        }
                    }
                    for (Object o : removeOutputFormats) {
                        supportedByAllFeatureTypes.remove(o);
                    }
                    if (supportedByAllFeatureTypes.size() == 0) {
                        break;
                    }
                }
            }
        }

        Set<String> outputFormats = new HashSet<String>();
        for (DomainType param : parameters) {
            String paramName = param.getName();
            if ("outputFormat".equals(paramName)) {
                @SuppressWarnings("unchecked")
                List<String> value = param.getValue();
                outputFormats.addAll(value);
            }
        }
        if (supportedByAllFeatureTypes != null)
            outputFormats.addAll(supportedByAllFeatureTypes);
        return outputFormats;
    }



    @SuppressWarnings("unchecked")
    @Override
    protected EObject createGetFeatureRequestPost(GetFeatureRequest query) throws IOException {
        final WfsFactory factory = WfsFactory.eINSTANCE;

        GetFeatureType getFeature = factory.createGetFeatureType();
        getFeature.setService("WFS");
        getFeature.setVersion(getServiceVersion().toString());
        getFeature.setOutputFormat(query.getOutputFormat());

        getFeature.setHandle("GeoTools " + GeoTools.getVersion() + " WFS DataStore");
        Integer maxFeatures = query.getMaxFeatures();
        if (maxFeatures != null) {
            getFeature.setMaxFeatures(BigInteger.valueOf(maxFeatures.intValue()));
        }

        ResultType resultType = query.getResultType();
        getFeature.setResultType(RESULTS == resultType ? ResultTypeType.RESULTS_LITERAL
                : ResultTypeType.HITS_LITERAL);

        QueryType wfsQuery = factory.createQueryType();
        wfsQuery.setTypeName(Collections.singletonList(query.getTypeName()));

        Filter serverFilter = query.getFilter();
        if (!Filter.INCLUDE.equals(serverFilter)) {
            wfsQuery.setFilter(serverFilter);
        }
        String srsName = query.getSrsName();
        try {
            wfsQuery.setSrsName(new URI(srsName));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Can't create a URI from the query CRS: " + srsName, e);
        }
        String[] propertyNames = query.getPropertyNames();
        boolean retrieveAllProperties = propertyNames == null;
        if (!retrieveAllProperties) {
            @SuppressWarnings("unchecked")
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
}
