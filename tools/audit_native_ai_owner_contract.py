#!/usr/bin/env python3
"""Audit the native Sukuna AI owner contract against the supplied JujutsuCraft JAR."""
from pathlib import Path
import glob
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JARS = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert JARS, 'JujutsuCraft v50.1 JAR not found in /mnt/data'
JAR = JARS[0]

def javap(class_name: str) -> str:
    return subprocess.run(
        ['javap', '-classpath', JAR, '-c', '-p', class_name],
        check=True, capture_output=True, text=True
    ).stdout

sukuna_ai = javap('net.mcreator.jujutsucraft.procedures.AISukunaProcedure')
active_ai = javap('net.mcreator.jujutsucraft.procedures.AIActiveProcedure')
entity_source = ROOT / 'src/main/java/com/jujutsuenhancement/entity/HeianSukunaEntity.java'
source = entity_source.read_text(encoding='utf-8')

assert 'AIActiveProcedure.execute' in sukuna_ai, 'AISukunaProcedure no longer calls AIActiveProcedure'
assert 'instanceof    #21                 // class net/minecraft/world/entity/LivingEntity' in active_ai, \
    'AIActiveProcedure LivingEntity gate not found; review bytecode manually'
assert 'instanceof    #39                 // class net/minecraft/world/entity/Mob' in active_ai, \
    'AIActiveProcedure Mob target branch not found; review bytecode manually'
assert 'class HeianSukunaEntity extends Entity implements GeoEntity' in source, \
    'Custom entity superclass changed; update this contract audit'
print('PASS: AISukunaProcedure delegates to AIActiveProcedure.')
print('PASS: AIActiveProcedure rejects non-LivingEntity owners and only reads Mob targets through the Mob branch.')
print('PASS: HeianSukunaEntity currently extends base Entity, so it cannot enter this native AI route.')
print('LIMIT: static bytecode/source audit only; no Java compilation or game launch was performed.')
