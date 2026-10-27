#!/usr/bin/env python3
"""Audit GeoModel resource paths against files packaged in this Forge project."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'src/main/java/com/jujutsuenhancement/client/model'
ASSETS=ROOT/'src/main/resources/assets/jujutsuenhancement'
errors=[]; rows=[]
for jf in sorted(JAVA.glob('*Model.java')):
    src=jf.read_text(encoding='utf-8')
    vals={k:v for k,v in re.findall(r'(MODEL|TEXTURE|ANIMATION)\s*=\s*new ResourceLocation\(\s*JujutsuEnhancement\.MODID,\s*"([^"]+)"\s*\)',src, re.S)}
    for key,rel in vals.items():
        path=ASSETS/rel
        ok=path.is_file()
        rows.append((jf.name,key,rel,ok))
        if not ok: errors.append(f'{jf.name}: {key} points to missing {rel}')
    if len(vals)!=3: errors.append(f'{jf.name}: expected MODEL/TEXTURE/ANIMATION constants, found {sorted(vals)}')
for name,key,rel,ok in rows:
    print(('OK   ' if ok else 'MISS ')+f'{name}: {key} -> {rel}')
print(f'checked resource references: {len(rows)}; errors: {len(errors)}')
for e in errors: print('ERROR',e)
sys.exit(bool(errors))
