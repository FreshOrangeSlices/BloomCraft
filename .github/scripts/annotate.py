#!/usr/bin/env python3
"""Turns a failed Gradle build log into GitHub annotations.

Annotations can be read through the GitHub API (job logs sometimes can't), so a failed build reports its
compile errors this way. GitHub shows at most 10 error annotations per step, so the errors are packed into
chunks and each "report" step prints one group of 10 chunks:

    annotate.py build.log <group>
"""
import os
import re
import sys

CHUNK = 3800          # characters per annotation
PER_STEP = 10         # error annotations GitHub keeps per step

ERROR = re.compile(r"^(/\S+\.java):(\d+): error: (.*)$")


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def javac_errors(lines, workspace):
    errors = []
    i = 0
    while i < len(lines):
        m = ERROR.match(lines[i])
        if not m:
            i += 1
            continue
        path = m.group(1)
        if workspace and path.startswith(workspace):
            path = path[len(workspace):].lstrip("/")
        block = [f"{path}:{m.group(2)}: {m.group(3)}"]
        i += 1
        # the source line, the caret and "symbol:/location:" lines that follow
        while i < len(lines) and not ERROR.match(lines[i]) and lines[i].strip() \
                and not lines[i].startswith(("> Task", "FAILURE", "BUILD")) \
                and not re.match(r"^\d+ errors?$", lines[i].strip()) \
                and not lines[i].startswith("Note:"):
            block.append(lines[i].rstrip())
            i += 1
        errors.append("\n".join(block))
    return errors


def other_failure(lines):
    """No javac errors: show Gradle's own explanation and the end of the log."""
    text = "\n".join(lines)
    out = []
    m = re.search(r"\* What went wrong:\n(.*?)(?:\n\* Try:|\Z)", text, re.S)
    if m:
        out.append("What went wrong:\n" + m.group(1).strip())
    out.append("End of log:\n" + "\n".join(lines[-150:]))
    return out


def chunks(items):
    result, current = [], ""
    for item in items:
        while len(item) > CHUNK:      # one huge item: split it
            if current:
                result.append(current)
                current = ""
            result.append(item[:CHUNK])
            item = item[CHUNK:]
        if current and len(current) + len(item) + 2 > CHUNK:
            result.append(current)
            current = ""
        current = f"{current}\n\n{item}" if current else item
    if current:
        result.append(current)
    return result


def main():
    log, group = sys.argv[1], int(sys.argv[2])
    with open(log, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    workspace = os.environ.get("GITHUB_WORKSPACE", "")
    errors = javac_errors(lines, workspace)
    if errors:
        title = f"{len(errors)} compile errors"
        parts = chunks(errors)
    else:
        title = "Build failed"
        parts = chunks(other_failure(lines))
    selected = parts[group * PER_STEP:(group + 1) * PER_STEP]
    for n, part in enumerate(selected, start=group * PER_STEP + 1):
        print(f"::error title={title} ({n}/{len(parts)})::{escape(part)}")


if __name__ == "__main__":
    main()
