# r40 change log

## Native Mahito integration
- Added `src/main/java/com/jujutsuenhancement/integration/NativeMahitoFormBridge.java`.
- Native JujutsuCraft Mahito (`MahitoEntity`) skill `1520` now triggers a four-server-tick delay before selecting the existing native phase-two texture identifier `mahito_phase2__body`.
- Original texture is saved and restored after the native skill leaves `1520`.
- Added `tools/audit_native_mahito_form_bridge.py` and included it in `tools/run_regression_audits.py`.
- Added `NATIVE_MAHITO_FORM_BRIDGE_2026-10-11-r40.md` with behavior and limitations.

## Verification
- Native Mahito bridge static audit: 8/8 passed.
- Complete static audit suite: 15/15 scripts passed.
- Resource validation passed.
- Gradle Java compilation and in-game testing remain unverified because ForgeGradle cannot be resolved in offline mode.
