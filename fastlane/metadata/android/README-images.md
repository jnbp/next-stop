# Store images (Google Play, fastlane supply layout)

| File | Size | Format | Source |
|---|---|---|---|
| `de-DE/images/icon.png` | 512 x 512 | PNG, 32-bit RGBA | launcher icon (adaptive layers) |
| `en-US/images/icon.png` | 512 x 512 | PNG, 32-bit RGBA | same file as `de-DE` |
| `de-DE/images/featureGraphic.png` | 1024 x 500 | PNG, 24-bit RGB, no alpha | `de-DE/images/featureGraphic.svg` |
| `en-US/images/featureGraphic.png` | 1024 x 500 | PNG, 24-bit RGB, no alpha | `en-US/images/featureGraphic.svg` |

The SVGs next to the PNGs are the editable sources of the feature graphics. The Wear OS app uses
the same launcher icon (identical drawables in `wear/src/main/res`), so there is no separate
watch icon.

## How they are made

All four PNGs come from one script, `tools/store_art.py`. Re-run it after changing the launcher
icon or a feature graphic SVG:

```sh
pip install resvg-py pillow
python3 tools/store_art.py
```

- **icon.png**: reads `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, converts its background
  and foreground VectorDrawables to SVG (viewport, paths with fill/stroke colour and alpha,
  line caps and joins, fill type, groups with pivot/translate/scale/rotate, clip paths and
  gradients), stacks them on the 108dp adaptive canvas and crops the centred 72dp area that a
  launcher shows, scaled to 512 px. The square is full-bleed without rounded corners or shadow;
  Play applies its own mask. The mark sits well inside the 66dp safe zone.
- **featureGraphic.png**: renders the SVG with resvg, using only the Google Sans Flex files in
  `app/src/main/res/font` (system fonts are skipped, so text cannot be substituted). The
  bundled static fonts are named "Google Sans Flex 18pt", which the script puts first in the
  SVG's font-family list for rendering. The SVG asks for weight 500 in the subtitle; the
  bundled weights are 400, 600, 700 and 800, so it is set in 400 (CSS font matching).
  The result is flattened onto `#1B4FD8` and saved without an alpha channel.

Limits checked by the script: icon at most 1 MB, feature graphic at most 15 MB, exact pixel sizes.
