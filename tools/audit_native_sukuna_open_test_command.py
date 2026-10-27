#!/usr/bin/env python3
"""Static audit for the guarded native Sukuna skill-dispatch test command."""
from pathlib import Path
import glob, shutil, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java').read_text()
assert 'sukuna_native_open_test' in source
print('PASS: command is registered')
assert 'getEntitiesOfClass(Mob.class' in source and 'entity.getTarget() != null' in source and 'entity.getTarget().isAlive()' in source
print('PASS: command only chooses native Mob entities already targeting a living entity')
for name in ('SukunaEntity', 'SukunaFushiguroEntity', 'SukunaPerfectEntity'):
    assert f'net.mcreator.jujutsucraft.entity.{name}' in source
print('PASS: native entity class allowlist is explicit')
assert 'net.mcreator.jujutsucraft.procedures.CursedTechniqueSukunaProcedure' in source
assert 'putDouble("skill", 107.0D)' in source
assert 'double.class, double.class, double.class, Entity.class' in source
assert 'target.getPersistentData().putDouble("cnt1", 0.0D)' in source
assert 'target.getPersistentData().putBoolean(NATIVE_SUKUNA_OPEN_TEST_RUNNING, true)' in source
assert 'execute.invoke(null, living.level(), living.getX(), living.getY(), living.getZ(), living)' in source
print('PASS: command schedules skill 107 and resets its native tick counter')
assert 'NPC AI 自动选技仍未接入' in source
print('PASS: command output states this is manual testing, not automatic AI integration')

jars = (glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
        + glob.glob('/mnt/data/project_inputs/*JujutsuCraft-ver50.1-forge-1.20.1.jar'))
assert jars, 'JujutsuCraft v50.1 JAR not found'
with tempfile.TemporaryDirectory() as td:
    jar = Path(td) / 'native.jar'
    shutil.copyfile(jars[0], jar)
    out = subprocess.run(['javap', '-classpath', str(jar), '-p', 'net.mcreator.jujutsucraft.procedures.CursedTechniqueSukunaProcedure'],
                         check=True, capture_output=True, text=True).stdout
    assert 'execute(net.minecraft.world.level.LevelAccessor, double, double, double, net.minecraft.world.entity.Entity)' in out
    bytecode = subprocess.run(['javap', '-classpath', str(jar), '-c', 'net.mcreator.jujutsucraft.procedures.CursedTechniqueSukunaProcedure'],
                              check=True, capture_output=True, text=True).stdout
    assert 'OpenProcedure.execute' in bytecode and 'double 107.0d' in bytecode
print('PASS: supplied JujutsuCraft v50.1 JAR confirms dispatcher signature and skill 107 routes to OpenProcedure')
print('LIMIT: static verification only; this does not prove runtime effects, animation, or successful projectile spawn in game')
