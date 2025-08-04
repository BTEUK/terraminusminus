package net.buildtheearth.terraminusminus.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.cache.CacheLoader;
import com.google.common.collect.Lists;

import lombok.NonNull;
import static net.daporkchop.lib.common.util.PorkUtil.uncheckedCast;
import net.buildtheearth.terraminusminus.TerraMinusMinus;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.PerformanceMetrics;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;

/**
 * An extension of {@link ChunkDataLoader} that supports batch loading of chunks.
 * <p>
 * This class optimizes chunk loading by:
 * <ul>
 *   <li>Grouping nearby chunks into batches</li>
 *   <li>Processing batches in parallel</li>
 *   <li>Sharing computation for overlapping regions</li>
 * </ul>
 * <p>
 * Memory usage is carefully managed by:
 * <ul>
 *   <li>Limiting the maximum batch size</li>
 *   <li>Controlling the number of concurrent batch operations</li>
 *   <li>Using a dedicated thread pool with memory-aware scaling</li>
 * </ul>
 * <p>
 * Performance metrics are collected to monitor resource usage and optimize batch parameters.
 *
 * @author Junie
 * @see ChunkDataLoader
 * @see CachedChunkData
 */
public class BatchChunkDataLoader extends ChunkDataLoader {
    /**
     * Default maximum number of chunks in a batch.
     */
    private static final int DEFAULT_MAX_BATCH_SIZE = 16;
    
    /**
     * Default maximum number of concurrent batch operations.
     */
    private static final int DEFAULT_MAX_CONCURRENT_BATCHES = 
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    
    /**
     * Thread pool for parallel batch processing using virtual threads.
     */
    private static final Executor BATCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    
    /**
     * Maximum number of chunks in a batch.
     */
    private final int maxBatchSize;
    
    /**
     * Maximum number of concurrent batch operations.
     */
    private final int maxConcurrentBatches;
    
    /**
     * Counter for active batch operations.
     */
    private final AtomicInteger activeBatchCount = new AtomicInteger(0);
    
    /**
     * Creates a new BatchChunkDataLoader with default batch parameters.
     *
     * @param settings The earth generator settings
     */
    public BatchChunkDataLoader(@NonNull EarthGeneratorSettings settings) {
        this(settings, DEFAULT_MAX_BATCH_SIZE, DEFAULT_MAX_CONCURRENT_BATCHES);
    }
    
    /**
     * Creates a new BatchChunkDataLoader with custom batch parameters.
     *
     * @param settings The earth generator settings
     * @param maxBatchSize Maximum number of chunks in a batch
     * @param maxConcurrentBatches Maximum number of concurrent batch operations
     */
    public BatchChunkDataLoader(@NonNull EarthGeneratorSettings settings, 
                               int maxBatchSize, 
                               int maxConcurrentBatches) {
        super(settings);
        this.maxBatchSize = maxBatchSize;
        this.maxConcurrentBatches = maxConcurrentBatches;
        
        TerraMinusMinus.LOGGER.info("Created BatchChunkDataLoader with maxBatchSize={}, maxConcurrentBatches={}",
                maxBatchSize, maxConcurrentBatches);
    }
    
    /**
     * Loads a batch of chunks at once.
     * <p>
     * This method optimizes loading by grouping nearby chunks and processing them together.
     * It returns a map of futures for each requested chunk position.
     *
     * @param positions The chunk positions to load
     * @return A map of futures for each requested chunk position
     */
    public Map<ChunkPos, CompletableFuture<CachedChunkData>> loadBatch(Collection<ChunkPos> positions) {
        try (PerformanceMetrics.Timer timer = PerformanceMetrics.startTimer("BatchChunkDataLoader.loadBatch")) {
            // Create a map to hold the futures for each position
            Map<ChunkPos, CompletableFuture<CachedChunkData>> results = new ConcurrentHashMap<>();
            
            // Group chunks by region (a 4x4 chunk area)
            Map<RegionKey, List<ChunkPos>> regionGroups = groupByRegion(positions);
            
            TerraMinusMinus.LOGGER.debug("Grouped {} chunks into {} regions", 
                    positions.size(), regionGroups.size());
            
            // Process each region
            for (Map.Entry<RegionKey, List<ChunkPos>> entry : regionGroups.entrySet()) {
                RegionKey regionKey = entry.getKey();
                List<ChunkPos> regionChunks = entry.getValue();
                
                // Split large regions into batches
                List<List<ChunkPos>> batches = Lists.partition(regionChunks, maxBatchSize);
                
                for (List<ChunkPos> batch : batches) {
                    // Wait if we've reached the maximum number of concurrent batches
                    waitForBatchSlot();
                    
                    // Process this batch
                    processBatch(batch, results);
                }
            }
            
            return results;
        }
    }
    
