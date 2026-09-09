package net.buildtheearth.terraminusminus;

import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.util.geo.CoordinateParseUtils;
import net.buildtheearth.terraminusminus.util.geo.LatLng;

import java.util.concurrent.CompletableFuture;

public class TerraminusminusServiceImpl implements TerraminusminusService {

    private final EarthGeneratorSettings settings;

    public TerraminusminusServiceImpl() {
        this.settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);
    }

    @Override
    public double[] toGeo(double x, double z) throws OutOfProjectionBoundsException {
        return settings.projection().toGeo(x, z);
    }

    @Override
    public double[] fromGeo(double lon, double lat) throws OutOfProjectionBoundsException {
        return settings.projection().fromGeo(lon, lat);
    }

    @Override
    public CompletableFuture<Double> getHeight(double x, double z) {
        try {
            double[] geo = toGeo(x, z);
            GeneratorDatasets datasets = new GeneratorDatasets(settings);
            return datasets.<IScalarDataset>getCustom(EarthGeneratorPipelines.KEY_DATASET_HEIGHTS)
                    .getAsync(geo[0], geo[1])
                    .thenApply(h -> h + 1.0d);
        } catch (OutOfProjectionBoundsException e) {
            return CompletableFuture.completedFuture(0.0);
        }
    }

    @Override
    public LatLng parseCoordinates(String coordinates) {
        return CoordinateParseUtils.parseVerbatimCoordinates(coordinates);
    }

    @Override
    public EarthGeneratorSettings getSettings() {
        return settings;
    }
}
