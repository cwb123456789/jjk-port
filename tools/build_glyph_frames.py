#!/usr/bin/env python3
"""Cut the Bedrock addon's impact-frame glyph page (glyph_E3.png / .webp, 16x16 cells) into one PNG per frame.

The addon shows an impact frame with `/title @a title \\ueXYZ`; the glyph is a full-screen picture that lives in the
cell XYZ of the glyph page. Usage:

    python3 tools/build_glyph_frames.py <glyph_E3 page> <assets dir> [hex ...]

`<assets dir>` is src/main/resources/assets/jujutsuenhancement. Without codepoints every frame of the page is
exported (about 5 MB), otherwise only the listed low bytes, e.g. `30 32 34`. Frames that are only a 1-pixel sliver
(the addon uses them as "blank" titles) are skipped; the client treats a missing frame as blank.
"""
import json
import os
import sys

from PIL import Image


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 1
    page = Image.open(argv[1]).convert("RGBA")
    out = os.path.join(argv[2], "textures", "bedrock", "glyph")
    os.makedirs(out, exist_ok=True)
    cell = page.size[0] / 16.0
    wanted = {int(x, 16) for x in argv[3:]} if len(argv) > 3 else set(range(256))
    done = 0
    for idx in sorted(wanted):
        r, c = idx >> 4, idx & 15
        x0, x1 = int(round(c * cell)), int(round((c + 1) * cell))
        y0, y1 = int(round(r * cell)), int(round((r + 1) * cell))
        tile = page.crop((x0, y0, x1, y1))
        box = tile.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
        if box is None or box[2] - box[0] < 8:
            continue
        frame = tile.crop(box)
        name = "e3%02x" % idx
        frame.save(os.path.join(out, name + ".png"), optimize=True)
        with open(os.path.join(out, name + ".png.mcmeta"), "w") as f:
            json.dump({"texture": {"blur": True}}, f)
        done += 1
    print("exported", done, "frames to", out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
