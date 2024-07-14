package net.buildtheearth.terraminusminus.dataset.geojson.dataset;

import static net.daporkchop.lib.common.util.PorkUtil.uncheckedCast;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.util.internal.ConcurrentSet;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.dataset.IDataset;
import net.buildtheearth.terraminusminus.dataset.IElementDataset;
import net.buildtheearth.terraminusminus.dataset.TiledDataset;
import net.buildtheearth.terraminusminus.dataset.geojson.GeoJsonObject;
import net.buildtheearth.terraminusminus.projection.EquirectangularProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;

/**
 * @author DaPorkchop_
 */
public class TiledGeoJsonDataset extends TiledDataset<GeoJsonObject[]> implements IElementDataset<GeoJsonObject[]> {
    protected final IDataset<String, GeoJsonObject[]> delegate;

    public TiledGeoJsonDataset(@NonNull IDataset<String, GeoJsonObject[]> delegate) {
        super(new EquirectangularProjection(), 1.0d / 64.0d);

        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<GeoJsonObject[]> load(@NonNull ChunkPos key) throws Exception {
        return this.delegate.getAsync(String.format("tile/%d/%d.json", key.x(), key.z()));
    }

    @Override
    public CompletableFuture<GeoJsonObject[][]> getAsync(@NonNull CornerBoundingBox2d bounds) throws OutOfProjectionBoundsException {
        Bounds2d localBounds = bounds.fromGeo(this.projection).axisAlign();
        CompletableFuture<GeoJsonObject[]>[] futures = uncheckedCast(Arrays.stream(localBounds.toTiles(this.tileSize))
                .map(this::getAsync)
                .toArray(CompletableFuture[]::new));

        return CompletableFuture.allOf(futures).thenApply(unused -> {
            ConcurrentLinkedQueue<GeoJsonObject[]> completedFuturesList = new ConcurrentLinkedQueue<>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Arrays.stream(futures).forEach(future -> executor.submit(() -> completedFuturesList.add(future.join())));
                executor.awaitTermination(1L, TimeUnit.MINUTES);
                return completedFuturesList.toArray(GeoJsonObject[][]::new);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
