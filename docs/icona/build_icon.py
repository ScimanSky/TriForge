"""Rebuilds the launcher icon layers from the source artwork.

    python3 docs/icona/build_icon.py

Reads docs/icona/icona-sorgente.png (the artwork on a near-black square) and
writes, for every density, android/app/src/main/res/mipmap-*/
ic_launcher_foreground.png (artwork on transparent) and
ic_launcher_monochrome.png (same shape in one colour, for themed icons).
The dark background comes from @color/background in ic_launcher.xml.

Needs Pillow and numpy. If the artwork changes, measure its enclosing circle
again and update CX, CY, R below.
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "icona-sorgente.png")
RES = os.path.join(HERE, "..", "..", "android", "app", "src", "main", "res")

# Colour of the source background, and the smallest circle around the artwork.
BACKGROUND = np.array([10.4, 11.9, 14.9])
CX, CY, R = 626.5, 646.5, 420.0
# Radius the artwork gets in the 108-unit adaptive layer. The safe zone, which
# every launcher shape shows in full, has radius 33.
ARTWORK_UNITS = 31.5

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def main():
    pixels = np.asarray(Image.open(SOURCE).convert("RGB")).astype(np.float64)
    # Every artwork colour has one channel near 255 and the background has
    # none above 17, so the brightest channel gives the coverage of a pixel.
    alpha = np.clip((pixels.max(axis=2) - 17.0) / (225.0 - 17.0), 0, 1)
    # Take the background back out of the edge pixels.
    covered = np.maximum(alpha, 1e-3)[..., None]
    colour = np.clip((pixels - (1 - alpha[..., None]) * BACKGROUND) / covered, 0, 255)
    artwork = Image.fromarray(
        np.dstack([colour, alpha * 255]).round().astype(np.uint8), "RGBA"
    )

    side = int(round(R * 108 / ARTWORK_UNITS))
    layer = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    layer.paste(artwork, (int(round(side / 2 - CX)), int(round(side / 2 - CY))))

    white = np.asarray(layer).copy()
    white[..., :3] = 255
    monochrome = Image.fromarray(white, "RGBA")

    for density, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        for image, name in (
            (layer, "ic_launcher_foreground.png"),
            (monochrome, "ic_launcher_monochrome.png"),
        ):
            # Premultiplied resize, so transparent pixels do not darken edges.
            scaled = image.convert("RGBa").resize((size, size), Image.LANCZOS)
            scaled.convert("RGBA").save(os.path.join(folder, name), optimize=True)


if __name__ == "__main__":
    main()
