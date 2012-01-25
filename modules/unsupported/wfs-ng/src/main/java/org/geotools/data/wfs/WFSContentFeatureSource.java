package org.geotools.data.wfs;

import static org.geotools.data.wfs.protocol.WFSOperationType.GET_FEATURE;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.DataSourceException;
import org.geotools.data.DataUtilities;
import org.geotools.data.EmptyFeatureReader;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureSource;
import org.geotools.data.FilteringFeatureReader;
import org.geotools.data.MaxFeatureReader;
import org.geotools.data.Query;
import org.geotools.data.ReTypeFeatureReader;
import org.geotools.data.Transaction;
import org.geotools.data.crs.ReprojectFeatureReader;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.wfs.protocol.GetFeature;
import org.geotools.data.wfs.protocol.GetFeature.ResultType;
import org.geotools.data.wfs.protocol.GetFeatureParser;
import org.geotools.data.wfs.protocol.WFSException;
import org.geotools.data.wfs.protocol.WFSExtensions;
import org.geotools.data.wfs.protocol.WFSProtocol;
import org.geotools.data.wfs.protocol.WFSResponse;
import org.geotools.data.wfs.v1_1_0.GetFeatureQueryAdapter;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.spatial.ReprojectingFilterVisitor;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.gml2.bindings.GML2EncodingUtils;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

public class WFSContentFeatureSource extends ContentFeatureSource {

    private static final Logger LOGGER = Logging.getLogger(WFSContentFeatureSource.class);

    private Integer maxFeaturesHardLimit;

    private boolean preferPostOverGet;

    private final WFSProtocol wfsImpl;

    public WFSContentFeatureSource(final ContentEntry entry, final WFSProtocol wfsImpl,
            final Integer maxFeaturesHardLimit, final boolean preferPostOverGet) {
        super(entry, null);
        this.wfsImpl = wfsImpl;
        this.maxFeaturesHardLimit = maxFeaturesHardLimit;
        this.preferPostOverGet = preferPostOverGet;
    }

    WFSProtocol getWfs() {
        return wfsImpl;
    }

    /**
     * @return {@code true}
     * @see org.geotools.data.store.ContentFeatureSource#canSort()
     */
    @Override
    protected boolean canSort() {
        return true;
    }

    /**
     * @return {@code true}
     * @see org.geotools.data.store.ContentFeatureSource#canRetype()
     */
    @Override
    protected boolean canRetype() {
        return true;
    }

    /**
     * @return {@code true}
     * @see org.geotools.data.store.ContentFeatureSource#canFilter()
     */
    @Override
    protected boolean canFilter() {
        return true;
    }

    /**
     * @return {@code true}
     * @see org.geotools.data.store.ContentFeatureSource#canLimit()
     */
    @Override
    protected boolean canLimit() {
        return true;
    }

    /**
     * @return the WFS advertised bounds of the feature type if
     *         {@code Filter.INCLUDE ==  query.getFilter()}, reprojected to the Query's crs, or
     *         {@code null} otherwise as it would be too expensive to calculate.
     * @see FeatureSource#getBounds(Query)
     * @see org.geotools.data.store.ContentFeatureSource#getBoundsInternal(org.geotools.data.Query)
     */
    @Override
    protected ReferencedEnvelope getBoundsInternal(Query query) throws IOException {
        if (!Filter.INCLUDE.equals(query.getFilter())) {
            return null;
        }

        final String typeName = getEntry().getTypeName();
        final ReferencedEnvelope wgs84Bounds = wfsImpl.getFeatureTypeWGS84Bounds(typeName);

        final CoordinateReferenceSystem ftypeCrs = getFeatureTypeCRS(typeName);

        ReferencedEnvelope nativeBounds;
        try {
            nativeBounds = wgs84Bounds.transform(ftypeCrs, true);
        } catch (TransformException e) {
            LOGGER.log(
                    Level.WARNING,
                    "Can't transform bounds of " + typeName + " to "
                            + wfsImpl.getDefaultCRS(typeName), e);
            nativeBounds = new ReferencedEnvelope(ftypeCrs);
        } catch (FactoryException e) {
            LOGGER.log(
                    Level.WARNING,
                    "Can't transform bounds of " + typeName + " to "
                            + wfsImpl.getDefaultCRS(typeName), e);
            nativeBounds = new ReferencedEnvelope(ftypeCrs);
        }
        return nativeBounds;
    }

