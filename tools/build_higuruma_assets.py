"""Build the awakened-Higuruma animation set (gavel, executioner's sword, trial domain).
Usage: python3 -I tools/build_higuruma_assets.py <bedrock-rp-dir> <assets-dir>
"""
import json, os, sys
rp, assets = sys.argv[1], sys.argv[2]
src = json.load(open(os.path.join(rp, "animations/judgeman.animation.json")))["animations"]
mapping = {}
for i in range(1, 6): mapping[f"animation.gavel1.attack{i}"] = f"gavel_attack{i}"
for n in ("ab1", "ab2", "ab3"): mapping[f"animation.gavel1.{n}"] = f"gavel_{n}"
for i in range(1, 6): mapping[f"animation.executioners_attack{i}"] = f"sword_attack{i}"
for n in ("ab1", "ab2", "ab3", "ab4", "ab5"): mapping[f"animation.executioners_{n}"] = f"sword_{n}"
mapping.update({"animation.death_sentence": "death_sentence", "animation.objection": "objection",
                "animation.judgeman.domain": "domain_start", "animation.judgeman.domain2": "domain",
                "animation.judgeman.domain_hold": "domain_hold", "animation.judge_evidence": "evidence"})
out = {}
for old, new in mapping.items():
    a = json.loads(json.dumps(src[old]))
    for k in ("sound_effects", "particle_effects", "timeline", "override_previous_animation", "loop"):
        a.pop(k, None)
    out["animation.higuruma." + new] = a
gojo = json.load(open(os.path.join(assets, "animations/gojo_awakened.animation.json")))["animations"]
for n in ("idle", "walk", "stunned", "death"):
    out["animation.higuruma." + n] = gojo["animation.gojo." + n]
json.dump({"format_version": "1.8.0", "animations": out},
          open(os.path.join(assets, "animations/higuruma_awakened.animation.json"), "w"), separators=(",", ":"))
print({k: v.get("animation_length") for k, v in out.items()})
