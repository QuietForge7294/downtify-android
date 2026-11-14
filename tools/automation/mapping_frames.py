#!/usr/bin/env python3
"""Show source candidates for an R8 stack, without printing exception payloads.

This is a bounded mapping inspection, not a replacement for official Retrace.
Inlining candidates sharing a line range are intentionally retained.
"""
import argparse
import json
import re
from pathlib import Path


def inspect(mapping: Path, stack: Path):
    frames = []
    for line in stack.read_text(encoding="utf-8-sig").splitlines():
        match = re.search(r"\bat ([\w.$]+)\.([\w$]+)\([^:]+:(\d+)\)", line)
        if match:
            frames.append((match[1], match[2], int(match[3])))
    targets = {name for name, _, _ in frames}
    sections = {}
    current = None
    original = None
    for line in mapping.read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"^(\S+) -> (\S+):$", line)
        if match:
            original, current = match.groups()
            if current in targets:
                sections[current] = (original, [])
        elif current in sections and line.startswith("    "):
            sections[current][1].append(line.strip())
    result = []
    for name, method, number in frames:
        section = sections.get(name)
        if not section:
            continue
        candidates = []
        for line in section[1]:
            match = re.match(r"^(\d+):(\d+):(.+) -> (\S+)$", line)
            if match and match[4] == method and int(match[1]) <= number <= int(match[2]):
                candidates.append(match[3])
        result.append({"class": section[0], "obfuscated_line": number,
                       "source_candidates": candidates[:25]})
    return {"status": "mapping_inspection_only", "frames": result}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mapping", type=Path)
    parser.add_argument("stack", type=Path)
    args = parser.parse_args()
    print(json.dumps(inspect(args.mapping, args.stack), indent=2))
