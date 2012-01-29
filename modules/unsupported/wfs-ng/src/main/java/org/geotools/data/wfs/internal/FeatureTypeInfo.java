package org.geotools.data.wfs.internal;

import java.util.List;

import org.geotools.data.ResourceInfo;
import org.geotools.geometry.jts.ReferencedEnvelope;

public interface FeatureTypeInfo extends ResourceInfo{

    ReferencedEnvelope getWGS84BoundingBox();

    String getDefaultSRS();

    List<String> getOtherSRS();

}
