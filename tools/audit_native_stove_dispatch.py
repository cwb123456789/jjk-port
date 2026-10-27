#!/usr/bin/env python3
"""Trace direct class-constant references to JujutsuCraft's native flame-arrow procedure."""
from pathlib import Path
import glob, zipfile, subprocess, tempfile, shutil

jars = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert jars, 'JujutsuCraft v50.1 JAR not found under /mnt/data'
source = Path(jars[0])
needle = b'net/mcreator/jujutsucraft/procedures/AIFlameArrowProcedure'
with zipfile.ZipFile(source) as zf:
    class_names = [n for n in zf.namelist() if n.endswith('.class')]
    callers = []
    for name in class_names:
        if name.endswith('/AIFlameArrowProcedure.class'):
            continue
        if needle in zf.read(name):
            callers.append(name)
assert 'net/mcreator/jujutsucraft/entity/FlameArrowEntity.class' in callers, 'Expected FlameArrowEntity reference missing'
print(f'PASS: scanned {len(class_names)} class files for direct procedure class references')
print('Direct constant-pool references:')
for name in callers:
    print(f'  - {name}')
print('LIMIT: class-constant scanning cannot discover reflective dispatch or generic procedure routing; absence of other references is not proof no caller exists.')

with tempfile.TemporaryDirectory() as td:
    jar = Path(td) / 'native.jar'
    shutil.copyfile(source, jar)
    out = subprocess.run(['javap', '-classpath', str(jar), '-c', '-p', 'net.mcreator.jujutsucraft.procedures.AIFlameArrowProcedure'], check=True, capture_output=True, text=True).stdout
    with zipfile.ZipFile(jar) as zf:
        proc_bytes = zf.read('net/mcreator/jujutsucraft/procedures/AIFlameArrowProcedure.class')
    assert b'cnt1' in proc_bytes and b'skill' in proc_bytes
    assert b'FlameArrowEntity' in proc_bytes
print('PASS: native flame-arrow procedure contains skill/cnt1 state and FlameArrowEntity references')
