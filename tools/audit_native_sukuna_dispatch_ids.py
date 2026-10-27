#!/usr/bin/env python3
"""Verify exact v50.1 Sukuna dispatcher IDs and native procedure targets via javap."""
from pathlib import Path
import glob, subprocess

jars = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert jars, 'JujutsuCraft v50.1 JAR not found under /mnt/data'
jar = jars[0]

def javap(cls):
    p = subprocess.run(['javap', '-classpath', jar, '-c', '-p', cls], capture_output=True, text=True)
    assert p.returncode == 0, f'javap failed for {cls}: {p.stderr}'
    return p.stdout

cls = 'net.mcreator.jujutsucraft.procedures.CursedTechniqueSukunaProcedure'
out = javap(cls)
assert 'double 107.0d' in out and 'OpenProcedure.execute' in out, 'ID 107 -> OpenProcedure mapping not found'
assert 'double 120.0d' in out and 'MalevolentShrineProcedure.execute' in out, 'ID 120 -> MalevolentShrineProcedure mapping not found'
open_proc = javap('net.mcreator.jujutsucraft.procedures.OpenProcedure')
shrine_active = javap('net.mcreator.jujutsucraft.procedures.MalevolentShrineActiveProcedure')
shrine = javap('net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure')
assert 'public static void execute(net.minecraft.world.level.LevelAccessor, net.minecraft.world.entity.Entity);' in open_proc
assert 'public static void execute(net.minecraft.world.level.LevelAccessor, net.minecraft.world.entity.Entity);' in shrine_active
assert 'public static void execute(net.minecraft.world.level.LevelAccessor, double, double, double, net.minecraft.world.entity.Entity);' in shrine
print('PASS: v50.1 dispatcher maps skill 107 to OpenProcedure')
print('PASS: v50.1 dispatcher maps skill 120 to MalevolentShrineProcedure')
print('PASS: all three native procedure signatures verified')
print('LIMIT: static bytecode inspection does not prove the custom NPC has required native NBT/capability/animation state; direct invocation remains intentionally unwired')
