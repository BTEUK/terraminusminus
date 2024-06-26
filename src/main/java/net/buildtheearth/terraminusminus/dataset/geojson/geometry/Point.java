package net.buildtheearth.terraminusminus.dataset.geojson.geometry;

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
public final class Point implements Geometry {
    protected final double lon;
    protected final double lat;

    @Override
    public Point project(@NonNull ProjectionFunction projection) throws OutOfProjectionBoundsException {
        double[] proj = projection.project(this.lon, this.lat);
        return new Point(proj[0], proj[1]);
    }

    @Override
    public Bounds2d bounds() {
        return Bounds2d.of(this.lon, this.lon, this.lat, this.lat);
    }
}
