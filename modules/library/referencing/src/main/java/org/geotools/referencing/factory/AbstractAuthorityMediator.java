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

import static org.geotools.factory.AbstractFactory.MAXIMUM_PRIORITY;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import javax.measure.unit.Unit;
import org.geotools.factory.BufferedFactory;
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
import com.google.common.cache.CacheBuilder;

/**
 * An authority mediator that consults (a possibily shared) cache before delegating the generation
 * of the content to a "worker" authority factory.
 * </p>
 * The behaviour of the {@code createFoo(String)} methods first looks if a previously created object
 * exists for the given code. If such an object exists, it is returned directly. The testing of the
 * cache is synchronized and may block if the referencing object is under construction.
 * <p>
 * If the object is not yet created, the definition is delegated to the appropriate
 * {@code createFoo} method of the factory, which will cache the result for next time.
 * <p>
 * This object is responsible for maintaining an {{ObjectCache}} of "workers" based on the following:
 * <ul>
 * <li>Hints.AUTHORITY_MAX_ACTIVE (default 2) - indicates the maximum number of worker created, if non
 * positive the number of workers is unbounded.
 * <li>Hints.
 * </ul>
 * </p>
 *
 * @since 2.4
 *
 *
 * @source $URL$
 * @version $Id$
 * @author Jody Garnett (Refractions Research)
 * @author Cory Horner (Refractions Research)
 */
