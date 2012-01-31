package org.geotools.data.wfs.internal;

import static org.geotools.data.wfs.internal.HttpMethod.*;
import static org.geotools.data.wfs.internal.WFSOperationType.GET_FEATURE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.geotools.data.ows.AbstractRequest;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.ows.Request;

public abstract class WFSRequest extends AbstractRequest implements Request {

    protected final WFSStrategy strategy;

    protected final WFSOperationType operation;

    protected final WFSConfig config;

    private final boolean doPost;

    public WFSRequest(WFSOperationType operation, final WFSConfig config, final WFSStrategy strategy) {
        super(url(operation, config, strategy), (Properties) null);
        this.operation = operation;
        this.config = config;
        this.strategy = strategy;

        if (!config.isPreferPostOverGet()) {
            this.doPost = !strategy.supportsOperation(operation, GET);
        } else {
            this.doPost = strategy.supportsOperation(operation, POST);
        }
    }

    public WFSStrategy getStrategy() {
        return strategy;
    }

    private static URL url(final WFSOperationType operation, final WFSConfig config,
            final WFSStrategy strategy) {

        if (!strategy.supportsOperation(operation, GET)
                && !strategy.supportsOperation(operation, POST)) {
            throw new IllegalArgumentException("WFS doesn't support " + operation.getName());
        }

        HttpMethod method;
        if (!config.isPreferPostOverGet()) {
            method = !strategy.supportsOperation(GET_FEATURE, GET) ? POST : GET;
        } else {
            method = strategy.supportsOperation(GET_FEATURE, POST) ? POST : GET;
        }

        URL targetUrl = strategy.getOperationURL(WFSOperationType.GET_FEATURE, method);

        return targetUrl;
    }

    public WFSOperationType getOperation() {
        return operation;
    }

    @Override
    public boolean requiresPost() {
        return doPost;
    }

    @Override
    protected void initService() {
        setProperty(SERVICE, "WFS");
    }

    @Override
    protected void initVersion() {
        setProperty(VERSION, strategy.getVersion());
    }

    @Override
    protected void initRequest() {
        setProperty(REQUEST, operation.getName());
    }

    @Override
    public URL getFinalURL() {
        if (requiresPost()) {
            return super.onlineResource;
        }

        URL finalURL = strategy.buildUrlGET(this);
        return finalURL;
    }

    @Override
    public String getPostContentType() {
        String postContentType = strategy.getPostContentType(this);
        return postContentType;
    }

    @Override
    public void performPostOutput(OutputStream outputStream) throws IOException {

        InputStream in = strategy.getPostContents(this);
        try {
            IOUtils.copy(in, outputStream);
        } finally {
            in.close();
        }
    }

    @Override
    public WFSResponse createResponse(HTTPResponse response) throws IOException {

        final String contentType = response.getContentType();

        WFSResponseFactory responseFactory = WFSExtensions.findResponseFactory(this, contentType);

        WFSResponse wfsResponse = responseFactory.createResponse(this, response);

        return wfsResponse;
    }

}