    /**
     * @return the remote WFS advertised number of features for the given query only if the query
     *         filter is fully supported AND the wfs returns that information in as an attribute of
     *         the FeatureCollection (since the request is performed with resultType=hits),
     *         otherwise {@code -1} as it would be too expensive to calculate.
     * @see FeatureSource#getCount(Query)
     * @see org.geotools.data.store.ContentFeatureSource#getCountInternal(org.geotools.data.Query)
     */
    @Override
    protected int getCountInternal(Query query) throws IOException {
        final Filter[] filters = wfsImpl.splitFilters(query.getFilter());
        final Filter postFilter = filters[1];
        if (!Filter.INCLUDE.equals(postFilter)) {
            // Filter not fully supported, can't know without a full scan of the results
            return -1;
        }

        final SimpleFeatureType contentType = getQueryType(query);
        WFSResponse response = executeGetFeatures(query, Transaction.AUTO_COMMIT, ResultType.HITS);
        response.setQueryType(contentType);
        response.setRemoteTypeName(wfsImpl.getFeatureTypeName(getEntry().getTypeName()));

        Object process = WFSExtensions.process(response);
        if (!(process instanceof GetFeatureParser)) {
            LOGGER.info("GetFeature with resultType=hits resulted in " + process);
        }
        int hits = ((GetFeatureParser) process).getNumberOfFeatures();
        int maxFeatures = getMaxFeatures(query);
        if (hits != -1 && maxFeatures > 0) {
            hits = Math.min(hits, maxFeatures);
        }
        return hits;
    }

    /**
     * @see FeatureSource#getFeatures(Query)
     * @see org.geotools.data.store.ContentFeatureSource#getReaderInternal(org.geotools.data.Query)
     */
    @Override
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getReaderInternal(Query localQuery)
            throws IOException {

        if (Filter.EXCLUDE.equals(localQuery.getFilter())) {
            return new EmptyFeatureReader<SimpleFeatureType, SimpleFeature>(getSchema());
        }

        localQuery = new Query(localQuery);
        Filter[] filters = wfsImpl.splitFilters(localQuery.getFilter());
        Filter supportedFilter = filters[0];
        Filter postFilter = filters[1];
        System.out.println("Supported filter:  " + supportedFilter);
        System.out.println("Unupported filter: " + postFilter);
        localQuery.setFilter(supportedFilter);
        localQuery.setMaxFeatures(getMaxFeatures(localQuery));

        final CoordinateReferenceSystem queryCrs = localQuery.getCoordinateSystem();

        final SimpleFeatureType contentType = getQueryType(localQuery);

        WFSResponse response = executeGetFeatures(localQuery, transaction, ResultType.RESULTS);
        response.setQueryType(contentType);
        response.setRemoteTypeName(wfsImpl.getFeatureTypeName(getEntry().getTypeName()));

        Object result = WFSExtensions.process(response);

        GetFeatureParser parser;
        if (result instanceof WFSException) {
            // try to recover from common server implementation errors
            throw (WFSException) result;
        } else if (result instanceof GetFeatureParser) {
            parser = (GetFeatureParser) result;
        } else {
            throw new IllegalStateException("Unknown response result for GetFeature: " + result);
        }

        FeatureReader<SimpleFeatureType, SimpleFeature> reader;
        reader = new WFSFeatureReader((GetFeatureParser) parser);

        if (!reader.hasNext()) {
            return new EmptyFeatureReader<SimpleFeatureType, SimpleFeature>(contentType);
        }

        final SimpleFeatureType readerType = reader.getFeatureType();

        CoordinateReferenceSystem readerCrs = readerType.getCoordinateReferenceSystem();
        if (queryCrs != null && !queryCrs.equals(readerCrs)) {
            try {
                reader = new ReprojectFeatureReader(reader, queryCrs);
            } catch (Exception e) {
                throw new DataSourceException(e);
            }
        }

        if (Filter.INCLUDE != postFilter) {
            reader = new FilteringFeatureReader<SimpleFeatureType, SimpleFeature>(reader,
                    postFilter);
        }

        if (!contentType.equals(readerType)) {
            final boolean cloneContents = false;
            reader = new ReTypeFeatureReader(reader, contentType, cloneContents);
        }

        if (maxFeaturesHardLimit != null && maxFeaturesHardLimit.intValue() > 0
                || localQuery.getMaxFeatures() != Integer.MAX_VALUE) {
            int maxFeatures = maxFeaturesHardLimit.intValue() > 0 ? Math.min(
                    maxFeaturesHardLimit.intValue(), localQuery.getMaxFeatures()) : localQuery
                    .getMaxFeatures();
            reader = new MaxFeatureReader<SimpleFeatureType, SimpleFeature>(reader, maxFeatures);
        }
        return reader;
    }