public abstract class AbstractAuthorityMediator extends AbstractAuthorityFactory
        implements
            AuthorityFactory,
            CRSAuthorityFactory,
            CSAuthorityFactory,
            DatumAuthorityFactory,
            CoordinateOperationAuthorityFactory,
            BufferedFactory {

    static final int PRIORITY = MAXIMUM_PRIORITY - 10;

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
    Cache<Object, Object> cache;

    /**
     * The findCache is used to store search results; often match a "raw" CoordinateReferenceSystem
     * created from WKT (as the key) with a "real" CoordianteReferenceSystem as defined
     * by this authority.
     */
    Cache<Object, Object> findCache;

    /**
     * Pool to hold workers which will be used to construct referencing objects which are not
     * present in the cache.
     */
    // private ObjectPool workers;

    /**
     * Configuration object for the object pool. The constructor reads its hints and sets the pool
     * configuration in this object;
     */
    CacheBuilder<Object, Object> poolConfig;

    /**
     * A container of the "real factories" actually used to construct objects.
     */
    protected final ReferencingFactoryContainer factories;

    /**
     * Constructs an instance making use of the default cache and priority level.
     */
    protected AbstractAuthorityMediator() {
        this(PRIORITY);
    }

    /**
     * Constructs an instance based on the provided Hints
     *
     * @param factory The factory to cache. Can not be {@code null}.
     */
    protected AbstractAuthorityMediator( Hints hints ) {
        this(PRIORITY, hints);
    }
    /**
     * Constructs an instance making use of the default cache.
     *
     * @param factory The factory to cache. Can not be {@code null}.
     */
    protected AbstractAuthorityMediator( int priority ) {
        this(priority, ObjectCaches.create("weak", 50), ReferencingFactoryContainer.instance(null));
    }

    /**
     * Constructs an instance making use of the default cache.
     *
     * @param factory The factory to cache. Can not be {@code null}.
     */
    protected AbstractAuthorityMediator( int priority, Hints hints ) {
        this(priority, ObjectCaches.create(hints), ReferencingFactoryContainer.instance(hints));
        // configurable behaviour
        int minIdle = Hints.AUTHORITY_MIN_IDLE.toValue(hints);
        int maxIdle = Hints.AUTHORITY_MAX_IDLE.toValue(hints);
        int maxActive = Hints.AUTHORITY_MAX_ACTIVE.toValue(hints);
        int minEvictableIdleTimeMillis = Hints.AUTHORITY_MIN_EVICT_IDLETIME.toValue(hints);
        int softMinEvictableIdleTimeMillis = Hints.AUTHORITY_SOFTMIN_EVICT_IDLETIME
                .toValue(hints);
        int timeBetweenEvictionRunsMillis = Hints.AUTHORITY_TIME_BETWEEN_EVICTION_RUNS
                .toValue(hints);

        // static behaviour
        //int maxWait = -1; // block indefinitely until a worker is available
        //poolConfig.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK;
        
        poolConfig = CacheBuilder.newBuilder();
        
    }

    /**
     * Constructs an instance making use of the indicated cache.
     * <p>
     * This constructor is protected because subclasses must declare which of the
     * {@link DatumAuthorityFactory}, {@link CSAuthorityFactory}, {@link CRSAuthorityFactory} and
     * {@link CoordinateOperationAuthorityFactory} interfaces they choose to implement.
     *
     * @param factory The factory to cache. Can not be {@code null}.
     * @param maxStrongReferences The maximum number of objects to keep by strong reference.
     */
    protected AbstractAuthorityMediator( int priority, Cache<Object, Object> cache,
            ReferencingFactoryContainer container ) {
        super(priority);
        this.factories = container;
        this.cache = cache;
        this.findCache = ObjectCaches.chain( ObjectCaches.create("weak",0), cache );
    }

    protected void completeHints() {
        hints.put(Hints.DATUM_AUTHORITY_FACTORY, this);
        hints.put(Hints.CS_AUTHORITY_FACTORY, this);
        hints.put(Hints.CRS_AUTHORITY_FACTORY, this );
        hints.put(Hints.COORDINATE_OPERATION_AUTHORITY_FACTORY, this );

    }

//GR: smells to dead code
//    /**
//     * True if this mediator is currently connected to one or more workers.
//     *
//     * @return
//     */
//    public boolean isConnected() {
//        return (workers.getNumActive() + workers.getNumIdle()) > 0;
//    }
//
//
//    void setPool( ObjectPool pool ) {
//        this.workers = pool;
//    }

    //
    // Utility Methods and Cache Care and Feeding
    //
    protected String toKey( String code ) {
        return ObjectCaches.toKey(getAuthority(), code);
    }

    /**
     * Trims the authority scope, if present. For example if this factory is an EPSG authority
     * factory and the specified code start with the "EPSG:" prefix, then the prefix is removed.
     * Otherwise, the string is returned unchanged (except for leading and trailing spaces).
     *
     * @param code The code to trim.
     * @return The code without the authority scope.
     */
    protected String trimAuthority( String code ) {
        return toKey(code);
    }

    /**
     * The authority body of the objects this factory provides.
     */
    public abstract Citation getAuthority();

    public Set getAuthorityCodes( final Class type ) throws FactoryException {
        Set codes = get(type, new Callable<Set>() {
            @Override
            public Set call() throws Exception {
                AbstractCachedAuthorityFactory worker = makeWorker();
                try {
                    return  worker.getAuthorityCodes(type);
                }finally{
                    disposeWorker(worker);
                }
            }
        });

        return codes;
    }

    public abstract InternationalString getDescriptionText( final String code ) throws FactoryException;

    public IdentifiedObject createObject( final String code ) throws FactoryException {
        final String key = toKey(code);
        IdentifiedObject obj = get(key, new Callable<IdentifiedObject>() {
            @Override
            public IdentifiedObject call() throws Exception {
                final AbstractCachedAuthorityFactory worker = makeWorker();
                DerivedCRS derivedCrs;
                try{
                    derivedCrs = worker.createDerivedCRS(code);
                }finally{
                    disposeWorker(worker);
                }
                return derivedCrs;
            }
        });
        
        return obj;
    }

    //
    // CRSAuthority
    //
    public synchronized CompoundCRS createCompoundCRS( final String code ) throws FactoryException {
        final String key = toKey(code);
        CompoundCRS crs = get(key, new Callable<CompoundCRS>() {
            @Override
            public CompoundCRS call() throws Exception {
                AbstractCachedAuthorityFactory worker = makeWorker();
                try {
                    return worker.createCompoundCRS(code);
                } finally {
                    disposeWorker(worker);
                }
            }
        });
        return crs;
    }

    public CoordinateReferenceSystem createCoordinateReferenceSystem( String code )
            throws FactoryException {
        final String key = toKey(code);
        return createWith( key, new WorkerSafeRunnable(){
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createCoordinateReferenceSystem(key);
			}
    	});
    }


    public DerivedCRS createDerivedCRS(String code) throws FactoryException {
		final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createEngineeringCRS(key);
			}
		});
	}

    public GeocentricCRS createGeocentricCRS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createGeocentricCRS(key);
			}
		});
    }

    public GeographicCRS createGeographicCRS( String code ) throws FactoryException {
        final String key = toKey(code);
        return createWith(key, new WorkerSafeRunnable(){
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createGeographicCRS(key);
			}
        });
    }

    public ImageCRS createImageCRS( String code ) throws FactoryException {
        final String key = toKey(code);
        return createWith(key, new WorkerSafeRunnable(){
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createImageCRS(key);
			}
        });
    }

    public ProjectedCRS createProjectedCRS( String code ) throws FactoryException {
        final String key = toKey(code);
        return createWith(key, new WorkerSafeRunnable(){
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createProjectedCRS(key);
			}
        });
    }
    public TemporalCRS createTemporalCRS( String code ) throws FactoryException {
        final String key = toKey(code);
        return createWith(key, new WorkerSafeRunnable(){
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createTemporalCRS(key);
			}
        });
    }


    public VerticalCRS createVerticalCRS(String code) throws FactoryException {
		final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createVerticalCRS(key);
			}
		});
	}

    //
    // CSAuthority
    //
    public CartesianCS createCartesianCS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createCartesianCS(key);
			}
		});
    }

    public CoordinateSystem createCoordinateSystem( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createCoordinateSystem(key);
			}
		});
    }

    // sample implemenation with get/test
    public CoordinateSystemAxis createCoordinateSystemAxis( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createCoordinateSystemAxis(key);
			}
		});
    }

    public CylindricalCS createCylindricalCS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createCylindricalCS(key);
			}
		});
    }

    public EllipsoidalCS createEllipsoidalCS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createEllipsoidalCS(key);
			}
		});
    }

    public PolarCS createPolarCS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createPolarCS(key);
			}
		});
    }

    public SphericalCS createSphericalCS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createSphericalCS(key);
			}
		});
    }

    public TimeCS createTimeCS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createTimeCS(key);
			}
		});
    }

    public Unit<?> createUnit( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createUnit(key);
			}
		});
    }

    public VerticalCS createVerticalCS( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createVerticalCS(key);
			}
		});
    }

    //
    // DatumAuthorityFactory
    //
    public Datum createDatum( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createDatum(key);
			}
		});
    }

    public Ellipsoid createEllipsoid( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createEllipsoid(key);
			}
		});
    }

    public EngineeringDatum createEngineeringDatum( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createEngineeringDatum(key);
			}
		});
    }

    public GeodeticDatum createGeodeticDatum( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createGeodeticDatum(key);
			}
		});
    }

    public ImageDatum createImageDatum( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createImageDatum(key);
			}
		});
    }

    public PrimeMeridian createPrimeMeridian( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createPrimeMeridian(key);
			}
		});
    }

    public TemporalDatum createTemporalDatum( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createTemporalDatum(key);
			}
		});
    }

    public VerticalDatum createVerticalDatum( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createVerticalDatum(key);
			}
		});
    }

    public CoordinateOperation createCoordinateOperation( String code ) throws FactoryException {
        final String key = toKey(code);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createCoordinateOperation(key);
			}
		});
    }

    public synchronized Set/* <CoordinateOperation> */createFromCoordinateReferenceSystemCodes(
            final String sourceCode, final String targetCode ) throws FactoryException {

        final Object key = ObjectCaches.toKey(getAuthority(), sourceCode, targetCode);
		return createWith(key, new WorkerSafeRunnable() {
			public Object run(AbstractCachedAuthorityFactory worker)
					throws FactoryException {
				return worker.createFromCoordinateReferenceSystemCodes(sourceCode, targetCode);
			}
		});
    }

    /**
     * This method is used to cut down the amount of try/catch/finally code
     * needed when working with the cache and workers.
     * <p>
     * This code brings together two try/catch/finally blocks.
     *
     * For cache management:<pre><code>
     *  T value = (T) cache.get(key);
     *  if (value == null) {
     *      try {
     *          cache.writeLock(key);
     *          value = (T) cache.peek(key);
     *          if (value == null) {
     *          	....generate value....
     *              cache.put( key, value );
     *          }
     *      } finally {
     *          cache.writeUnLock(key);
     *      }
     *  }
     * </code></pre>
     * And worker management when generating values:<pre><code>
     * AbstractCachedAuthorityFactory worker = null;
     * try {
     *  worker = (AbstractCachedAuthorityFactory) getPool().borrowObject();
     *  value = (T) runner.run( worker );
     * } catch (FactoryException e) {
     *     throw e;
     * } catch (Exception e) {
     *     throw new FactoryException(e);
     * } finally {
     *     try {
     *         getPool().returnObject(worker);
     *     } catch (Exception e) {
     *         LOGGER.log(Level.WARNING, "Unable to return worker " + e, e);
     *     }
     * }
     * </code></pre>
     *
     * @param key Used to look in the cache
     * @param runner Used to generate a value in the case of a cache miss
     * @return value from either the cache or generated
     */
    protected <T> T createWith( Object key, final WorkerSafeRunnable runner ) throws FactoryException {
        
        T value = get(key, new Callable<T>() {
            @Override
            public T call() throws Exception {
                AbstractCachedAuthorityFactory worker = makeWorker();
                try {
                    return (T) runner.run(worker);
                } finally {
                    disposeWorker(worker);
                }
            }
        });
        return value;
    }
    /**
     * An interface describing a portion of work for which a worker is needed.
     * <p>
     * The worker is borrowed from the pool
     */
    protected abstract class WorkerSafeRunnable {
    	public abstract Object run( AbstractCachedAuthorityFactory worker ) throws FactoryException;
    }

    public String getBackingStoreDescription() throws FactoryException {
        AbstractCachedAuthorityFactory worker;
        try {
            worker = makeWorker();
        } catch (Exception e) {
            Throwables.propagateIfInstanceOf(e, FactoryException.class);
            throw Throwables.propagate(e);
        }
        try {
            return worker.getBackingStoreDescription();
        } catch (FactoryException e) {
            throw e;
        } catch (Exception e) {
            throw new FactoryException(e);
        }
        finally {
            try {
                disposeWorker(worker);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unable to return worker " + e, e);
            }
        }
    }
    /**
     * Clean up the object pool of workers (since we are shutting down).
     * <p>
     * Subclasses may wish to override this method if they have their own resources
     * to clean up (like a database connection). If you do this please remember to call
     * super.dispose().
     * </p>
     */
    public void dispose() throws FactoryException {
//        if (workers != null) {
//            try {
//                workers.clear();
//            } catch (FactoryException e) {
//                throw e;
//            } catch (Exception e) {
//                throw new FactoryException( e );
//            }
//            workers = null;
//        }
    }

