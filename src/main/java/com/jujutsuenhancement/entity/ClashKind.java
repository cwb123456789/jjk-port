package com.jujutsuenhancement.entity;

/**
 * The domains that can take part in a domain clash. {@code resistance} is the value the Bedrock addon gives the clasher
 * when the fight starts ({@code domain_clash_main.mcfunction}, ticks 148..151); {@code ahead} / {@code up} is where the
 * clasher is put relative to its domain entity ({@code tp @e[tag=domainclashing] ^^up^ahead}, same file, ticks 0..15).
 */
public enum ClashKind {
    UNLIMITED_VOID("unlimited_void", 700, 14.0D, 0.0D),
    MALEVOLENT_SHRINE("malevolent_shrine", 250, 3.0D, 7.5D),
    PERFECTION("perfection", 600, 16.0D, 0.0D),
    DEADLY_SENTENCING("deadly_sentencing", 600, 2.0D, 0.0D);

    public final String id;
    public final int resistance;
    public final double ahead;
    public final double up;

    ClashKind(String id, int resistance, double ahead, double up) {
        this.id = id;
        this.resistance = resistance;
        this.ahead = ahead;
        this.up = up;
    }

    public static ClashKind byOrdinal(int i) {
        ClashKind[] all = values();
        return i >= 0 && i < all.length ? all[i] : null;
    }
}
