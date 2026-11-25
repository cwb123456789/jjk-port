#!/usr/bin/env python3
"""Splits the built mod jar so that every file stays under the 30 MB upload limit.

The heavy, code-free assets (sounds, particle textures, impact-frame glyphs) move to numbered resource-only mods
(modLoader lowcodefml, namespace jujutsuenhancement), which Forge loads next to the main jar; the main jar keeps the code and the
rest.  usage: split_jar.py <in.jar> <out dir> [max MB per part]
"""
import os
import sys
import zipfile

HEAVY = ('assets/jujutsuenhancement/sounds/bedrock/', 'assets/jujutsuenhancement/textures/bedrock/particle/',
         'assets/jujutsuenhancement/textures/bedrock/glyph/')

TOML = '''modLoader="lowcodefml"
loaderVersion="[47,)"
license="All Rights Reserved"
[[mods]]
modId="%s"
version="0.1.0"
displayName="Jujutsu Enhancement assets %d"
description="Assets of Jujutsu Enhancement (sounds / textures). Keep it next to jujutsuenhancement.jar."
'''


def main(src, out, limit_mb=22.0):
    os.makedirs(out, exist_ok=True)
    zin = zipfile.ZipFile(src)
    heavy = [i for i in zin.infolist() if i.filename.startswith(HEAVY) and not i.is_dir()]
    heavy.sort(key=lambda i: -i.compress_size)
    limit = int(limit_mb * 1024 * 1024)
    parts = []   # [size, [infos]]
    for i in heavy:
        for p in parts:
            if p[0] + i.compress_size <= limit:
                p[0] += i.compress_size
                p[1].append(i)
                break
        else:
            parts.append([i.compress_size, [i]])
    moved = {i.filename for p in parts for i in p[1]}
    base = os.path.basename(src)
    stem = base[:-4]
    with zipfile.ZipFile(os.path.join(out, base), 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for i in zin.infolist():
            if i.filename in moved:
                continue
            z.writestr(i, zin.read(i.filename), zipfile.ZIP_DEFLATED, 9) if not i.is_dir() else z.writestr(i, b'')
    for n, (size, infos) in enumerate(parts, 1):
        name = '%s-assets%d.jar' % (stem, n)
        with zipfile.ZipFile(os.path.join(out, name), 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            z.writestr('META-INF/mods.toml', TOML % ('jujutsuenhancement_assets%d' % n, n))
            z.writestr('pack.mcmeta', '{"pack":{"description":"Jujutsu Enhancement assets","pack_format":15}}')
            for i in infos:
                z.writestr(i.filename, zin.read(i.filename))
    for f in sorted(os.listdir(out)):
        print(f, os.path.getsize(os.path.join(out, f)))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 22.0)
