package net.buildtheearth.terraminusminus;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.ChunkDataLoader;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.PerformanceTracker;
import net.buildtheearth.terraminusminus.util.http.Http;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PerformanceTest {

    @Test
    public void runTest() throws OutOfProjectionBoundsException {

        Http.configChanged();

        long startTime = System.nanoTime();
        try {
            EarthGeneratorSettings settings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

            final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache = CacheBuilder.newBuilder()
                    .expireAfterAccess(5L, TimeUnit.MINUTES)
                    .softValues()
                    .build(new ChunkDataLoader(settings));

            double[] startPos = settings.projection().fromGeo(14.80963, 50.88887);
            int chunkX = (int) startPos[0] >> 4;
            int chunkZ = (int) startPos[1] >> 4;

            for (int i = chunkX; i <= chunkX + 100; i++) {
                for (int j = chunkZ; j <= chunkZ + 100; j++) {
                    ChunkPos pos = new ChunkPos(i, j);
                    cache.getUnchecked(pos).join();
                }
            }
        } finally {
            long duration = System.nanoTime() - startTime;
            PerformanceTracker.trackOperation("Full test", duration);
        }

        TerraMinusMinus.LOGGER.info("Completed test run");
        PerformanceTracker.printAllStats();
        PerformanceTracker.shutdown();
    }

}
