# Mahito domain intro timer boundary fix

The stage-two transformation tick set `domainIntroTicks = 60` and then the same `tick()` call immediately reached the intro countdown, decrementing the freshly initialized timer to 59. This made the configured 60-tick opening sequence one tick shorter.

The tick handler now records whether the intro began during the current tick and skips the countdown decrement in that case. The configured duration remains 60 ticks (3 seconds at 20 TPS), and the existing NBT save/load fields are unchanged.

Validation: `python tools/audit_mahito_domain_intro_duration.py` checks the timer initialization, same-tick decrement guard, countdown clear, and save/load fields. This is a static regression check; Java compilation and in-game verification are not claimed.
