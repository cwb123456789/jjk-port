#!/usr/bin/env python3
"""Every remaining item of the behaviour pack in items/{cts,small_cts,particles,relics,secrets} and the root folder (technique, skill and
utility items) as a plain Bedrock item of the mod: name/lore/icon/model through build_item_assets, the list in bedrock/items_generic.json
({id, stack, foil}); ModItems registers them. Armour (armour/) and weapons (weps/) are done with their own models later.
usage: build_generic_items.py <scratchpad> <resources/assets/jujutsuenhancement>"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_item_assets as b

src, dst = sys.argv[1], sys.argv[2]
bp = os.path.join(src, 'ba', '81e3bc74', 'JujutsuAwa', 'items')
TOPS = {'cts', 'small_cts', 'particles', 'relics', 'secrets', '(root)'}
found = {}
for allow_copy in (False, True):
    for f in sorted(glob.glob(os.path.join(bp, '**', '*.json'), recursive=True)):
        if (' - Copy' in f) != allow_copy:
            continue
        rel = os.path.relpath(f, bp)
        top = rel.split(os.sep)[0] if os.sep in rel else '(root)'
        if top not in TOPS:
            continue
        try:
            d = json.load(open(f, encoding='utf-8-sig'))
        except Exception:
            continue
        it = d.get('minecraft:item')
        if not it:
            continue
        ident = it['description']['identifier']
        if not ident.startswith('ds:') or ident in found:
            continue
        found[ident] = it
ported = set(b.ITEMS)
rows = []
for ident, it in sorted(found.items()):
    name = ident[3:]
    if name in ported:
        continue
    c = it['components']
    if 'minecraft:display_name' not in c:
        continue
    b.ITEMS[name] = 'generated'
    stack = c.get('minecraft:max_stack_size', 64)
    rows.append({'id': name, 'stack': int(stack) if isinstance(stack, (int, float)) else 64, 'foil': bool(c.get('minecraft:foil', False) or c.get('minecraft:glint', False))})
# only the new ones are processed
keep = {r['id'] for r in rows}
for k in list(b.ITEMS):
    if k not in keep:
        del b.ITEMS[k]
b.main(src, dst)
out = os.path.join(dst, 'bedrock', 'items_generic.json')
json.dump(rows, open(out, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print(len(rows), 'generic items ->', out)
