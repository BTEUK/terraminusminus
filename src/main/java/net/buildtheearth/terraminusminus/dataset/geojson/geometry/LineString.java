package net.buildtheearth.terraminusminus.dataset.geojson.geometry;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.daporkchop.lib.common.util.PValidation.checkArg;

import java.util.Objects;

import lombok.Data;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.dataset.geojson.Geometry;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.projection.ProjectionFunction;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;

/**
 * @author DaPorkchop_
 */
@Data
public final class LineString implements Geometry {
    protected final Point[] points;

    public LineString(@NonNull Point[] points) {
        checkArg(points.length >= 2, "LineString must contain at least 2 points!");
        this.points = points;
    }

    public boolean isLinearRing() {
        return this.points.length >= 4 && Objects.equals(this.points[0], this.points[this.points.length - 1]);
    }

    @Override
    public LineString project(@NonNull ProjectionFunction projection) throws OutOfProjectionBoundsException {
        Point[] out = this.points.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] = out[i].project(projection);
        }
        return new LineString(out);
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
