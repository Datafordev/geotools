package org.geotools.data.wfs.internal;

import java.io.IOException;
import java.net.URL;

import org.geotools.data.ows.AbstractGetCapabilitiesRequest;
import org.geotools.data.ows.GetCapabilitiesRequest;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.ows.Request;
import org.geotools.data.ows.Response;
import org.geotools.ows.ServiceException;

public class WFSGetCapabilitiesRequest extends AbstractGetCapabilitiesRequest implements
        GetCapabilitiesRequest {

    private final WFSStrategy wfsStrategy;

    public WFSGetCapabilitiesRequest(WFSStrategy wfsStrategy, URL serverURL) {
        super(serverURL);
        this.wfsStrategy = wfsStrategy;
        setProperty(VERSION, wfsStrategy.getVersion());
    }

    @Override
    protected void initService() {
        setProperty(Request.SERVICE, "WFS");
    }

    @Override
    protected void initRequest() {
        super.initRequest();
    }

    @Override
    protected void initVersion() {
        // do nothing, wfsStrategy is not set yet, this method is called by the supe constructor
    }

    @Override
    public Response createResponse(HTTPResponse response) throws ServiceException, IOException {
        return new WFSGetCapabilitiesResponse(response);
    }

}
