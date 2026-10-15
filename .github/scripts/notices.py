#!/usr/bin/env python3
"""Prints a text file as notice annotations, 10 per step (GitHub's limit):  notices.py <file> <group>"""
import sys

CHUNK = 3800
PER_STEP = 10


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main():
    path, group = sys.argv[1], int(sys.argv[2])
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    parts, current = [], ""
    for line in lines:
        if current and len(current) + len(line) + 1 > CHUNK:
            parts.append(current)
            current = ""
        current = f"{current}\n{line}" if current else line[:CHUNK]
    if current:
        parts.append(current)
    for n, part in enumerate(parts[group * PER_STEP:(group + 1) * PER_STEP], start=group * PER_STEP + 1):
        print(f"::notice title=API ({n}/{len(parts)})::{escape(part)}")


if __name__ == "__main__":
    main()
