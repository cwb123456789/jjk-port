#!/usr/bin/env python3
"""Exports the items of the Bedrock addon that the mod has ported: name, description (lore), icon and item model.

usage: build_item_assets.py <scratchpad with ba/81e3bc74/JujutsuAwa, rpnew/, rp8/, rp4/, rp1/> <resources/assets/jujutsuenhancement>

The addon puts the name and the description of an item in one display_name ("name\\ndescription\\n..."), with Bedrock's
§ formatting. The first line becomes item.jujutsuenhancement.<id>, the remaining lines item.jujutsuenhancement.<id>.lore
(joined by "\\n"); the § codes are kept, com.jujutsuenhancement.text.BedrockText applies Bedrock's rules to them at runtime.
The icon is searched in the updated resource pack first (rpnew), then in the older ones.
"""
import glob
import json
import os
import sys

from PIL import Image

# id -> model parent ("generated": flat icon, "handheld": held like a tool)
ITEMS = {
    'unobtanium_ingot': 'generated',
    'rough_crystal': 'generated',
    # equipment (their geometry / texture / animation come from build_armor_assets.py)
    'pigs_crown': 'generated',
    'gojo_cloak': 'generated',
    'kusakabe_fit': 'generated',
    'katana': 'handheld',
    # techniques (术式); the icons are the ones the item definitions name (limitless2 shows the limitless3 texture and vice versa)
    'limitless2': 'generated',
    'limitless3': 'generated',
    'limitless_technique': 'generated',
    'limitless1': 'generated',
    'limitless4': 'generated',
    'limitless5': 'generated',
    'limitless6': 'generated',
    'yuji_technique': 'generated',
    'sukuna_technique': 'generated',
    'sukuna1': 'generated',
    'sukuna2': 'generated',
    'sukuna5': 'generated',
    'black_fists': 'generated',
    'soul_shrine1': 'generated',
    'soul_shrine2': 'generated',
    'blood_manipulation': 'generated',
}
# items that a layer draws in the third person with their own geometry: the flat model is shrunk to nothing there
HIDDEN_THIRD_PERSON = {'katana'}

TAB_NAME_ZH = '咒术觉醒'
TAB_NAME_EN = 'Jujutsu Awakening'


def find_item_json(behavior, ident):
    for f in glob.glob(os.path.join(behavior, 'items', '**', '*.json'), recursive=True):
        if ' - Copy' in f:
            continue
        try:
            d = json.load(open(f, encoding='utf-8-sig'))
        except Exception:
            continue
        item = d.get('minecraft:item')
        if item and item['description']['identifier'] == 'ds:' + ident:
            return item
    return None


def resolve_icon(src, name):
    """The icon file of the texture short name (item_texture.json maps it to textures/items/<x>)."""
    for pack in ('rpnew/2e5ccfa5-__', 'rp8/textures', 'rp4/textures', 'rp1/textures'):
        base = os.path.join(src, pack)
        tex = None
        for tf in (os.path.join(base, 'item_texture.json'), os.path.join(base, 'textures', 'item_texture.json')):
            if os.path.exists(tf):
                td = json.load(open(tf, encoding='utf-8-sig'))['texture_data']
                if name in td:
                    t = td[name]['textures']
                    tex = t[0] if isinstance(t, list) else t
                    if isinstance(tex, dict):
                        tex = tex.get('path')
                break
        for rel in ([tex] if tex else []) + ['textures/items/' + name]:
            for root in (base, os.path.join(base, '..')):
                for cand in (os.path.join(root, rel + '.png'),
                             os.path.join(root, rel.replace('textures/', '', 1) + '.png')):
                    if os.path.exists(cand):
                        return cand
    return None


def main(src, dst):
    behavior = os.path.join(src, 'ba', '81e3bc74', 'JujutsuAwa')
    langs = {}
    for lang in ('zh_cn', 'en_us'):
        path = os.path.join(dst, 'lang', lang + '.json')
        langs[lang] = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    os.makedirs(os.path.join(dst, 'textures', 'item'), exist_ok=True)
    os.makedirs(os.path.join(dst, 'models', 'item'), exist_ok=True)
    for ident, parent in ITEMS.items():
        item = find_item_json(behavior, ident)
        if item is None:
            print('missing item json', ident)
            continue
        comps = item['components']
        lines = comps['minecraft:display_name']['value'].split('\n')
        name, lore = lines[0], '\n'.join(lines[1:])
        for lang in langs.values():
            lang['item.jujutsuenhancement.' + ident] = name
            if lore:
                lang['item.jujutsuenhancement.' + ident + '.lore'] = lore
            else:
                lang.pop('item.jujutsuenhancement.' + ident + '.lore', None)
        icon_name = comps['minecraft:icon']['textures']['default'] if 'minecraft:icon' in comps else ident
        icon = resolve_icon(src, icon_name)
        if icon is None:
            print('missing icon', ident, icon_name)
            continue
        img = Image.open(icon).convert('RGBA')
        if max(img.size) > 64:
            img = img.resize((64, 64), Image.LANCZOS)  # the slot squeezes it into a square anyway
        img.save(os.path.join(dst, 'textures', 'item', ident + '.png'))
        with open(os.path.join(dst, 'models', 'item', ident + '.json'), 'w', encoding='utf-8') as fh:
            model = {'parent': 'minecraft:item/' + parent, 'textures': {'layer0': 'jujutsuenhancement:item/' + ident}}
            if ident in HIDDEN_THIRD_PERSON:
                zero = {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0, 0, 0]}
                model['display'] = {'thirdperson_righthand': zero, 'thirdperson_lefthand': zero}
            json.dump(model, fh, indent=2)
        print('item', ident, repr(name), 'stack', comps.get('minecraft:max_stack_size'))
    langs['zh_cn']['itemGroup.jujutsuenhancement'] = TAB_NAME_ZH
    langs['en_us']['itemGroup.jujutsuenhancement'] = TAB_NAME_EN
    for lang, data in langs.items():
        with open(os.path.join(dst, 'lang', lang + '.json'), 'w', encoding='utf-8') as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write('\n')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
