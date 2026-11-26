package com.jujutsuenhancement.bfn;

/** A score / number range of a selector or of {@code matches}: {@code 5}, {@code 1..}, {@code ..5}, {@code 1..5}, each optionally negated with {@code !}. */
public final class Range {
    public final boolean neg;
    public final double lo;
    public final double hi;

    public Range(boolean neg, double lo, double hi) {
        this.neg = neg;
        this.lo = lo;
        this.hi = hi;
    }

    public static Range parse(String s) {
        boolean neg = false;
        if (s.startsWith("!")) {
            neg = true;
            s = s.substring(1);
        }
        int i = s.indexOf("..");
        if (i < 0) {
            double v = Double.parseDouble(s);
            return new Range(neg, v, v);
        }
        String a = s.substring(0, i);
        String b = s.substring(i + 2);
        return new Range(neg, a.isEmpty() ? Double.NEGATIVE_INFINITY : Double.parseDouble(a), b.isEmpty() ? Double.POSITIVE_INFINITY : Double.parseDouble(b));
    }

    public boolean test(double v) {
        return (v >= lo && v <= hi) != neg;
    }

    @Override
    public String toString() {
        return (neg ? "!" : "") + lo + ".." + hi;
    }
}
