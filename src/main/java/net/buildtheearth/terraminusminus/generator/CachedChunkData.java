package net.buildtheearth.terraminusminus.generator;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.daporkchop.lib.common.math.PMath.clamp;
import static net.daporkchop.lib.common.math.PMath.floorI;
import static net.daporkchop.lib.common.math.PMath.lerp;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.substitutes.Biome;
import net.buildtheearth.terraminusminus.util.CustomAttributeContainer;
import net.buildtheearth.terraminusminus.util.ImmutableCompactArray;
import net.daporkchop.lib.common.reference.ReferenceStrength;
import net.daporkchop.lib.common.reference.cache.Cached;
import net.daporkchop.lib.common.util.PorkUtil;

/**
 * A collection of terrain data cached per-chunk by earth generators.
 * 
 * This class stores and provides access to various terrain properties for a 16x16 chunk:
 * - Surface height: The highest point (including water)
 * - Ground height: The highest solid block (excluding water)
 * - Water depth: Information about water bodies (oceans, rivers, lakes)
 * - Surface blocks: Special blocks that should appear on the surface (from OSM data)
 * - Biomes: The biome type for each column
 * 
 * The coordinate system used is:
 * - x, z: Local chunk coordinates (0-15)
 * - Heights: Absolute Y coordinates in the "cube" coordinate system
 * 
 * Use the utility methods in {@link ChunkPos} to convert between block and cube coordinates.
 * 
 * This class is immutable once constructed. Use the {@link Builder} to create instances.
 *
 * @author DaPorkchop_
 * @see ChunkDataLoader
 * @see EarthGeneratorSettings
 */
public class CachedChunkData extends CustomAttributeContainer {
    public static final int BLANK_HEIGHT = -1;

    public static final int WATER_DEPTH_OFFSET = 1;

    public static final int WATERDEPTH_DEFAULT = (byte) 0x80;

    public static final int WATERDEPTH_TYPE_MASK = (byte) 0xC0;
    public static final int WATERDEPTH_TYPE_DEFAULT = (byte) 0x80;
    public static final int WATERDEPTH_TYPE_WATER = (byte) 0x00;
    public static final int WATERDEPTH_TYPE_OCEAN = (byte) 0x40;

    private static final Cached<Builder> BUILDER_CACHE = Cached.threadLocal(Builder::new, ReferenceStrength.SOFT);

    public static Builder builder() {
        return BUILDER_CACHE.get().reset();
    }

    private static int extractActualDepth(int waterDepth) {
        //discard upper 2 bits from least significant byte and then sign-extend everything back down
        return ((waterDepth & 0x3F) - 32) << 26 >> 26;
    }

    private final int[] surfaceHeight;
    private final int[] groundHeight;

    @Getter
    private final byte[] biomes;

    private final ImmutableCompactArray<BlockState> surfaceBlocks;

    private final int surfaceMinCube;
    private final int surfaceMaxCube;

