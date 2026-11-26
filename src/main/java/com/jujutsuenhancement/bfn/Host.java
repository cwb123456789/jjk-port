package com.jujutsuenhancement.bfn;

import java.util.List;

/** The world the engine runs in (Forge: {@code McHost}; tests: a mock). */
public interface Host {
    /** all entities of a dimension */
    Iterable<? extends Ent> entities(int dim);

    List<? extends Ent> players(int dim);

    Scores scores();

    /** Bedrock id of the block (without {@code minecraft:}); "air" outside the world */
    String blockId(int dim, int x, int y, int z);

    /** the lines of a function ({@code fighting/katana/slash}), or null */
    List<String> functionLines(String path);

    /** every command the engine does not do itself (tp, damage, summon, particle ...) */
    void command(Engine engine, Ctx ctx, Line line);

    /** a command that failed to run is reported here (once per kind) */
    default void unknown(String what) {}
}
