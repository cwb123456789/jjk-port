package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.List;

/** One parsed command line: a chain of {@code execute} sub-commands (possibly empty) and the final command. */
public final class Line {
    public enum T { AS, AT, POS, POS_AS, ANCHOR, IF_ENTITY, IF_SCORE, IF_BLOCK, IF_BLOCKS, FACING, FACING_ENTITY, ROTATED, ROTATED_AS, ALIGN, IN }

    public static final class Sub {
        public T type;
        public boolean unless;
        public String sel;          // selector text (as / at / entity / facing entity / score target)
        public Coords coords;       // positioned / facing / if block
        public Coords coords2, coords3;
        public String text;         // anchor eyes|feet, align axes, block id, dimension, score objective
        public String text2;        // block states / blocks mode / score operator
        public String sel2;         // score source target
        public String obj2;         // score source objective
        public Range range;         // score matches
        public double yaw, pitch;   // rotated numbers (relative flags below)
        public boolean yawRel, pitchRel;
        public String anchor;       // facing entity anchor
    }

    public final String raw;
    public final List<Sub> subs = new ArrayList<>();
    public String verb = "";
    public List<String> args = List.of();

    private Line(String raw) {
        this.raw = raw;
    }

    public static Line parse(String raw) {
        Line ln = new Line(raw);
        // the pack has a few `positioned ^^^7run ...` (no space before run): Bedrock reads the number and goes on
        List<String> w = Tok.words(raw.strip().replaceAll("([\\d.])run ", "$1 run ").replaceAll("\\](at|as|run|if|unless|positioned|rotated|facing|anchored) ", "] $1 "));
        w = Tok.splitCoords(w);
        int i = 0;
        while (i < w.size() && w.get(i).equals("execute")) {
            i++;
            i = ln.sub(w, i);
        }
        if (i < w.size()) {
            ln.verb = w.get(i);
            ln.args = new ArrayList<>(w.subList(i + 1, w.size()));
        }
        return ln;
    }

    /** parses sub-commands until "run" (consumed) or the end */
    private int sub(List<String> w, int i) {
        int[] nx = new int[1];
        while (i < w.size()) {
            String k = w.get(i++);
            Sub s = new Sub();
            switch (k) {
                case "run" -> {
                    return i;
                }
                case "as" -> {
                    s.type = T.AS;
                    s.sel = w.get(i++);
                }
                case "at" -> {
                    s.type = T.AT;
                    s.sel = w.get(i++);
                }
                case "positioned" -> {
                    if (w.get(i).equals("as")) {
                        s.type = T.POS_AS;
                        s.sel = w.get(i + 1);
                        i += 2;
                    } else {
                        s.type = T.POS;
                        s.coords = Coords.read(w, i, nx);
                        i = nx[0];
                    }
                }
                case "anchored" -> {
                    s.type = T.ANCHOR;
                    s.text = w.get(i++);
                }
                case "if", "unless" -> {
                    s.unless = k.equals("unless");
                    String what = w.get(i++);
                    switch (what) {
                        case "entity" -> {
                            s.type = T.IF_ENTITY;
                            s.sel = w.get(i++);
                        }
                        case "score" -> {
                            s.type = T.IF_SCORE;
                            s.sel = w.get(i++);
                            s.text = w.get(i++);
                            String op = w.get(i++);
                            if (op.equals("matches")) {
                                s.text2 = "matches";
                                s.range = Range.parse(w.get(i++));
                            } else {
                                s.text2 = op;
                                s.sel2 = w.get(i++);
                                s.obj2 = w.get(i++);
                            }
                        }
                        case "block" -> {
                            s.type = T.IF_BLOCK;
                            s.coords = Coords.read(w, i, nx);
                            i = nx[0];
                            s.text = w.get(i++);
                            if (i < w.size() && !isSubKeyword(w.get(i))) s.text2 = w.get(i++);
                        }
                        case "blocks" -> {
                            s.type = T.IF_BLOCKS;
                            s.coords = Coords.read(w, i, nx);
                            s.coords2 = Coords.read(w, nx[0], nx);
                            s.coords3 = Coords.read(w, nx[0], nx);
                            i = nx[0];
                            s.text = w.get(i++);
                        }
                        default -> throw new IllegalArgumentException("execute " + k + " " + what);
                    }
                }
                case "facing" -> {
                    if (w.get(i).equals("entity")) {
                        s.type = T.FACING_ENTITY;
                        s.sel = w.get(i + 1);
                        s.anchor = w.get(i + 2);
                        i += 3;
                    } else {
                        s.type = T.FACING;
                        s.coords = Coords.read(w, i, nx);
                        i = nx[0];
                    }
                }
                case "rotated" -> {
                    if (w.get(i).equals("as")) {
                        s.type = T.ROTATED_AS;
                        s.sel = w.get(i + 1);
                        i += 2;
                    } else {
                        s.type = T.ROTATED;
                        String a = w.get(i++);
                        String b = w.get(i++);
                        s.yawRel = a.startsWith("~");
                        s.pitchRel = b.startsWith("~");
                        String an = s.yawRel ? a.substring(1) : a;
                        String bn = s.pitchRel ? b.substring(1) : b;
                        s.yaw = an.isEmpty() ? 0 : Double.parseDouble(an);
                        s.pitch = bn.isEmpty() ? 0 : Double.parseDouble(bn);
                    }
                }
                case "align" -> {
                    s.type = T.ALIGN;
                    s.text = w.get(i++);
                }
                case "in" -> {
                    s.type = T.IN;
                    s.text = w.get(i++);
                }
                default -> throw new IllegalArgumentException("execute sub " + k + " in " + raw);
            }
            subs.add(s);
        }
        return i;
    }

    private static boolean isSubKeyword(String t) {
        return switch (t) {
            case "as", "at", "positioned", "anchored", "if", "unless", "facing", "rotated", "align", "in", "run" -> true;
            default -> false;
        };
    }
}