//    /**
//     * Reinitialize an instance to be returned by the pool.
//     * <p>
//     * Please note that BEFORE this method has been called AbstractAuthorityMediator has already:
//     * <ul>
//     * <li>provided the worker with the single shared <code>cache</code>
//     * <li>provided the worker with the single shared <code>findCache</code>
//     * </ul>
//     */
//    protected abstract void activateWorker( AbstractCachedAuthorityFactory worker ) throws Exception;

    /**
     * Disposes a worker, may return it to a pool depending on implementation.
     */
    protected abstract void disposeWorker( AbstractCachedAuthorityFactory worker ) throws Exception;

    /**
     * Returns a worker instance, may be from a pool; be sure to call disposeWorker after using it.
     */
    protected abstract AbstractCachedAuthorityFactory makeWorker() throws Exception;

//    /**
//     * Un-initialize an instance to be returned to the pool.
//     */
//    protected abstract void passivateWorker( AbstractCachedAuthorityFactory worker ) throws Exception;

//    /**
//     * Ensures that the instance is safe to be returned by the pool.
//     */
//    protected abstract boolean validateWorker( AbstractCachedAuthorityFactory worker );

    /**
     * Returns a finder which can be used for looking up unidentified objects.
     * <p>
     * The returned implementation will make use of workers as needed.
     *
     * @param type The type of objects to look for.
     * @return A finder to use for looking up unidentified objects.
     * @throws FactoryException if the finder can not be created.
     * @since 2.4
     */
    public IdentifiedObjectFinder getIdentifiedObjectFinder(
            final Class/* <? extends IdentifiedObject> */type ) throws FactoryException {
        return new LazyCachedFinder(type);
    }
    /**
     * An {@link IdentifiedObjectFinder} which uses a worker when searching.
     * <p>
     * The worker used is configured to store answers in a separate <code>findCache</code>, rather
     * that disrupt the regular <code>cached</code>(which is focused on retaining codes requested
     * by the user application). This is because hundred of objects may be created during a
     * scan while only one will be typically retained. We don't want to overload the cache with
     * every false candidates that we encounter during the scan.
     * <p>
     * Because the worker is configured differently before use we must be careful to return it to
     * its original state before returning back to the <code>workers</code> pool.
     */
    private final class LazyCachedFinder extends IdentifiedObjectFinder {
        private Class type;
        /**
         * Creates a finder for the underlying backing store.
         */
        LazyCachedFinder(final Class type) {
            super( AbstractAuthorityMediator.this, type);
            this.type = type;
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
                candidate = (IdentifiedObject) findCache.get(object, new Callable<IdentifiedObject>() {
                    @Override
                    public IdentifiedObject call() throws Exception {
                        IdentifiedObject found;
                        final AbstractCachedAuthorityFactory worker = makeWorker();
                        try {
                            worker.cache = ObjectCaches.chain( ObjectCaches.create("weak",3000), cache );
                            worker.findCache = findCache;

                            setProxy(AuthorityFactoryProxy.getInstance(worker, type));

                            found = doFind(object);
                        } catch (Exception e) {
                            throw new FactoryException(e);
                        }
                        finally {
                            setProxy(null);
                            worker.cache = cache;
                            worker.findCache = findCache;
                            try {
                                disposeWorker(worker);
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Unable to return worker " + e, e);
                            }
                        }
                        if( found == null) {
                            return null; // not found
                        }
                        IdentifiedObject candidate = (IdentifiedObject) findCache.getIfPresent(object);
                        if( candidate == null ){
                            findCache.put(object, found);
                            return found;
                        }
                        else {
                            return candidate;
                        }
                    }
                });
            } catch (ExecutionException e) {
                Throwables.propagateIfInstanceOf(e.getCause(), FactoryException.class);
                throw Throwables.propagate(e.getCause());
            }

            return candidate;
        }
        protected IdentifiedObject doFind(IdentifiedObject object) throws FactoryException {
            return super.find(object);
        }

        protected Citation getAuthority(){
            return AbstractAuthorityMediator.this.getAuthority();
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