    private CachedChunkData(@NonNull Builder builder, @NonNull Map<String, Object> custom) {
        super(custom);

        this.surfaceHeight = builder.surfaceHeight.clone();
        this.groundHeight = builder.surfaceHeight.clone();

        for (int i = 0; i < 16 * 16; i++) {
            int waterDepth = builder.waterDepth[i];
            int d = extractActualDepth(waterDepth);

            switch (waterDepth & WATERDEPTH_TYPE_MASK) {
                case WATERDEPTH_TYPE_DEFAULT: //no water
                    //do nothing
                    break;
                case WATERDEPTH_TYPE_WATER: //water - lake/river/pond
                    if (d + WATER_DEPTH_OFFSET >= 0) {
                        this.groundHeight[i] -= d + WATER_DEPTH_OFFSET;
                        builder.biomes[(i >>> 4) | ((i & 0xF) << 4)] = Biome.RIVER;
                    }
                    break;
                case WATERDEPTH_TYPE_OCEAN:
                    if (d < 0) {
                        double t = (~d) / 8.0d;
                        this.surfaceHeight[i] = floorI(lerp(0.0d, this.surfaceHeight[i], t));
                        this.groundHeight[i] = floorI(lerp(-1.0d, this.groundHeight[i], t));
                    } else {
                        this.surfaceHeight[i] = 0;
                        this.groundHeight[i] = min(this.groundHeight[i], -2);
                        builder.biomes[(i >>> 4) | ((i & 0xF) << 4)] = Biome.DEEP_OCEAN;
                    }
                    break;
                default:
                    throw new IllegalStateException();
            }
        }

        this.biomes = new byte[16 * 16];
        for (int i = 0; i < 16 * 16; i++) {
            this.biomes[i] = (byte) PorkUtil.fallbackIfNull(builder.biomes[i], Biome.DEEP_OCEAN).numericId;
        }

        this.surfaceBlocks = new ImmutableCompactArray<>(builder.surfaceBlocks);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 16 * 16; i++) {
            min = min(min, min(this.groundHeight[i], this.surfaceHeight[i]));
            max = max(max, max(this.groundHeight[i], this.surfaceHeight[i]));
        }
        this.surfaceMinCube = ChunkPos.blockToCube(min) - 1;
        this.surfaceMaxCube = ChunkPos.blockToCube(max) + 1;
    }

    /**
     * Determines if the given Y-coordinate cube intersects with the terrain surface.
     * 
     * @param cubeY The Y-coordinate in cube coordinates to check
     * @return true if the cube intersects with the terrain surface, false otherwise
     */
    public boolean intersectsSurface(int cubeY) {
        return cubeY >= this.surfaceMinCube && cubeY <= this.surfaceMaxCube;
    }

    /**
     * Determines if the given Y-coordinate cube, with additional margins above and below,
     * intersects with the terrain surface.
     * 
     * @param cubeY The Y-coordinate in cube coordinates to check
     * @param includeBelow Number of cubes below to include in the check
     * @param includeAbove Number of cubes above to include in the check
     * @return true if the expanded range intersects with the terrain surface, false otherwise
     */
    public boolean intersectsSurface(int cubeY, int includeBelow, int includeAbove) {
        return cubeY + includeBelow >= this.surfaceMinCube && cubeY - includeAbove <= this.surfaceMaxCube;
    }

    /**
     * Determines if the given Y-coordinate cube is entirely above the terrain surface.
     * 
     * @param cubeY The Y-coordinate in cube coordinates to check
     * @return true if the cube is above the terrain surface, false otherwise
     */
    public boolean aboveSurface(int cubeY) {
        return cubeY > this.surfaceMaxCube;
    }

    /**
     * Determines if the given Y-coordinate cube is entirely below the terrain surface.
     * 
     * @param cubeY The Y-coordinate in cube coordinates to check
     * @return true if the cube is below the terrain surface, false otherwise
     */
    public boolean belowSurface(int cubeY) {
        return cubeY < this.surfaceMinCube;
    }

    /**
     * Gets the minimum Y value (in cube coordinates) where the surface exists in this chunk.
     * This is useful for optimizing terrain generation by avoiding scanning for the surface.
     * 
     * @return The minimum Y value in cube coordinates where the surface exists
     */
    public int getMinSurfaceY() {
        return this.surfaceMinCube;
    }

    /**
     * Gets the maximum Y value (in cube coordinates) where the surface exists in this chunk.
     * This is useful for optimizing terrain generation by avoiding scanning for the surface.
     * 
     * @return The maximum Y value in cube coordinates where the surface exists
     */
    public int getMaxSurfaceY() {
        return this.surfaceMaxCube;
    }

    /**
     * Gets the surface height at the specified coordinates.
     * The surface height includes water (it's the highest point including water).
     * 
     * @param x The x-coordinate within the chunk (0-15)
     * @param z The z-coordinate within the chunk (0-15)
     * @return The surface height in block coordinates
     */
    public int surfaceHeight(int x, int z) {
        return this.surfaceHeight[x * 16 + z];
    }

    /**
     * Gets the ground height at the specified coordinates.
     * The ground height is the highest solid block (excluding water).
     * 
     * @param x The x-coordinate within the chunk (0-15)
     * @param z The z-coordinate within the chunk (0-15)
     * @return The ground height in block coordinates
     */
    public int groundHeight(int x, int z) {
        return this.groundHeight[x * 16 + z];
    }

    /**
     * Gets the water height at the specified coordinates.
     * This is typically one block below the surface height.
     * 
     * @param x The x-coordinate within the chunk (0-15)
     * @param z The z-coordinate within the chunk (0-15)
     * @return The water height in block coordinates
     */
    public int waterHeight(int x, int z) {
        return this.surfaceHeight(x, z) - 1;
    }

    /**
     * Gets the special surface block at the specified coordinates, if any.
     * This is typically used for OSM-defined features like roads, buildings, etc.
     * 
     * @param x The x-coordinate within the chunk (0-15)
     * @param z The z-coordinate within the chunk (0-15)
     * @return The surface block state, or null if no special block is defined
     */
    public BlockState surfaceBlock(int x, int z) {
        return this.surfaceBlocks.get(x * 16 + z);
    }

    /**
     * Gets the biome ID at the specified coordinates.
     * 
     * @param x The x-coordinate within the chunk (0-15)
     * @param z The z-coordinate within the chunk (0-15)
     * @return The biome ID
     */
    public int biome(int x, int z) {
        return this.biomes[z * 16 + x];
    }

    /**
     * Builder class for {@link CachedChunkData}.
     *
     * @author DaPorkchop_
     */
    @Getter
    @Setter
    public static final class Builder extends CustomAttributeContainer implements IEarthAsyncDataBuilder<CachedChunkData> {
        private final int[] surfaceHeight = new int[16 * 16];
        private final byte[] waterDepth = new byte[16 * 16];

        private final Biome[] biomes = new Biome[16 * 16];

        protected final BlockState[] surfaceBlocks = new BlockState[16 * 16];

        /**
         * @deprecated use {@link #builder()} unless you have a specific reason to invoke this constructor directly
         */
        @Deprecated
        public Builder() {
            super(new Object2ObjectOpenHashMap<>());
            this.reset();
        }

        /**
         * Sets the surface height at the specified coordinates.
         * 
         * @param x The x-coordinate within the chunk (0-15)
         * @param z The z-coordinate within the chunk (0-15)
         * @param value The surface height in block coordinates
         * @return This builder instance for method chaining
         */
        public Builder surfaceHeight(int x, int z, int value) {
            this.surfaceHeight[x * 16 + z] = value;
            return this;
        }

        /**
         * Updates the water depth at the specified coordinates.
         * This is used for lakes, rivers, and other non-ocean water bodies.
         * The method will only update the depth if the new depth is greater than the existing depth.
         * 
         * @param x The x-coordinate within the chunk (0-15)
         * @param z The z-coordinate within the chunk (0-15)
         * @param depth The water depth value (will be clamped to valid range)
         * @return This builder instance for method chaining
         */
        public Builder updateWaterDepth(int x, int z, int depth) {
            depth = clamp(depth + 32, 0, 0x3F) | WATERDEPTH_TYPE_WATER;
            if (depth > this.waterDepth[x * 16 + z]) {
                this.waterDepth[x * 16 + z] = (byte) depth;
            }
            return this;
        }

        /**
         * Updates the ocean depth at the specified coordinates.
         * This is used for oceans and affects both the surface and ground height.
         * The method will only update the depth if the new depth is greater than the existing depth.
         * 
         * @param x The x-coordinate within the chunk (0-15)
         * @param z The z-coordinate within the chunk (0-15)
         * @param depth The ocean depth value (will be clamped to valid range)
         * @return This builder instance for method chaining
         */
        public Builder updateOceanDepth(int x, int z, int depth) {
            depth = clamp(depth + 32, 0, 0x3F) | WATERDEPTH_TYPE_OCEAN;
            if (depth > this.waterDepth[x * 16 + z]) {
                this.waterDepth[x * 16 + z] = (byte) depth;
            }
            return this;
        }

        /**
         * Gets the current surface height at the specified coordinates.
         * This is useful for incremental building where you need to reference existing values.
         * 
         * @param x The x-coordinate within the chunk (0-15)
         * @param z The z-coordinate within the chunk (0-15)
         * @return The current surface height in block coordinates
         */
        public int surfaceHeight(int x, int z) {
            return this.surfaceHeight[x * 16 + z];
        }

        /**
         * Adds a custom attribute to the chunk data.
         * Custom attributes can be used to store additional information that isn't part of the standard terrain data.
         * 
         * @param key The key for the custom attribute
         * @param value The value for the custom attribute
         */
        public void putCustom(@NonNull String key, @NonNull Object value) {
            this.custom.put(key, value);
        }

        /**
         * Resets the builder to its initial state.
         * This clears all surface heights, water depths, surface blocks, and custom attributes.
         * 
         * @return This builder instance for method chaining
         */
        public Builder reset() {
            Arrays.fill(this.surfaceHeight, BLANK_HEIGHT);
            Arrays.fill(this.waterDepth, (byte) WATERDEPTH_DEFAULT);
            Arrays.fill(this.surfaceBlocks, null);
            this.custom.clear();
            return this;
        }

        /**
         * Builds a new {@link CachedChunkData} instance with the current state of this builder.
         * This method creates an immutable copy of the custom attributes and then clears them from the builder.
         * 
         * @return A new {@link CachedChunkData} instance
         */
        @Override
        public CachedChunkData build() {
            Map<String, Object> custom = ImmutableMap.copyOf(this.custom);
            this.custom.clear();
            return new CachedChunkData(this, custom);
        }
    }
}
