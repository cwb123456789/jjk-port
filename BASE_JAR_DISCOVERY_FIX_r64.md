# r64 — Base JAR discovery and regression audit repair

## Change

The supplied JujutsuCraft v50.1 JAR is stored under `/mnt/data/project_inputs/` in this workspace, while four compatibility audits previously searched only `/mnt/data/jujutsucraft-base.jar` or `/mnt/data/*JujutsuCraft-ver50.1-forge-1.20.1.jar`. This produced false failures even though the provided dependency was present.

Updated the native Mahito animation, geometry, and domain-animation compatibility audits to check the canonical path first and then the provided `project_inputs` path. Updated the native Sukuna dispatcher audit to search that input directory as well.

## Validation

Run `python tools/run_regression_audits.py` from the project root. This remains static/resource validation only; it does not replace Java compilation or an in-game test.
