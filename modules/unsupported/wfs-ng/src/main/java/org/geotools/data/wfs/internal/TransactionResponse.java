package org.geotools.data.wfs.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import org.geotools.data.ows.HTTPResponse;
import org.geotools.ows.ServiceException;
import org.opengis.filter.identity.FeatureId;

public class TransactionResponse extends WFSResponse{

    public TransactionResponse(HTTPResponse httpResponse, String targetUrl,
            WFSRequest originatingRequest, Charset charset, String contentType, InputStream in)
            throws ServiceException, IOException {
        super(httpResponse, targetUrl, originatingRequest, charset, contentType, in);
         throw new UnsupportedOperationException("Not yet implemented");
    }

    public List<FeatureId> getInsertedFids();

    public int getUpdatedCount();

    public int getDeleteCount();
}