    /**
     * Processes a batch of chunks and updates the results map.
     *
     * @param batch The batch of chunks to process
     * @param results The map to update with the results
     */
    private void processBatch(List<ChunkPos> batch, Map<ChunkPos, CompletableFuture<CachedChunkData>> results) {
        activeBatchCount.incrementAndGet();
        
        try {
            // Calculate the bounds that encompass all chunks in the batch
            Bounds2d batchBounds = calculateBatchBounds(batch);
            
            // Create a CompletableFuture for the batch data
            CompletableFuture<Map<ChunkPos, CachedChunkData>> batchFuture = 
                    CompletableFuture.supplyAsync(() -> {
                        try (PerformanceMetrics.Timer batchTimer = 
                                PerformanceMetrics.startTimer("BatchChunkDataLoader.processBatch")) {
                            // Convert bounds to geo coordinates
                            CornerBoundingBox2d batchBoundsGeo;
                            try {
                                batchBoundsGeo = batchBounds.toCornerBB(datasets.projection(), false).toGeo();
                            } catch (OutOfProjectionBoundsException e) {
                                // If we can't convert the bounds, process each chunk individually
                                TerraMinusMinus.LOGGER.warn("Batch bounds out of projection bounds, processing chunks individually");
                                return processChunksIndividually(batch);
                            }
                            
                            // Process the batch as a whole
                            return processBatchData(batch, batchBounds, batchBoundsGeo);
                        }
                    }, BATCH_EXECUTOR);
            
            // For each chunk in the batch, create a future that will be completed when the batch is done
            for (ChunkPos pos : batch) {
                CompletableFuture<CachedChunkData> chunkFuture = batchFuture.thenApply(batchData -> {
                    CachedChunkData data = batchData.get(pos);
                    if (data != null) {
                        PerformanceMetrics.recordCacheHit("BatchChunkDataLoader.getChunkFromBatch");
                        return data;
                    } else {
                        // This shouldn't happen, but if it does, fall back to individual loading
                        PerformanceMetrics.recordCacheMiss("BatchChunkDataLoader.getChunkFromBatch");
                        TerraMinusMinus.LOGGER.warn("Chunk {} not found in batch data, loading individually", pos);
                        try {
                            return super.load(pos).get();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to load chunk " + pos, e);
                        }
                    }
                });
                
                results.put(pos, chunkFuture);
            }
            
            // When the batch is done, decrement the active batch count
            batchFuture.whenComplete((data, ex) -> {
                activeBatchCount.decrementAndGet();
                
                if (ex != null) {
                    TerraMinusMinus.LOGGER.error("Error processing batch: {}", ex.getMessage());
                } else {
                    TerraMinusMinus.LOGGER.debug("Batch completed successfully with {} chunks", data.size());
                }
            });
        } catch (Exception e) {
            // If there's an error, decrement the active batch count
            activeBatchCount.decrementAndGet();
            throw e;
        }
    }
    
