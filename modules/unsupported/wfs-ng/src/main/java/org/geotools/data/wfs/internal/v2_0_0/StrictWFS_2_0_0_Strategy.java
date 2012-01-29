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
package org.geotools.data.wfs.internal.v2_0_0;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import net.opengis.fes20.FilterCapabilitiesType;
import net.opengis.wfs20.FeatureTypeListType;
import net.opengis.wfs20.FeatureTypeType;
import net.opengis.wfs20.WFSCapabilitiesType;

import org.geotools.data.wfs.impl.WFSServiceInfo;
import org.geotools.data.wfs.internal.AbstractWFSStrategy;
import org.geotools.data.wfs.internal.FeatureTypeInfo;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.util.Version;
import org.geotools.xml.Configuration;
import org.opengis.filter.capability.FilterCapabilities;

/**
 * 
 */
public class StrictWFS_2_0_0_Strategy extends AbstractWFSStrategy {

    private static final List<String> PREFERRED_FORMATS = Collections.unmodifiableList(Arrays
            .asList("text/xml; subtype=gml/3.2", "application/gml+xml; version=3.2", "gml32",
                    "text/xml; subtype=gml/3.1.1", "gml3", "text/xml; subtype=gml/2.1.2", "GML2"));

    private net.opengis.wfs20.WFSCapabilitiesType capabilities;

    private final Map<QName, FeatureTypeType> typeInfos;

    public StrictWFS_2_0_0_Strategy() {
        super();
        typeInfos = new HashMap<QName, FeatureTypeType>();
    }

    /*---------------------------------------------------------------------
     * AbstractWFSStrategy methods
     * ---------------------------------------------------------------------*/
    @Override
    protected Configuration getFilterConfiguration() {
        return FILTER_2_0_0_CONFIGURATION;
    }

    @Override
    protected Configuration getWfsConfiguration() {
        return WFS_2_0_0_CONFIGURATION;
    }

    /*---------------------------------------------------------------------
     * WFSStrategy methods
     * ---------------------------------------------------------------------*/

    @Override
    public void setCapabilities(WFSGetCapabilities capabilities) {
        net.opengis.wfs20.WFSCapabilitiesType caps = (WFSCapabilitiesType) capabilities
                .getParsedCapabilities();
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
        return new Capabilities200ServiceInfo("http://schemas.opengis.net/wfs/2.0/wfs.xsd",
                getCapsUrl, capabilities);
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

    @Override
    public Version getServiceVersion() {
        return Versions.v2_0_0;
    }

    @Override
    public String getDefaultOutputFormat(WFSOperationType operation) {
        switch (operation) {
        case GET_FEATURE:
            Set<String> serverFormats = getSupportedGetFeatureOutputFormats();
            String outputFormat = findExact(PREFERRED_FORMATS, serverFormats);
            if (outputFormat == null) {
                outputFormat = findFuzzy(PREFERRED_FORMATS, serverFormats);
                if (outputFormat != null) {
                    // TODO: log
                }
            }
            if (outputFormat == null) {
                throw new IllegalArgumentException(
                        "Server does not support any of the client's supported formats: "
                                + serverFormats);
            }
            return outputFormat;

        default:
            throw new UnsupportedOperationException(
                    "Not implemented for other than GET_FEATURE yet");
        }
    }

    private String findExact(List<String> preferredFormats, Set<String> serverFormats) {
        for (String preferred : preferredFormats) {
            for (String serverFormat : serverFormats) {
                if (serverFormat.equalsIgnoreCase(preferred)) {
                    return preferred;
                }
            }
        }
        return null;
    }

    private String findFuzzy(List<String> preferredFormats, Set<String> serverFormats) {
        for (String preferred : preferredFormats) {

            preferred = preferred.toLowerCase();

            for (String serverFormat : serverFormats) {
                if (serverFormat.toLowerCase().startsWith(preferred)) {
                    return preferred;
                }
            }
        }
        return null;
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
    public FilterCapabilities getFilterCapabilities() {
        FilterCapabilitiesType filterCapabilities = capabilities.getFilterCapabilities();
        //TODO
        throw new UnsupportedOperationException();
    }
}
