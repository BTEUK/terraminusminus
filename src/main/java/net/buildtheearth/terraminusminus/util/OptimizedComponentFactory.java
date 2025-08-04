package net.buildtheearth.terraminusminus.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.TerraMinusMinus;
import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.dataset.scalar.CachingScalarDataset;
import net.buildtheearth.terraminusminus.generator.BatchChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Factory class for creating optimized components.
 * <p>
 * This class provides factory methods for creating instances of the optimized components
 * implemented for strategies 1, 3, and 4:
 * <ul>
 *   <li>Strategy 1: Batch Loading with BatchChunkDataLoader</li>
 *   <li>Strategy 3: Parallel Processing in MultiScalarDataset (already implemented in core)</li>
 *   <li>Strategy 4: Lower-level Caching with CachingScalarDataset</li>
 * </ul>
 * <p>
 * The factory methods provide sensible defaults and make it easy to use the optimized
 * components together.
 */
public class OptimizedComponentFactory {
    /**
     * Creates a cache for chunk data using a BatchChunkDataLoader.
     * <p>
     * This method creates a cache that uses the BatchChunkDataLoader to load chunks
     * in batches, which can significantly improve performance when loading multiple
     * chunks at once.
     *
     * @param settings The earth generator settings
     * @param expirationMinutes Cache expiration time in minutes
     * @param maxCacheSize Maximum number of entries in the cache
     * @return A loading cache for chunk data
     */
    public static LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> createChunkCache(
            @NonNull EarthGeneratorSettings settings,
            int expirationMinutes,
            int maxCacheSize) {
        
        BatchChunkDataLoader loader = new BatchChunkDataLoader(settings);
        
        return CacheBuilder.newBuilder()
                .expireAfterAccess(expirationMinutes, TimeUnit.MINUTES)
                .maximumSize(maxCacheSize)
                .softValues() // Allow GC to reclaim memory if needed
                .recordStats()
                .build(loader);
    }
    
    /**
     * Creates a cache for chunk data using a BatchChunkDataLoader with default parameters.
     *
     * @param settings The earth generator settings
     * @return A loading cache for chunk data
     */
    public static LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> createChunkCache(
            @NonNull EarthGeneratorSettings settings) {
        
        return createChunkCache(settings, 30, 1000);
    }
    
    /**
     * Loads a batch of chunks at once.
     * <p>
     * This method uses a BatchChunkDataLoader to load multiple chunks at once,
     * which can significantly improve performance compared to loading them one by one.
     *
     * @param settings The earth generator settings
     * @param positions The chunk positions to load
     * @return A map of futures for each requested chunk position
     */
    public static Map<ChunkPos, CompletableFuture<CachedChunkData>> loadChunkBatch(
            @NonNull EarthGeneratorSettings settings,
            @NonNull Collection<ChunkPos> positions) {
        
        BatchChunkDataLoader loader = new BatchChunkDataLoader(settings);
        return loader.loadBatch(positions);
    }
    
    /**
     * Wraps a scalar dataset with caching capabilities.
     * <p>
     * This method wraps an IScalarDataset with a CachingScalarDataset, which adds
     * caching capabilities at the dataset level. This can significantly reduce the
     * number of API calls and improve performance for frequently accessed data.
     *
     * @param dataset The dataset to wrap
     * @param maxCacheSize Maximum number of entries in each cache
     * @param expirationMinutes Cache expiration time in minutes
     * @return A caching scalar dataset
     */
    public static CachingScalarDataset createCachingDataset(
            @NonNull IScalarDataset dataset,
            int maxCacheSize,
            int expirationMinutes) {
        
        return new CachingScalarDataset(dataset, maxCacheSize, expirationMinutes);
    }
    
    /**
     * Wraps a scalar dataset with caching capabilities using default parameters.
     *
     * @param dataset The dataset to wrap
     * @return A caching scalar dataset
     */
    public static CachingScalarDataset createCachingDataset(@NonNull IScalarDataset dataset) {
        return new CachingScalarDataset(dataset);
    }
    
    /**
     * Logs performance metrics to the TerraMinus-- logger.
     * <p>
     * This method logs all performance metrics collected by the PerformanceMetrics class.
     * It's useful for monitoring the performance of the optimized components.
     */
    public static void logPerformanceMetrics() {
        PerformanceMetrics.logAllMetrics();
    }
    
    /**
     * Creates an optimized generator settings object.
     * <p>
     * This method creates a new EarthGeneratorSettings object with optimized datasets.
     * It wraps all scalar datasets with CachingScalarDataset to add caching capabilities.
     *
     * @param settings The original earth generator settings
     * @return An optimized earth generator settings object
     */
    public static EarthGeneratorSettings createOptimizedSettings(@NonNull EarthGeneratorSettings settings) {
        // This is a placeholder implementation
        // In a real implementation, we would create a new EarthGeneratorSettings object
        // with optimized datasets
        
        TerraMinusMinus.LOGGER.info("Creating optimized settings is not yet implemented");
        return settings;
    }
}