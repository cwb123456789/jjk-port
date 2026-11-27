package com.jujutsuenhancement.bfn;

import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The scoreboard of the Bedrock command engine, saved with the world. */
public final class BfnData extends SavedData {
    public final Scores scores = new Scores();

    public static BfnData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BfnData::load, BfnData::new, "jje_bfn");
    }

    static BfnData load(CompoundTag tag) {
        BfnData d = new BfnData();
        CompoundTag objs = tag.getCompound("o");
        for (String name : objs.getAllKeys()) {
            d.scores.addObjective(name);
            CompoundTag m = objs.getCompound(name);
            for (String holder : m.getAllKeys()) d.scores.set(name, holder, m.getInt(holder));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag objs = new CompoundTag();
        for (String name : scores.objectiveNames()) {
            CompoundTag m = new CompoundTag();
            for (Map.Entry<String, Integer> en : scores.objective(name).entrySet()) m.putInt(en.getKey(), en.getValue());
            objs.put(name, m);
        }
        tag.put("o", objs);
        return tag;
    }
}
