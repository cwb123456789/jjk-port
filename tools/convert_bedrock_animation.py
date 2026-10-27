#!/usr/bin/env python3
"""First-pass Bedrock animation JSON -> GeckoLib-compatible animation JSON normalizer.

Usage: python tools/convert_bedrock_animation.py input.animation.json output.animation.json
Converts rotation/position/scale channels into GeckoLib 4 keyframe objects.
Bedrock Molang, particle/sound events, animation controllers, axis/pivot differences,
and exact interpolation semantics are not ported and need explicit implementation/testing.
"""
import json
import math
import sys
from pathlib import Path


def is_numeric_vector(value):
    return (isinstance(value, list) and len(value) == 3
            and all(isinstance(component, (int, float)) and math.isfinite(component) for component in value))


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, dst = map(Path, sys.argv[1:])
    data = json.loads(src.read_text(encoding="utf-8"))
    warnings = []
    animations = {}
    for name, anim in data.get("animations", {}).items():
        converted = {}
        for key in ("loop", "animation_length"):
            if key in anim:
                converted[key] = anim[key]
        bones = {}
        for bone_name, channels in anim.get("bones", {}).items():
            out_channels = {}
            for channel_name, frames in channels.items():
                if channel_name not in {"rotation", "position", "scale"} or not isinstance(frames, dict):
                    warnings.append(f"{name}.{bone_name}: skipped channel {channel_name}")
                    continue
                out_frames = {}
                for time, value in frames.items():
                    if is_numeric_vector(value):
                        vec = value
                        out_frames[str(time)] = {"vector": vec, "easing": "linear"}
                    elif isinstance(value, list):
                        warnings.append(f"{name}.{bone_name}.{channel_name}@{time}: Molang/non-numeric vector omitted")
                    elif isinstance(value, dict):
                        # GeckoLib 4 keyframes use an object with a `vector` field;
                        # raw Bedrock arrays are not the full GeckoLib keyframe schema.
                        vec = value.get("post", value.get("pre"))
                        if is_numeric_vector(vec):
                            out_frames[str(time)] = {"vector": vec, "easing": "linear"}
                            if value.get("lerp_mode") == "catmullrom":
                                warnings.append(f"{name}.{bone_name}.{channel_name}@{time}: Catmull-Rom flattened to linear")
                        else:
                            warnings.append(f"{name}.{bone_name}.{channel_name}@{time}: unsupported keyframe omitted")
                    else:
                        warnings.append(f"{name}.{bone_name}.{channel_name}@{time}: invalid vector omitted")
                if out_frames:
                    out_channels[channel_name] = out_frames
            if out_channels:
                bones[bone_name] = out_channels
        converted["bones"] = bones
        animations[name] = converted
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps({"format_version": "1.8.0", "animations": animations}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Converted {len(animations)} animation(s) -> {dst}")
    print(f"Warnings: {len(warnings)}")
    for warning in warnings[:30]:
        print(f"- {warning}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
