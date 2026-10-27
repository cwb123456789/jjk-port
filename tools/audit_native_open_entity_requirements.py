#!/usr/bin/env python3
"""Check whether the custom Heian Sukuna entity satisfies OpenProcedure's observed entity requirements.

Static bytecode audit only; it does not invoke the JujutsuCraft procedure.
"""
from pathlib import Path
import glob, shutil, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java'
assert source.exists(), f'Missing source: {source}'
java = source.read_text(encoding='utf-8')
assert 'extends Entity implements GeoEntity' in java, 'Expected current custom Sukuna base class changed; re-audit manually'

jars = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert jars, 'JujutsuCraft v50.1 JAR not found under /mnt/data'
with tempfile.TemporaryDirectory() as td:
    jar = Path(td) / 'jujutsucraft.jar'
    shutil.copyfile(jars[0], jar)  # avoid localized filename issues with JVM tools
    p = subprocess.run(['javap', '-classpath', str(jar), '-c', '-p',
                        'net.mcreator.jujutsucraft.procedures.OpenProcedure'],
                       check=True, capture_output=True, text=True)
    bytecode = p.stdout

assert 'instanceof    #33                 // class net/minecraft/world/entity/LivingEntity' in bytecode
assert 'instanceof    #78                 // class net/minecraft/world/entity/Mob' in bytecode
assert 'String cnt1' in bytecode
assert 'String COOLDOWN_TICKS' in bytecode
assert 'JujutsucraftModMobEffects.COOLDOWN_TIME' in bytecode
assert 'PlayAnimationProcedure.execute' in bytecode
assert 'EntityType' in bytecode and 'SetRangedAmmoProcedure.execute' in bytecode
print('PASS: OpenProcedure bytecode requires/branches on LivingEntity and Mob target capability')
print('PASS: OpenProcedure reads cnt1 and COOLDOWN_TICKS, applies native cooldown effect, animation, and ranged-ammo setup')
print('PASS: custom HeianSukunaEntity currently extends Entity, not LivingEntity/Mob')
print('BLOCKER: direct OpenProcedure invocation on this custom entity is not a safe implementation of native 灶')
print('LIMIT: this is static bytecode evidence; no game runtime behavior was executed')
