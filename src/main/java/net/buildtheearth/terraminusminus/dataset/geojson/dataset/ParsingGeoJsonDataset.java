package net.buildtheearth.terraminusminus.dataset.geojson.dataset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.dataset.KeyedHttpDataset;
import net.buildtheearth.terraminusminus.dataset.geojson.GeoJson;
import net.buildtheearth.terraminusminus.dataset.geojson.GeoJsonObject;
import net.buildtheearth.terraminusminus.util.PerformanceTracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ParsingGeoJsonDataset extends KeyedHttpDataset<GeoJsonObject[]> {

    public ParsingGeoJsonDataset(@NonNull String[] urls) {
        super(urls);
    }

    /**
     * Converts a {@link ByteBuf} to an array of {@link GeoJsonObject}
     *
     * @param path path
     * @param data the byte buffer
     * @return the array of objects
     * @throws IOException exception
     */
    @Override
    protected GeoJsonObject[] decode(@NonNull String path, @NonNull ByteBuf data) throws IOException {
        long startTime = System.nanoTime();
        try {
            List<GeoJsonObject> objects = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new ByteBufInputStream(data)),
                    32768)) { // Optimized buffer size for I/O

                String line;
                while ((line = reader.readLine()) != null) {
                    objects.add(GeoJson.parse(line));
                }
            }
            return objects.toArray(new GeoJsonObject[0]);
        } finally {
            long duration = System.nanoTime() - startTime;
            PerformanceTracker.trackOperation("GeoJson Decode", duration);
        }
    }
}