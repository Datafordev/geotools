package org.geotools.data.wfs.internal;

import static org.geotools.data.wfs.internal.HttpMethod.GET;
import static org.geotools.data.wfs.internal.HttpMethod.POST;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Level;

import javax.xml.namespace.QName;

import org.apache.commons.io.IOUtils;
import org.geotools.data.ows.AbstractRequest;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.data.ows.Request;
import org.geotools.factory.FactoryNotFoundException;

public abstract class WFSRequest extends AbstractRequest implements Request {

    protected final WFSStrategy strategy;

    protected final WFSOperationType operation;

    protected final WFSConfig config;

    private final boolean doPost;

    private QName typeName;

    private String outputFormat;

    public WFSRequest(final WFSOperationType operation, final WFSConfig config,
            final WFSStrategy strategy) {

        super(url(operation, config, strategy), (Properties) null);
        this.operation = operation;
        this.config = config;
        this.strategy = strategy;

        switch (config.getPreferredMethod()) {
        case HTTP_POST:
            this.doPost = strategy.supportsOperation(operation, POST);
            break;
        case HTTP_GET:
            this.doPost = !strategy.supportsOperation(operation, GET);
            break;
        default:
            this.doPost = strategy.supportsOperation(operation, POST);
            break;
        }

        this.outputFormat = strategy.getDefaultOutputFormat(operation);

        setProperty(SERVICE, "WFS");
        setProperty(VERSION, strategy.getVersion());
        setProperty(REQUEST, operation.getName());

    }

    public String getOutputFormat() {
        return outputFormat;
    }

    /**
     * @param outputFormat
     *            the outputFormat to set
     */
    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public void setTypeName(QName typeName) {
        this.typeName = typeName;
    }

    public QName getTypeName() {
        return typeName;
    }

    public WFSStrategy getStrategy() {
        return strategy;
    }

    private static URL url(final WFSOperationType operation, final WFSConfig config,
            final WFSStrategy strategy) {

        final boolean suportsGet = strategy.supportsOperation(operation, GET);
        final boolean suportsPost = strategy.supportsOperation(operation, POST);
        if (!(suportsGet || suportsPost)) {
            throw new IllegalArgumentException("WFS doesn't support " + operation.getName());
        }

        HttpMethod method;
        switch (config.getPreferredMethod()) {
        case AUTO:
        case HTTP_POST:
            method = suportsPost ? POST : GET;
            break;
        default:
            method = suportsGet ? GET : POST;
            break;
        }

        URL targetUrl = strategy.getOperationURL(operation, method);

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
    }

    @Override
    protected void initVersion() {
    }

    @Override
    protected void initRequest() {
    }

    @Override
    public URL getFinalURL() {
        if (requiresPost()) {
            return super.getFinalURL();
        }

        URL finalURL = strategy.buildUrlGET(this);
        return finalURL;
    }

    @Override
    public String getPostContentType() {
        return getOutputFormat();
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

        WFSResponseFactory responseFactory;
        try {
            responseFactory = WFSExtensions.findResponseFactory(this, contentType);
        } catch (FactoryNotFoundException fnf) {
            Loggers.MODULE.log(Level.WARNING, fnf.getMessage());
            try {
                if (contentType != null && contentType.startsWith("text")) {
                    byte buff[] = new byte[1024];
                    response.getResponseStream().read(buff);
                    Loggers.MODULE.info("Failed response snippet: " + new String(buff));
                }
                throw fnf;
            } catch (Exception ignore) {
                throw fnf;
            }
        }

        WFSResponse wfsResponse = responseFactory.createResponse(this, response);

        return wfsResponse;
    }
}
