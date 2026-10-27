# Native Mahito repeated-domain animation fix — r44

## Change

The native Mahito bridge previously used the persistent phase-two transformation marker as an early-return condition. After the first successful transformation, later activations of native skill `1520` therefore could not replay the domain-start animation.

The bridge now tracks two separate states:

- `jujutsuenhancement_native_mahito_phase2_applied`: persistent transformation/texture state.
- `jujutsuenhancement_native_mahito_domain_animation_triggered`: one activation of the native domain-start clip.

When skill `1520` ends, only the per-activation animation marker and pending tick counter are cleared. The phase-two texture remains applied. On a later activation, the four-tick delay is observed again and the native `domain_expansion1` animation is retriggered without redundantly changing the texture.

## Validation

- `audit_native_mahito_form_bridge.py`: 10/10 checks passed.
- `audit_native_mahito_domain_animation_bridge.py`: 11/11 checks passed.
- `audit_native_mahito_animation_compatibility.py`: 10/10 checks passed.
- `run_regression_audits.py`: 17/17 static audit scripts passed.
- Resource/JSON validation passed.
- Gradle compilation and in-game validation have not been completed.

## Limitations

This is a repeated-animation lifecycle fix for the original JujutsuCraft Mahito entity. It does not replace the native skill dispatcher, change the native model skeleton, or yet attach the Bedrock-derived stage-two animation to the native renderer.
