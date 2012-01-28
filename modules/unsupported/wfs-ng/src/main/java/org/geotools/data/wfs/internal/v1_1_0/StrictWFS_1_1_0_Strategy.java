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

import java.util.Set;

import net.opengis.wfs.GetFeatureType;

import org.geotools.data.wfs.internal.AbstractWFSStrategy;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.filter.v1_1.OGCConfiguration;
import org.geotools.util.Version;
import org.geotools.wfs.v1_1.WFSConfiguration;
import org.geotools.xml.Configuration;
import org.opengis.filter.Filter;

/**
 * {@link WFSStrategy} implementation to talk to a WFS 1.1.0 server leveraging the GeoTools
 * {@code xml-xsd} subsystem for schema assisted parsing and encoding of WFS requests and responses.
 * <p>
 * Additional extension hooks:
 * <ul>
 * <li> {@link #supportsGet()}
 * <li> {@link #supportsPost()}
 * <li> {@link #createGetFeatureRequest(GetFeatureRequest)}
 * <li> {@link #buildGetFeatureParametersForGet(GetFeatureType)}
 * <li> {@link #encodeGetFeatureGetFilter(Filter)}
 * </ul>
 * </p>
 * 
 * @author groldan
 */
public class StrictWFS_1_1_0_Strategy extends AbstractWFSStrategy {

    protected static final String DEFAULT_OUTPUT_FORMAT = "text/xml; subtype=gml/3.1.1";

    public StrictWFS_1_1_0_Strategy() {
        super();
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

}
