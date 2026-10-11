#!/usr/bin/env python3
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "play-store/listing/en-US/screenshots.json"


def caption_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in (
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: finalize_play_screenshots.py RAW_SCREENSHOT_ROOT")
    raw_root = Path(sys.argv[1])
    groups = json.loads(METADATA.read_text())
    for group, entries in groups.items():
        for entry in entries:
            target = (METADATA.parent / entry["file"]).resolve()
            source = raw_root / group / target.name
            image = Image.open(source).convert("RGB")
            if group == "phone":
                assert image.size == (1080, 1920), f"unexpected phone size: {source}"
                band_height = 168
                draw = ImageDraw.Draw(image)
                draw.rectangle((0, image.height - band_height, image.width, image.height), fill="#151718")
                draw.rectangle((0, image.height - band_height, image.width, image.height - band_height + 4), fill="#cd8892")
                font = caption_font(46)
                caption = entry["caption"]
                bounds = draw.textbbox((0, 0), caption, font=font)
                x = (image.width - (bounds[2] - bounds[0])) // 2
                y = image.height - band_height + (band_height - (bounds[3] - bounds[1])) // 2 - bounds[1]
                draw.text((x, y), caption, font=font, fill="#f3d8dc")
            target.parent.mkdir(parents=True, exist_ok=True)
            image.save(target, optimize=True)
    print("Finalized phone captions and RGB Play screenshots.")


if __name__ == "__main__":
    main()
