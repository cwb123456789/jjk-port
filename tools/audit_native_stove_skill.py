#!/usr/bin/env python3
"""Audit JujutsuCraft v50.1's native Sukuna flame-arrow procedure surface."""
from pathlib import Path
import glob, shutil, subprocess, tempfile

jars = glob.glob('/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar')
assert jars, 'JujutsuCraft v50.1 JAR not found under /mnt/data'
source = Path(jars[0])
classes = {
    'AISukunaProcedure': 'net.mcreator.jujutsucraft.procedures.AISukunaProcedure',
    'AIFlameArrowProcedure': 'net.mcreator.jujutsucraft.procedures.AIFlameArrowProcedure',
    'SelectSukunaProcedure': 'net.mcreator.jujutsucraft.procedures.SelectSukunaProcedure',
    'CursedTechniqueSukunaProcedure': 'net.mcreator.jujutsucraft.procedures.CursedTechniqueSukunaProcedure',
}
with tempfile.TemporaryDirectory() as td:
    jar = Path(td) / 'native.jar'
    shutil.copyfile(source, jar)
    outputs = {}
    for key, cls in classes.items():
        outputs[key] = subprocess.run(['javap','-classpath',str(jar),'-c','-p',cls], check=True, capture_output=True, text=True).stdout
    for key in classes:
        assert 'public static void execute(' in outputs[key], f'{key}: execute method missing'
        print(f'PASS: {key} has inspectable execute method')
    ai = outputs['AISukunaProcedure']
    arrow = outputs['AIFlameArrowProcedure']
    selector = outputs['SelectSukunaProcedure']
    cursed = outputs['CursedTechniqueSukunaProcedure']
    assert 'String skill' in ai and 'COOLDOWN_TIME' in ai
    print('PASS: native Sukuna AI reads skill state and checks native cooldown effects')
    assert 'FlameArrowEntity' in arrow and 'String cnt1' in arrow and 'String skill' in arrow
    print('PASS: native flame-arrow AI uses the dedicated FlameArrow entity and cnt1/skill state')
    assert 'jujutsucraft:skill_sukuna' in selector and 'SelectedProcedure.execute' in selector
    print('PASS: SelectSukunaProcedure performs player advancement/selection setup')
    assert all(f'SkillSukunaCombo{i}Procedure.execute' in cursed for i in (1,2,3))
    print('PASS: cursed-technique procedure dispatches native Sukuna combo procedures')
    print('LIMIT: no safe evidence here that direct invocation alone reproduces the full stove activation, targeting, animation, cooldown, and entity state. Do not wire direct calls into the custom entity yet.')
