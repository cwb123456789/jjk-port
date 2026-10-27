# Testing the Mahito form/domain prototype

This step adds a direct in-game spawn command for the existing custom prototype entity.
It is intended to make the current 0.2-second form switch and domain visual sequence testable;
it does **not** replace the native JujutsuCraft `MahitoEntity` yet.

In a world with cheats enabled, run:

```text
/jujutsuenhancement mahito_prototype
```

The prototype appears in front of the player. Sneak + right-click triggers the prototype's
transformation/domain sequence. After the short switch delay, the prototype uses the imported
stage-two texture/animation path. Right-click and sneak + right-click after the transformation
exercise the prototype's two skill test paths.

This command is only a test harness. The next implementation step is to connect the Bedrock-derived
form/animation and skill behavior to the native `MahitoEntity` dispatch and renderer path, while
preserving the original NPC's combat/AI compatibility.
