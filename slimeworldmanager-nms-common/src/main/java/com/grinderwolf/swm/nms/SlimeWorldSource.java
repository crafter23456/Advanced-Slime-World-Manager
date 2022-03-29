package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.*;
import com.grinderwolf.swm.api.loaders.*;
import com.grinderwolf.swm.api.world.*;
import com.grinderwolf.swm.api.world.properties.*;
import com.grinderwolf.swm.nms.world.*;
import it.unimi.dsi.fastutil.longs.*;

import java.util.*;

public interface SlimeWorldSource {

    default SlimeLoadedWorld createSlimeWorld(SlimeLoader loader, String worldName, Long2ObjectOpenHashMap<SlimeChunk> chunks, CompoundTag extraCompound, List<CompoundTag> mapList, byte worldVersion, SlimePropertyMap worldPropertyMap, boolean readOnly) {
        return createSlimeWorld(loader, worldName, chunks, extraCompound, mapList, worldVersion, worldPropertyMap, readOnly, !readOnly, Collections.emptyList());
    }

    SlimeLoadedWorld createSlimeWorld(SlimeLoader loader, String worldName, Long2ObjectOpenHashMap<SlimeChunk> chunks,
                                      CompoundTag extraCompound, List<CompoundTag> mapList, byte worldVersion,
                                      SlimePropertyMap worldPropertyMap, boolean readOnly, boolean lock,
                                      List<CompoundTag> entities);

}
