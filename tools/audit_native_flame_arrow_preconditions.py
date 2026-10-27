#!/usr/bin/env python3
"""Audit the exact state gates used by JujutsuCraft v50.1's AIFlameArrowProcedure."""
from pathlib import Path
import glob, shutil, subprocess, tempfile, zipfile

matches = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert matches, 'JujutsuCraft v50.1 JAR not found'
source = Path(matches[0])
class_name = 'net/mcreator/jujutsucraft/procedures/AIFlameArrowProcedure.class'
with zipfile.ZipFile(source) as zf:
    raw = zf.read(class_name)
    assert b'OWNER_UUID' in raw and b'NameRanged_ranged' in raw and b'NameRanged' in raw
    assert b'skill' in raw and b'cnt1' in raw and b'x_power' in raw and b'y_power' in raw
    assert b'FlameArrowEntity' in raw
with tempfile.TemporaryDirectory() as td:
    jar = Path(td) / 'jc.jar'
    shutil.copyfile(source, jar)
    out = subprocess.run(['javap', '-classpath', str(jar), '-c', '-p',
        'net.mcreator.jujutsucraft.procedures.AIFlameArrowProcedure'],
        check=True, capture_output=True, text=True).stdout
    required = ['String OWNER_UUID', 'String NameRanged_ranged', 'String NameRanged',
                'String skill', 'String cnt1', 'double 30.0d', 'String x_power', 'String y_power']
    missing = [s for s in required if s not in out]
    assert not missing, f'Missing expected bytecode gates: {missing}'
print('PASS: AIFlameArrowProcedure bytecode contains owner UUID lookup, ranged-skill identity match, positive skill gate, cnt1 <= 30 gate, and power propagation.')
print('SAFETY: this is only the projectile/AI update procedure; it does not identify the native skill-selection number or prove a safe external activation sequence. Do not call it as a standalone skill trigger.')
