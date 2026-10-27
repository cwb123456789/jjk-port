# Sukuna Fuga ready-state timeout

## Change

- After the 55-tick charge completes, the release state is available for 100 ticks (5 seconds).
- If no valid release happens in that window, the entity clears the charge owner and resets the transient animation state.
- This prevents a player who abandons the interaction from leaving the custom Sukuna entity permanently locked in the ready state.
- Successful release and explicit cancellation still clear the ready timer immediately.
- The ready timer is transient; loading a saved entity resets the visual/charge state as before.

## Verification

Run `python3 tools/audit_sukuna_fuga_charge_flow.py` to check the charge, ownership, timeout, animation, and resource contracts. This is a static source audit, not a Java compilation or in-game test.
