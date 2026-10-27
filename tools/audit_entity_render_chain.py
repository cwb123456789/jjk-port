#!/usr/bin/env python3
"""Audit the Forge entity -> renderer -> GeoModel -> resource chain."""
from pathlib import Path
import json, re, sys
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/jujutsuenhancement"
ASSETS = ROOT / "src/main/resources/assets/jujutsuenhancement"
errors, warnings, checks = [], [], []
def check(ok, msg):
    (checks if ok else errors).append(("PASS: " if ok else "FAIL: ") + msg)
# entity registration references
reg = (JAVA / "registry/ModEntities.java").read_text()
events = (JAVA / "client/ClientModEvents.java").read_text()
for entity, model, renderer, texture, anim, geometry, entity_class in [
    ("HEIAN_SUKUNA", "HeianSukunaModel", "HeianSukunaRenderer", "heian_sukuna.png", "heian_sukuna.animation.json", "heian_sukuna.geo.json", "HeianSukunaEntity"),
    ("MALEVOLENT_SHRINE", "DomainShrineModel", "DomainShrineRenderer", "malevolent_shrine.png", "malevolent_shrine.animation.json", "malevolent_shrine.geo.json", "DomainShrineEntity"),
    ("MAHITO_DOMAIN_HANDS", "MahitoDomainHandsModel", "MahitoDomainHandsRenderer", "perfection_domain_hands.png", "empty.animation.json", "perfection_domain_hands_compat.geo.json", "MahitoDomainHandsEntity"),
    ("MAHITO_DOMAIN_BARRIER", "MahitoDomainBarrierModel", "MahitoDomainBarrierRenderer", "mahito_domain_barrier.png", "empty.animation.json", "mahito_domain_barrier.geo.json", "MahitoDomainBarrierEntity")]:
    check(entity in reg, f"{entity} is registered")
    check(renderer in events, f"{renderer} is registered on client")
    model_file = next(iter(JAVA.rglob(model + ".java")), None)
    check(model_file is not None, f"{model} source exists")
    if model_file:
        src = model_file.read_text()
        for token, desc in [(texture, "texture"), (anim, "animation"), (geometry, "geometry")]:
            check(token in src, f"{model} points to {desc} resource")
            path = ASSETS / (("textures/entity/" if desc == "texture" else "animations/" if desc == "animation" else "geo/") + token)
            check(path.exists(), f"{model} {desc} file exists: {path.relative_to(ROOT)}")
        ad = ASSETS / "animations" / anim
        if ad.exists():
            names = set(json.loads(ad.read_text()).get("animations", {}))
            entfile = next(iter(JAVA.rglob(entity_class + ".java")), None)
            entsrc = entfile.read_text() if entfile else ""
            refs = re.findall(r'"(animation\.[A-Za-z0-9_.-]+)"', entsrc)
            for ref in refs:
                check(ref in names, f"{entity} controller animation exists: {ref}")
# GeckoLib 4 uses Bedrock-style geometry JSON; validate its structure instead of
# incorrectly flagging the expected `minecraft:geometry` wrapper as incompatible.
for p in (ASSETS/"geo").glob("*.json"):
    try:
        data=json.loads(p.read_text())
        check(True, f"valid JSON: {p.name}")
        geometries=data.get("minecraft:geometry")
        check(isinstance(geometries, list) and bool(geometries), f"{p.name} has Bedrock/GeckoLib geometry array")
        if isinstance(geometries, list):
            for gi, geometry in enumerate(geometries):
                desc=geometry.get("description", {})
                bones=geometry.get("bones", [])
                check(bool(desc.get("identifier")), f"{p.name}[{gi}] has geometry identifier")
                check(isinstance(bones, list) and bool(bones), f"{p.name}[{gi}] has bone list")
                names=[b.get("name") for b in bones]
                check(all(isinstance(n, str) and n for n in names), f"{p.name}[{gi}] bone names are non-empty")
                check(len(names)==len(set(names)), f"{p.name}[{gi}] bone names are unique")
                known=set(names)
                for bone in bones:
                    parent=bone.get("parent")
                    if parent:
                        check(parent in known, f"{p.name}: parent {parent} exists for {bone.get('name')}")
    except Exception as e: check(False, f"invalid JSON {p.name}: {e}")
print("\n".join(checks+["WARN: "+x for x in warnings]+["ERROR: "+x for x in errors]))
print(f"SUMMARY: {len(checks)} passed, {len(warnings)} warnings, {len(errors)} errors")
sys.exit(1 if errors else 0)
