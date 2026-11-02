"""Extract melee move timelines from the Bedrock behaviour-pack functions.

Usage: python3 -I tools/extract_moves.py <functions-dir> <out.json> <function-path>...
Each function path is relative to functions-dir, without extension, e.g. sukuna/attacks/basic_combat/demonic_blows.

The Bedrock functions count a `move` score DOWN from the animation length; an event on
`move=N` therefore happens (start - N) ticks after the move began. Only the parts that matter
for a Forge re-implementation are kept: which animation plays, how long the move lasts, and
when/where hits land (forward offset, radius, damage, stun, knockback, levitation).
"""
import json, os, re, sys

fdir, out = sys.argv[1], sys.argv[2]
paths = sys.argv[3:]

RANGE = r"move=(\d+)(?:\.\.(\d+))?"
HIT = re.compile(r"execute as @s\[scores=\{" + RANGE + r"[^\]]*\] at @s positioned \^([\d.\-]*)\^([\d.\-]*)\^([\d.\-]*) (?:run )?(.*)")
ANIM = re.compile(r"playanimation @s\[scores=\{" + r"move=(\d+)" + r"[^\]]*\] (animation\.[\w.]+)")
KB = re.compile(r"execute as @s\[scores=\{" + RANGE + r"[^\]]*\] at @s run scriptevent ds:knockback (-?\d+)")
DASH = re.compile(r"execute as @s\[scores=\{" + RANGE + r"[^\]]*\] at @s run tp @s \^([\d.\-]*)\^([\d.\-]*)\^([\d.\-]*)\s*$")
SEL_R = re.compile(r"r=([\d.]+)")

def num(s):
    return float(s) if s not in ("", "-") else 0.0

result = {}
for path in paths:
    name = os.path.basename(path)
    lines = [l.strip() for l in open(os.path.join(fdir, path + ".mcfunction"), encoding="utf-8", errors="ignore")]
    anims = []
    for l in lines:
        m = ANIM.search(l)
        if m:
            anims.append((int(m.group(1)), m.group(2)))
    if not anims:
        print("skip (no animation):", path)
        continue
    anims.sort(reverse=True)
    start, anim = anims[0]
    hits = {}
    kbs = []
    dashes = []
    for l in lines:
        m = HIT.match(l)
        if m:
            hi = int(m.group(2)) if m.group(2) else int(m.group(1))
            t = start - hi
            fwd = num(m.group(5))
            rest = m.group(6)
            h = hits.setdefault((t, round(fwd, 2)), {"t": t, "fwd": fwd, "r": 0.0, "dmg": 0.0, "stun": 0, "lev": 0})
            d = re.match(r"damage @e\[([^\]]*)\] (\d+)", rest)
            if d:
                r = SEL_R.search(d.group(1))
                h["r"] = max(h["r"], float(r.group(1)) if r else 2.5)
                h["dmg"] += int(d.group(2))
            s = re.match(r"scoreboard players set @e\[([^\]]*)\] Stun (\d+)", rest)
            if s:
                r = SEL_R.search(s.group(1))
                h["r"] = max(h["r"], float(r.group(1)) if r else 2.5)
                h["stun"] = max(h["stun"], int(s.group(2)))
            e = re.match(r"effect @e\[([^\]]*)\] levitation \d+ (\d+)", rest)
            if e:
                h["lev"] = max(h["lev"], int(e.group(2)))
            continue
        m = KB.match(l)
        if m:
            hi = int(m.group(2)) if m.group(2) else int(m.group(1))
            kbs.append({"t": start - hi, "kb": int(m.group(3))})
            continue
        m = DASH.match(l)
        if m:
            hi = int(m.group(2)) if m.group(2) else int(m.group(1))
            dashes.append({"t": start - hi, "d": num(m.group(5))})
    hl = [h for h in hits.values() if h["dmg"] > 0]
    hl.sort(key=lambda h: h["t"])
    result[name] = {"anim": anim.replace("animation.", "", 1), "ticks": start, "hits": hl,
                    "kb": kbs[:6], "dash": [d for d in dashes if d["d"] != 0][:6]}
json.dump(result, open(out, "w"), indent=1)
for n, v in result.items():
    print(n, v["anim"], v["ticks"], "hits:", [(h["t"], h["fwd"], h["r"], h["dmg"], h["stun"]) for h in v["hits"]][:5])
