#!/usr/bin/env python3
"""Check that RawAnimation names referenced by GeoEntity controllers exist in JSON resources."""
import json, re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java'
ASSETS = ROOT / 'src/main/resources/assets/jujutsuenhancement'
errors=[]; checked=0
for source in JAVA.rglob('*.java'):
    text=source.read_text(encoding='utf-8')
    for anim in re.findall(r'then(?:Loop|PlayOnce)\("([^"]+)"\)', text):
        checked += 1
        matches=[]
        for f in (ASSETS/'animations').glob('*.json'):
            try: data=json.loads(f.read_text(encoding='utf-8'))
            except Exception as e:
                errors.append(f'{f.relative_to(ROOT)} invalid JSON: {e}'); continue
            if anim in data.get('animations', {}): matches.append(f.name)
        if not matches:
            errors.append(f'{source.relative_to(ROOT)} references missing animation: {anim}')
        else:
            print(f'OK {anim} -> {", ".join(matches)}')
print(f'Animation controller references checked: {checked}')
if errors:
    print('FAIL:')
    for e in errors: print(' -',e)
    sys.exit(1)
print('PASS: every hard-coded RawAnimation name exists in an animation JSON.')
