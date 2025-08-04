package net.buildtheearth.terraminusminus.dataset.scalar;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.TerraMinusMinus;
import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.PerformanceMetrics;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper around an {@link IScalarDataset} that adds caching capabilities.
 * <p>
 * This class implements caching at the dataset level, which can significantly reduce
 * the number of API calls and improve performance for frequently accessed data.
 * <p>
 * Memory usage is carefully managed by:
 * <ul>
 *   <li>Using soft references for cache values (allows GC to reclaim memory when needed)</li>
 *   <li>Setting expiration times for cached data</li>
 *   <li>Limiting the maximum cache size</li>
 * </ul>
 * <p>
 * Performance metrics are collected to monitor cache hit rates and memory usage.
 */
public class CachingScalarDataset implements IScalarDataset {
    /**
     * Default maximum cache size.
     */
    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;
    
    /**
     * Default cache expiration time in minutes.
     */
    private static final int DEFAULT_EXPIRATION_MINUTES = 30;
    
    /**
     * The wrapped dataset.
     */
    private final IScalarDataset delegate;
    
    /**
     * Cache for single point values.
     */
    private final LoadingCache<PointKey, CompletableFuture<Double>> pointCache;
    
    /**
     * Cache for region values.
     */
    private final LoadingCache<RegionKey, CompletableFuture<double[]>> regionCache;
    
    /**
     * Creates a new CachingScalarDataset with default cache parameters.
     *
     * @param delegate The dataset to wrap
     */
    public CachingScalarDataset(@NonNull IScalarDataset delegate) {
        this(delegate, DEFAULT_MAX_CACHE_SIZE, DEFAULT_EXPIRATION_MINUTES);
    }
    
    /**
     * Creates a new CachingScalarDataset with custom cache parameters.
     *
     * @param delegate The dataset to wrap
     * @param maxCacheSize Maximum number of entries in each cache
     * @param expirationMinutes Cache expiration time in minutes
     */
    public CachingScalarDataset(@NonNull IScalarDataset delegate, int maxCacheSize, int expirationMinutes) {
        this.delegate = delegate;
        
        // Create point cache
        this.pointCache = CacheBuilder.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterAccess(expirationMinutes, TimeUnit.MINUTES)
                .softValues() // Allow GC to reclaim memory if needed
                .recordStats()
                .build(new CacheLoader<PointKey, CompletableFuture<Double>>() {
                    @Override
                    public CompletableFuture<Double> load(PointKey key) throws Exception {
                        PerformanceMetrics.recordCacheMiss("CachingScalarDataset.pointCache");
                        return delegate.getAsync(key.lon, key.lat);
                    }
                });
        
        // Create region cache
        this.regionCache = CacheBuilder.newBuilder()
                .maximumSize(maxCacheSize / 4) // Regions are larger, so we use a smaller cache
                .expireAfterAccess(expirationMinutes, TimeUnit.MINUTES)
                .softValues() // Allow GC to reclaim memory if needed
                .recordStats()
                .build(new CacheLoader<RegionKey, CompletableFuture<double[]>>() {
                    @Override
                    public CompletableFuture<double[]> load(RegionKey key) throws Exception {
                        PerformanceMetrics.recordCacheMiss("CachingScalarDataset.regionCache");
                        return delegate.getAsync(key.bounds, key.sizeX, key.sizeZ);
                    }
                });
        
        TerraMinusMinus.LOGGER.info("Created CachingScalarDataset with maxCacheSize={}, expirationMinutes={}",
                maxCacheSize, expirationMinutes);
    }
    
