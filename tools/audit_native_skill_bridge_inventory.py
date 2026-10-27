#!/usr/bin/env python3
"""Verify the local JujutsuCraft JAR contains the native classes used for bridge research."""
from pathlib import Path
import glob
import zipfile

jars = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert jars, 'JujutsuCraft 1.20.1 JAR not found under /mnt/data'
jar = Path(jars[0])
with zipfile.ZipFile(jar) as zf:
    names = set(zf.namelist())
checks = {
    'Gojo NPC class': 'net/mcreator/jujutsucraft/entity/GojoSatoruEntity.class',
    'Sukuna-possessed Megumi NPC class': 'net/mcreator/jujutsucraft/entity/SukunaFushiguroEntity.class',
    'Malevolent Shrine active procedure': 'net/mcreator/jujutsucraft/procedures/MalevolentShrineActiveProcedure.class',
    'Malevolent Shrine procedure': 'net/mcreator/jujutsucraft/procedures/MalevolentShrineProcedure.class',
    'Malevolent Shrine entity': 'net/mcreator/jujutsucraft/entity/EntityMalevolentShrineEntity.class',
    'Alternate Malevolent Shrine entity': 'net/mcreator/jujutsucraft/entity/EntityMalevolentShrine2Entity.class',
}
for label, entry in checks.items():
    ok = entry in names
    print(('PASS' if ok else 'FAIL') + ': ' + label)
assert all(entry in names for entry in checks.values()), 'One or more expected native classes are absent'
print(f'PASS: inspected {jar.name} ({jar.stat().st_size} bytes)')
