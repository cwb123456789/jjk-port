#!/usr/bin/env python3
"""Fail if common/server entity and registry classes import client-only APIs."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SCOPE = [ROOT / 'src/main/java/com/jujutsuenhancement/entity',
         ROOT / 'src/main/java/com/jujutsuenhancement/registry']
FORBIDDEN = re.compile(r'^import\s+(?:net\.minecraft\.client|com\.jujutsuenhancement\.client)(?:\.|;)')
errors = []
checked = 0
for folder in SCOPE:
    for source in sorted(folder.rglob('*.java')):
        checked += 1
        for line_no, line in enumerate(source.read_text(encoding='utf-8').splitlines(), 1):
            if FORBIDDEN.search(line.strip()):
                errors.append(f'{source.relative_to(ROOT)}:{line_no}: client-only import in common/server code: {line.strip()}')
if errors:
    print('\n'.join(errors))
    print(f'FAILED: {len(errors)} client-only import(s) in {checked} Java file(s).')
    sys.exit(1)
print(f'PASS: {checked} entity/registry Java files checked; no client-only imports.')
