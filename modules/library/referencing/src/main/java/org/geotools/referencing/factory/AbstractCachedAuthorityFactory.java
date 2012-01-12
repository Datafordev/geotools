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
import org.geotools.factory.Hints;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.util.NameFactory;
import org.geotools.util.ObjectCaches;
import org.opengis.metadata.citation.Citation;
import org.opengis.referencing.AuthorityFactory;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.NoSuchAuthorityCodeException;
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
import org.opengis.util.GenericName;
import org.opengis.util.InternationalString;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;

/**
 * An authority factory that consults (a possibly shared) cache before generating
 * content itself.
 * </p>
 * The behavior of the {@code createFoo(String)} methods first looks if a
 * previously created object exists for the given code. If such an object
 * exists, it is returned directly. The testing of the cache is synchronized and
 * may block if the referencing object is under construction.
 * <p>
 * If the object is not yet created, the definition is delegated to the
 * appropriate the {@code generateFoo} method and the result is cached for
 * next time.
 * <p>
 * This object is responsible for using a provided {{ReferencingObjectCache}}.
 * </p>
 *
 * @since 2.4
 *
 *
 * @source $URL$
 * @version $Id: BufferedAuthorityDecorator.java 26038 2007-06-27 01:58:12Z
 *          jgarnett $
 * @author Jody Garnett
 */
