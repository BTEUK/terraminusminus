package io.github.terra121.dataset.vector.geojson.geometry;

import com.google.common.collect.Iterators;
import io.github.terra121.dataset.vector.geojson.Geometry;
import io.github.terra121.projection.OutOfProjectionBoundsException;
import io.github.terra121.projection.ProjectionFunction;
import io.github.terra121.util.bvh.Bounds2d;
import lombok.Data;
import lombok.NonNull;

import java.util.Iterator;

import static java.lang.Math.*;

/**
 * @author DaPorkchop_
 */
@Data
public final class MultiPoint implements Geometry, Iterable<Point> {
    @NonNull
    protected final Point[] points;

    @Override
    public Iterator<Point> iterator() {
        return Iterators.forArray(this.points);
    }

    @Override
    public MultiPoint project(@NonNull ProjectionFunction projection) throws OutOfProjectionBoundsException {
        Point[] out = this.points.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] = out[i].project(projection);
        }
        return new MultiPoint(out);
    }

    @Override
    public Bounds2d bounds() {
        if (this.points.length == 0) {
            return null;
        }

        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        for (Point point : this.points) {
            minLon = min(minLon, point.lon);
            maxLon = max(maxLon, point.lon);
            minLat = min(minLat, point.lat);
            maxLat = max(maxLat, point.lat);
        }
        return Bounds2d.of(minLon, maxLon, minLat, maxLat);
    }
}
