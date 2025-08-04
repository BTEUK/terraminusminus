package net.buildtheearth.terraminusminus.util;

import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.dataset.scalar.CachingScalarDataset;
import net.buildtheearth.terraminusminus.dataset.scalar.ConfigurableDoubleTiledDataset;
import net.buildtheearth.terraminusminus.generator.BatchChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
 * Tests for the optimized components.
 */
public class OptimizedComponentsTest {

    /**
     * Tests that the CachingScalarDataset correctly caches values.
     */
    @Test
    public void testCachingScalarDataset() throws OutOfProjectionBoundsException, ExecutionException, InterruptedException {
        // Create a mock dataset that counts the number of calls
        CountingMockDataset mockDataset = new CountingMockDataset();
        
        // Wrap it with a CachingScalarDataset
        CachingScalarDataset cachingDataset = OptimizedComponentFactory.createCachingDataset(mockDataset);
        
        // Request the same point twice
        double value1 = cachingDataset.getAsync(0.0, 0.0).get();
        double value2 = cachingDataset.getAsync(0.0, 0.0).get();
        
        // Verify that the values are the same
        assertEquals("Values should be the same", value1, value2, 0.0001);
        
        // Verify that the mock dataset was only called once
        assertEquals("Mock dataset should be called only once", 1, mockDataset.getPointCallCount());
        
        // Log cache statistics
        cachingDataset.logCacheStats();
    }
    
    /**
     * Tests that the BatchChunkDataLoader correctly loads batches of chunks.
     */
    @Test
    public void testBatchChunkDataLoader() throws ExecutionException, InterruptedException {
        // Create a simple EarthGeneratorSettings
        EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
        
        // Create a BatchChunkDataLoader
        BatchChunkDataLoader loader = new BatchChunkDataLoader(settings);
        
        // Create a list of chunk positions
        List<ChunkPos> positions = new ArrayList<>();
        positions.add(new ChunkPos(0, 0));
        positions.add(new ChunkPos(1, 0));
        positions.add(new ChunkPos(0, 1));
        positions.add(new ChunkPos(1, 1));
        
        // Load the chunks in batch
        Map<ChunkPos, CompletableFuture<CachedChunkData>> results = loader.loadBatch(positions);
        
        // Verify that all chunks were loaded
        assertEquals("All chunks should be loaded", positions.size(), results.size());
        
        // Verify that each chunk has a valid future
        for (ChunkPos pos : positions) {
            assertTrue("Chunk should have a future", results.containsKey(pos));
            assertNotNull("Future should not be null", results.get(pos));
            
            // Get the chunk data
            CachedChunkData data = results.get(pos).get();
            assertNotNull("Chunk data should not be null", data);
            
            // Verify that the chunk data has valid surface bounds
            assertTrue("Min surface Y should be less than or equal to max surface Y", 
                    data.getMinSurfaceY() <= data.getMaxSurfaceY());
        }
    }
    
    /**
     * Tests that the OptimizedComponentFactory correctly creates optimized components.
     */
    @Test
    public void testOptimizedComponentFactory() {
        // Create a simple EarthGeneratorSettings
        EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
        
        // Create a chunk cache
        assertNotNull("Chunk cache should not be null", 
                OptimizedComponentFactory.createChunkCache(settings));
        
        // Create a caching dataset
        IScalarDataset mockDataset = new CountingMockDataset();
        assertNotNull("Caching dataset should not be null", 
                OptimizedComponentFactory.createCachingDataset(mockDataset));
        
        // Log performance metrics
        OptimizedComponentFactory.logPerformanceMetrics();
    }
    
    /**
     * A mock dataset that counts the number of calls.
     */
    private static class CountingMockDataset implements IScalarDataset {
        private int pointCallCount = 0;
        private int regionCallCount = 0;
        
        @Override
        public CompletableFuture<Double> getAsync(double lon, double lat) throws OutOfProjectionBoundsException {
            pointCallCount++;
            return CompletableFuture.completedFuture(lon + lat);
        }
        
        @Override
        public CompletableFuture<double[]> getAsync(CornerBoundingBox2d bounds, int sizeX, int sizeZ) throws OutOfProjectionBoundsException {
            regionCallCount++;
            double[] result = new double[sizeX * sizeZ];
            for (int i = 0; i < result.length; i++) {
                result[i] = i;
            }
            return CompletableFuture.completedFuture(result);
        }
        
        public int getPointCallCount() {
            return pointCallCount;
        }
        
        public int getRegionCallCount() {
            return regionCallCount;
        }
    }
}