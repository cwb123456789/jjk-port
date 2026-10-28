"""Generate the awakened-Gojo geometry and animation resources.

Usage: python3 -I tools/build_gojo_assets.py <bedrock-rp-dir> <out-assets-dir>
The Bedrock animations are copied bone-for-bone (bone names already match the
geometry written here); sound/particle events are stripped because the Forge
entity drives its own effects in Java.
"""
import json, sys, os

rp, out = sys.argv[1], sys.argv[2]

def cube(origin, size, uv, inflate=0.0, mirror=False):
    c = {"origin": origin, "size": size, "uv": uv}
    if inflate:
        c["inflate"] = inflate
    if mirror:
        c["mirror"] = True
    return c

bones = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "body", "parent": "root", "pivot": [0, 24, 0], "cubes": [
        cube([-4, 12, -2], [8, 12, 4], [16, 16]),
        cube([-4, 12, -2], [8, 12, 4], [16, 32], 0.25)]},
    {"name": "head", "parent": "body", "pivot": [0, 24, 0], "cubes": [
        cube([-4, 24, -4], [8, 8, 8], [0, 0]),
        cube([-4, 24, -4], [8, 8, 8], [32, 0], 0.5)]},
    {"name": "rightArm", "parent": "body", "pivot": [-5, 22, 0], "cubes": [
        cube([-8, 12, -2], [4, 12, 4], [40, 16]),
        cube([-8, 12, -2], [4, 12, 4], [40, 32], 0.25)]},
    {"name": "rightItem", "parent": "rightArm", "pivot": [-6, 15, 1]},
    {"name": "leftArm", "parent": "body", "pivot": [5, 22, 0], "cubes": [
        cube([4, 12, -2], [4, 12, 4], [32, 48]),
        cube([4, 12, -2], [4, 12, 4], [48, 48], 0.25)]},
    {"name": "rightLeg", "parent": "root", "pivot": [-1.9, 12, 0], "cubes": [
        cube([-3.9, 0, -2], [4, 12, 4], [0, 16]),
        cube([-3.9, 0, -2], [4, 12, 4], [0, 32], 0.25)]},
    {"name": "leftLeg", "parent": "root", "pivot": [1.9, 12, 0], "cubes": [
        cube([-0.1, 0, -2], [4, 12, 4], [16, 48]),
        cube([-0.1, 0, -2], [4, 12, 4], [0, 48], 0.25)]},
]
geo = {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.awakened_humanoid",
            "texture_width": 64, "texture_height": 64,
            "visible_bounds_width": 4, "visible_bounds_height": 4,
            "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]
}

src = json.load(open(os.path.join(rp, "animations/limitless.animation.json")))["animations"]
wanted = {
    "hollowpurple": "purple_normal",
    "limitless_purple": "purple_cutscene",
    "200_purple": "purple_200",
    "imaginary_purple": "purple_unlimited",
    "infinite_void": "domain",
    "infinite_void_2sec": "domain_quick",
    "infinite_void_standby": "domain_standby",
    "limitless_attack1": "attack1", "limitless_attack2": "attack2",
    "limitless_attack3": "attack3", "limitless_attack4": "attack4",
    "limitless_attack5": "attack5",
    "stunned": "stunned",
}
anims = {}
for old, new in wanted.items():
    a = json.loads(json.dumps(src["animation." + old]))
    for k in ("sound_effects", "particle_effects", "timeline", "override_previous_animation"):
        a.pop(k, None)
    if "animation_length" not in a or a["animation_length"] is None:
        raise SystemExit("missing length " + old)
    anims["animation.gojo." + new] = a

def kf(vals):
    return {str(t): {"vector": v, "lerp_mode": "catmullrom"} for t, v in vals}

anims["animation.gojo.idle"] = {
    "loop": True, "animation_length": 4.0,
    "bones": {
        "body": {"rotation": kf([(0, [0, 0, 0]), (2, [1.2, 0, 0]), (4, [0, 0, 0])])},
        "head": {"rotation": kf([(0, [0, 0, 0]), (2, [-1.0, 0, 0]), (4, [0, 0, 0])])},
        "rightArm": {"rotation": kf([(0, [0, 0, 3]), (2, [1, 0, 4]), (4, [0, 0, 3])])},
        "leftArm": {"rotation": kf([(0, [0, 0, -3]), (2, [1, 0, -4]), (4, [0, 0, -3])])},
    }}
anims["animation.gojo.walk"] = {
    "loop": True, "animation_length": 1.0,
    "bones": {
        "rightLeg": {"rotation": kf([(0, [30, 0, 0]), (0.5, [-30, 0, 0]), (1, [30, 0, 0])])},
        "leftLeg": {"rotation": kf([(0, [-30, 0, 0]), (0.5, [30, 0, 0]), (1, [-30, 0, 0])])},
        "rightArm": {"rotation": kf([(0, [-25, 0, 3]), (0.5, [25, 0, 3]), (1, [-25, 0, 3])])},
        "leftArm": {"rotation": kf([(0, [25, 0, -3]), (0.5, [-25, 0, -3]), (1, [25, 0, -3])])},
    }}
anims["animation.gojo.death"] = {
    "loop": "hold_on_last_frame", "animation_length": 1.0,
    "bones": {"root": {"rotation": kf([(0, [0, 0, 0]), (1, [-90, 0, 0])]),
                       "position": kf([(0, [0, 0, 0]), (1, [0, 0, 0])])}}}

os.makedirs(os.path.join(out, "geo"), exist_ok=True)
os.makedirs(os.path.join(out, "animations"), exist_ok=True)
json.dump(geo, open(os.path.join(out, "geo/gojo_awakened.geo.json"), "w"), indent=1)
json.dump({"format_version": "1.8.0", "animations": anims},
          open(os.path.join(out, "animations/gojo_awakened.animation.json"), "w"), separators=(",", ":"))
print("animations:", {k: v["animation_length"] for k, v in anims.items()})