public abstract class AbstractCachedAuthorityFactory extends AbstractAuthorityFactory
		implements AuthorityFactory, CRSAuthorityFactory, CSAuthorityFactory,
		DatumAuthorityFactory, CoordinateOperationAuthorityFactory,
		BufferedFactory {

	/**
     * Cache to be used for referencing objects defined by this authority. Please note that this
     * cache may be shared!
     * <p>
     * Your cache may grow to considerable size during actual use; in addition to storing
     * CoordinateReferenceSystems (by code); it will also store all the component parts
     * (each under its own code), along with MathTransformations between two
     * CoordinateReferenceSystems. So even if you are only planning on working with
     * 50 CoordianteReferenceSystems please keep in mind that you will need larger
     * cache size in order to prevent a bottleneck.
     */
	protected Cache<Object, Object> cache;

	/**
     * The findCache is used to store search results; often match a "raw"
     * CoordinateReferenceSystem created from WKT (as the key) with a
     * "real" CoordianteReferenceSystem as defined by this authority.
     */
	Cache<Object, Object> findCache;

	/**
	 * A container of the "real factories" actually used to construct objects.
	 */
    protected ReferencingFactoryContainer factories;


	/**
	 * Constructs an instance making use of the default cache.
	 *
	 * @param factory
	 *            The factory to cache. Can not be {@code null}.
	 */
	protected AbstractCachedAuthorityFactory( int priority ) {
		this( priority, ObjectCaches.create("weak", 50 ), ReferencingFactoryContainer.instance( null ) );
	}

	/**
	 * Constructs an instance making use of the default cache.
	 *
	 * @param factory
	 *            The factory to cache. Can not be {@code null}.
	 */
	protected AbstractCachedAuthorityFactory( int priority, Hints hints ) {
		this( priority, ObjectCaches.create( hints ), ReferencingFactoryContainer.instance( hints ) );
	}

	/**
	 * Constructs an instance making use of the indicated cache.
	 * <p>
	 * This constructor is protected because subclasses must declare which of
	 * the {@link DatumAuthorityFactory}, {@link CSAuthorityFactory},
	 * {@link CRSAuthorityFactory} and
	 * {@link CoordinateOperationAuthorityFactory} interfaces they choose to
	 * implement.
	 *
	 * @param factory
	 *            The factory to cache. Can not be {@code null}.
	 * @param maxStrongReferences
	 *            The maximum number of objects to keep by strong reference.
	 */
	protected AbstractCachedAuthorityFactory(int priority, Cache<Object, Object> cache, ReferencingFactoryContainer container) {
		super( priority );
		this.factories = container;
		this.cache = cache;
		this.findCache = ObjectCaches.create("weak",0);
	}

    final void completeHints() {
        hints.put(Hints.DATUM_AUTHORITY_FACTORY, this );
        hints.put(Hints.CS_AUTHORITY_FACTORY, this );
        hints.put(Hints.CRS_AUTHORITY_FACTORY, this );
        hints.put(Hints.COORDINATE_OPERATION_AUTHORITY_FACTORY, this );
    }

	//
	// Utility Methods and Cache Care and Feeding
	//
	protected String toKey(String code) {
		return ObjectCaches.toKey( getAuthority(), code);
	}

    /**
     * Trims the authority scope, if present. For example if this factory is an EPSG authority
     * factory and the specified code start with the "EPSG:" prefix, then the prefix is removed.
     * Otherwise, the string is returned unchanged (except for leading and trailing spaces).
     *
     * @param  code The code to trim.
     * @return The code without the authority scope.
     */
    protected String trimAuthority(String code) {
        /*
         * IMPLEMENTATION NOTE: This method is overridden in
         * PropertyAuthorityFactory. If the implementation below is modified, it
         * is probably worth revisiting the overridden method as well.
         */
        code = code.trim();
        final GenericName name  = NameFactory.create(code);
        final GenericName scope = name.scope().name();
        if (scope == null) {
            return code;
        }
        if (Citations.identifierMatches(getAuthority(), scope.toString())) {
            return name.tip().toString().trim();
        }
        return code;
    }

    /**
     * Creates an exception for an unknown authority code. This convenience method is provided
     * for implementation of {@code createXXX} methods.
     *
     * @param  type  The GeoAPI interface that was to be created
     *               (e.g. {@code CoordinateReferenceSystem.class}).
     * @param  code  The unknown authority code.
     * @param  cause The cause of this error, or {@code null}.
     * @return An exception initialized with an error message built
     *         from the specified informations.
     */
    protected NoSuchAuthorityCodeException noSuchAuthorityCode(final Class              type,
                                                             final String             code,
                                                             final ClassCastException cause)
    {
        final NoSuchAuthorityCodeException exception = noSuchAuthorityCode(type, code);
        exception.initCause(cause);
        return exception;
    }
	//
	// AuthorityFactory
	//
	public abstract Citation getAuthority();

    public Set getAuthorityCodes(final Class type) throws FactoryException {
        Set codes = get(type, new Callable<Set>() {

            @Override
            public Set call() throws Exception {
                Set codes = generateAuthorityCodes(type);
                return codes;
            }
        });

        return codes;
    }

	protected abstract Set generateAuthorityCodes( Class type ) throws FactoryException;

	public abstract InternationalString getDescriptionText(String code)  throws FactoryException;

    public IdentifiedObject createObject(final String code) throws FactoryException {
        final String key = toKey(code);

        IdentifiedObject obj = get(key, new Callable<IdentifiedObject>() {

            @Override
            public IdentifiedObject call() throws Exception {
                return generateObject(code);
            }
        });

        return obj;
    }

	protected abstract IdentifiedObject generateObject( String code ) throws FactoryException;

	//
	// CRSAuthority
	//
    /**
     * Creates a 3D coordinate reference system from a code.
     *
     * @param code Value allocated by authority.
     * @throws NoSuchAuthorityCodeException if the specified {@code code} was not found.
     * @throws FactoryException if the object creation failed for some other reason.
     */
    public CompoundCRS createCompoundCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (CompoundCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(CompoundCRS.class, code, exception);
        }
    }

    public CoordinateReferenceSystem createCoordinateReferenceSystem(final String code)
            throws FactoryException {
        final String key = toKey(code);
        CoordinateReferenceSystem crs = get(key, new Callable<CoordinateReferenceSystem>() {

            @Override
            public CoordinateReferenceSystem call() throws Exception {
                return generateCoordinateReferenceSystem(code);
            }
        });

        return crs;
    }

	protected abstract CoordinateReferenceSystem generateCoordinateReferenceSystem(String code) throws FactoryException;

    public DerivedCRS createDerivedCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (DerivedCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(DerivedCRS.class, code, exception);
        }
    }

    public EngineeringCRS createEngineeringCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (EngineeringCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(EngineeringCRS.class, code, exception);
        }
    }

    public GeocentricCRS createGeocentricCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (GeocentricCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(GeocentricCRS.class, code, exception);
        }
    }

    public GeographicCRS createGeographicCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (GeographicCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(GeographicCRS.class, code, exception);
        }
    }

    public ImageCRS createImageCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (ImageCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(ImageCRS.class, code, exception);
        }
    }

    public ProjectedCRS createProjectedCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (ProjectedCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(ProjectedCRS.class, code, exception);
        }
    }

    public TemporalCRS createTemporalCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (TemporalCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(TemporalCRS.class, code, exception);
        }
    }

    public VerticalCRS createVerticalCRS(final String code) throws FactoryException {
        final CoordinateReferenceSystem crs = createCoordinateReferenceSystem(code);
        try {
            return (VerticalCRS) crs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(VerticalCRS.class, code, exception);
        }
    }
	//
	// CSAuthority
	//

    /**
     * Creates a cartesian coordinate system from a code.
     * The default implementation invokes
     * <code>{@linkplain #createCoordinateSystem createCoordinateSystem}(code)</code>.
     *
     * @param  code Value allocated by authority.
     * @throws NoSuchAuthorityCodeException if the specified {@code code} was not found.
     * @throws FactoryException if the object creation failed for some other reason.
     */
    public CartesianCS createCartesianCS(final String code) throws FactoryException {
        final CoordinateSystem cs = createCoordinateSystem(code);
        try {
            return (CartesianCS) cs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(CartesianCS.class, code, exception);
        }
    }

	public CoordinateSystem createCoordinateSystem(final String code)
			throws FactoryException {
		final String key = toKey(code);
		CoordinateSystem cs;
        try {
            cs = (CoordinateSystem) cache.get(key, new Callable<Object>() {

                @Override
                public Object call() throws Exception {
                    return generateCoordinateSystem(code);
                }

            });
        } catch (ExecutionException e) {
            Throwables.propagateIfInstanceOf(e.getCause(), FactoryException.class);
            throw Throwables.propagate(e.getCause());
        }
        return cs;
    }

	protected abstract CoordinateSystem generateCoordinateSystem(String code) throws FactoryException;

	// sample implemenation with get/test
	public CoordinateSystemAxis createCoordinateSystemAxis(final String code)
			throws FactoryException {
        final String key = toKey(code);
        CoordinateSystemAxis axis;
        try {
            axis = (CoordinateSystemAxis) cache.get(key, new Callable<Object>() {

                @Override
                public Object call() throws Exception {
                    return generateCoordinateSystemAxis(code);
                }

            });
        } catch (ExecutionException e) {
            Throwables.propagateIfInstanceOf(e.getCause(), FactoryException.class);
            throw Throwables.propagate(e.getCause());
        }
        return axis;
	}

	protected abstract CoordinateSystemAxis generateCoordinateSystemAxis(String code)  throws FactoryException;

    /**
     * The default implementation invokes
     * <code>{@linkplain #createCoordinateSystem createCoordinateSystem}(code)</code>.
     *
     * @param  code Value allocated by authority.
     * @throws NoSuchAuthorityCodeException if the specified {@code code} was not found.
     * @throws FactoryException if the object creation failed for some other reason.
     */
    public CylindricalCS createCylindricalCS(final String code) throws FactoryException {
        final CoordinateSystem cs = createCoordinateSystem(code);
        try {
            return (CylindricalCS) cs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(CylindricalCS.class, code, exception);
        }
    }

    public EllipsoidalCS createEllipsoidalCS(final String code) throws FactoryException {
        final CoordinateSystem cs = createCoordinateSystem(code);
        try {
            return (EllipsoidalCS) cs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(EllipsoidalCS.class, code, exception);
        }
    }

    public PolarCS createPolarCS(final String code) throws FactoryException {
        final CoordinateSystem cs = createCoordinateSystem(code);
        try {
            return (PolarCS) cs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(PolarCS.class, code, exception);
        }
    }

    public SphericalCS createSphericalCS(final String code) throws FactoryException {
        final CoordinateSystem cs = createCoordinateSystem(code);
        try {
            return (SphericalCS) cs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(SphericalCS.class, code, exception);
        }
    }

    public TimeCS createTimeCS(final String code) throws FactoryException {
        final CoordinateSystem cs = createCoordinateSystem(code);
        try {
            return (TimeCS) cs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(TimeCS.class, code, exception);
        }
    }

	public Unit<?> createUnit(final String code) throws FactoryException {
		final String key = toKey(code);
        Unit<?> unit = get(key, new Callable<Unit<?>>() {

            @Override
            public Unit<?> call() throws Exception {
                return generateUnit(code);
            }
        });

        return unit;
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
	
	protected abstract Unit<?> generateUnit(String code) throws FactoryException;

    public VerticalCS createVerticalCS(final String code) throws FactoryException {
        final CoordinateSystem cs = createCoordinateSystem(code);
        try {
            return (VerticalCS) cs;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(VerticalCS.class, code, exception);
        }
    }

	//
	// DatumAuthorityFactory
	//
	public Datum createDatum(final String code) throws FactoryException {
        final String key = toKey(code);
        Datum datum = get(key, new Callable<Datum>() {

            @Override
            public Datum call() throws Exception {
                return generateDatum(code);
            }
        });
        return datum;
	}

	protected abstract Datum generateDatum(String code) throws FactoryException;

	public Ellipsoid createEllipsoid(final String code) throws FactoryException {
		final String key = toKey(code);

        Ellipsoid ellipsoid = get(key, new Callable<Ellipsoid>() {

            @Override
            public Ellipsoid call() throws Exception {
                return generateEllipsoid(code);
            }
        });
        return ellipsoid;
	}

	protected abstract Ellipsoid generateEllipsoid(String code) throws FactoryException;

    public EngineeringDatum createEngineeringDatum(final String code) throws FactoryException {
        final Datum datum = createDatum(code);
        try {
            return (EngineeringDatum) datum;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(EngineeringDatum.class, code, exception);
        }
    }

    public GeodeticDatum createGeodeticDatum(final String code) throws FactoryException {
        final Datum datum = createDatum(code);
        try {
            return (GeodeticDatum) datum;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(GeodeticDatum.class, code, exception);
        }
    }

    public ImageDatum createImageDatum(final String code) throws FactoryException {
        final Datum datum = createDatum(code);
        try {
            return (ImageDatum) datum;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(ImageDatum.class, code, exception);
        }
    }

	public PrimeMeridian createPrimeMeridian(final String code)
			throws FactoryException {
		final String key = toKey(code);
        PrimeMeridian datum = get(key, new Callable<PrimeMeridian>() {

            @Override
            public PrimeMeridian call() throws Exception {
                return generatePrimeMeridian(code);
            }
        });
		return datum;
	}

	protected abstract PrimeMeridian generatePrimeMeridian(String code) throws FactoryException;

    public TemporalDatum createTemporalDatum(final String code) throws FactoryException {
        final Datum datum = createDatum(code);
        try {
            return (TemporalDatum) datum;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(TemporalDatum.class, code, exception);
        }
    }

    public VerticalDatum createVerticalDatum(final String code) throws FactoryException {
        final Datum datum = createDatum(code);
        try {
            return (VerticalDatum) datum;
        } catch (ClassCastException exception) {
            throw noSuchAuthorityCode(VerticalDatum.class, code, exception);
        }
    }

	public CoordinateOperation createCoordinateOperation(final String code)
			throws FactoryException {
		final String key = toKey(code);
        CoordinateOperation operation = get(key, new Callable<CoordinateOperation>() {

            @Override
            public CoordinateOperation call() throws Exception {
                return generateCoordinateOperation(code);
            }
        });
		return operation;
	}

	protected abstract CoordinateOperation generateCoordinateOperation(String code)  throws FactoryException;

	public synchronized Set/*<CoordinateOperation>*/ createFromCoordinateReferenceSystemCodes(
			final String sourceCode, final String targetCode)
			throws FactoryException {

		final Object key = ObjectCaches.toKey( getAuthority(),  sourceCode, targetCode );
        Set operations = get(key, new Callable<Set>() {

            @Override
            public Set call() throws Exception {
                return generateFromCoordinateReferenceSystemCodes(sourceCode, targetCode);
            }
        });
        return operations;
	}

	protected abstract Set generateFromCoordinateReferenceSystemCodes(String sourceCode, String targetCode)  throws FactoryException;

	/** We will clear out our cache and factories reference
	 * @throws FactoryException */
    public void dispose() throws FactoryException {
        this.cache = null;
        this.factories = null;
    }

    /**
     * Returns a finder which can be used for looking up unidentified objects.
     * The default implementation delegates lookup to the underlying backing
     * store and caches the result.
     *
     * @since 2.4
     */
    @Override
    public synchronized IdentifiedObjectFinder getIdentifiedObjectFinder(
            final Class/*<? extends IdentifiedObject>*/ type) throws FactoryException
    {
        return new CachedFinder( type );
    }

    /**
     * An implementation of {@link IdentifiedObjectFinder} which delegates
     * the work to the underlying backing store and caches the result.
     * <p>
     * A separate ObjectCache, findCache, is used to store the values created over the course
     * of finding. The findCache is set up as a "chain" allowing it to use our cache
     * to prevent duplication of effort. In the future this findCache may be shared between
     * instances.
     * <p>
     * <b>Implementation note:</b> we will create objects using directly the underlying backing
     * store, not using the cache. This is because hundred of objects may be created during a
     * scan while only one will be typically retained. We don't want to overload the cache with
     * every false candidates that we encounter during the scan.
     */
    private final class CachedFinder extends IdentifiedObjectFinder {
        /**
         * Creates a finder for the underlying backing store.
         */
        CachedFinder(Class type) {
            super( AbstractCachedAuthorityFactory.this, type );
        }

        /**
         * Looks up an object from this authority factory which is equals, ignoring metadata,
         * to the specified object. The default implementation performs the same lookup than
         * the backing store and caches the result.
         */
        @Override
        public IdentifiedObject find(final IdentifiedObject object) throws FactoryException {
            IdentifiedObject candidate;

            try {
                candidate = (IdentifiedObject) findCache.get(object, new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        IdentifiedObject found = doFind(object);
                        return found;
                    }
                });
            } catch (ExecutionException e) {
                Throwables.propagateIfInstanceOf(e.getCause(), FactoryException.class);
                throw Throwables.propagate(e.getCause());
            }
            
            return candidate;
        }

        private IdentifiedObject doFind(IdentifiedObject object) throws FactoryException {
            return super.find(object);
        }

        /**
         * Returns the identifier for the specified object.
         */
        @Override
        public String findIdentifier(final IdentifiedObject object) throws FactoryException {
            IdentifiedObject candidate;
            candidate = (IdentifiedObject) findCache.getIfPresent(object);
            if (candidate != null) {
                return getIdentifier(candidate);
            }
            return super.findIdentifier(object);
        }
    }
}
