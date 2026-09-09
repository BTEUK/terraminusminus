package net.buildtheearth.terraminusminus;

import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.geo.LatLng;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for Terraminusminus.
 */
public interface TerraminusminusService {
    /**
     * Gets the geographical location from in-game coordinates using default BTE settings.
     *
     * @param x X-Axis in-game
     * @param z Z-Axis in-game
     * @return The geographical location (Long, Lat)
     * @throws OutOfProjectionBoundsException If coordinates are out of bounds
     */
    double[] toGeo(double x, double z) throws OutOfProjectionBoundsException;

    /**
     * Gets in-game coordinates from geographical location using default BTE settings.
     *
     * @param lon Geographical Longitude
     * @param lat Geographic Latitude
     * @return The in-game coordinates (x, z)
     * @throws OutOfProjectionBoundsException If coordinates are out of bounds
     */
    double[] fromGeo(double lon, double lat) throws OutOfProjectionBoundsException;

    /**
     * Gets the altitude of a location using default BTE settings.
     *
     * @param x X-Axis in-game
     * @param z Z-Axis in-game
     * @return The altitude future
     */
    CompletableFuture<Double> getHeight(double x, double z);

    /**
     * Parses coordinates from a string.
     *
     * @param coordinates The coordinate string
     * @return The parsed LatLng, or null if parsing failed
     */
    LatLng parseCoordinates(String coordinates);

    /**
     * Gets the default BTE generator settings.
     *
     * @return The generator settings
     */
    EarthGeneratorSettings getSettings();
}
