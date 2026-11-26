package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A parsed Bedrock target selector ({@code @e[type=!player,tag=a,scores={x=1..},r=10,c=1]}) or a plain name (fake player / player name). */
public final class Selector {
    public record Neg(boolean neg, String value) {}

    public record HasItem(String item, Integer data, String location, Integer slot, Range quantity) {}

    /** one of s, e, a, p, r, n (nearest-first like p), or 0 for a plain name */
    public final char kind;
    public final String name;
    public final List<Neg> types = new ArrayList<>();
    public final List<Neg> tags = new ArrayList<>();
    public final List<Neg> families = new ArrayList<>();
    public final List<Neg> names = new ArrayList<>();
    public final Map<String, List<Range>> scores = new LinkedHashMap<>();
    public final List<HasItem> hasItems = new ArrayList<>();
    public final Map<String, String> props = new LinkedHashMap<>();
    public Double r;
    public Double rm;
    public Double x, y, z, dx, dy, dz;
    /** x / y / z given relative to the origin ({@code x=~-0.3}) */
    public boolean xRel, yRel, zRel;
    public Integer c;
    public Neg gamemode;
    public Double l, lm, rx, rxm, ry, rym;
    /** true when the selector has a distance/volume/c condition and so depends on the origin or on the order */
    public boolean ordered;

    private Selector(char kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    public static Selector parse(String s) {
        if (s.length() < 2 || s.charAt(0) != '@') return new Selector((char) 0, s);
        char kind = s.charAt(1);
        Selector sel = new Selector(kind, null);
        int br = s.indexOf('[');
        if (br < 0) {
            if (s.length() != 2) return new Selector((char) 0, s);
            return sel;
        }
        String body = s.substring(br + 1, s.lastIndexOf(']'));
        for (String part : Tok.commas(body)) {
            int eq = part.indexOf('=');
            if (eq < 0) throw new IllegalArgumentException("selector arg: " + part);
            String k = part.substring(0, eq).trim();
            String v = part.substring(eq + 1).trim();
            switch (k) {
                case "type" -> sel.types.add(neg(v));
                case "tag" -> sel.tags.add(neg(v));
                case "family" -> sel.families.add(neg(v));
                case "name" -> sel.names.add(neg(unquote(v)));
                case "scores" -> {
                    for (String sc : Tok.commas(v.substring(1, v.length() - 1))) {
                        int e = sc.indexOf('=');
                        sel.scores.computeIfAbsent(sc.substring(0, e).trim(), z -> new ArrayList<>()).add(Range.parse(sc.substring(e + 1).trim()));
                    }
                }
                case "hasitem" -> sel.parseHasItem(v);
                case "has_property", "haspermission" -> sel.props.put(k + ":" + sel.props.size(), v);
                case "r" -> sel.r = Double.parseDouble(v);
                case "rm" -> sel.rm = Double.parseDouble(v);
                case "x" -> {
                    sel.xRel = v.startsWith("~");
                    sel.x = rel(v);
                }
                case "y" -> {
                    sel.yRel = v.startsWith("~");
                    sel.y = rel(v);
                }
                case "z" -> {
                    sel.zRel = v.startsWith("~");
                    sel.z = rel(v);
                }
                case "dx" -> sel.dx = Double.parseDouble(v);
                case "dy" -> sel.dy = Double.parseDouble(v);
                case "dz" -> sel.dz = Double.parseDouble(v);
                case "c" -> sel.c = Integer.parseInt(v);
                case "m" -> sel.gamemode = neg(v);
                case "l" -> sel.l = Double.parseDouble(v);
                case "lm" -> sel.lm = Double.parseDouble(v);
                case "rx" -> sel.rx = Double.parseDouble(v);
                case "rxm" -> sel.rxm = Double.parseDouble(v);
                case "ry" -> sel.ry = Double.parseDouble(v);
                case "rym" -> sel.rym = Double.parseDouble(v);
                default -> throw new IllegalArgumentException("selector key: " + k);
            }
        }
        sel.ordered = sel.c != null;
        return sel;
    }

    private static double rel(String v) {
        if (v.startsWith("~")) return v.length() == 1 ? 0 : Double.parseDouble(v.substring(1));
        return Double.parseDouble(v);
    }

    private static Neg neg(String v) {
        return v.startsWith("!") ? new Neg(true, v.substring(1)) : new Neg(false, v);
    }

    private static String unquote(String v) {
        boolean n = v.startsWith("!");
        String t = n ? v.substring(1) : v;
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        return n ? "!" + t : t;
    }

    private void parseHasItem(String v) {
        String body = v.startsWith("[") ? v.substring(1, v.length() - 1) : v;
        List<String> items = v.startsWith("[") ? splitObjects(body) : List.of(body.substring(body.startsWith("{") ? 1 : 0, body.endsWith("}") ? body.length() - 1 : body.length()));
        for (String one : items) {
            String item = null;
            String loc = null;
            Integer data = null;
            Integer slot = null;
            Range q = null;
            for (String kv : Tok.commas(one)) {
                int e = kv.indexOf('=');
                String k = kv.substring(0, e).trim();
                String val = kv.substring(e + 1).trim();
                switch (k) {
                    case "item" -> item = val;
                    case "location" -> loc = val;
                    case "data" -> data = Integer.parseInt(val);
                    case "slot" -> slot = Integer.parseInt(val);
                    case "quantity" -> q = Range.parse(val);
                    default -> { }
                }
            }
            hasItems.add(new HasItem(item, data, loc, slot, q));
        }
    }

    private static List<String> splitObjects(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int st = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                if (depth++ == 0) st = i + 1;
            } else if (c == '}') {
                if (--depth == 0) out.add(s.substring(st, i));
            }
        }
        return out;
    }
}
