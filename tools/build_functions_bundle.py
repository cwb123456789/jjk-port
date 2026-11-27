#!/usr/bin/env python3
"""Packs the behaviour pack's mcfunctions into src/main/resources/assets/jujutsuenhancement/bedrock/functions/<top folder>.txt
(root-level functions: _root.txt).  One file per top folder, '@@<path>' header lines, comments / blank lines dropped.
usage: build_functions_bundle.py <BP dir>"""
import os
import sys

bp = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/jujutsuenhancement/bedrock/functions')
fdir = os.path.join(bp, 'functions')
groups = {}
for r, d, f in os.walk(fdir):
    for x in f:
        if not x.endswith('.mcfunction'):
            continue
        p = os.path.join(r, x)
        rel = os.path.relpath(p, fdir)[:-len('.mcfunction')].replace(os.sep, '/')
        top = rel.split('/')[0] if '/' in rel else '_root'
        lines = []
        for ln in open(p, encoding='utf-8-sig', errors='replace').read().split('\n'):
            ln = ln.strip()
            if not ln or ln.startswith('#'):
                continue
            if ln.startswith('/'):
                ln = ln[1:]
            lines.append(ln)
        groups.setdefault(top, []).append((rel, lines))
os.makedirs(out, exist_ok=True)
for fn in os.listdir(out):
    os.remove(os.path.join(out, fn))
tot = 0
for top, items in sorted(groups.items()):
    items.sort()
    with open(os.path.join(out, top + '.txt'), 'w', encoding='utf-8', newline='\n') as w:
        for rel, lines in items:
            w.write('@@' + rel + '\n')
            for ln in lines:
                w.write(ln + '\n')
            tot += len(lines)
print(len(groups), 'files', sum(len(v) for v in groups.values()), 'functions', tot, 'lines')
