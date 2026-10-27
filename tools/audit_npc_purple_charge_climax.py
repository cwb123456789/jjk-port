from pathlib import Path

source = Path(__file__).resolve().parents[1] / 'src/main/java/com/jujutsuenhancement/integration/PrototypeCommands.java'
text = source.read_text(encoding='utf-8')
checks = {
    'charge progress is computed once': 'long chargeElapsed = now - chargeStart;' in text,
    'final second begins at 80 of 100 ticks': 'chargeElapsed >= CHARGE_TICKS - 20L' in text,
    'climax particles are server-side': 'level.sendParticles(ParticleTypes.ELECTRIC_SPARK' in text,
    'climax effect is rate-limited to every two ticks': 'gojo.tickCount % 2 == 0' in text,
    'charge still waits for full five seconds': 'if (chargeElapsed < CHARGE_TICKS)' in text,
    'charged projectile is still explicitly marked': 'purple.setCharged200Percent();' in text,
}
for label, passed in checks.items():
    print(('PASS' if passed else 'FAIL') + ': ' + label)
print(f'{sum(checks.values())}/{len(checks)} checks passed')
raise SystemExit(0 if all(checks.values()) else 1)
