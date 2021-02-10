package net.buildtheearth.terraplusplus.dataset.vector.draw;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import net.buildtheearth.terraplusplus.dataset.osm.JsonParser;
import net.buildtheearth.terraplusplus.generator.CachedChunkData;
import lombok.NonNull;

import java.io.IOException;

/**
 * {@link DrawFunction} which updates the water depth based on the pixel weight.
 *
 * @author DaPorkchop_
 */
@JsonAdapter(Ocean.Parser.class)
final class Ocean implements DrawFunction {
    @Override
    public void drawOnto(@NonNull CachedChunkData.Builder data, int x, int z, int weight) {
        data.updateOceanDepth(x, z, weight);
    }

    static class Parser extends JsonParser<Ocean> {
        @Override
        public Ocean read(JsonReader in) throws IOException {
            in.beginObject();
            in.endObject();
            return new Ocean();
        }
    }
}