    /**
     * Gets a single value at the given point, using the cache if available.
     *
     * @param lon The longitude
     * @param lat The latitude
     * @return A future that will be completed with the value
     */
    @Override
    public CompletableFuture<Double> getAsync(double lon, double lat) throws OutOfProjectionBoundsException {
        try (PerformanceMetrics.Timer timer = PerformanceMetrics.startTimer("CachingScalarDataset.getAsync.point")) {
            PointKey key = new PointKey(lon, lat);
            
            try {
                CompletableFuture<Double> future = pointCache.get(key);
                PerformanceMetrics.recordCacheHit("CachingScalarDataset.pointCache");
                return future;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof OutOfProjectionBoundsException) {
                    throw (OutOfProjectionBoundsException) e.getCause();
                }
                TerraMinusMinus.LOGGER.error("Error getting value from cache: {}", e.getMessage());
                return delegate.getAsync(lon, lat);
            }
        }
    }
    
    /**
     * Gets a bunch of values at the given coordinates, using the cache if available.
     *
     * @param bounds The bounds of the region
     * @param sizeX The number of samples to take along the X axis
     * @param sizeZ The number of samples to take along the Z axis
     * @return A future that will be completed with the values
     */
    @Override
    public CompletableFuture<double[]> getAsync(@NonNull CornerBoundingBox2d bounds, int sizeX, int sizeZ) throws OutOfProjectionBoundsException {
        try (PerformanceMetrics.Timer timer = PerformanceMetrics.startTimer("CachingScalarDataset.getAsync.region")) {
            // For very small regions, it might be more efficient to use the point cache
            if (sizeX * sizeZ <= 4) {
                return getSmallRegionAsync(bounds, sizeX, sizeZ);
            }
            
            RegionKey key = new RegionKey(bounds, sizeX, sizeZ);
            
            try {
                CompletableFuture<double[]> future = regionCache.get(key);
                PerformanceMetrics.recordCacheHit("CachingScalarDataset.regionCache");
                return future;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof OutOfProjectionBoundsException) {
                    throw (OutOfProjectionBoundsException) e.getCause();
                }
                TerraMinusMinus.LOGGER.error("Error getting region from cache: {}", e.getMessage());
                return delegate.getAsync(bounds, sizeX, sizeZ);
            }
        }
    }
    
    /**
     * Gets a small region by fetching individual points.
     * This can be more efficient for very small regions.
     *
     * @param bounds The bounds of the region
     * @param sizeX The number of samples to take along the X axis
     * @param sizeZ The number of samples to take along the Z axis
     * @return A future that will be completed with the values
     */
    private CompletableFuture<double[]> getSmallRegionAsync(CornerBoundingBox2d bounds, int sizeX, int sizeZ) throws OutOfProjectionBoundsException {
        double[] result = new double[sizeX * sizeZ];
        CompletableFuture<?>[] futures = new CompletableFuture[sizeX * sizeZ];
        
        double minLon = bounds.minX();
        double maxLon = bounds.maxX();
        double minLat = bounds.minZ();
        double maxLat = bounds.maxZ();
        
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double lon = minLon + (maxLon - minLon) * x / (sizeX - 1);
                double lat = minLat + (maxLat - minLat) * z / (sizeZ - 1);
                
                int index = x * sizeZ + z;
                futures[index] = getAsync(lon, lat).thenAccept(value -> result[index] = value);
            }
        }
        
        return CompletableFuture.allOf(futures).thenApply(v -> result);
    }
    
    /**
     * Logs cache statistics to the TerraMinus-- logger.
     */
    public void logCacheStats() {
        TerraMinusMinus.LOGGER.info("=== CachingScalarDataset Cache Statistics ===");
        TerraMinusMinus.LOGGER.info("Point Cache:");
        TerraMinusMinus.LOGGER.info("  Hit Rate: {}%", String.format("%.2f", pointCache.stats().hitRate() * 100));
        TerraMinusMinus.LOGGER.info("  Hit Count: {}", pointCache.stats().hitCount());
        TerraMinusMinus.LOGGER.info("  Miss Count: {}", pointCache.stats().missCount());
        TerraMinusMinus.LOGGER.info("  Load Exception Count: {}", pointCache.stats().loadExceptionCount());
        TerraMinusMinus.LOGGER.info("  Eviction Count: {}", pointCache.stats().evictionCount());
        
        TerraMinusMinus.LOGGER.info("Region Cache:");
        TerraMinusMinus.LOGGER.info("  Hit Rate: {}%", String.format("%.2f", regionCache.stats().hitRate() * 100));
        TerraMinusMinus.LOGGER.info("  Hit Count: {}", regionCache.stats().hitCount());
        TerraMinusMinus.LOGGER.info("  Miss Count: {}", regionCache.stats().missCount());
        TerraMinusMinus.LOGGER.info("  Load Exception Count: {}", regionCache.stats().loadExceptionCount());
        TerraMinusMinus.LOGGER.info("  Eviction Count: {}", regionCache.stats().evictionCount());
        TerraMinusMinus.LOGGER.info("=== End of Cache Statistics ===");
    }
    
    /**
     * Clears all caches.
     */
    public void clearCaches() {
        pointCache.invalidateAll();
        regionCache.invalidateAll();
        TerraMinusMinus.LOGGER.info("Cleared all caches in CachingScalarDataset");
    }
    
    /**
     * A key for the point cache.
     */
    private static class PointKey {
        private final double lon;
        private final double lat;
        
        public PointKey(double lon, double lat) {
            this.lon = lon;
            this.lat = lat;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PointKey pointKey = (PointKey) o;
            return Double.compare(pointKey.lon, lon) == 0 && Double.compare(pointKey.lat, lat) == 0;
        }
        
        @Override
        public int hashCode() {
            return 31 * Double.hashCode(lon) + Double.hashCode(lat);
        }
    }
    
    /**
     * A key for the region cache.
     */
    private static class RegionKey {
        private final CornerBoundingBox2d bounds;
        private final int sizeX;
        private final int sizeZ;
        private final int hashCode;
        
        public RegionKey(CornerBoundingBox2d bounds, int sizeX, int sizeZ) {
            this.bounds = bounds;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            
            // Pre-compute hash code
            int result = 31 * Double.hashCode(bounds.minX()) + Double.hashCode(bounds.maxX());
            result = 31 * result + Double.hashCode(bounds.minZ());
            result = 31 * result + Double.hashCode(bounds.maxZ());
            result = 31 * result + sizeX;
            result = 31 * result + sizeZ;
            this.hashCode = result;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegionKey regionKey = (RegionKey) o;
            
            return sizeX == regionKey.sizeX &&
                   sizeZ == regionKey.sizeZ &&
                   Double.compare(bounds.minX(), regionKey.bounds.minX()) == 0 &&
                   Double.compare(bounds.maxX(), regionKey.bounds.maxX()) == 0 &&
                   Double.compare(bounds.minZ(), regionKey.bounds.minZ()) == 0 &&
                   Double.compare(bounds.maxZ(), regionKey.bounds.maxZ()) == 0;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}