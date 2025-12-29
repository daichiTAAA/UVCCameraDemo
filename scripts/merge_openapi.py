#!/usr/bin/env python3
import json
import sys
from typing import Any, Dict


def deep_merge(dst: Dict[str, Any], src: Dict[str, Any]) -> Dict[str, Any]:
    """Deep merge src into dst (dicts only)."""
    for key, value in src.items():
        if isinstance(value, dict) and isinstance(dst.get(key), dict):
            deep_merge(dst[key], value)
        else:
            dst[key] = value
    return dst


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "Usage: merge_openapi.py <base.json> <overlay.json> <out.json>",
            file=sys.stderr,
        )
        return 2

    base_path, overlay_path, out_path = sys.argv[1:4]

    with open(base_path, "r", encoding="utf-8") as f:
        base = json.load(f)
    with open(overlay_path, "r", encoding="utf-8") as f:
        overlay = json.load(f)

    if not isinstance(base, dict) or not isinstance(overlay, dict):
        print("Base and overlay must be JSON objects", file=sys.stderr)
        return 2

    # Merge top-level keys (paths/components etc.)
    merged = deep_merge(base, overlay)

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)
        f.write("\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
