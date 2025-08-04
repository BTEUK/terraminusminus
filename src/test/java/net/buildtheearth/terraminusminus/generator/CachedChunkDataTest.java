package net.buildtheearth.terraminusminus.generator;

import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link CachedChunkData} class.
 */
public class CachedChunkDataTest {

    /**
     * Tests that the getMinSurfaceY and getMaxSurfaceY methods return the correct values.
     */
    @Test
    public void testGetSurfaceYBounds() {
        // Create a builder and set some surface heights
        CachedChunkData.Builder builder = CachedChunkData.builder();
        
        // Set surface heights for a simple terrain with varying heights
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Create a simple height pattern: higher in the center, lower at the edges
                int height = 100 - (Math.abs(x - 8) + Math.abs(z - 8)) * 5;
                builder.surfaceHeight(x, z, height);
            }
        }
        
        // Build the CachedChunkData
        CachedChunkData chunkData = builder.build();
        
        // The minimum height in our pattern is 20 (at the corners)
        // The maximum height in our pattern is 100 (at the center)
        // These should be converted to cube coordinates
        int expectedMinCube = ChunkPos.blockToCube(20) - 1;
        int expectedMaxCube = ChunkPos.blockToCube(100) + 1;
        
        // Verify that getMinSurfaceY and getMaxSurfaceY return the expected values
        assertEquals("getMinSurfaceY should return the minimum surface Y in cube coordinates",
                expectedMinCube, chunkData.getMinSurfaceY());
        assertEquals("getMaxSurfaceY should return the maximum surface Y in cube coordinates",
                expectedMaxCube, chunkData.getMaxSurfaceY());
        
        // Also verify that the intersectsSurface, aboveSurface, and belowSurface methods
        // are consistent with the min/max values
        assertTrue("intersectsSurface should return true for the minimum surface Y",
                chunkData.intersectsSurface(expectedMinCube));
        assertTrue("intersectsSurface should return true for the maximum surface Y",
                chunkData.intersectsSurface(expectedMaxCube));
        assertTrue("belowSurface should return true for Y below the minimum surface Y",
                chunkData.belowSurface(expectedMinCube - 1));
        assertTrue("aboveSurface should return true for Y above the maximum surface Y",
                chunkData.aboveSurface(expectedMaxCube + 1));
    }
}