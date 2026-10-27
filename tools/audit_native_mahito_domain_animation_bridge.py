#!/usr/bin/env python3
"""Static checks for native Mahito domain animation trigger."""
from pathlib import Path
import json, subprocess, zipfile
ROOT = Path(__file__).resolve().parents[1]
src = (ROOT / "src/main/java/com/jujutsuenhancement/integration/NativeMahitoFormBridge.java").read_text(encoding="utf-8")
checks = []
def check(label, ok):
    checks.append((label, bool(ok)))
check("native Mahito exact-class guard", 'MAHITO_CLASS.equals(entity.getClass().getName())' in src)
check("server-side skill bridge retained", 'entity.level().isClientSide' in src and 'DOMAIN_SKILL_ID = 1520' in src)
check("skill ID read matches native double NBT storage", 'double skill = data.getDouble(SKILL_KEY)' in src)
commands = (ROOT / "src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java").read_text(encoding="utf-8")
check("diagnostic command reads native skill as double", 'double skill = target.getPersistentData().getDouble("skill")' in commands)
check("four tick transition retained", 'FORM_DELAY_TICKS = 4' in src)
check("phase-two texture is set", 'setTexture.invoke(entity, PHASE_TWO_TEXTURE)' in src)
check("native synced animation setter is called", 'getMethod("setAnimation", String.class)' in src and 'setAnimation.invoke(entity, DOMAIN_START_ANIMATION)' in src)
check("uses native domain animation key", 'DOMAIN_START_ANIMATION = "domain_expansion1"' in src)
check("phase-two texture is reconciled after reload or external reset", 'if (!data.getBoolean(APPLIED_KEY) || ticks % 20 == 0)' in src and '!PHASE_TWO_TEXTURE.equals(currentTexture)' in src and 'setTexture.invoke(entity, PHASE_TWO_TEXTURE)' in src)
check("domain animation has a separate per-activation marker", 'DOMAIN_ANIMATION_TRIGGERED_KEY' in src and 'data.putBoolean(DOMAIN_ANIMATION_TRIGGERED_KEY, true)' in src)
check("domain animation marker resets after skill 1520 ends", src.index('data.remove(DOMAIN_ANIMATION_TRIGGERED_KEY)') < src.index('if (data.getBoolean(DOMAIN_ANIMATION_TRIGGERED_KEY))'))
def find_base_jar():
    candidates = [
        Path("/mnt/data/jujutsucraft-base.jar"),
        Path("/mnt/data/project_inputs/[墨蓝＆玄道水源汉化]JujutsuCraft-ver50.1-forge-1.20.1.jar"),
        Path("/mnt/data/[墨蓝＆玄道水源汉化]JujutsuCraft-ver50.1-forge-1.20.1.jar"),
    ]
    return next((candidate for candidate in candidates if candidate.is_file()), candidates[0])

jar = find_base_jar()
if jar.exists():
    with zipfile.ZipFile(jar) as z:
        anim = json.loads(z.read('assets/jujutsucraft/animations/human.animation.json'))
        check("native animation resource has domain_expansion1", 'domain_expansion1' in anim.get('animations', {}))
    cls = subprocess.run(['javap','-classpath',str(jar),'-p','net.mcreator.jujutsucraft.entity.MahitoEntity'], capture_output=True, text=True)
    check("native Mahito exposes setAnimation(String)", 'public void setAnimation(java.lang.String)' in cls.stdout)
else:
    check("base JAR available for native API check", False)
print('\n'.join(('PASS' if ok else 'FAIL') + ': ' + label for label, ok in checks))
print(f'Native Mahito domain animation bridge audit: {sum(ok for _, ok in checks)}/{len(checks)} passed')
raise SystemExit(0 if all(ok for _, ok in checks) else 1)
