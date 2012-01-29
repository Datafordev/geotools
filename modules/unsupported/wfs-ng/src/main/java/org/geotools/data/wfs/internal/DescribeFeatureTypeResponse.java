package org.geotools.data.wfs.internal;

import java.io.IOException;
import java.io.InputStream;

import org.geotools.data.ows.HTTPResponse;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.ows.ServiceException;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class DescribeFeatureTypeResponse extends WFSResponse {

    private FeatureType parsed;

    public DescribeFeatureTypeResponse(final DescribeFeatureTypeRequest request,
            final HTTPResponse httpResponse, InputStream in) throws ServiceException, IOException {

        super(request, httpResponse, in);

        final WFSStrategy strategy = request.getStrategy();
        try {

            final SimpleFeatureType featureType;
            CoordinateReferenceSystem crs = getFeatureTypeCRS(remoteTypeName);
            featureType = client.issueDescribeFeatureTypeGET(remoteTypeName, crs);

            // adapt the feature type name
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.init(featureType);
            builder.setName(remoteTypeName);
            String namespaceOverride = entry.getName().getNamespaceURI();
            if (namespaceOverride != null) {
                builder.setNamespaceURI(namespaceOverride);
            }
            GeometryDescriptor defaultGeometry = featureType.getGeometryDescriptor();
            if (defaultGeometry != null) {
                builder.setDefaultGeometry(defaultGeometry.getLocalName());
                builder.setCRS(defaultGeometry.getCoordinateReferenceSystem());
            }
            final SimpleFeatureType adaptedFeatureType = builder.buildFeatureType();
            return adaptedFeatureType;

        } finally {
            dispose();
        }

    }

    public FeatureType getFeatureType() {
        return parsed;
    }
}
