#!/usr/bin/env python3
"""Static checks for the native Mahito animation resource compatibility findings."""
from pathlib import Path
import json, zipfile, subprocess
ROOT = Path(__file__).resolve().parents[1]
checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

source = (ROOT / "src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java").read_text(encoding="utf-8")
check("read-only native Mahito diagnostic command registered", 'Commands.literal("mahito_native_status")' in source)
check("diagnostic checks exact native Mahito class", 'net.mcreator.jujutsucraft.entity.MahitoEntity' in source)
check("diagnostic reports texture and synced animation", 'getTexture' in source and 'getSyncedAnimation' in source)
check("diagnostic reports bridge applied state", 'jujutsuenhancement_native_mahito_phase2_applied' in source)

bedrock_path = ROOT / "src/main/resources/assets/jujutsuenhancement/animations/mahito_compatible.animation.json"
with bedrock_path.open(encoding="utf-8") as f:
    bedrock = json.load(f)
keys = set(bedrock.get("animations", {}))
check("Bedrock-derived stage2 animation exists", "animation.mahito.stage2" in keys)
check("Bedrock-derived domain-start animation exists", "animation.mahito.domain_start" in keys)

def find_base_jar():
    candidates = [
        Path("/mnt/data/jujutsucraft-base.jar"),
        Path("/mnt/data/project_inputs/[墨蓝＆玄道水源汉化]JujutsuCraft-ver50.1-forge-1.20.1.jar"),
        Path("/mnt/data/[墨蓝＆玄道水源汉化]JujutsuCraft-ver50.1-forge-1.20.1.jar"),
    ]
    return next((candidate for candidate in candidates if candidate.is_file()), candidates[0])

jar = find_base_jar()
if jar.is_file():
    with zipfile.ZipFile(jar) as z:
        native = json.loads(z.read("assets/jujutsucraft/animations/human.animation.json"))
        native_keys = set(native.get("animations", {}))
        check("native animation file includes domain_expansion1", "domain_expansion1" in native_keys)
        check("native animation file does not contain Bedrock stage2 identifier", "animation.mahito.stage2" not in native_keys)
        model = subprocess.run(["javap", "-classpath", str(jar), "-c", "-p", "net.mcreator.jujutsucraft.entity.model.MahitoModel"], capture_output=True, text=True)
        check("native model bytecode points to human.geo.json", "geo/human.geo.json" in model.stdout)
        check("native model bytecode points to human.animation.json", "animations/human.animation.json" in model.stdout)
else:
    check("supplied base JAR available for compatibility audit", False)

for name, passed in checks:
    print(("PASS" if passed else "FAIL") + ": " + name)
print(f"Native Mahito animation compatibility audit: {sum(ok for _, ok in checks)}/{len(checks)} passed")
raise SystemExit(0 if all(ok for _, ok in checks) else 1)
