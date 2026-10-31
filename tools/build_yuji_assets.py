"""Build the awakened-Yuji animation set from the Bedrock pack.
Usage: python3 -I tools/build_yuji_assets.py <bedrock-rp-dir> <assets-dir>
"""
import json, os, sys
rp, assets = sys.argv[1], sys.argv[2]
def load(rel):
    return json.load(open(os.path.join(rp, "animations", rel)))["animations"]
out = {}
def take(rel, mapping):
    src = load(rel)
    for old, new in mapping.items():
        a = json.loads(json.dumps(src[old]))
        for k in ("sound_effects", "particle_effects", "timeline", "override_previous_animation", "loop"):
            a.pop(k, None)
        out["animation.yuji." + new] = a
take("yuji.animation.json", {
    "animation.yuji.attack1": "attack1", "animation.yuji.attack2": "attack2",
    "animation.yuji.attack3": "attack3", "animation.yuji.attack4": "attack4",
    "animation.yuji.attack5": "attack5", "animation.yuji.attack6": "attack6",
    "animation.yuji.manji_kick": "manji_kick", "animation.yuji.manji_kick2": "manji_kick2",
    "animation.yuji.triple_kick": "triple_kick", "animation.yuji.triple_kick2": "triple_kick2",
    "animation.yuji.fierce_rush": "fierce_rush", "animation.yuji.fierce_rush2": "fierce_rush2",
    "animation.yuji.divergent_blow": "divergent_blow"})
take("enraged_yuji_cutscene.animation.json", {"animation.enraged_yuji_mahito": "execute_mahito"})
gojo = json.load(open(os.path.join(assets, "animations/gojo_awakened.animation.json")))["animations"]
for n in ("idle", "walk", "stunned", "death"):
    out["animation.yuji." + n] = gojo["animation.gojo." + n]
json.dump({"format_version": "1.8.0", "animations": out},
          open(os.path.join(assets, "animations/yuji_awakened.animation.json"), "w"), separators=(",", ":"))
print({k: v.get("animation_length") for k, v in out.items()})
