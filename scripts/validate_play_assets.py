#!/usr/bin/env python3
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "play-store/listing/en-US/screenshots.json"
EXPECTED_COUNTS = {"phone": 5, "7-inch-tablet": 4, "10-inch-tablet": 4}


def png_info(path: Path) -> tuple[int, int, int, bool]:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}"
    assert data[12:16] == b"IHDR", f"missing IHDR: {path}"
    width, height, _, color_type = struct.unpack(">IIBB", data[16:26])
    return width, height, color_type, b"tRNS" in data


def main() -> None:
    groups = json.loads(METADATA.read_text())
    for group, expected in EXPECTED_COUNTS.items():
        entries = groups[group]
        assert len(entries) == expected, f"{group}: expected {expected} images"
        for entry in entries:
            assert 1 <= len(entry["alt"]) <= 140, f"invalid alt text: {entry['file']}"
            path = (METADATA.parent / entry["file"]).resolve()
            width, height, color_type, transparency = png_info(path)
            assert color_type not in (4, 6) and not transparency, f"alpha is not allowed: {path}"
            if group == "phone":
                assert (width, height) == (1080, 1920), f"phone image must be 1080x1920: {path}"
                assert entry.get("caption"), f"missing phone caption: {path}"
            else:
                assert max(width, height) >= 1080, f"tablet image is too small: {path}"
                assert width * 16 == height * 9 or height * 16 == width * 9, f"tablet image must be 9:16 or 16:9: {path}"
                assert "caption" not in entry, f"tablet captures must not have captions: {path}"
    print("Play listing assets pass structural validation.")


if __name__ == "__main__":
    main()
