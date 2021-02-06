package io.github.terra121.dataset.vector.geojson.object;

import io.github.terra121.dataset.vector.geojson.GeoJSONObject;
import io.github.terra121.dataset.vector.geojson.Geometry;
import lombok.Data;
import lombok.NonNull;

import java.util.Map;

/**
 * @author DaPorkchop_
 */
@Data
public final class Feature implements GeoJSONObject {
    @NonNull
    protected final Geometry geometry;
    protected final Map<String, String> properties;
    protected final String id;
}
