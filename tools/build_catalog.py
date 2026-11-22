#!/usr/bin/env python3
"""Genera el catálogo de la tienda de Lumora.

Uso:
    python3 tools/build_catalog.py

Lee `tools/content-manifest.json`, inspecciona los archivos de `docs/content/` y escribe:
  * docs/catalog.json                              (publicado con GitHub Pages)
  * core/data/src/main/assets/catalog_fallback.json (catálogo offline empaquetado en el APK)

Solo usa la librería estándar + `identify` (ImageMagick) de forma opcional.
"""
from __future__ import annotations

import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
CONTENT = DOCS / "content"
MANIFEST = ROOT / "tools" / "content-manifest.json"
FALLBACK = ROOT / "core" / "data" / "src" / "main" / "assets" / "catalog_fallback.json"

LICENSE = "CC0-1.0"
LICENSE_URL = "https://creativecommons.org/publicdomain/zero/1.0/"


def image_size(path: pathlib.Path) -> tuple[int, int]:
    try:
        out = subprocess.run(
            ["identify", "-format", "%w %h", str(path)],
            capture_output=True, text=True, check=True,
        ).stdout.split()
        return int(out[0]), int(out[1])
    except Exception:
        return 1080, 1920


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    base = manifest["baseUrl"].rstrip("/")
    items = []

    for entry in manifest["items"]:
        base_name = entry["file"]
        kind = entry["kind"]
        if kind == "video":
            full = CONTENT / "videos" / f"{base_name}.mp4"
            preview = CONTENT / "previews" / f"{base_name}-preview.mp4"
            thumb = CONTENT / "thumbs" / f"{base_name}.webp"
            width, height = entry.get("width", 720), entry.get("height", 1280)
            duration = entry.get("durationMs", 8000)
        else:
            full = CONTENT / "full" / f"{base_name}.webp"
            preview = full
            thumb = CONTENT / "thumbs" / f"{base_name}.webp"
            width, height = image_size(full)
            duration = 0

        def url(path: pathlib.Path) -> str:
            return f"{base}/" + str(path.relative_to(DOCS)).replace("\\", "/")

        items.append({
            "id": entry["id"],
            "title": entry["title"],
            "author": entry.get("author", "Lumora Studio"),
            "kind": kind,
            "categories": entry.get("categories", []),
            "tags": entry.get("tags", []),
            "width": width,
            "height": height,
            "durationMs": duration,
            "sizeBytes": full.stat().st_size if full.exists() else 0,
            "thumbUrl": url(thumb),
            "previewUrl": url(preview),
            "fullUrl": url(full),
            "license": LICENSE,
            "licenseUrl": LICENSE_URL,
            "sourceUrl": "https://github.com/karmaoriginal/fluffy-rotary-phone/tree/main/docs/content",
            "addedAt": entry.get("addedAt", ""),
            "downloads": entry.get("downloads", 0),
            "featured": entry.get("featured", False),
        })

    catalog = {
        "version": manifest.get("version", 1),
        "updatedAt": manifest.get("updatedAt", ""),
        "categories": manifest["categories"],
        "items": items,
    }

    text = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    (DOCS / "catalog.json").write_text(text, encoding="utf-8")
    FALLBACK.parent.mkdir(parents=True, exist_ok=True)
    FALLBACK.write_text(text, encoding="utf-8")
    print(f"OK: {len(items)} elementos -> {DOCS / 'catalog.json'} y {FALLBACK}")


if __name__ == "__main__":
    main()
