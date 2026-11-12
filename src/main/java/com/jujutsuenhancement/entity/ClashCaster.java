package com.jujutsuenhancement.entity;

/**
 * A creature that can open a domain and therefore get into a domain clash. The clash itself is run by
 * {@link DomainClashEntity}; the caster only reports its domain state and reacts to the outcome.
 */
public interface ClashCaster {
    ClashKind clashKind();

    /**
     * Ticks since this creature began casting its domain, or a large number (1000) while the domain it already cast
     * is still standing, or -1 when it has no domain at all. The addon starts a clash when somebody who started
     * casting 4..70 ticks ago sees another standing domain within 48 blocks.
     */
    int clashCastTicks();

    /** The clash has taken hold: stop the creature's own domain script, it keeps fighting as a normal creature. */
    void clashBegin();

    /** While true the creature must not start anything new (the clash freezes everybody during its set-up). */
    void clashHold(boolean hold);

    /** This creature's domain won. {@code clash} is still alive (the winner may keep the sky arena). */
    void clashWin(DomainClashEntity clash);

    /** This creature's domain was broken. */
    void clashLose();

    /** True while the winner wants everybody to stay in the sky arena (Unlimited Void keeps playing up there). */
    default boolean clashKeepArena() {
        return false;
    }
}
