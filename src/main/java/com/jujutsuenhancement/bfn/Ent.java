package com.jujutsuenhancement.bfn;

import java.util.List;
import java.util.Set;

/** What the engine needs to know about an entity (implemented over Forge entities, and by a mock in the tests). */
public interface Ent {
    /** the score holder key: the player name, or the UUID of other entities */
    String key();

    /** Bedrock identifier: {@code player}, {@code zombie}, {@code ds:projectile} */
    String typeId();

    /** the name tag ("" when none); a player's name for players */
    String nameTag();

    boolean isPlayer();

    boolean isAlive();

    Set<String> tags();

    double x();

    double y();

    double z();

    float yaw();

    float pitch();

    double eyeHeight();

    int dim();

    List<String> families();

    /** minX, minY, minZ, maxX, maxY, maxZ */
    double[] box();

    /** 0 survival, 1 creative, 2 adventure, 3 spectator (6 for non-players) */
    int gamemode();

    int level();

    /** the property of the Bedrock entity ({@code property:sixeyes}); 0 when unknown */
    double property(String name);

    boolean hasItem(Selector.HasItem q);

    /** the underlying game object */
    Object handle();
}
