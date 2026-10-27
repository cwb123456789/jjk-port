#!/usr/bin/env python3
"""Static checks for the native Mahito phase-two renderer adapter (r66)."""
from pathlib import Path
import json
ROOT = Path(__file__).resolve().parents[1]
model = ROOT / 'src/main/java/com/jujutsuenhancement/client/model/NativeMahitoPhase2GeoModel.java'
events = ROOT / 'src/main/java/com/jujutsuenhancement/client/ClientModEvents.java'
geo_path = ROOT / 'src/main/resources/assets/jujutsuenhancement/geo/mahito_stage2_native.geo.json'
source = model.read_text(encoding='utf-8')
event_source = events.read_text(encoding='utf-8')
geo = json.loads(geo_path.read_text(encoding='utf-8'))
checks = {
    'phase-two GeoModel adapter exists': 'extends GeoModel<GeoAnimatable>' in source,
    'native human geometry remains selected outside phase-two form': 'isPhaseTwo(entity) ? PHASE_TWO_MODEL : NATIVE_MODEL' in source,
    'phase-two geometry is selected from the synchronized native texture': 'isPhaseTwo(entity) ? PHASE_TWO_MODEL : NATIVE_MODEL' in source and 'getNativeTextureName(entity)' in source,
    'phase-two texture is selected for the transformed form': 'textures/entities/mahito_phase2__body.png' in source,
    'native animation resource is retained for existing skill animations': 'animations/human.animation.json' in source,
    'renderer adapter attaches during AddLayers event': 'EntityRenderersEvent.AddLayers' in event_source and 'attachNativeMahitoModel' in event_source,
    'adapter locates the native Mahito registry entry': 'jujutsucraft", "mahito' in event_source,
    'renderer replacement is guarded by GeckoLib renderer type': 'instanceof GeoEntityRenderer<?>' in event_source,
    'phase-two geometry has valid Bedrock geometry wrapper': bool(geo.get('minecraft:geometry')),
}
for label, passed in checks.items():
    print(('PASS' if passed else 'FAIL') + ': ' + label)
print(f'Native Mahito phase-two renderer adapter audit: {sum(checks.values())}/{len(checks)} passed')
raise SystemExit(0 if all(checks.values()) else 1)
