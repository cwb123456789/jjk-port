#!/usr/bin/env python3
"""Verify native JujutsuCraft shrine procedure signatures and activation preconditions."""
from pathlib import Path
import glob
import shutil
import subprocess
import tempfile

jars = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert jars, 'JujutsuCraft v50.1 JAR not found under /mnt/data'
source_jar = Path(jars[0])

# Java tools on some Windows-oriented launch setups cannot decode the original
# Chinese filename. Copy to an ASCII path before invoking javap.
with tempfile.TemporaryDirectory() as tmp:
    jar = Path(tmp) / 'jujutsucraft.jar'
    shutil.copyfile(source_jar, jar)
    cases = {
        'MalevolentShrineActiveProcedure': (
            'net.mcreator.jujutsucraft.procedures.MalevolentShrineActiveProcedure',
            'execute(net.minecraft.world.level.LevelAccessor, net.minecraft.world.entity.Entity)',
        ),
        'MalevolentShrineProcedure': (
            'net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure',
            'execute(net.minecraft.world.level.LevelAccessor, double, double, double, net.minecraft.world.entity.Entity)',
        ),
    }
    outputs = {}
    for label, (class_name, signature) in cases.items():
        result = subprocess.run(
            ['javap', '-classpath', str(jar), '-p', class_name],
            check=True, capture_output=True, text=True,
        )
        outputs[label] = result.stdout
        assert signature in result.stdout, f'{label}: expected signature not found'
        print(f'PASS: {label} exposes {signature}')

    active = subprocess.run(
        ['javap', '-classpath', str(jar), '-c', '-p', cases['MalevolentShrineActiveProcedure'][0]],
        check=True, capture_output=True, text=True,
    ).stdout
    shrine = subprocess.run(
        ['javap', '-classpath', str(jar), '-c', '-p', cases['MalevolentShrineProcedure'][0]],
        check=True, capture_output=True, text=True,
    ).stdout
    assert 'String skill' in active and 'String COOLDOWN_TICKS' in active
    assert 'DomainExpansionCreateBarrierProcedure.execute' in shrine
    assert 'String cnt1' in shrine and 'String select' in shrine
    print('PASS: bytecode references skill/cooldown state in active procedure')
    print('PASS: shrine procedure initializes select and invokes barrier creation')
    print('NOTE: signatures are verified; calling either procedure directly is not yet validated as a complete player/NPC activation.')