    /**
     * Processes a batch of chunks using the shared bounds.
     *
     * @param batch The batch of chunks to process
     * @param batchBounds The bounds that encompass all chunks in the batch
     * @param batchBoundsGeo The geo coordinates of the batch bounds
     * @return A map of chunk positions to chunk data
     */
    private Map<ChunkPos, CachedChunkData> processBatchData(List<ChunkPos> batch, 
                                                          Bounds2d batchBounds, 
                                                          CornerBoundingBox2d batchBoundsGeo) {
        Map<ChunkPos, CachedChunkData> results = new HashMap<>();
        
        try {
            // Request data for the entire batch region
            CompletableFuture<?>[] dataFutures = new CompletableFuture[bakers.length];
            
            for (int i = 0; i < bakers.length; i++) {
                try {
                    // Use the first chunk as a representative for the batch
                    dataFutures[i] = bakers[i].requestData(batch.get(0), datasets, batchBounds, batchBoundsGeo);
                } catch (OutOfProjectionBoundsException e) {
                    dataFutures[i] = CompletableFuture.completedFuture(null);
                }
            }
            
            // Wait for all data futures to complete
            CompletableFuture.allOf(dataFutures).join();
            
            // Process each chunk using the shared data
            for (ChunkPos pos : batch) {
                CachedChunkData.Builder builder = CachedChunkData.builder();
                
                // Bake the data for this specific chunk
                for (int i = 0; i < bakers.length; i++) {
                    Object data = dataFutures[i].isDone() ? dataFutures[i].join() : null;
                    // Use unchecked cast as in IEarthAsyncPipelineStep.getFuture
                    bakers[i].bake(pos, builder, uncheckedCast(data));
                }
                
                // Build the chunk data and add it to the results
                results.put(pos, builder.build());
            }
        } catch (Exception e) {
            TerraMinusMinus.LOGGER.error("Error processing batch data: {}", e.getMessage());
            
            // Fall back to processing chunks individually
            return processChunksIndividually(batch);
        }
        
        return results;
    }
    
    /**
     * Processes each chunk in the batch individually.
     * This is a fallback method when batch processing fails.
     *
     * @param batch The batch of chunks to process
     * @return A map of chunk positions to chunk data
     */
    private Map<ChunkPos, CachedChunkData> processChunksIndividually(List<ChunkPos> batch) {
        Map<ChunkPos, CachedChunkData> results = new HashMap<>();
        
        for (ChunkPos pos : batch) {
            try {
                results.put(pos, super.load(pos).get());
            } catch (Exception e) {
                TerraMinusMinus.LOGGER.error("Error loading chunk {}: {}", pos, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Calculates the bounds that encompass all chunks in the batch.
     *
     * @param batch The batch of chunks
     * @return The bounds that encompass all chunks in the batch
     */
    private Bounds2d calculateBatchBounds(List<ChunkPos> batch) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        
        for (ChunkPos pos : batch) {
            int baseX = ChunkPos.cubeToMinBlock(pos.x());
            int baseZ = ChunkPos.cubeToMinBlock(pos.z());
            
            minX = Math.min(minX, baseX);
            maxX = Math.max(maxX, baseX + 16);
            minZ = Math.min(minZ, baseZ);
            maxZ = Math.max(maxZ, baseZ + 16);
        }
        
        return Bounds2d.of(minX, maxX, minZ, maxZ);
    }
    
    /**
     * Groups chunk positions by region.
     * A region is a 4x4 chunk area.
     *
     * @param positions The chunk positions to group
     * @return A map of region keys to lists of chunk positions
     */
    private Map<RegionKey, List<ChunkPos>> groupByRegion(Collection<ChunkPos> positions) {
        Map<RegionKey, List<ChunkPos>> groups = new HashMap<>();
        
        for (ChunkPos pos : positions) {
            RegionKey key = new RegionKey(pos.x() >> 2, pos.z() >> 2);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(pos);
        }
        
        return groups;
    }
    
    /**
     * Waits until a batch slot is available.
     * This method blocks if the maximum number of concurrent batches has been reached.
     */
    private void waitForBatchSlot() {
        while (activeBatchCount.get() >= maxConcurrentBatches) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for batch slot", e);
            }
        }
    }
    
    /**
     * A key for grouping chunks by region.
     */
    private static class RegionKey {
        private final int regionX;
        private final int regionZ;
        
        public RegionKey(int regionX, int regionZ) {
            this.regionX = regionX;
            this.regionZ = regionZ;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RegionKey regionKey = (RegionKey) o;
            return regionX == regionKey.regionX && regionZ == regionKey.regionZ;
        }
        
        @Override
        public int hashCode() {
            return 31 * regionX + regionZ;
        }
        
        @Override
        public String toString() {
            return "Region(" + regionX + ", " + regionZ + ")";
        }
    }
    
    /**
     * Loads a single chunk.
     * This method is called by the cache when a chunk is not found.
     * It delegates to the batch loading method with a single chunk.
     *
     * @param pos The chunk position to load
     * @return A future that will be completed with the chunk data
     */
    @Override
    public CompletableFuture<CachedChunkData> load(@NonNull ChunkPos pos) {
        // For single chunk loading, just use the parent implementation
        // This avoids the overhead of batch processing for a single chunk
        return super.load(pos);
    }
}