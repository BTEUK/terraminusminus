package net.buildtheearth.terraminusminus.generator;

import java.util.concurrent.CompletableFuture;

import com.google.common.cache.CacheLoader;

import lombok.NonNull;
import net.buildtheearth.terraminusminus.generator.data.IEarthDataBaker;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;

/**
 * {@link CacheLoader} implementation for earth generators, which asynchronously aggregates information from multiple datasets and stores it
 * in a {@link CachedChunkData} for use by the generator.
 * 
 * This class is responsible for loading terrain data asynchronously using a pipeline of data bakers. The loading process works as follows:
 * 
 * 1. When a chunk is requested, this loader creates a CompletableFuture that will eventually contain the chunk data
 * 2. The loader uses the EarthGeneratorPipelines to get a set of data bakers configured for the current settings
 * 3. Each data baker processes the chunk coordinates and contributes to building the final CachedChunkData
 * 4. The data bakers may access various datasets (elevation, biome, OSM features) to generate the terrain
 * 5. Once all data bakers have processed the chunk, the CompletableFuture is completed with the final CachedChunkData
 * 
 * This asynchronous approach allows the terrain generation to happen in the background without blocking the main thread.
 * The loader is typically used with a LoadingCache to provide efficient caching of generated chunks.
 *
 * @author DaPorkchop_
 * @see CachedChunkData
 * @see EarthGeneratorSettings
 * @see IEarthDataBaker
 */
public class ChunkDataLoader extends CacheLoader<ChunkPos, CompletableFuture<CachedChunkData>> {
	protected final GeneratorDatasets datasets;
	protected final IEarthDataBaker<?>[] bakers;

	/**
	 * Creates a new ChunkDataLoader with the specified generator settings.
	 * 
	 * @param settings The earth generator settings that configure how terrain is generated
	 */
	public ChunkDataLoader(@NonNull EarthGeneratorSettings settings) {
		this.datasets = settings.datasets();
		this.bakers = EarthGeneratorPipelines.dataBakers(settings);
	}

	/**
	 * Loads the chunk data for the specified chunk position asynchronously.
	 * 
	 * This method initiates the asynchronous pipeline process to generate terrain data for the chunk.
	 * It returns a CompletableFuture that will be completed when all data bakers have processed the chunk.
	 * 
	 * @param pos The chunk position to load data for
	 * @return A CompletableFuture that will contain the CachedChunkData when completed
	 */
	@Override
	public CompletableFuture<CachedChunkData> load(@NonNull ChunkPos pos) {
		return IEarthAsyncPipelineStep.getFuture(pos, this.datasets, this.bakers, CachedChunkData::builder);
	}

}