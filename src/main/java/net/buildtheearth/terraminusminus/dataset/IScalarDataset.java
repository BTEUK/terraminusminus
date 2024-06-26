package net.buildtheearth.terraminusminus.dataset;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.NonNull;
import net.buildtheearth.terraminusminus.dataset.scalar.ConfigurableDoubleTiledDataset;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;

/**
 * A dataset consisting of floating-point scalar values.
 *
 * @author DaPorkchop_
 */
@JsonDeserialize(as = ConfigurableDoubleTiledDataset.class)
public interface IScalarDataset {
    /**
     * @param point the point
     * @see #getAsync(double, double)
     */
    default CompletableFuture<Double> getAsync(@NonNull double[] point) throws OutOfProjectionBoundsException {
        return this.getAsync(point[0], point[1]);
    }

    /**
     * Asynchronously gets a single value at the given point.
     *
     * @param lon the longitude
     * @param lat the latitude
     * @return a {@link CompletableFuture} which will be completed with the value
     */
    CompletableFuture<Double> getAsync(double lon, double lat) throws OutOfProjectionBoundsException;

    /**
     * Asynchronously gets a bunch of values at the given coordinates.
     *
     * @param sizeX the number of samples to take along the X axis
     * @param sizeZ the number of samples to take along the Z axis
     * @return a {@link CompletableFuture} which will be completed with the values
     */
    CompletableFuture<double[]> getAsync(@NonNull CornerBoundingBox2d bounds, int sizeX, int sizeZ) throws OutOfProjectionBoundsException;
}
