package net.buildtheearth.terraminusminus.dataset;

import java.util.concurrent.CompletableFuture;

import lombok.NonNull;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;

/**
 * A dataset consisting of arbitrary elements.
 *
 * @author DaPorkchop_
 */
public interface IElementDataset<V> {
    /**
     * Gets all of the elements that intersect the given bounding box.
     *
     * @param bounds the bounding box
     * @return a {@link CompletableFuture} which will be completed with the elements
     */
    CompletableFuture<V[]> getAsync(@NonNull CornerBoundingBox2d bounds) throws OutOfProjectionBoundsException;
}
