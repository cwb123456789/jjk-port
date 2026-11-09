package com.jujutsuenhancement.client.bedrock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Just enough of Bedrock's Molang to evaluate the particle files of the addon:
 * arithmetic, comparisons, {@code &&} {@code ||} {@code !}, {@code ?:}, the {@code math.*} functions that
 * the addon uses and the {@code variable.particle_*} / {@code variable.emitter_*} inputs.
 * No Minecraft classes are used here so the same code can be run and tested outside the game.
 */
public final class Molang {
    private Molang() {}

    /** Values a particle expression can read. */
    public static final class Ctx {
        public double age;
        public double lifetime = 1.0;
        public double[] random = new double[4];
        public double emitterAge;
        public double emitterLifetime = 1.0;
        public double[] emitterRandom = new double[4];
    }

    public interface Expr {
        double eval(Ctx c);
    }

    private static final Random RNG = new Random();
    private static final Map<String, Expr> CACHE = new HashMap<>();

    public static double random() {
        return RNG.nextDouble();
    }

    public static Expr constant(double v) {
        return c -> v;
    }

    public static synchronized Expr compile(String source) {
        Expr cached = CACHE.get(source);
        if (cached != null) return cached;
        Expr e;
        try {
            e = new Parser(source).parseAll();
        } catch (RuntimeException ex) {
            e = constant(0.0);
        }
        CACHE.put(source, e);
        return e;
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        private final List<String> tokens = new ArrayList<>();
        private int pos;

        Parser(String s) {
            tokenize(s.toLowerCase(Locale.ROOT));
        }

        private void tokenize(String s) {
            int i = 0;
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (Character.isWhitespace(ch)) {
                    i++;
                } else if (Character.isDigit(ch) || (ch == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                    int j = i;
                    while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                    tokens.add(s.substring(i, j));
                    i = j;
                } else if (Character.isLetter(ch) || ch == '_') {
                    int j = i;
                    while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '.')) j++;
                    tokens.add(s.substring(i, j));
                    i = j;
                } else {
                    String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
                    if (two.equals("&&") || two.equals("||") || two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                        tokens.add(two);
                        i += 2;
                    } else {
                        tokens.add(String.valueOf(ch));
                        i++;
                    }
                }
            }
        }

        private String peek() {
            return pos < tokens.size() ? tokens.get(pos) : "";
        }

        private boolean accept(String t) {
            if (peek().equals(t)) {
                pos++;
                return true;
            }
            return false;
        }

        Expr parseAll() {
            // A trailing ';' and a leading 'return' are tolerated.
            if (peek().equals("return")) pos++;
            Expr e = ternary();
            accept(";");
            return e;
        }

        private Expr ternary() {
            Expr cond = or();
            if (accept("?")) {
                Expr a = ternary();
                Expr b = accept(":") ? ternary() : constant(0.0);
                return c -> cond.eval(c) != 0.0 ? a.eval(c) : b.eval(c);
            }
            return cond;
        }

        private Expr or() {
            Expr l = and();
            while (accept("||")) {
                Expr a = l;
                Expr r = and();
                l = c -> (a.eval(c) != 0.0 || r.eval(c) != 0.0) ? 1.0 : 0.0;
            }
            return l;
        }

        private Expr and() {
            Expr l = equality();
            while (accept("&&")) {
                Expr a = l;
                Expr r = equality();
                l = c -> (a.eval(c) != 0.0 && r.eval(c) != 0.0) ? 1.0 : 0.0;
            }
            return l;
        }

        private Expr equality() {
            Expr l = relational();
            while (true) {
                if (accept("==")) {
                    Expr a = l;
                    Expr r = relational();
                    l = c -> a.eval(c) == r.eval(c) ? 1.0 : 0.0;
                } else if (accept("!=")) {
                    Expr a = l;
                    Expr r = relational();
                    l = c -> a.eval(c) != r.eval(c) ? 1.0 : 0.0;
                } else {
                    return l;
                }
            }
        }

        private Expr relational() {
            Expr l = additive();
            while (true) {
                if (accept("<=")) {
                    Expr a = l;
                    Expr r = additive();
                    l = c -> a.eval(c) <= r.eval(c) ? 1.0 : 0.0;
                } else if (accept(">=")) {
                    Expr a = l;
                    Expr r = additive();
                    l = c -> a.eval(c) >= r.eval(c) ? 1.0 : 0.0;
                } else if (accept("<")) {
                    Expr a = l;
                    Expr r = additive();
                    l = c -> a.eval(c) < r.eval(c) ? 1.0 : 0.0;
                } else if (accept(">")) {
                    Expr a = l;
                    Expr r = additive();
                    l = c -> a.eval(c) > r.eval(c) ? 1.0 : 0.0;
                } else {
                    return l;
                }
            }
        }

        private Expr additive() {
            Expr l = multiplicative();
            while (true) {
                if (accept("+")) {
                    Expr a = l;
                    Expr r = multiplicative();
                    l = c -> a.eval(c) + r.eval(c);
                } else if (peek().equals("-")) {
                    pos++;
                    Expr a = l;
                    Expr r = multiplicative();
                    l = c -> a.eval(c) - r.eval(c);
                } else {
                    return l;
                }
            }
        }

        private Expr multiplicative() {
            Expr l = unary();
            while (true) {
                if (accept("*")) {
                    Expr a = l;
                    Expr r = unary();
                    l = c -> a.eval(c) * r.eval(c);
                } else if (accept("/")) {
                    Expr a = l;
                    Expr r = unary();
                    l = c -> {
                        double d = r.eval(c);
                        return d == 0.0 ? 0.0 : a.eval(c) / d;
                    };
                } else {
                    return l;
                }
            }
        }

        private Expr unary() {
            if (accept("-")) {
                Expr e = unary();
                return c -> -e.eval(c);
            }
            if (accept("+")) return unary();
            if (accept("!")) {
                Expr e = unary();
                return c -> e.eval(c) == 0.0 ? 1.0 : 0.0;
            }
            return primary();
        }

        private Expr primary() {
            String t = peek();
            if (t.isEmpty()) throw new IllegalStateException("unexpected end");
            if (accept("(")) {
                Expr e = ternary();
                accept(")");
                return e;
            }
            char first = t.charAt(0);
            if (Character.isDigit(first) || first == '.') {
                pos++;
                double v = Double.parseDouble(t);
                return constant(v);
            }
            pos++;
            if (peek().equals("(")) {
                pos++;
                List<Expr> args = new ArrayList<>();
                if (!peek().equals(")")) {
                    do {
                        args.add(ternary());
                    } while (accept(","));
                }
                accept(")");
                return function(t, args);
            }
            return variable(t);
        }

        private Expr variable(String name) {
            String n = name;
            if (n.startsWith("variable.")) n = n.substring(9);
            else if (n.startsWith("v.")) n = n.substring(2);
            else if (n.equals("math.pi")) return constant(Math.PI);
            switch (n) {
                case "particle_age":
                    return c -> c.age;
                case "particle_lifetime":
                    return c -> c.lifetime;
                case "particle_random_1":
                    return c -> c.random[0];
                case "particle_random_2":
                    return c -> c.random[1];
                case "particle_random_3":
                    return c -> c.random[2];
                case "particle_random_4":
                    return c -> c.random[3];
                case "emitter_age":
                    return c -> c.emitterAge;
                case "emitter_lifetime":
                    return c -> c.emitterLifetime;
                case "emitter_random_1":
                    return c -> c.emitterRandom[0];
                case "emitter_random_2":
                    return c -> c.emitterRandom[1];
                case "emitter_random_3":
                    return c -> c.emitterRandom[2];
                case "emitter_random_4":
                    return c -> c.emitterRandom[3];
                default:
                    return constant(0.0);
            }
        }

        private Expr function(String name, List<Expr> a) {
            String n = name.startsWith("math.") ? name.substring(5) : name;
            switch (n) {
                case "random":
                    return c -> {
                        double lo = a.get(0).eval(c);
                        double hi = a.size() > 1 ? a.get(1).eval(c) : 0.0;
                        return lo + RNG.nextDouble() * (hi - lo);
                    };
                case "random_integer":
                    return c -> {
                        double lo = a.get(0).eval(c);
                        double hi = a.get(1).eval(c);
                        double min = Math.min(lo, hi);
                        double max = Math.max(lo, hi);
                        return Math.floor(min + RNG.nextDouble() * (max - min + 1.0));
                    };
                case "round":
                    return c -> Math.floor(a.get(0).eval(c) + 0.5);
                case "floor":
                    return c -> Math.floor(a.get(0).eval(c));
                case "ceil":
                    return c -> Math.ceil(a.get(0).eval(c));
                case "trunc":
                    return c -> {
                        double v = a.get(0).eval(c);
                        return v < 0 ? Math.ceil(v) : Math.floor(v);
                    };
                case "abs":
                    return c -> Math.abs(a.get(0).eval(c));
                case "sqrt":
                    return c -> Math.sqrt(Math.max(0.0, a.get(0).eval(c)));
                case "pow":
                    return c -> Math.pow(a.get(0).eval(c), a.get(1).eval(c));
                case "min":
                    return c -> Math.min(a.get(0).eval(c), a.get(1).eval(c));
                case "max":
                    return c -> Math.max(a.get(0).eval(c), a.get(1).eval(c));
                case "clamp":
                    return c -> Math.max(a.get(1).eval(c), Math.min(a.get(2).eval(c), a.get(0).eval(c)));
                case "lerp":
                    return c -> {
                        double x = a.get(0).eval(c);
                        double y = a.get(1).eval(c);
                        double t = a.get(2).eval(c);
                        return x + (y - x) * t;
                    };
                case "sin":
                    return c -> Math.sin(Math.toRadians(a.get(0).eval(c)));
                case "cos":
                    return c -> Math.cos(Math.toRadians(a.get(0).eval(c)));
                case "mod":
                    return c -> {
                        double d = a.get(1).eval(c);
                        return d == 0.0 ? 0.0 : a.get(0).eval(c) % d;
                    };
                case "exp":
                    return c -> Math.exp(a.get(0).eval(c));
                case "ln":
                    return c -> Math.log(a.get(0).eval(c));
                default:
                    return constant(0.0);
            }
        }
    }
}