    @Override
    protected SimpleFeatureType buildFeatureType() throws IOException {

        final String localTypeName = super.entry.getTypeName();

        final SimpleFeatureType featureType;
        CoordinateReferenceSystem crs = getFeatureTypeCRS(localTypeName);
        featureType = wfsImpl.issueDescribeFeatureTypeGET(localTypeName, crs);

        // adapt the feature type name
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.init(featureType);
        builder.setName(localTypeName);
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
    }

    /**
     * @return a non null CRS for the feature type, if the actual CRS can't be determined,
     *         {@link DefaultEngineeringCRS#GENERIC_2D} is returned
     * @see WFSDataStore#getFeatureTypeCRS(String)
     */
    protected CoordinateReferenceSystem getFeatureTypeCRS(String localTypeName) {
        final String defaultCRS = wfsImpl.getDefaultCRS(localTypeName);
        CoordinateReferenceSystem crs = null;
        try {
            crs = CRS.decode(defaultCRS);
        } catch (NoSuchAuthorityCodeException e) {
            LOGGER.info("Authority not found for " + localTypeName + " CRS: " + defaultCRS);
            // HACK HACK HACK!: remove when
            // http://jira.codehaus.org/browse/GEOT-1659 is fixed
            if (defaultCRS.toUpperCase().startsWith("URN")) {
                String code = defaultCRS.substring(defaultCRS.lastIndexOf(":") + 1);
                String epsgCode = "EPSG:" + code;
                try {
                    crs = CRS.decode(epsgCode);
                } catch (Exception e1) {
                    LOGGER.log(Level.WARNING, "can't decode CRS " + epsgCode + " for "
                            + localTypeName + ". Assigning DefaultEngineeringCRS.GENERIC_2D: "
                            + DefaultEngineeringCRS.GENERIC_2D);
                    crs = DefaultEngineeringCRS.GENERIC_2D;
                }
            }
        } catch (FactoryException e) {
            LOGGER.log(Level.WARNING, "Error creating CRS " + localTypeName + ": " + defaultCRS, e);
        }
        return crs;
    }

    protected int getMaxFeatures(Query query) {
        int maxFeaturesDataStoreLimit = this.maxFeaturesHardLimit == null ? Query.DEFAULT_MAX
                : maxFeaturesHardLimit.intValue();

        int queryMaxFeatures = query.getMaxFeatures();
        int maxFeatures = Query.DEFAULT_MAX;
        if (Query.DEFAULT_MAX != queryMaxFeatures) {
            maxFeatures = queryMaxFeatures;
        }
        if (maxFeaturesDataStoreLimit > 0) {
            maxFeatures = Math.min(maxFeaturesDataStoreLimit, maxFeatures);
        }
        return maxFeatures;
    }

    private WFSResponse executeGetFeatures(final Query localQuery, final Transaction transaction,
            final ResultType resultType) throws IOException {
        // TODO: handle output format preferences
        final String localTypeName = getEntry().getTypeName();
        final String outputFormat = wfsImpl.getDefaultOutputFormat(GET_FEATURE);

        String srsName = adaptQueryForSupportedCrs(localQuery);

        final Query remoteQuery = new Query(localQuery);
        remoteQuery.setTypeName(localTypeName);

        GetFeature request = new GetFeatureQueryAdapter(remoteQuery, outputFormat, srsName,
                resultType);

        final WFSResponse response = sendGetFeatures(request);
        return response;
    }

    /**
     * Sends the GetFeature request using the appropriate HTTP method depending on the
     * {@link #isPreferPostOverGet()} preference and what the server supports.
     * 
     * @param request
     *            the request to send
     * @param map
     * @return the server response handle
     * @throws IOException
     *             if a communication error occurs. If a server returns an exception report that's a
     *             normal response, no exception will be thrown here.
     */
    private WFSResponse sendGetFeatures(GetFeature request) throws IOException {
        final WFSResponse response;
        if (useHttpPost()) {
            response = wfsImpl.issueGetFeaturePOST(request);
        } else {
            response = wfsImpl.issueGetFeatureGET(request);
        }
        return response;
    }

