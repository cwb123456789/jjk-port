# Native domain clash edge-trigger fix — r54

## Change
The native domain clash bridge previously used a 60-tick cooldown, which meant two entities holding their domain skill IDs could emit another clash cue every three seconds. This did not represent a new clash; it was a repeated cue for the same overlap.

The bridge now remembers the last observed active tick for each UUID pair and emits the cue only when the pair first becomes active, or after a gap longer than 10 ticks. Pair state older than 200 ticks is cleaned up. This also allows a later clash between the same two entities to trigger again after their prior overlap ends.

## Scope and limitations
- Server-side detection only.
- Native domain IDs remain Mahito 1520, Sukuna 120, and Gojo 220.
- The change affects only the visual/audio cue frequency.
- It does **not** implement domain winner resolution, barrier cancellation, domain damage, Yuji's domain, or Higuruma's domain.
- Java compilation and in-game validation have not been performed in this environment.
