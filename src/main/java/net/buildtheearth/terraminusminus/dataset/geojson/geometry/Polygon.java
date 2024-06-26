package net.buildtheearth.terraminusminus.dataset.geojson.geometry;

import static net.daporkchop.lib.common.util.PValidation.checkArg;

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
public final class Polygon implements Geometry {
    protected final LineString outerRing;
    protected final LineString[] innerRings;

    public Polygon(@NonNull LineString outerRing, @NonNull LineString[] innerRings) {
        checkArg(outerRing.isLinearRing(), "outerRing is not a linear ring!");
        for (int i = 0; i < innerRings.length; i++) {
            checkArg(innerRings[i].isLinearRing(), "innerRings[%d] is not a linear ring!", i);
        }
        this.outerRing = outerRing;
        this.innerRings = innerRings;
    }

    @Override
    public Polygon project(@NonNull ProjectionFunction projection) throws OutOfProjectionBoundsException {
        LineString outerRing = this.outerRing.project(projection);
        LineString[] innerRings = this.innerRings.clone();
        for (int i = 0; i < innerRings.length; i++) {
            innerRings[i] = innerRings[i].project(projection);
        }
        return new Polygon(outerRing, innerRings);
    }

    @Override
    public Bounds2d bounds() {
        return this.outerRing.bounds();
    }
}