    /**
     * @return <code>true<code> if HTTP POST method should be used to issue the given WFS operation, <code>false</code>
     *         if HTTP GET method should be used instead
     */
    private boolean useHttpPost() {
        if (preferPostOverGet) {
            if (wfsImpl.supportsOperation(GET_FEATURE, true)) {
                return true;
            }
        }
        if (wfsImpl.supportsOperation(GET_FEATURE, false)) {
            return false;
        }
        throw new IllegalArgumentException("Neither POST nor GET method is supported for the "
                + GET_FEATURE + " operation by the server");
    }

    /**
     * Checks if the query requested CRS is supported by the query feature type and if not, adapts
     * the query to the feature type default CRS, returning the CRS identifier to use for the WFS
     * query.
     * <p>
     * If the query CRS is not advertised as supported in the WFS capabilities for the requested
     * feature type, the query filter is modified so that any geometry literal is reprojected to the
     * default CRS for the feature type, otherwise the query is not modified at all. In any case,
     * the crs identifier to actually use in the WFS GetFeature operation is returned.
     * </p>
     * 
     * @param query
     * @return
     * @throws IOException
     */
    private String adaptQueryForSupportedCrs(Query query) throws IOException {

        final String localTypeName = getEntry().getTypeName();
        // The CRS the query is performed in
        final CoordinateReferenceSystem queryCrs = query.getCoordinateSystem();
        final String defaultCrs = wfsImpl.getDefaultCRS(localTypeName);

        if (queryCrs == null) {
            LOGGER.warning("Query does not provide a CRS, using default: " + query);
            return defaultCrs;
        }

        String epsgCode;

        final CoordinateReferenceSystem crsNative = getFeatureTypeCRS(localTypeName);

        if (CRS.equalsIgnoreMetadata(queryCrs, crsNative)) {
            epsgCode = defaultCrs;
            LOGGER.fine("request and native crs for " + localTypeName + " are the same: "
                    + epsgCode);
        } else {
            boolean transform = false;
            epsgCode = GML2EncodingUtils.epsgCode(queryCrs);
            if (epsgCode == null) {
                LOGGER.fine("Can't find the identifier for the request CRS, "
                        + "query will be performed in native CRS");
                transform = true;
            } else {
                epsgCode = "EPSG:" + epsgCode;
                LOGGER.fine("Request CRS is " + epsgCode + ", checking if its supported for "
                        + localTypeName);

                Set<String> supportedCRSIdentifiers = wfsImpl
                        .getSupportedCRSIdentifiers(localTypeName);
                if (supportedCRSIdentifiers.contains(epsgCode)) {
                    LOGGER.fine(epsgCode + " is supported, request will be performed asking "
                            + "for reprojection over it");
                } else {
                    LOGGER.fine(epsgCode + " is not supported for " + localTypeName
                            + ". Query will be adapted to default CRS " + defaultCrs);
                    transform = true;
                }
                if (transform) {
                    epsgCode = defaultCrs;
                    FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);
                    SimpleFeatureType ftype = getSchema();
                    ReprojectingFilterVisitor visitor = new ReprojectingFilterVisitor(ff, ftype);
                    Filter filter = query.getFilter();
                    Filter reprojectedFilter = (Filter) filter.accept(visitor, null);
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.finer("Original Filter: " + filter + "\nReprojected filter: "
                                + reprojectedFilter);
                    }
                    LOGGER.fine("Query filter reprojected to native CRS for " + localTypeName);
                    query.setFilter(reprojectedFilter);
                }
            }
        }
        return epsgCode;
    }

    /**
     * Returns the feature type that shall result of issueing the given request, adapting the
     * original feature type for the request's type name in terms of the query CRS and requested
     * attributes.
     * 
     * @param query
     * @return
     * @throws IOException
     */
    SimpleFeatureType getQueryType(final Query query) throws IOException {

        final SimpleFeatureType featureType = getSchema();
        final CoordinateReferenceSystem coordinateSystemReproject = query
                .getCoordinateSystemReproject();

        String[] propertyNames = query.getPropertyNames();

        SimpleFeatureType queryType = featureType;
        if (propertyNames != null && propertyNames.length > 0) {
            try {
                queryType = DataUtilities.createSubType(queryType, propertyNames);
            } catch (SchemaException e) {
                throw new DataSourceException(e);
            }
        } else {
            propertyNames = DataUtilities.attributeNames(featureType);
        }

        if (coordinateSystemReproject != null) {
            try {
                queryType = DataUtilities.createSubType(queryType, propertyNames,
                        coordinateSystemReproject);
            } catch (SchemaException e) {
                throw new DataSourceException(e);
            }
        }

        return queryType;
    }

}
