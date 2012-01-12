/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2005-2008, Open Source Geospatial Foundation (OSGeo)
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
 *
 *    This package contains documentation from OpenGIS specifications.
 *    OpenGIS consortium's work is fully acknowledged here.
 */
package org.geotools.referencing.factory;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.measure.unit.Unit;

import org.geotools.factory.BufferedFactory;
import org.geotools.factory.FactoryRegistryException;
import org.geotools.factory.GeoTools;
import org.geotools.factory.Hints;
import org.geotools.util.ObjectCaches;
import org.opengis.metadata.citation.Citation;
import org.opengis.referencing.AuthorityFactory;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CompoundCRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.DerivedCRS;
import org.opengis.referencing.crs.EngineeringCRS;
import org.opengis.referencing.crs.GeocentricCRS;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.ImageCRS;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.crs.TemporalCRS;
import org.opengis.referencing.crs.VerticalCRS;
import org.opengis.referencing.cs.CSAuthorityFactory;
import org.opengis.referencing.cs.CartesianCS;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.cs.CylindricalCS;
import org.opengis.referencing.cs.EllipsoidalCS;
import org.opengis.referencing.cs.PolarCS;
import org.opengis.referencing.cs.SphericalCS;
import org.opengis.referencing.cs.TimeCS;
import org.opengis.referencing.cs.VerticalCS;
import org.opengis.referencing.datum.Datum;
import org.opengis.referencing.datum.DatumAuthorityFactory;
import org.opengis.referencing.datum.Ellipsoid;
import org.opengis.referencing.datum.EngineeringDatum;
import org.opengis.referencing.datum.GeodeticDatum;
import org.opengis.referencing.datum.ImageDatum;
import org.opengis.referencing.datum.PrimeMeridian;
import org.opengis.referencing.datum.TemporalDatum;
import org.opengis.referencing.datum.VerticalDatum;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationAuthorityFactory;
import org.opengis.util.InternationalString;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;


/**
 * An authority factory that caches all objects created by delegate factories.
 * This class is set up to cache the full complement of referencing objects:
 * In many cases a single implementation will be used for several the authority factory
 * interfaces - but this is not a requirement.
 * </p>
 * The behaviour of the {@code createFoo(String)} methods first looks if a
 * previously created object exists for the given code. If such an object
 * exists, it is returned directly. The testing of the cache is synchronized and
 * may block if the referencing object is under construction.
 * <p>
 * If the object is not yet created, the definition is delegated to the
 * appropriate the {@linkplain an AuthorityFactory authority factory} and the
 * result is cached for next time.
 * <p>
 * This object is responsible for owning a {{ReferencingObjectCache}}; there are
 * several implementations to choose from on construction.
 * </p>
 *
 * @since 2.4
 *
 *
 * @source $URL$
 * @version $Id$
 * @author Jody Garnett
 */
