# Server-side import audit

The common entity and registry packages must not import Minecraft client classes or the mod's client renderer/model packages. Forge loads common entity classes on a dedicated server, where client-only types are unavailable.

Run:

```sh
python3 tools/audit_server_side_imports.py
```

This audit checks imports only; it does not replace compilation or a dedicated-server launch test.
