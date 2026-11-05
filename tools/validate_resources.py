#!/usr/bin/env python3
"""Validate JSON, geometry/animation bone references, GeckoLib keyframe shape, and renderer texture paths."""
import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / "src/main/resources/assets/jujutsuenhancement"
JAVA_ROOT = ROOT / "src/main/java"
errors = []
json_files = sorted(RESOURCE_ROOT.rglob("*.json"))
for path in json_files:
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"Invalid JSON: {path.relative_to(ROOT)}: {exc}")

geo_files = sorted((RESOURCE_ROOT / "geo").glob("*.geo.json"))
for geo_path in geo_files:
    try:
        geo = json.loads(geo_path.read_text(encoding="utf-8"))
        geometry_bones = set()
        geometry_stats = []
        for model in geo.get("minecraft:geometry", []):
            description = model.get("description", {})
            texture_width = description.get("texture_width")
            texture_height = description.get("texture_height")
            cube_count = 0
            uv_face_count = 0
            bones = model.get("bones", [])
            bone_names = [bone.get("name") for bone in bones]
            duplicates = sorted({name for name in bone_names if bone_names.count(name) > 1})
            if duplicates:
                errors.append(f"Duplicate bone names: {geo_path.relative_to(ROOT)}: {duplicates}")
            geometry_bones.update(name for name in bone_names if isinstance(name, str))
            for bone in bones:
                parent = bone.get("parent")
                if parent is not None and parent not in bone_names:
                    errors.append(f"Missing parent bone: {geo_path.relative_to(ROOT)} / {bone.get('name')} -> {parent}")
                pivot = bone.get("pivot", [0, 0, 0])
                if not isinstance(pivot, list) or len(pivot) != 3 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in pivot):
                    errors.append(f"Invalid/non-finite bone pivot: {geo_path.relative_to(ROOT)} / {bone.get('name')}")
                for cube in bone.get("cubes", []):
                    cube_count += 1
                    origin = cube.get("origin", [0, 0, 0])
                    size = cube.get("size", [0, 0, 0])
                    if not isinstance(origin, list) or len(origin) != 3 or not isinstance(size, list) or len(size) != 3:
                        errors.append(f"Invalid cube origin/size: {geo_path.relative_to(ROOT)} / {bone.get('name')}")
                    elif not all(isinstance(v, (int, float)) and math.isfinite(v) for v in origin + size):
                        errors.append(f"Non-finite cube coordinates: {geo_path.relative_to(ROOT)} / {bone.get('name')}")
                    elif any(v < 0 for v in size):
                        errors.append(f"Negative cube dimension: {geo_path.relative_to(ROOT)} / {bone.get('name')} size={size}")
                    uv = cube.get("uv", {})
                    if isinstance(uv, dict):
                        for face, face_uv in uv.items():
                            if not isinstance(face_uv, dict) or not isinstance(face_uv.get("uv"), list) or not isinstance(face_uv.get("uv_size"), list):
                                errors.append(f"Invalid face UV: {geo_path.relative_to(ROOT)} / {bone.get('name')}.{face}")
                                continue
                            start, span = face_uv["uv"], face_uv["uv_size"]
                            if len(start) != 2 or len(span) != 2 or not all(isinstance(v, (int, float)) and math.isfinite(v) for v in start + span):
                                errors.append(f"Invalid/non-finite face UV values: {geo_path.relative_to(ROOT)} / {bone.get('name')}.{face}")
                                continue
                            uv_face_count += 1
                            if isinstance(texture_width, (int, float)) and isinstance(texture_height, (int, float)):
                                u0, v0 = start
                                u1, v1 = u0 + span[0], v0 + span[1]
                                if min(u0, u1) < -0.01 or max(u0, u1) > texture_width + 0.01 or min(v0, v1) < -0.01 or max(v0, v1) > texture_height + 0.01:
                                    errors.append(f"UV outside declared {texture_width}x{texture_height} atlas: {geo_path.relative_to(ROOT)} / {bone.get('name')}.{face} {start}+{span}")
            geometry_stats.append((texture_width, texture_height, cube_count, uv_face_count))
        stem = geo_path.name.removesuffix(".geo.json")
        for texture_width, texture_height, cube_count, uv_face_count in geometry_stats:
            print(f"{stem}: {len(geometry_bones)} bones; {cube_count} cubes; {uv_face_count} UV faces within declared {texture_width}x{texture_height} atlas")
        anim_path = RESOURCE_ROOT / "animations" / f"{stem}.animation.json"
        if not anim_path.exists():
            print(f"{stem}: no paired animation resource; geometry/UV checks only")
            continue
        anim = json.loads(anim_path.read_text(encoding="utf-8"))
        animated_bones = set()
        for animation in anim.get("animations", {}).values():
            animated_bones.update(animation.get("bones", {}).keys())
            for bone_name, channels in animation.get("bones", {}).items():
                for channel_name, frames in channels.items():
                    if channel_name not in {"rotation", "position", "scale"}:
                        errors.append(f"Unsupported animation channel {channel_name}: {anim_path.relative_to(ROOT)} / {bone_name}")
                    for time_key, keyframe in frames.items():
                        try:
                            frame_time = float(time_key)
                            if not math.isfinite(frame_time) or frame_time < 0:
                                raise ValueError("time must be finite and non-negative")
                        except (TypeError, ValueError):
                            errors.append(f"Invalid animation time: {anim_path.relative_to(ROOT)} / {bone_name}.{channel_name}@{time_key}")
                        # GeckoLib reads both {"vector": [...]} and Blockbench/Bedrock {"post"/"pre": [...], "lerp_mode": ...}.
                        vec = keyframe if isinstance(keyframe, list) else None  # plain [x, y, z] is valid too
                        if isinstance(keyframe, dict):
                            for key in ("vector", "post", "pre"):
                                if isinstance(keyframe.get(key), list):
                                    vec = keyframe[key]
                                    break
                        if vec is None or len(vec) != 3:
                            errors.append(f"Invalid GeckoLib keyframe schema: {anim_path.relative_to(ROOT)} / {bone_name}.{channel_name}@{time_key}")
                        elif not all(isinstance(v, (int, float)) and math.isfinite(v) for v in vec):
                            errors.append(f"Non-finite animation vector: {anim_path.relative_to(ROOT)} / {bone_name}.{channel_name}@{time_key}")
        unknown = sorted(animated_bones - geometry_bones)
        if unknown:
            errors.append(f"{anim_path.relative_to(ROOT)} references bones absent from {geo_path.name}: {unknown}")
        print(f"{stem}: {len(animated_bones)} animated bone names; {len(animated_bones & geometry_bones)} match")
    except Exception as exc:
        errors.append(f"Could not audit {geo_path.relative_to(ROOT)}: {exc}")