public final class CachedAuthorityDecorator extends AbstractAuthorityFactory
		implements AuthorityFactory, CRSAuthorityFactory, CSAuthorityFactory,
		DatumAuthorityFactory, CoordinateOperationAuthorityFactory,
		BufferedFactory {

	/** Cache to be used for referencing objects. */
	Cache<Object, Object> cache;

	/** The delegate authority. */
	private AuthorityFactory authority;

	/** The delegate authority for coordinate reference systems. */
	private CRSAuthorityFactory crsAuthority;

	/** The delegate authority for coordinate sytems. */
	private CSAuthorityFactory csAuthority;

	/** The delegate authority for datums. */
	private DatumAuthorityFactory datumAuthority;

	/** The delegate authority for coordinate operations. */
	private CoordinateOperationAuthorityFactory operationAuthority;

	/** The delegate authority for searching. */
    private AbstractAuthorityFactory delegate;

	/**
	 * Constructs an instance wrapping the specified factory with a default
	 * cache.
	 * <p>
	 * The provided authority factory must implement
	 * {@link DatumAuthorityFactory}, {@link CSAuthorityFactory},
	 * {@link CRSAuthorityFactory} and
	 * {@link CoordinateOperationAuthorityFactory} .
	 *
	 * @param factory
	 *            The factory to cache. Can not be {@code null}.
	 */
	public CachedAuthorityDecorator(final AuthorityFactory factory) {
		this(factory, createCache(GeoTools.getDefaultHints()));
	}

	/**
	 * Constructs an instance wrapping the specified factory. The
	 * {@code maxStrongReferences} argument specify the maximum number of
	 * objects to keep by strong reference. If a greater amount of objects are
	 * created, then the strong references for the oldest ones are replaced by
	 * weak references.
	 * <p>
	 * This constructor is protected because subclasses must declare which of
	 * the {@link DatumAuthorityFactory}, {@link CSAuthorityFactory},
	 * {@link CRSAuthorityFactory} {@link SearchableAuthorityFactory} and
	 * {@link CoordinateOperationAuthorityFactory} interfaces they choose to
	 * implement.
	 *
	 * @param factory
	 *            The factory to cache. Can not be {@code null}.
	 * @param maxStrongReferences
	 *            The maximum number of objects to keep by strong reference.
	 */
	protected CachedAuthorityDecorator(AuthorityFactory factory,
	        Cache<Object, Object> cache) {
	    super( ((ReferencingFactory)factory).getPriority() ); // TODO
		this.cache = cache;
		authority = factory;
		crsAuthority = (CRSAuthorityFactory) factory;
		csAuthority = (CSAuthorityFactory) factory;
		datumAuthority = (DatumAuthorityFactory) factory;
		operationAuthority = (CoordinateOperationAuthorityFactory) factory;
		this.delegate = (AbstractAuthorityFactory) factory;
	}

	/** Utility method used to produce cache based on hint */
	protected static Cache<Object, Object> createCache(final Hints hints)
			throws FactoryRegistryException {
		return ObjectCaches.create(hints);
	}

	//
	// Utility Methods and Cache Care and Feeding
	//
	protected String toKey(String code) {
		return ObjectCaches.toKey( getAuthority(), code);
	}

	//
	// AuthorityFactory
	//
	public IdentifiedObject createObject(final String code) throws FactoryException {
		final String key = toKey(code);
        IdentifiedObject obj = get(key, new Callable<IdentifiedObject>() {

            @Override
            public IdentifiedObject call() throws Exception {
                return authority.createObject(code);
            }
        });
        return obj;
	}

	public Citation getAuthority() {
		return authority.getAuthority();
	}

	public Set getAuthorityCodes(Class type) throws FactoryException {
		return authority.getAuthorityCodes(type);
	}

	public InternationalString getDescriptionText(String code)
			throws FactoryException {
		return authority.getDescriptionText(code);
	}

	//
	// CRSAuthority
	//
	public synchronized CompoundCRS createCompoundCRS(final String code)
			throws FactoryException {
		final String key = toKey(code);
        CompoundCRS crs = get(key, new Callable<CompoundCRS>() {

            @Override
            public CompoundCRS call() throws Exception {
                return crsAuthority.createCompoundCRS(code);
            }
        });
        return crs;
	}

	public CoordinateReferenceSystem createCoordinateReferenceSystem(final String code)
			throws FactoryException {
		final String key = toKey(code);
        CoordinateReferenceSystem crs = get(key, new Callable<CoordinateReferenceSystem>() {

            @Override
            public CoordinateReferenceSystem call() throws Exception {
                return crsAuthority.createCoordinateReferenceSystem(code);
            }
        });
        return crs;
	}

	public DerivedCRS createDerivedCRS(final String code) throws FactoryException {
		final String key = toKey(code);
        DerivedCRS crs = get(key, new Callable<DerivedCRS>() {

            @Override
            public DerivedCRS call() throws Exception {
                return crsAuthority.createDerivedCRS(code);
            }
        });
        return crs;
	}

	public EngineeringCRS createEngineeringCRS(final String code)
			throws FactoryException {
		final String key = toKey(code);
        EngineeringCRS crs = get(key, new Callable<EngineeringCRS>() {

            @Override
            public EngineeringCRS call() throws Exception {
                return crsAuthority.createEngineeringCRS(code);
            }
        });
        return crs;
	}

	public GeocentricCRS createGeocentricCRS(final String code)
			throws FactoryException {
		final String key = toKey(code);
        GeocentricCRS crs = get(key, new Callable<GeocentricCRS>() {

            @Override
            public GeocentricCRS call() throws Exception {
                return crsAuthority.createGeocentricCRS(code);
            }
        });
        return crs;
	}

	public GeographicCRS createGeographicCRS(final String code)
			throws FactoryException {
		final String key = toKey(code);
        GeographicCRS crs = get(key, new Callable<GeographicCRS>() {

            @Override
            public GeographicCRS call() throws Exception {
                return crsAuthority.createGeographicCRS(code);
            }
        });
        return crs;
	}

	public ImageCRS createImageCRS(final String code) throws FactoryException {
		final String key = toKey(code);
        ImageCRS crs = get(key, new Callable<ImageCRS>() {

            @Override
            public ImageCRS call() throws Exception {
                return crsAuthority.createImageCRS(code);
            }
        });
        return crs;
	}

	public ProjectedCRS createProjectedCRS(final String code) throws FactoryException {
		final String key = toKey(code);
        ProjectedCRS crs = get(key, new Callable<ProjectedCRS>() {

            @Override
            public ProjectedCRS call() throws Exception {
                return crsAuthority.createProjectedCRS(code);
            }
        });
        return crs;
	}

	public TemporalCRS createTemporalCRS(final String code) throws FactoryException {
		final String key = toKey(code);
        TemporalCRS crs = get(key, new Callable<TemporalCRS>() {

            @Override
            public TemporalCRS call() throws Exception {
                return crsAuthority.createTemporalCRS(code);
            }
        });
        return crs;
	}

	public VerticalCRS createVerticalCRS(final String code) throws FactoryException {
		final String key = toKey(code);
        VerticalCRS crs = get(key, new Callable<VerticalCRS>() {

            @Override
            public VerticalCRS call() throws Exception {
                return crsAuthority.createVerticalCRS(code);
            }
        });
        return crs;
	}

	//
	// CSAuthority
	//
	public CartesianCS createCartesianCS(final String code) throws FactoryException {
		final String key = toKey(code);
        CartesianCS cs = get(key, new Callable<CartesianCS>() {

            @Override
            public CartesianCS call() throws Exception {
                return csAuthority.createCartesianCS(code);
            }
        });
        return cs;
	}

	public CoordinateSystem createCoordinateSystem(final String code)
			throws FactoryException {
		final String key = toKey(code);
        CoordinateSystem cs = get(key, new Callable<CoordinateSystem>() {

            @Override
            public CoordinateSystem call() throws Exception {
                return csAuthority.createCoordinateSystem(code);
            }
        });
        return cs;
	}

	// sample implemenation with get/test
	public CoordinateSystemAxis createCoordinateSystemAxis(final String code)
			throws FactoryException {
		final String key = toKey(code);
        CoordinateSystemAxis axis = get(key, new Callable<CoordinateSystemAxis>() {

            @Override
            public CoordinateSystemAxis call() throws Exception {
                return csAuthority.createCoordinateSystemAxis(code);
            }
        });
        return axis;
	}

	public CylindricalCS createCylindricalCS(final String code)
			throws FactoryException {
		final String key = toKey(code);
        CylindricalCS cs = get(key, new Callable<CylindricalCS>() {

            @Override
            public CylindricalCS call() throws Exception {
                return csAuthority.createCylindricalCS(code);
            }
        });
        return cs;
	}

	public EllipsoidalCS createEllipsoidalCS(final String code)
			throws FactoryException {
		final String key = toKey(code);
        EllipsoidalCS cs = get(key, new Callable<EllipsoidalCS>() {

            @Override
            public EllipsoidalCS call() throws Exception {
                return csAuthority.createEllipsoidalCS(code);
            }
        });
        return cs;
	}

	public PolarCS createPolarCS(final String code) throws FactoryException {
		final String key = toKey(code);
        PolarCS cs = get(key, new Callable<PolarCS>() {

            @Override
            public PolarCS call() throws Exception {
                return csAuthority.createPolarCS(code);
            }
        });
        return cs;
	}

	public SphericalCS createSphericalCS(final String code) throws FactoryException {
		final String key = toKey(code);
        SphericalCS cs = get(key, new Callable<SphericalCS>() {

            @Override
            public SphericalCS call() throws Exception {
                return csAuthority.createSphericalCS(code);
            }
        });
        return cs;
	}

	public TimeCS createTimeCS(final String code) throws FactoryException {
		final String key = toKey(code);
        TimeCS cs = get(key, new Callable<TimeCS>() {

            @Override
            public TimeCS call() throws Exception {
                return csAuthority.createTimeCS(code);
            }
        });
        return cs;
	}

	public Unit<?> createUnit(final String code) throws FactoryException {
		final String key = toKey(code);
        Unit<?> unit = get(key, new Callable<Unit<?>>() {

            @Override
            public Unit<?> call() throws Exception {
                return csAuthority.createUnit(code);
            }
        });
        return unit;
	}

	public VerticalCS createVerticalCS(final String code) throws FactoryException {
		final String key = toKey(code);
        VerticalCS cs = get(key, new Callable<VerticalCS>() {

            @Override
            public VerticalCS call() throws Exception {
                return csAuthority.createVerticalCS(code);
            }
        });
        return cs;
	}

	//
	// DatumAuthorityFactory
	//
	public Datum createDatum(final String code) throws FactoryException {
		final String key = toKey(code);
        Datum datum = get(key, new Callable<Datum>() {

            @Override
            public Datum call() throws Exception {
                return datumAuthority.createDatum(code);
            }
        });
        return datum;
	}

	public Ellipsoid createEllipsoid(final String code) throws FactoryException {
		final String key = toKey(code);
        Ellipsoid ellipsoid = get(key, new Callable<Ellipsoid>() {

            @Override
            public Ellipsoid call() throws Exception {
                return datumAuthority.createEllipsoid(code);
            }
        });
        return ellipsoid;
	}

	public EngineeringDatum createEngineeringDatum(final String code)
			throws FactoryException {
		final String key = toKey(code);
        EngineeringDatum datum = get(key, new Callable<EngineeringDatum>() {

            @Override
            public EngineeringDatum call() throws Exception {
                return datumAuthority.createEngineeringDatum(code);
            }
        });
        return datum;
	}

	public GeodeticDatum createGeodeticDatum(final String code)
			throws FactoryException {
		final String key = toKey(code);
        GeodeticDatum datum = get(key, new Callable<GeodeticDatum>() {

            @Override
            public GeodeticDatum call() throws Exception {
                return datumAuthority.createGeodeticDatum(code);
            }
        });
        return datum;
	}

	public ImageDatum createImageDatum(final String code) throws FactoryException {
		final String key = toKey(code);
        ImageDatum datum = get(key, new Callable<ImageDatum>() {

            @Override
            public ImageDatum call() throws Exception {
                return datumAuthority.createImageDatum(code);
            }
        });
        return datum;
	}

	public PrimeMeridian createPrimeMeridian(final String code)
			throws FactoryException {
		final String key = toKey(code);
        PrimeMeridian datum = get(key, new Callable<PrimeMeridian>() {

            @Override
            public PrimeMeridian call() throws Exception {
                return datumAuthority.createPrimeMeridian(code);
            }
        });
        return datum;
	}

	public TemporalDatum createTemporalDatum(final String code)
			throws FactoryException {
		final String key = toKey(code);
        TemporalDatum datum = get(key, new Callable<TemporalDatum>() {

            @Override
            public TemporalDatum call() throws Exception {
                return datumAuthority.createTemporalDatum(code);
            }
        });
        return datum;
	}

	public VerticalDatum createVerticalDatum(final String code)
			throws FactoryException {
		final String key = toKey(code);
        VerticalDatum datum = get(key, new Callable<VerticalDatum>() {

            @Override
            public VerticalDatum call() throws Exception {
                return datumAuthority.createVerticalDatum(code);
            }
        });
        return datum;
	}

	public CoordinateOperation createCoordinateOperation(final String code)
			throws FactoryException {
		final String key = toKey(code);
        CoordinateOperation operation = get(key, new Callable<CoordinateOperation>() {

            @Override
            public CoordinateOperation call() throws Exception {
                return operationAuthority.createCoordinateOperation(code);
            }
        });
        return operation;
	}

	public synchronized Set/*<CoordinateOperation>*/ createFromCoordinateReferenceSystemCodes(
			final String sourceCode, final String targetCode)
			throws FactoryException {

		final Object key = ObjectCaches.toKey( getAuthority(),  sourceCode, targetCode );
        Set operations = get(key, new Callable<Set>() {

            @Override
            public Set call() throws Exception {
                return operationAuthority.createFromCoordinateReferenceSystemCodes(sourceCode,
                        targetCode);
            }
        });
        return operations;
	}
	
    protected <T> T get(final Object key, final Callable<T> loader) throws FactoryException {
        Object object;
        try {
            object = cache.get(key, loader);
        } catch (ExecutionException e) {
            Throwables.propagateIfInstanceOf(e.getCause(), FactoryException.class);
            throw Throwables.propagate(e.getCause());
        }
        return (T) object;
    }

	//
	// AbstractAuthorityFactory
	//
    public IdentifiedObjectFinder getIdentifiedObjectFinder( Class type ) throws FactoryException {
        return delegate.getIdentifiedObjectFinder( type );
    }

    public void dispose() throws FactoryException {
        delegate.dispose();
        cache.invalidateAll();
        cache.cleanUp();
        cache = null;
        delegate = null;
    }

    public String getBackingStoreDescription() throws FactoryException {
        return delegate.getBackingStoreDescription();
    }
}
