package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.List;

/** Three coordinates: absolute, relative ({@code ~}) or local ({@code ^}); written as one word ({@code ^^1^2.5}) or as three. */
public final class Coords {
    public final boolean local;
    public final boolean[] rel = new boolean[3];
    public final double[] v = new double[3];

    private Coords(boolean local) {
        this.local = local;
    }

    /** Reads three coordinates from {@code w} starting at {@code idx}; returns the coords and writes the next index to {@code next[0]}. */
    static Coords read(List<String> w, int idx, int[] next) {
        List<String> parts = new ArrayList<>();
        int i = idx;
        while (parts.size() < 3 && i < w.size()) {
            String t = w.get(i++);
            if (t.length() > 0 && (t.charAt(0) == '~' || t.charAt(0) == '^')) {
                int st = 0;
                for (int k = 1; k <= t.length(); k++) {
                    if (k == t.length() || t.charAt(k) == '~' || t.charAt(k) == '^') {
                        parts.add(t.substring(st, k));
                        st = k;
                    }
                }
            } else {
                parts.add(t);
            }
        }
        if (parts.size() != 3) throw new IllegalArgumentException("coords: " + w.subList(idx, Math.min(w.size(), idx + 3)));
        boolean local = parts.get(0).startsWith("^");
        Coords c = new Coords(local);
        for (int k = 0; k < 3; k++) {
            String p = parts.get(k);
            if (local != p.startsWith("^") && p.startsWith("~") || local && !p.startsWith("^")) {
                // mixed local/relative is an error in the game
                throw new IllegalArgumentException("mixed coords: " + parts);
            }
            if (p.startsWith("~") || p.startsWith("^")) {
                c.rel[k] = true;
                String num = p.substring(1);
                c.v[k] = num.isEmpty() || num.equals("-") ? 0 : Double.parseDouble(num);
            } else {
                c.v[k] = Double.parseDouble(p);
            }
        }
        next[0] = i;
        return c;
    }

    public boolean isZeroOffset() {
        return (local || rel[0] && rel[1] && rel[2]) && v[0] == 0 && v[1] == 0 && v[2] == 0;
    }
}
