#!/usr/bin/env python3
"""Run the project's targeted source/resource regression audits sequentially.

This is a static validation harness. It does not compile Java or launch Minecraft.
Run from any working directory with Python 3:
    python3 tools/run_regression_audits.py
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
AUDITS = [
    "tools/audit_sukuna_attack_spawn_atomicity.py",
    "tools/audit_sukuna_interaction_consumption.py",
    "tools/audit_sukuna_interaction_server_flow.py",
    "tools/audit_sukuna_fuga_charge_flow.py",
    "tools/audit_sukuna_fuga_owner_lifecycle_r65.py",
    "tools/audit_sukuna_fuga_damage_attribution_r60.py",
    "tools/audit_sukuna_fuga_splash_damage_r61.py",
    "tools/audit_sukuna_fuga_impact_feedback_r62.py",
    "tools/audit_sukuna_fuga_blast_occlusion_r63.py",
    "tools/audit_sukuna_entity_owned_cooldowns.py",
    "tools/audit_sukuna_persistent_state.py",
    "tools/audit_sukuna_animation_trigger.py",
    "tools/audit_purple_charge_session_lifecycle.py",
    "tools/audit_mahito_domain_visual_recovery.py",
    "tools/audit_mahito_stale_visual_reload_cleanup.py",
    "tools/audit_mahito_domain_chunk_lifecycle.py",
    "tools/audit_mahito_domain_test_commands.py",
    "tools/audit_mahito_prototype_spawn_command.py",
    "tools/audit_native_sukuna_open_test_command.py",
    "tools/audit_native_sukuna_open_test_cooldown.py",
    "tools/audit_native_sukuna_open_test_tick_driver.py",
    "tools/audit_native_sukuna_open_test_tick_phase.py",
    "tools/audit_native_domain_clash_bridge.py",
    "tools/audit_native_domain_clash_yuji_higuruma_r55.py",
    "tools/audit_native_domain_clash_sequence_r56.py",
    "tools/audit_gojo_low_health_crouch_purple_r57.py",
    "tools/audit_hollow_purple_block_impact_r58.py",
    "tools/audit_native_mahito_form_bridge.py",
    "tools/audit_native_mahito_animation_compatibility.py",
    "tools/audit_native_mahito_geometry_compatibility.py",
    "tools/audit_native_mahito_stage2_bone_mapping.py",
    "tools/audit_native_mahito_phase2_renderer_adapter_r66.py",
    "tools/audit_native_mahito_renderer_field_safety_r67.py",
    "tools/audit_native_mahito_renderer_bytecode_findings_r68.py",
    "tools/audit_native_mahito_domain_animation_bridge.py",
    "tools/audit_native_mahito_texture_reload_reconcile_r59.py",
    "tools/validate_resources.py",
]


def main() -> int:
    results = []
    for relative in AUDITS:
        script = ROOT / relative
        print(f"\n===== {relative} =====", flush=True)
        if not script.is_file():
            print("FAIL: audit script missing", flush=True)
            results.append((relative, 127))
            continue
        proc = subprocess.run([sys.executable, str(script)], cwd=ROOT)
        results.append((relative, proc.returncode))
    print("\n===== REGRESSION SUMMARY =====")
    for name, code in results:
        print(f"{'PASS' if code == 0 else 'FAIL'} (exit {code}): {name}")
    passed = sum(code == 0 for _, code in results)
    print(f"Static audits: {passed}/{len(results)} passed")
    print("Note: this does not replace Gradle compilation or an in-game test.")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
