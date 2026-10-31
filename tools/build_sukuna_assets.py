"""Build the awakened-Sukuna (possessed Megumi) animation set.

Usage: python3 -I tools/build_sukuna_assets.py <bedrock-rp-dir> <assets-dir>
Copies animations from the Bedrock resource pack (effects stripped; the Forge entity
drives particles/sounds) and reuses the Gojo idle/walk loops for locomotion.
"""
import json, os, sys
rp, assets = sys.argv[1], sys.argv[2]

def load(rel):
    return json.load(open(os.path.join(rp, "animations", rel)))["animations"]

sources = {
    "sukuna/sukuna_moves.animation.json": {
        "animation.sukuna.attack1": "attack1", "animation.sukuna.attack2": "attack2",
        "animation.sukuna.attack3": "attack3", "animation.sukuna.attack4": "attack4",
        "animation.sukuna.attack5": "attack5", "animation.dismantle1": "dismantle",
        "animation.cleave": "cleave"},
    "sukuna/divine_flames.animation.json": {
        "animation.fuga_activate": "fuga_activate", "animation.fuga_activate_hold": "fuga_hold",
        "animation.fuga_arrow1": "furnace"},
    "sukuna/domain_expansions.animation.json": {
        "animation.malevolent_shrine": "shrine", "animation.malevolent_shrine_hold": "shrine_hold"},
    "sukuna/sukuna_awakenings.animation.json": {
        "animation.innate_sukuna": "possession", "animation.innate_im_ready": "possessed_ready"},
}
out = {}
for rel, names in sources.items():
    src = load(rel)
    for old, new in names.items():
        a = json.loads(json.dumps(src[old]))
        for k in ("sound_effects", "particle_effects", "timeline", "override_previous_animation"):
            a.pop(k, None)
        a.pop("loop", None)
        out["animation.sukuna." + new] = a

heian = json.load(open(os.path.join(assets, "animations/heian_sukuna.animation.json")))["animations"]
rel = json.loads(json.dumps(heian["animation.fuga_release"]))
rel.pop("loop", None)
out["animation.sukuna.fuga_release"] = rel

gojo = json.load(open(os.path.join(assets, "animations/gojo_awakened.animation.json")))["animations"]
for n in ("idle", "walk", "death", "stunned"):
    out["animation.sukuna." + n] = gojo["animation.gojo." + n]
# GeckoLib reads "loop" per animation; locomotion loops come from the gojo set.
json.dump({"format_version": "1.8.0", "animations": out},
          open(os.path.join(assets, "animations/sukuna_awakened.animation.json"), "w"), separators=(",", ":"))
print({k: v.get("animation_length") for k, v in out.items()})
