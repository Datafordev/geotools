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
package org.geotools.data.wfs.internal.v1_x;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;

import org.eclipse.emf.ecore.EObject;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.data.wfs.internal.WFSRequest;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.xml.Configuration;
import org.geotools.xml.Encoder;
import org.opengis.filter.BinaryLogicOperator;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.Or;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.xml.sax.SAXException;

/**
 * A strategy object to aid in querying a CubeWerx WFS 1.1 server
 * <p>
 * This strategy was created as per the limitations encountered at the CubeWerx server being tested
 * while developing this plugin.
 * </p>
 * <p>
 * For instance, the following issues were found:
 * <ul>
 * <li>resultType parameter is not supported in GetFeature
 * <li>Logically grouped spatial filters can't be handled
 * <li>CubeWerx does not support logical filters containing mixed geometry filters (eg, AND(BBOX,
 * Intersects)), no matter what the capabilities doc says
 * </ul>
 * </p>
 * 
 * @author groldan
 */
public class CubeWerxStrategy extends StrictWFS_1_x_Strategy {

    /**
     * @return {@code true} only if resultType == results, CubeWerx throws a service exception if
     *         the resultType parameter is set on a POST request, no matter it's value, and on a GET
     *         request it's just ignored; also the returned feature collection does not contain the
     *         number of features matched.
     */
    @Override
    public boolean supports(ResultType resultType) {
        return ResultType.RESULTS.equals(resultType);
    }

    @Override
    protected Map<String, String> buildGetFeatureParametersForGet(GetFeatureRequest request) {
        Map<String, String> params = super.buildGetFeatureParametersForGet(request);
        params.remove("RESULTTYPE");
        return params;
    }

    @Override
    public void encode(final WFSRequest request, final EObject requestObject, final OutputStream out)
            throws IOException {
        if (!(request instanceof GetFeatureRequest)) {
            super.encode(request, requestObject, out);
            return;
        }

        final Configuration configuration = getWfsConfiguration();
        Encoder encoder = new Encoder(configuration);

        final QName opName = getOperationName(request.getOperation());
        QName typeName = request.getTypeName();
        if (typeName != null && !XMLConstants.NULL_NS_URI.equals(typeName.getNamespaceURI())) {
            String prefix = typeName.getPrefix();
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
                prefix = "type_ns";
            }
            String namespaceURI = typeName.getNamespaceURI();
            encoder.getNamespaces().declarePrefix(prefix, namespaceURI);
        }

        Document dom;
        try {
            dom = encoder.encodeAsDOM(requestObject, opName);
        } catch (SAXException e) {
            throw new IOException(e);
        } catch (TransformerException e) {
            throw new IOException(e);
        }

        dom.getDocumentElement().removeAttribute("resultType");
        DOMImplementationLS domImpl = (DOMImplementationLS) dom.getImplementation();//safe cast as long as we're on Java6

        LSOutput destination = domImpl.createLSOutput();
        destination.setByteStream(out);
        domImpl.createLSSerializer().write(dom, destination);
    }

    @Override
    public Filter[] splitFilters(final QName typeName, final Filter queryFilter) {

        if (!(queryFilter instanceof BinaryLogicOperator)) {
            return super.splitFilters(typeName, queryFilter);
        }

        int spatialFiltersCount = 0;
        // if a logical operator, check no more than one geometry filter is enclosed on it
        List<Filter> children = ((BinaryLogicOperator) queryFilter).getChildren();
        for (Filter f : children) {
            if (f instanceof BinarySpatialOperator) {
                spatialFiltersCount++;
            }
        }
        if (spatialFiltersCount <= 1) {
            return super.splitFilters(typeName, queryFilter);
        }

        Filter serverFilter;
        Filter postFilter;
        if (queryFilter instanceof Or) {
            // can't know...
            serverFilter = Filter.INCLUDE;
            postFilter = queryFilter;
        } else {
            // its an And..
            List<Filter> serverChild = new ArrayList<Filter>();
            List<Filter> postChild = new ArrayList<Filter>();
            boolean spatialAdded = false;
            for (Filter f : children) {
                if (f instanceof BinarySpatialOperator) {
                    if (spatialAdded) {
                        postChild.add(f);
                    } else {
                        serverChild.add(f);
                        spatialAdded = true;
                    }
                } else {
                    serverChild.add(f);
                }
            }
            FilterFactory ff = CommonFactoryFinder.getFilterFactory(null);
            serverFilter = ff.and(serverChild);
            postFilter = ff.and(postChild);
            SimplifyingFilterVisitor sfv = new SimplifyingFilterVisitor();
            serverFilter = (Filter) serverFilter.accept(sfv, null);
            postFilter = (Filter) postFilter.accept(sfv, null);
        }

        return new Filter[] { serverFilter, postFilter };
    }
}
