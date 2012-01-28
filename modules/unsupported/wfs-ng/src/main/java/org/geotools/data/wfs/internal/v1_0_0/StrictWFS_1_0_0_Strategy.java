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

import java.util.Set;

import org.geotools.data.wfs.internal.AbstractWFSStrategy;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.data.wfs.internal.Versions;
import org.geotools.data.wfs.internal.WFSOperationType;
import org.geotools.data.wfs.internal.WFSStrategy;
import org.geotools.filter.v1_0.OGCConfiguration;
import org.geotools.util.Version;
import org.geotools.wfs.v1_0.WFSConfiguration;
import org.geotools.xml.Configuration;

/**
 * 
 */
public class StrictWFS_1_0_0_Strategy extends AbstractWFSStrategy {

    public StrictWFS_1_0_0_Strategy() {
        super();
    }

    /*---------------------------------------------------------------------
     * AbstractWFSStrategy methods
     * ---------------------------------------------------------------------*/
    @Override
    protected Configuration getFilterConfiguration() {
        return FILTER_1_0_0_CONFIGURATION;
    }

    @Override
    protected Configuration getWfsConfiguration() {
        return WFS_1_0_0_CONFIGURATION;
    }

    /*---------------------------------------------------------------------
     * WFSStrategy methods
     * ---------------------------------------------------------------------*/

    @Override
    public boolean supports(ResultType resultType) {
        switch (resultType) {
        case RESULTS:
            return true;
        default:
            return false;
        }
    }

    @Override
    public Version getServiceVersion() {
        return Versions.v1_0_0;
    }

    @Override
    public String getDefaultOutputFormat(WFSOperationType operation) {
        switch (operation) {
        case GET_FEATURE:
            Set<String> supportedOutputFormats = getSupportedGetFeatureOutputFormats();
            if (supportedOutputFormats.contains("GML3")) {
                return "GML3";
            } else if (supportedOutputFormats.contains("GML2")) {
                return "GML2";
            } else {
                throw new IllegalArgumentException(
                        "Server does not support 'GML2' or 'GML3' output formats: "
                                + supportedOutputFormats);
            }

        default:
            throw new UnsupportedOperationException(
                    "Not implemented for other than GET_FEATURE yet");
        }
    }
}
