package com.jujutsuenhancement.entity;

import net.minecraft.world.entity.LivingEntity;

/** Implemented by the awakened mobs so the /jje test command can trigger their skills on demand. */
public interface AwakenedCaster {
    /** Names accepted by {@link #cast}. */
    String[] skillNames();

    /** Starts the named skill against the target; false if unknown or the mob is busy. */
    boolean cast(String name, LivingEntity target);
}
