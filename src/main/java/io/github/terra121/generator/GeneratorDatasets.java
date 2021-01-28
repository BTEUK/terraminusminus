package io.github.terra121.generator;

import io.github.terra121.dataset.MultiresDataset;
import io.github.terra121.dataset.osm.OpenStreetMap;
import io.github.terra121.dataset.ScalarDataset;
import io.github.terra121.event.InitDatasetsEvent;
import io.github.terra121.event.InitEarthRegistryEvent;
import io.github.terra121.generator.process.HeightsBaker;
import io.github.terra121.generator.process.IChunkDataBaker;
import io.github.terra121.generator.process.InitialBiomesBaker;
import io.github.terra121.generator.process.OSMBaker;
import io.github.terra121.generator.process.TreeCoverBaker;
import io.github.terra121.projection.GeographicProjection;
import io.github.terra121.util.CustomAttributeContainer;
import io.github.terra121.util.OrderedRegistry;
import lombok.Getter;
import lombok.NonNull;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;

/**
 * Wrapper class which contains all of the datasets used by {@link EarthGenerator}.
 *
 * @author DaPorkchop_
 */
@Getter
public class GeneratorDatasets extends CustomAttributeContainer<Object> {
    protected static Map<String, Object> getCustomDatasets(@NonNull EarthGeneratorSettings settings) {
        InitDatasetsEvent event = new InitDatasetsEvent(settings);
        MinecraftForge.TERRAIN_GEN_BUS.post(event);
        return event.getAllCustomProperties();
    }

    protected final GeographicProjection projection;

    protected final ScalarDataset heights;
    protected final ScalarDataset trees;
    protected final OpenStreetMap osm;

    protected final IChunkDataBaker[] bakers;

    public GeneratorDatasets(@NonNull EarthGeneratorSettings settings) {
        super(getCustomDatasets(settings));

        this.projection = settings.projection();

        this.heights = new MultiresDataset("heights", settings.useDefaultHeights());
        this.trees = new MultiresDataset("trees", settings.useDefaultTrees());
        this.osm = new OpenStreetMap(settings);

        OrderedRegistry<IChunkDataBaker<?>> bakerRegistry = new OrderedRegistry<IChunkDataBaker<?>>()
                .addLast("initial_biomes", new InitialBiomesBaker(settings.biomeProvider()))
                .addLast("tree_cover", new TreeCoverBaker())
                .addLast("heights", new HeightsBaker())
                .addLast("osm", new OSMBaker());

        InitEarthRegistryEvent<IChunkDataBaker<?>> event = new InitEarthRegistryEvent<IChunkDataBaker<?>>(settings, bakerRegistry) {};
        MinecraftForge.TERRAIN_GEN_BUS.post(event);
        this.bakers = event.registry().entryStream().map(Map.Entry::getValue).toArray(IChunkDataBaker[]::new);
    }
}
