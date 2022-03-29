package com.grinderwolf.swm.nms.v1171;

import com.flowpowered.nbt.*;
import com.grinderwolf.swm.api.loaders.*;
import com.grinderwolf.swm.api.world.*;
import com.grinderwolf.swm.api.world.properties.*;
import com.grinderwolf.swm.nms.*;
import com.grinderwolf.swm.nms.world.*;
import it.unimi.dsi.fastutil.longs.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_17_R1.scheduler.*;
import org.bukkit.scheduler.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class v1171SlimeWorld extends AbstractSlimeNMSWorld {

    private static final MinecraftInternalPlugin INTERNAL_PLUGIN = new MinecraftInternalPlugin();

    private CustomWorldServer handle;

    public v1171SlimeWorld(byte version, SlimeLoader loader, String name, Long2ObjectOpenHashMap<SlimeChunk> chunks, CompoundTag extraData, SlimePropertyMap propertyMap, boolean readOnly, boolean lock, SlimeNMS nms) {
        super(version, loader, name, chunks, extraData, propertyMap, readOnly, lock, nms);
    }


    public void setHandle(CustomWorldServer handle) {
        this.handle = handle;
    }

    @Override
    public CompletableFuture<ChunkSerialization> serializeChunks(List<SlimeChunk> chunks, byte worldVersion) throws IOException {
        ByteArrayOutputStream outByteStream = new ByteArrayOutputStream(16384);
        DataOutputStream outStream = new DataOutputStream(outByteStream);

        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
        List<CompoundTag> entities = new ArrayList<>();
        List<CompoundTag> tileEntities = new ArrayList<>();

        for (SlimeChunk chunk : chunks) {
            CompletableFuture<Void> serializeFuture = new CompletableFuture<>();

            serializeFuture.thenRun(() -> {
                entities.addAll(chunk.getEntities());
                tileEntities.addAll(chunk.getTileEntities());

                try {
                    // Height Maps
                    byte[] heightMaps = serializeCompoundTag(chunk.getHeightMaps());
                    outStream.writeInt(heightMaps.length);
                    outStream.write(heightMaps);

                    // Biomes
                    int[] biomes = chunk.getBiomes();
                    outStream.writeInt(biomes.length);

                    for (int biome : biomes) {
                        outStream.writeInt(biome);
                    }

                    // Chunk sections
                    SlimeChunkSection[] sections = chunk.getSections();
                    BitSet sectionBitmask = new BitSet(16);

                    for (int i = 0; i < sections.length; i++) {
                        sectionBitmask.set(i, sections[i] != null);
                    }

                    writeBitSetAsBytes(outStream, sectionBitmask, 2);

                    for (SlimeChunkSection section : sections) {
                        if (section == null) {
                            continue;
                        }

                        // Block Light
                        boolean hasBlockLight = section.getBlockLight() != null;
                        outStream.writeBoolean(hasBlockLight);

                        if (hasBlockLight) {
                            outStream.write(section.getBlockLight().getBacking());
                        }

                        // Palette
                        List<CompoundTag> palette = section.getPalette().getValue();
                        outStream.writeInt(palette.size());

                        for (CompoundTag value : palette) {
                            byte[] serializedValue = serializeCompoundTag(value);

                            outStream.writeInt(serializedValue.length);
                            outStream.write(serializedValue);
                        }

                        // Block states
                        long[] blockStates = section.getBlockStates();

                        outStream.writeInt(blockStates.length);

                        for (long value : section.getBlockStates()) {
                            outStream.writeLong(value);
                        }

                        // Sky Light
                        boolean hasSkyLight = section.getSkyLight() != null;
                        outStream.writeBoolean(hasSkyLight);

                        if (hasSkyLight) {
                            outStream.write(section.getSkyLight().getBacking());
                        }
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            futures.add(serializeFuture);
        }


        // Force save the world if the server is currently stopping
        if (Bukkit.isStopping()) {
            if (!Bukkit.isPrimaryThread()) {
                throw new UnsupportedOperationException("Cannot save the world while the server is stopping async!");
            }

            for (CompletableFuture<Void> completableFuture : futures) {
                completableFuture.complete(null);
            }
            return CompletableFuture.completedFuture(new ChunkSerialization(outByteStream.toByteArray(), tileEntities, entities));
        } else {
            CompletableFuture<ChunkSerialization> future = new CompletableFuture<>();

            Iterator<CompletableFuture<Void>> futuresIterator = futures.iterator();

            /*
            Create a task that saves chunks for at the most 200 ms per tick.
             */
            BukkitRunnable runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    long timeSaved = 0;
                    long capturedTime = System.currentTimeMillis();

                    // 200 max ms on one tick for saving OR if the server is stopping force it to finish OR if it's on main thread to avoid deadlock
                    while (futuresIterator.hasNext() && (timeSaved < 200 || Bukkit.isStopping() || Bukkit.isPrimaryThread())) {
                        futuresIterator.next().complete(null);
                        timeSaved += System.currentTimeMillis() - capturedTime;
                    }

                    // Once it is empty, complete the future and stop it from executing further.
                    if (!futuresIterator.hasNext()) {
                        future.complete(new ChunkSerialization(outByteStream.toByteArray(), tileEntities, entities));
                        cancel();
                    }
                }
            };

            // If running on main thread, save it all to avoid a possible deadlock
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
            }

            // If there is still more to complete, start the task to begin saving on next ticks
            if (!future.isDone()) {
                runnable.runTaskTimer(INTERNAL_PLUGIN, 0, 1);
            }

            return future;
        }
    }

    @Override
    public SlimeLoadedWorld createSlimeWorld(SlimeLoader loader, String worldName, Long2ObjectOpenHashMap<SlimeChunk> chunks, CompoundTag extraCompound, List<CompoundTag> mapList, byte worldVersion, SlimePropertyMap worldPropertyMap, boolean readOnly, boolean lock) {
        return new v1171SlimeWorld(version, loader, name, new Long2ObjectOpenHashMap<>(chunks), extraData.clone(), propertyMap, readOnly, lock, nms);
    }
}
