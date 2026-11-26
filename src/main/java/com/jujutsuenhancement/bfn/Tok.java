package com.jujutsuenhancement.bfn;

import java.util.ArrayList;
import java.util.List;

/** Splits a command into words; brackets, braces and quotes keep their content together (selectors, JSON). */
final class Tok {
    private Tok() {}

    static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && s.charAt(i) == ' ') i++;
            if (i >= n) break;
            int st = i;
            int depth = 0;
            boolean q = false;
            while (i < n) {
                char c = s.charAt(i);
                if (q) {
                    if (c == '\\' && i + 1 < n) i++;
                    else if (c == '"') q = false;
                } else if (c == '"') q = true;
                else if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') depth--;
                else if (c == ' ' && depth <= 0) break;
                i++;
            }
            out.add(s.substring(st, i));
        }
        return out;
    }

    private static final java.util.regex.Pattern GLUED = java.util.regex.Pattern.compile("(?:[~^]-?[\\d.]*){2,}");
    private static final java.util.regex.Pattern ONE = java.util.regex.Pattern.compile("[~^]-?[\\d.]*");

    /** `~~-1~` is three words: coordinates written without spaces become separate words. */
    static List<String> splitCoords(List<String> w) {
        List<String> out = new ArrayList<>(w.size() + 4);
        for (String t : w) {
            if (GLUED.matcher(t).matches()) {
                var m = ONE.matcher(t);
                while (m.find()) out.add(m.group());
            } else out.add(t);
        }
        return out;
    }

    /** Splits at top-level commas. */
    static List<String> commas(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean q = false;
        int st = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q) {
                if (c == '\\') i++;
                else if (c == '"') q = false;
            } else if (c == '"') q = true;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(st, i));
                st = i + 1;
            }
        }
        if (st < s.length() || !out.isEmpty()) out.add(s.substring(st));
        return out;
    }
}
