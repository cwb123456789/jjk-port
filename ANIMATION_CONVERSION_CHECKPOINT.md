# Animation conversion checkpoint

## Completed in this step
- Converted `animation.malevolent_shrine.form` from the provided Bedrock resource pack into `assets/jujutsuenhancement/animations/malevolent_shrine.animation.json`.
- Connected the converted animation to the shrine model and added a GeckoLib loop controller to `DomainShrineEntity`.
- Verified JSON syntax and bone-name compatibility against the current 126-bone shrine geometry: 21 animated bone names match, with no missing bone names.
- Added `tools/convert_bedrock_animation.py` for repeatable first-pass conversion of vector keyframes.

## Known limitations
- The animation covers 21 of 126 geometry bones; 105 bones are not animated by this source animation.
- Bedrock Catmull-Rom keyframe objects are flattened to one vector value. Motion curves may look different and need visual tuning.
- Bedrock Molang, animation controllers, particle/sound events, and Script API behavior are not converted by the script.
- Build and Minecraft client render test remain blocked/unverified until Gradle 8.8 and dependencies can be resolved.
