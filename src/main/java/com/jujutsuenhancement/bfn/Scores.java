package com.jujutsuenhancement.bfn;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The scoreboard of the add-on: objective -> holder key -> score. A holder is a player name, an entity UUID or a fake name. */
public final class Scores {
    private final Map<String, Map<String, Integer>> objectives = new HashMap<>();

    public Map<String, Integer> objective(String name) {
        return objectives.get(name);
    }

    public void addObjective(String name) {
        objectives.computeIfAbsent(name, k -> new HashMap<>());
    }

    public void removeObjective(String name) {
        objectives.remove(name);
    }

    public Set<String> objectiveNames() {
        return objectives.keySet();
    }

    /** null = the holder has no score in the objective */
    public Integer get(String obj, String holder) {
        Map<String, Integer> m = objectives.get(obj);
        return m == null ? null : m.get(holder);
    }

    public void set(String obj, String holder, int v) {
        objectives.computeIfAbsent(obj, k -> new HashMap<>()).put(holder, v);
    }

    public int add(String obj, String holder, int d) {
        Map<String, Integer> m = objectives.computeIfAbsent(obj, k -> new HashMap<>());
        int v = m.getOrDefault(holder, 0) + d;
        m.put(holder, v);
        return v;
    }

    public void reset(String obj, String holder) {
        if (obj == null) {
            for (Map<String, Integer> m : objectives.values()) m.remove(holder);
        } else {
            Map<String, Integer> m = objectives.get(obj);
            if (m != null) m.remove(holder);
        }
    }

    public void removeHolder(String holder) {
        reset(null, holder);
    }
}
