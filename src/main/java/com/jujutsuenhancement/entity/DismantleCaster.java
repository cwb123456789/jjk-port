package com.jujutsuenhancement.entity;

import net.minecraft.world.entity.Entity;

/** An owner of Dismantle slashes that decides whom they hurt (so allies and creative players are spared). */
public interface DismantleCaster {
    boolean isDismantleTarget(Entity target);
}
