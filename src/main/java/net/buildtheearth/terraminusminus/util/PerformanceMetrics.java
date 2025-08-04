package net.buildtheearth.terraminusminus.util;

import lombok.Getter;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.TerraMinusMinus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for tracking and logging performance metrics.
 * This class provides methods to track execution time, memory usage, and operation counts
 * for various components of the TerraMinus-- library.
 */
public class PerformanceMetrics {
    private static final Map<String, MetricCategory> categories = new ConcurrentHashMap<>();
    
    /**
     * Gets or creates a metric category with the specified name.
     * 
     * @param name The name of the metric category
     * @return The metric category
     */
    public static MetricCategory getCategory(@NonNull String name) {
        return categories.computeIfAbsent(name, MetricCategory::new);
    }
    
    /**
     * Logs all current metrics to the TerraMinus-- logger.
     */
    public static void logAllMetrics() {
        TerraMinusMinus.LOGGER.info("=== TerraMinus-- Performance Metrics ===");
        
        categories.forEach((name, category) -> {
            TerraMinusMinus.LOGGER.info("Category: " + name);
            TerraMinusMinus.LOGGER.info("  Operations: " + category.getOperationCount());
            TerraMinusMinus.LOGGER.info("  Total Time: " + category.getTotalTimeMs() + "ms");
            TerraMinusMinus.LOGGER.info("  Avg Time: " + (category.getOperationCount() > 0 ? 
                    category.getTotalTimeMs() / category.getOperationCount() : 0) + "ms");
            TerraMinusMinus.LOGGER.info("  Memory Used: " + formatMemory(category.getMemoryUsedBytes()));
            TerraMinusMinus.LOGGER.info("  Peak Memory: " + formatMemory(category.getPeakMemoryBytes()));
            TerraMinusMinus.LOGGER.info("  Cache Hits: " + category.getCacheHits());
            TerraMinusMinus.LOGGER.info("  Cache Misses: " + category.getCacheMisses());
            
            if (category.getCacheHits() + category.getCacheMisses() > 0) {
                double hitRate = (double) category.getCacheHits() / 
                        (category.getCacheHits() + category.getCacheMisses()) * 100.0;
                TerraMinusMinus.LOGGER.info("  Cache Hit Rate: " + String.format("%.2f", hitRate) + "%");
            }
        });
        
        TerraMinusMinus.LOGGER.info("=== End of Performance Metrics ===");
    }
    
    /**
     * Formats memory size in bytes to a human-readable string.
     * 
     * @param bytes The memory size in bytes
     * @return A human-readable string representation of the memory size
     */
    private static String formatMemory(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Creates a timer for measuring the execution time of an operation.
     * 
     * @param category The category name for the operation
     * @return A Timer object that can be used to measure execution time
     */
    public static Timer startTimer(@NonNull String category) {
        return new Timer(getCategory(category));
    }
    
    /**
     * Records a cache hit for the specified category.
     * 
     * @param category The category name
     */
    public static void recordCacheHit(@NonNull String category) {
        getCategory(category).recordCacheHit();
    }
    
    /**
     * Records a cache miss for the specified category.
     * 
     * @param category The category name
     */
    public static void recordCacheMiss(@NonNull String category) {
        getCategory(category).recordCacheMiss();
    }
    
    /**
     * Records memory usage for the specified category.
     * 
     * @param category The category name
     * @param bytes The memory usage in bytes
     */
    public static void recordMemoryUsage(@NonNull String category, long bytes) {
        getCategory(category).recordMemoryUsage(bytes);
    }
    
    /**
     * Estimates the current memory usage of the JVM.
     * 
     * @return The current memory usage in bytes
     */
    public static long getCurrentMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    /**
     * A category of performance metrics.
     */
    public static class MetricCategory {
        private final String name;
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        private final AtomicInteger operationCount = new AtomicInteger(0);
        private final AtomicLong memoryUsedBytes = new AtomicLong(0);
        private final AtomicLong peakMemoryBytes = new AtomicLong(0);
        private final AtomicInteger cacheHits = new AtomicInteger(0);
        private final AtomicInteger cacheMisses = new AtomicInteger(0);
        
        private MetricCategory(String name) {
            this.name = name;
        }
        
        /**
         * Gets the name of this metric category.
         * @return The category name
         */
        public String getName() {
            return name;
        }
        
        /**
         * Gets the total execution time in milliseconds.
         * @return The total execution time
         */
        public long getTotalTimeMs() {
            return totalTimeMs.get();
        }
        
        /**
         * Gets the number of operations recorded.
         * @return The operation count
         */
        public int getOperationCount() {
            return operationCount.get();
        }
        
        /**
         * Gets the total memory used in bytes.
         * @return The memory used
         */
        public long getMemoryUsedBytes() {
            return memoryUsedBytes.get();
        }
        
        /**
         * Gets the peak memory usage in bytes.
         * @return The peak memory usage
         */
        public long getPeakMemoryBytes() {
            return peakMemoryBytes.get();
        }
        
        /**
         * Gets the number of cache hits.
         * @return The cache hit count
         */
        public int getCacheHits() {
            return cacheHits.get();
        }
        
        /**
         * Gets the number of cache misses.
         * @return The cache miss count
         */
        public int getCacheMisses() {
            return cacheMisses.get();
        }
        
        /**
         * Records the execution time of an operation.
         * 
         * @param timeMs The execution time in milliseconds
         */
        void recordTime(long timeMs) {
            totalTimeMs.addAndGet(timeMs);
            operationCount.incrementAndGet();
        }
        
        /**
         * Records memory usage.
         * 
         * @param bytes The memory usage in bytes
         */
        void recordMemoryUsage(long bytes) {
            memoryUsedBytes.addAndGet(bytes);
            peakMemoryBytes.updateAndGet(current -> Math.max(current, bytes));
        }
        
        /**
         * Records a cache hit.
         */
        void recordCacheHit() {
            cacheHits.incrementAndGet();
        }
        
        /**
         * Records a cache miss.
         */
        void recordCacheMiss() {
            cacheMisses.incrementAndGet();
        }
    }
    
    /**
     * A timer for measuring the execution time of an operation.
     */
    public static class Timer implements AutoCloseable {
        private final MetricCategory category;
        private final long startTime;
        private final long startMemory;
        private boolean closed = false;
        
        private Timer(MetricCategory category) {
            this.category = category;
            this.startTime = System.currentTimeMillis();
            this.startMemory = getCurrentMemoryUsage();
        }
        
        /**
         * Stops the timer and records the execution time.
         */
        @Override
        public void close() {
            if (!closed) {
                long timeMs = System.currentTimeMillis() - startTime;
                long memoryDelta = Math.max(0, getCurrentMemoryUsage() - startMemory);
                
                category.recordTime(timeMs);
                category.recordMemoryUsage(memoryDelta);
                
                closed = true;
            }
        }
    }
}