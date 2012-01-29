package org.geotools.data.wfs.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.geotools.data.ows.HTTPResponse;
import org.geotools.ows.ServiceException;

import com.vividsolutions.jts.geom.GeometryFactory;

public class GetFeatureResponse extends WFSResponse {

    public GetFeatureResponse(HTTPResponse httpResponse, String targetUrl,
            WFSRequest originatingRequest, Charset charset, String contentType, InputStream in)
            throws ServiceException, IOException {

        super(httpResponse, targetUrl, originatingRequest, charset, contentType, in);
    }

    public Integer getNumberOfFeatures() {
        // TODO Auto-generated method stub
        return null;
    }


    public GetFeatureParser getFeatures(GeometryFactory geometryFactory) {
        // TODO Auto-generated method stub
        return null;
    }

    public GetFeatureParser getSimpleFeatures(GeometryFactory geometryFactory) {
        // TODO Auto-generated method stub
        return null;
    }

}
