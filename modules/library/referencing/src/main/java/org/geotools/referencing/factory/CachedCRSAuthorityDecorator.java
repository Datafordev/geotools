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

// J2SE dependencies and extensions
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

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
import org.opengis.referencing.datum.DatumAuthorityFactory;
import org.opengis.referencing.operation.CoordinateOperationAuthorityFactory;
import org.opengis.util.InternationalString;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;

/**
 * An authority factory that caches all objects created by the delegate CRSAuthorityFactory.
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
 * @version $Id: BufferedAuthorityDecorator.java 26038 2007-06-27 01:58:12Z
 *          jgarnett $
 * @author Jody Garnett
 */
public final class CachedCRSAuthorityDecorator extends AbstractAuthorityFactory
		implements AuthorityFactory, CRSAuthorityFactory,
		BufferedFactory {

	/** Cache to be used for referencing objects. */
	Cache<Object, Object> cache;

	/** The delegate authority for coordinate reference systems. */
	private CRSAuthorityFactory crsAuthority;

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
	public CachedCRSAuthorityDecorator(final CRSAuthorityFactory factory) {
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
	protected CachedCRSAuthorityDecorator(CRSAuthorityFactory factory,
	        Cache<Object, Object> cache) {
	    super( ((ReferencingFactory)factory).getPriority() ); // TODO
		this.cache = cache;
		crsAuthority = (CRSAuthorityFactory) factory;
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
	protected String toKey(final String code) {
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
                return crsAuthority.createObject(code);
            }
        });
        return obj;
	}

	public Citation getAuthority() {
		return crsAuthority.getAuthority();
	}

	public Set getAuthorityCodes(Class type) throws FactoryException {
		return crsAuthority.getAuthorityCodes(type);
	}

	public InternationalString getDescriptionText(final String code)
			throws FactoryException {
		return crsAuthority.getDescriptionText(code);
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
	// AbstractAuthorityFactory
	//
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
        return new Finder( delegate.getIdentifiedObjectFinder(type), ObjectCaches.create("weak",250));
    }

    /**
     * An implementation of {@link IdentifiedObjectFinder} which delegates
     * the work to the underlying backing store and caches the result.
     * <p>
     * A separate Cache<Object, Object>, findCache, is used to store the values created over the course
     * of finding. The findCache is set up as a "chain" allowing it to use our cache
     * to prevent duplication of effort. In the future this findCache may be shared between
     * instances.
     * <p>
     * <b>Implementation note:</b> we will create objects using directly the underlying backing
     * store, not using the cache. This is because hundred of objects may be created during a
     * scan while only one will be typically retained. We don't want to overload the cache with
     * every false candidates that we encounter during the scan.
     */
    private final class Finder extends IdentifiedObjectFinder.Adapter {
        /** Cache used when finding */
        private Cache<Object, Object> findCache;

        /**
         * Creates a finder for the underlying backing store.
         */
        Finder(final IdentifiedObjectFinder finder, Cache<Object, Object> tempCache) {
            super(finder);
            this.findCache = tempCache;
        }

        /**
         * Looks up an object from this authority factory which is equals, ignoring metadata,
         * to the specified object. The default implementation performs the same lookup than
         * the backing store and caches the result.
         */
        @Override
        public IdentifiedObject find(final IdentifiedObject object) throws FactoryException {
            /*
             * Do not synchronize on 'BufferedAuthorityFactory.this'. This method may take a
             * while to execute and we don't want to block other threads. The synchronizations
             * in the 'create' methods and in the 'findPool' map should be suffisient.
             *
             * TODO: avoid to search for the same object twice. For now we consider that this
             *       is not a big deal if the same object is searched twice; it is "just" a
             *       waste of CPU.
             */
            IdentifiedObject candidate;
            candidate = (IdentifiedObject) findCache.getIfPresent(object);
            
            if (candidate == null) {
                // Must delegates to 'finder' (not to 'super') in order to take
                // advantage of the method overriden by AllAuthoritiesFactory.
                IdentifiedObject found = finder.find(object);
                if (found != null) {
                    candidate = (IdentifiedObject) findCache.getIfPresent(object);
                    if( candidate == null ){
                        findCache.put(object, found);
                        return found;
                    }
                }
            }
            return candidate;
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
            // We don't rely on super-class implementation, because we want to
            // take advantage of the method overriden by AllAuthoritiesFactory.
            return finder.findIdentifier(object);
        }
    }

    @SuppressWarnings("unchecked")
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
}
