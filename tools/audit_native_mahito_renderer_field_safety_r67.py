#!/usr/bin/env python3
"""Static safety checks for the r67 reflective Mahito renderer adapter."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
s = (ROOT / 'src/main/java/com/jujutsuenhancement/client/ClientModEvents.java').read_text(encoding='utf-8')
checks = {
    'reflection filters model fields by GeoModel type': 'GeoModel.class.isAssignableFrom(field.getType())' in s,
    'static model fields are excluded': '!Modifier.isStatic(modifiers)' in s,
    'final model fields are excluded': '!Modifier.isFinal(modifiers)' in s,
    'existing field value is checked before replacement': 'Object previous = field.get(geoRenderer)' in s and 'field.getType().isInstance(previous)' in s,
    'failed attachment retains original model': 'retaining original renderer model' in s,
    'successful attachment logs the selected field': 'field {}' in s,
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
print(f'Mahito renderer reflection safety audit: {sum(checks.values())}/{len(checks)} passed')
raise SystemExit(0 if all(checks.values()) else 1)