# Cross-audit animation files against every geometry, not only same-name pairs.
# This exposes assets that must not be wired together in a renderer yet.
geometry_bone_sets = {}
for geo_path in geo_files:
    try:
        geo = json.loads(geo_path.read_text(encoding="utf-8"))
        geometry_bone_sets[geo_path.stem.removesuffix(".geo")] = {
            bone.get("name")
            for model in geo.get("minecraft:geometry", [])
            for bone in model.get("bones", [])
            if isinstance(bone.get("name"), str)
        }
    except Exception:
        pass
for anim_path in sorted((RESOURCE_ROOT / "animations").glob("*.animation.json")):
    try:
        anim = json.loads(anim_path.read_text(encoding="utf-8"))
        anim_bones = set()
        for animation in anim.get("animations", {}).values():
            anim_bones.update(animation.get("bones", {}).keys())
        if not anim_bones:
            continue
        scores = []
        for geo_name, geo_bones in geometry_bone_sets.items():
            matched = len(anim_bones & geo_bones)
            scores.append((matched, geo_name, len(anim_bones - geo_bones)))
        scores.sort(reverse=True)
        best = scores[0] if scores else (0, "none", len(anim_bones))
        print(f"Animation compatibility: {anim_path.name} -> best geometry {best[1]} ({best[0]}/{len(anim_bones)} bones match)")
        if best[0] != len(anim_bones):
            print(f"WARNING: {anim_path.name} is not fully compatible with any geometry; do not bind it to a renderer without conversion.")
    except Exception as exc:
        errors.append(f"Could not cross-audit animation {anim_path.relative_to(ROOT)}: {exc}")

# Check texture paths in GeoModel source constants exist under assets/<namespace>/textures.
for java_path in JAVA_ROOT.rglob("*.java"):
    source = java_path.read_text(encoding="utf-8")
    for match in re.finditer(r'new ResourceLocation\(\s*JujutsuEnhancement\.MODID,\s*"textures/([^"]+)"\s*\)', source):
        relative = match.group(1)
        texture_path = RESOURCE_ROOT / "textures" / relative
        if not texture_path.exists():
            errors.append(f"Missing renderer texture {texture_path.relative_to(ROOT)} referenced by {java_path.relative_to(ROOT)}")
        else:
            print(f"Texture reference OK: {texture_path.relative_to(ROOT)}")

print(f"JSON files checked: {len(json_files)}")
if errors:
    print("FAIL")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print("PASS: JSON syntax, cube coordinates, UV bounds, paired bone names, keyframe schema, and renderer texture paths validated.")
