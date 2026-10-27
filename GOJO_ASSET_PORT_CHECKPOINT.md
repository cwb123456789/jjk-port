# Gojo asset-port checkpoint

Imported from the user-provided Jujutsu Awakening v1.81 resource pack:
- `geo/student_gojo.geo.json` (11 bones, 16 cubes; UVs normalized to the 64x64 atlas)
- `textures/entity/student_gojo.png`
- `animations/gojo_high.animation.json` (2.5 seconds; 6 animated bones)

The animation converter flattened Bedrock Catmull-Rom interpolation to linear keyframes (58 warnings). This is an approximate conversion, not frame-identical playback. Static checks confirm the six animated bone names exist in the Gojo geometry.

**Not yet wired to gameplay:** no Gojo entity, renderer/model class, or Hollow Purple skill trigger is registered in this checkpoint. Do not treat these imported assets as a playable Gojo implementation. Runtime rendering and Gradle compilation remain unverified because the Gradle distribution could not be downloaded in the current environment.
