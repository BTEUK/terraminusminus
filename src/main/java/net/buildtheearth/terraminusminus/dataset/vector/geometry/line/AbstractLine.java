package net.buildtheearth.terraminusminus.dataset.vector.geometry.line;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.LineString;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.MultiLineString;
import net.buildtheearth.terraminusminus.dataset.vector.draw.DrawFunction;
import net.buildtheearth.terraminusminus.dataset.vector.geometry.AbstractVectorGeometry;
import net.buildtheearth.terraminusminus.dataset.vector.geometry.Segment;
import net.buildtheearth.terraminusminus.util.bvh.BVH;

/**
 * @author DaPorkchop_
 */
@Getter
public abstract class AbstractLine extends AbstractVectorGeometry {
    protected final BVH<Segment> segments;

    public AbstractLine(@NonNull String id, double layer, @NonNull DrawFunction draw, @NonNull MultiLineString lines) {
        super(id, layer, draw);

        List<Segment> segments = new ArrayList<>();
        for (LineString line : lines.lines()) { //convert MultiLineString to line segments
            convertToSegments(line, segments);
        }
        this.segments = BVH.of(segments.toArray(new Segment[0]));
    }

    @Override
    public double minX() {
        return this.segments.minX();
    }

    @Override
    public double maxX() {
        return this.segments.maxX();
    }

    @Override
    public double minZ() {
        return this.segments.minZ();
    }

    @Override
    public double maxZ() {
        return this.segments.maxZ();
    }
}
