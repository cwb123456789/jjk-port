# Sukuna attack spawn atomicity fix

The temporary Heian Sukuna interaction used to start the animation and increment the five-clip sequence before the server confirmed that any projectile had been added. If all projectile spawns failed, the player could consume an animation state without a successful attack.

The animation trigger, sequence increment, and cooldown now occur only inside the `spawned` success branch. If all spawns fail, the normal entity interaction is preserved and the sequence/cooldown are not consumed.

This is a reliability fix for the existing placeholder attack only. It does not implement the native JujutsuCraft Furnace (`OpenProcedure`) skill. Native skill dispatch still requires confirming the player's technique state and the surrounding tick/input path before integration.
