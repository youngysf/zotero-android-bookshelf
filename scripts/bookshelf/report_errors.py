#!/usr/bin/env python3
"""Turns build failures into GitHub annotations.

Annotations are readable through GitHub's public API, so whoever is working
on the build can see why it failed without access to the full logs.
GitHub shows at most 10 error annotations per step, so this picks the most
useful lines: compiler errors first, then Gradle's "What went wrong" block,
then the end of the log.
"""
import pathlib
import re
import sys

MAX = 9


def esc(text):
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def annotate(title, text):
    print(f"::error title={esc(title)}::{esc(text[:3500])}")


lines = []
for name in sys.argv[1:]:
    path = pathlib.Path(name)
    if path.exists():
        lines += [(name, line.rstrip("\n")) for line in path.read_text(errors="replace").splitlines()]

count = 0
seen = set()
for _, line in lines:
    if re.match(r"^e: ", line) or re.search(r"error: ", line):
        key = line.strip()
        if key in seen:
            continue
        seen.add(key)
        annotate("Compile error", line)
        count += 1
        if count >= MAX - 2:
            break

text = "\n".join(line for _, line in lines)
match = re.search(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", text, re.S)
if match:
    annotate("What went wrong", match.group(1).strip())

tail = "\n".join(line for _, line in lines[-80:])
annotate("End of log", tail or "No log output was captured.")
