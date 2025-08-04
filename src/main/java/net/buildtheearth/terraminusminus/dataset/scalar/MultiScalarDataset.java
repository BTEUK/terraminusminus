package net.buildtheearth.terraminusminus.dataset.scalar;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import net.buildtheearth.terraminusminus.TerraConstants;
import net.buildtheearth.terraminusminus.TerraMinusMinus;
import net.buildtheearth.terraminusminus.config.condition.DoubleCondition;
import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.IntRange;
import net.buildtheearth.terraminusminus.util.PerformanceMetrics;
import net.buildtheearth.terraminusminus.util.bvh.BVH;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;
import net.buildtheearth.terraminusminus.util.http.Disk;
import net.daporkchop.lib.common.function.io.IOFunction;
import net.daporkchop.lib.common.function.throwing.EFunction;

import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * Implementation of {@link IScalarDataset} which can sample from multiple {@link IScalarDataset}s and combine the results.
 *
 * @author DaPorkchop_
 */
public class MultiScalarDataset implements IScalarDataset {
    // Maximum number of threads to use for parallel processing
    private static final int MAX_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    
    // Thread pool for parallel dataset processing using virtual threads
    private static final Executor DATASET_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    
    protected final BVH<WrappedDataset> bvh;

    @SneakyThrows(IOException.class)
    public MultiScalarDataset(@NonNull String name, boolean useDefault) {
        List<URL> configSources = new ArrayList<>();
        if (useDefault) { //add default configuration
            configSources.add(MultiScalarDataset.class.getResource(name + ".json5"));
        }

        try (Stream<Path> stream = Files.list(Files.createDirectories(Disk.configFile(name)))) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(".*\\.json5?$"))
                    .map(Path::toUri).map((EFunction<URI, URL>) URI::toURL)
                    .forEach(configSources::add);
        }

        this.bvh = BVH.of(configSources.stream()
                .map((IOFunction<URL, WrappedDataset[]>) url -> TerraConstants.JSON_MAPPER.readValue(url, WrappedDataset[].class))
                .flatMap(Arrays::stream)
                .flatMap(WrappedDataset::flatten)
                .toArray(WrappedDataset[]::new));
    }

    @Override
    public CompletableFuture<Double> getAsync(double lon, double lat) throws OutOfProjectionBoundsException {
        WrappedDataset[] datasets = this.bvh.getAllIntersecting(Bounds2d.of(lon, lon, lat, lat)).toArray(new WrappedDataset[0]);
        if (datasets.length == 0) { //no matching datasets!
            return CompletableFuture.completedFuture(Double.NaN);
        } else if (datasets.length == 1) { //only one dataset matches
            if (datasets[0].condition == null) { //if it doesn't have a condition, there's no reason to do any merging
                return datasets[0].dataset.getAsync(lon, lat);
            }
        }
        Arrays.sort(datasets); //ensure datasets are in priority order

        class State implements BiConsumer<Double, Throwable> {
            final CompletableFuture<Double> future = new CompletableFuture<>();
            int i = -1;

            @Override
            public void accept(Double v, Throwable cause) {
                if (cause != null) {
                    this.future.completeExceptionally(cause);
                } else if (!Double.isNaN(v) && datasets[this.i].test(v)) { //if the value in the input array is accepted, use it as the output
                    this.future.complete(v);
                } else { //sample the next dataset
                    this.advance();
                }
            }

            private void advance() {
                if (++this.i < datasets.length) {
                    try {
                        datasets[this.i].dataset.getAsync(lon, lat).whenComplete(this);
                    } catch (OutOfProjectionBoundsException e) {
                        this.future.completeExceptionally(e);
                    }
                } else { //no datasets remain, complete the future successfully with whatever value we currently have
                    this.future.complete(Double.NaN);
                }
            }
        }

        State state = new State();
        state.advance();
        return state.future;
    }

    @Override
    public CompletableFuture<double[]> getAsync(@NonNull CornerBoundingBox2d bounds, int sizeX, int sizeZ) throws OutOfProjectionBoundsException {
        try (PerformanceMetrics.Timer timer = PerformanceMetrics.startTimer("MultiScalarDataset.getAsync")) {
            if (notNegative(sizeX, "sizeX") == 0 | notNegative(sizeZ, "sizeZ") == 0) { //no input points -> no output points, ez
                return CompletableFuture.completedFuture(new double[0]);
            }
    
            WrappedDataset[] datasets = this.bvh.getAllIntersecting(bounds).toArray(new WrappedDataset[0]);
            if (datasets.length == 0) { //no matching datasets!
                return CompletableFuture.completedFuture(null);
            } else if (datasets.length == 1) { //only one dataset matches
                if (datasets[0].condition == null) { //if it doesn't have a condition, there's no reason to do any merging
                    return datasets[0].dataset.getAsync(bounds, sizeX, sizeZ);
                }
            }
            
            Arrays.sort(datasets); //ensure datasets are in priority order
            
            // Log the number of datasets being processed
            TerraMinusMinus.LOGGER.debug("Processing {} datasets in parallel for region at {}", 
                    datasets.length, bounds);
            
            // Create a result array filled with NaN values
            double[] result = new double[sizeX * sizeZ];
            Arrays.fill(result, Double.NaN);
            
            // Track how many points still need to be filled
            AtomicInteger remaining = new AtomicInteger(sizeX * sizeZ);
            
            // Create a CompletableFuture for the final result
            CompletableFuture<double[]> resultFuture = new CompletableFuture<>();
            
            // Create an array to hold all the dataset futures
            CompletableFuture<?>[] datasetFutures = new CompletableFuture[datasets.length];
            
            // Process datasets in parallel, but limit concurrency based on available memory
            // Higher priority datasets are processed first
            int batchSize = Math.min(datasets.length, MAX_THREADS);
            
            // Track which datasets have been processed
            AtomicInteger processedDatasets = new AtomicInteger(0);
            
            // Process the first batch of datasets
            for (int i = 0; i < batchSize; i++) {
                processNextDataset(i, datasets, bounds, sizeX, sizeZ, result, remaining, processedDatasets, datasetFutures, resultFuture);
            }
            
            // If all datasets have been processed or all points are filled, complete the future
            if (processedDatasets.get() >= datasets.length || remaining.get() == 0) {
                resultFuture.complete(result);
            }
            
            return resultFuture;
        }
    }
    
    /**
     * Processes the next dataset in the queue.
     * 
     * @param datasetIndex The index of the dataset to process
     * @param datasets The array of datasets
     * @param bounds The bounds to process
     * @param sizeX The X size
     * @param sizeZ The Z size
     * @param result The result array to update
     * @param remaining Counter for remaining points to fill
     * @param processedDatasets Counter for processed datasets
     * @param datasetFutures Array of futures for each dataset
     * @param resultFuture The future to complete when done
     */
    private void processNextDataset(int datasetIndex, WrappedDataset[] datasets, 
                                   CornerBoundingBox2d bounds, int sizeX, int sizeZ,
                                   double[] result, AtomicInteger remaining, 
                                   AtomicInteger processedDatasets, 
                                   CompletableFuture<?>[] datasetFutures,
                                   CompletableFuture<double[]> resultFuture) {
        datasetFutures[datasetIndex] = CompletableFuture.supplyAsync(() -> {
            try {
                return datasets[datasetIndex].dataset.getAsync(bounds, sizeX, sizeZ).join();
            } catch (OutOfProjectionBoundsException e) {
                return null;
            }
        }, DATASET_EXECUTOR).thenAccept(data -> {
            if (data != null) {
                WrappedDataset dataset = datasets[datasetIndex];
                int filled = processDatasetResult(result, data, dataset, remaining);
                
                if (filled > 0) {
                    TerraMinusMinus.LOGGER.debug("Dataset {} filled {} points", 
                            datasetIndex, filled);
                }
            }
            
            // Check if we need to process more datasets
            int nextIndex = processedDatasets.incrementAndGet();
            if (nextIndex < datasets.length && remaining.get() > 0) {
                // Process the next dataset
                processNextDataset(nextIndex, datasets, bounds, sizeX, sizeZ, result, 
                                  remaining, processedDatasets, datasetFutures, resultFuture);
            }
            
            // If all datasets are processed or all points are filled, complete the future
            if (processedDatasets.get() >= datasets.length || remaining.get() == 0) {
                resultFuture.complete(result);
            }
        }).exceptionally(ex -> {
            TerraMinusMinus.LOGGER.error("Error processing dataset {}: {}", 
                    datasetIndex, ex.getMessage());
            
            // Continue processing other datasets
            int nextIndex = processedDatasets.incrementAndGet();
            if (nextIndex < datasets.length && remaining.get() > 0) {
                processNextDataset(nextIndex, datasets, bounds, sizeX, sizeZ, result, 
                                  remaining, processedDatasets, datasetFutures, resultFuture);
            } else if (processedDatasets.get() >= datasets.length) {
                resultFuture.complete(result);
            }
            return null;
        });
    }
    
    /**
     * Processes the result from a dataset and updates the result array.
     * 
     * @param result The result array to update
     * @param data The data from the dataset
     * @param dataset The dataset that provided the data
     * @param remaining The counter for remaining points to fill
     * @return The number of points filled
     */
    private int processDatasetResult(double[] result, double[] data, WrappedDataset dataset, AtomicInteger remaining) {
        if (data == null) {
            return 0;
        }
        
        int filled = 0;
        synchronized (result) {
            for (int i = 0; i < result.length; i++) {
                if (Double.isNaN(result[i])) { // If value in output array is NaN, consider replacing it
                    double v = data[i];
                    if (!Double.isNaN(v) && dataset.test(v)) { // If the value in the input array is accepted, use it as the output
                        result[i] = v;
                        filled++;
                        remaining.decrementAndGet();
                    }
                }
            }
        }
        
        return filled;
    }

    /**
     * Wrapper around a dataset with a bounding box.
     *
     * @author DaPorkchop_
     */
    @JsonDeserialize
    @JsonSerialize
    @Getter
    private static class WrappedDataset implements Bounds2d, Comparable<WrappedDataset>, DoubleCondition {
        @Getter(onMethod_ = { @JsonGetter })
        protected final IScalarDataset dataset;
        @Getter(onMethod_ = { @JsonGetter })
        protected final DoubleCondition condition;
        @Getter(onMethod_ = { @JsonGetter })
        protected final IntRange zooms; //TODO: use this

        protected final Bounds2d[] bounds;

        protected final double minX;
        protected final double maxX;
        protected final double minZ;
        protected final double maxZ;

        @Getter(onMethod_ = { @JsonGetter })
        protected final double priority;

        @JsonCreator
        public WrappedDataset(
                @JsonProperty(value = "dataset", required = true) @NonNull IScalarDataset dataset,
                @JsonProperty(value = "bounds", required = true) @NonNull Bounds2d[] bounds,
                @JsonProperty(value = "zooms", required = true) @NonNull IntRange zooms,
                @JsonProperty(value = "priority", defaultValue = "0.0") double priority,
                @JsonProperty("condition") DoubleCondition condition) {
            this.dataset = dataset;
            this.condition = condition;
            this.zooms = zooms;
            this.priority = priority;

            checkArg(bounds.length > 0, "bounds may not be empty!");
            this.bounds = bounds.length > 1 ? bounds : null;
            this.minX = bounds[0].minX();
            this.maxX = bounds[0].maxX();
            this.minZ = bounds[0].minZ();
            this.maxZ = bounds[0].maxZ();
        }

        protected Stream<WrappedDataset> flatten() {
            return this.bounds == null
                    ? Stream.of(this)
                    : Stream.of(this.bounds).map(bounds -> new WrappedDataset(this.dataset, new Bounds2d[]{bounds}, this.zooms, this.priority, this.condition));
        }

        @Override
        public int compareTo(WrappedDataset o) {
            return -Double.compare(this.priority, o.priority);
        }

        @Override
        public boolean test(double value) {
            return this.condition == null || this.condition.test(value);
        }

        @JsonGetter("bounds")
        public Bounds2d bounds() {
            return Bounds2d.of(this.minX, this.maxX, this.minZ, this.maxZ);
        }
    }
}
