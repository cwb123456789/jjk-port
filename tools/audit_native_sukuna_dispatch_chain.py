#!/usr/bin/env python3
"""Verify the native Sukuna AI -> generic AIActive dispatch edge in the supplied v50.1 JAR.

This is a bytecode evidence audit, not a proof of runtime behavior or skill-number mapping.
"""
from pathlib import Path
import glob, shutil, subprocess, tempfile, zipfile

jars = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert jars, 'JujutsuCraft v50.1 JAR not found under /mnt/data'
source = Path(jars[0])
classes = [n for n in ('AISukunaProcedure', 'AIActiveProcedure', 'AIFlameArrowProcedure')]
with tempfile.TemporaryDirectory() as td:
    jar = Path(td) / 'native.jar'
    shutil.copyfile(source, jar)  # ASCII path avoids JVM issues with localized filenames.
    outputs = {}
    for simple in classes:
        fqcn = f'net.mcreator.jujutsucraft.procedures.{simple}'
        outputs[simple] = subprocess.run(
            ['javap', '-classpath', str(jar), '-c', '-p', fqcn],
            check=True, capture_output=True, text=True
        ).stdout
    sukuna = outputs['AISukunaProcedure']
    active = outputs['AIActiveProcedure']
    arrow = outputs['AIFlameArrowProcedure']
    assert 'AIActiveProcedure.execute:' in sukuna, 'AISukunaProcedure -> AIActiveProcedure edge missing'
    print('PASS: AISukunaProcedure invokes AIActiveProcedure.execute(LevelAccessor, x, y, z, Entity)')
    assert 'String skill' in active, 'AIActiveProcedure does not show expected skill NBT access'
    assert 'COOLDOWN_TIME' in active, 'AIActiveProcedure does not reference native cooldown effects'
    print('PASS: AIActiveProcedure reads skill NBT and references native cooldown effects')
    assert 'FlameArrowEntity' in arrow and 'String skill' in arrow and 'String cnt1' in arrow
    print('PASS: AIFlameArrowProcedure uses skill/cnt1 state and native FlameArrowEntity')
    with zipfile.ZipFile(source) as zf:
        names = [n for n in zf.namelist() if n.endswith('.class')]
        needle = b'net/mcreator/jujutsucraft/procedures/AIFlameArrowProcedure'
        callers = [n for n in names if n != 'net/mcreator/jujutsucraft/procedures/AIFlameArrowProcedure.class' and needle in zf.read(n)]
    assert 'net/mcreator/jujutsucraft/entity/FlameArrowEntity.class' in callers
    print(f'PASS: scanned {len(names)} class files; direct constant-pool callers: {", ".join(callers)}')
    print('LIMIT: this establishes a direct dispatch edge only to AIActiveProcedure, not the skill ID or conditions that enter the flame-arrow branch.')
    print('LIMIT: no runtime call was made and no custom entity was mutated.')
