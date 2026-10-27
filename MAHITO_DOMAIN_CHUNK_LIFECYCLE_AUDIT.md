# Mahito domain visual chunk lifecycle audit

## Scope

This audit covers source-level lifecycle invariants for the prototype domain's hand and barrier effect entities. It does not simulate Forge chunk loading or prove runtime behavior.

## Invariants checked

- Removing the prototype for chunk unload does not deliberately discard the visual entities.
- Removing the prototype for actual cancellation/death still requests cleanup of the two known visual entity types.
- The prototype persists each visual UUID so it can reconnect cleanup after reload.
- Each visual entity persists its own age, clamps restored age to the legal lifetime range, and expires on the server at 60 ticks.
- Domain completion clears the tracked UUID fields after requesting cleanup.

## Runtime caveat

The audit cannot guarantee the ordering of entity attachment to `ServerLevel` after a chunk reload, nor can it test whether Forge serializes/reloads all entities in the expected order. A Java build and in-game test with chunk unload/reload are still required.
