"""Bundle a MapSplat web export without changing any layer styling.

Run with the export directory as the sole argument. Only resource URLs are
adapted for Android; archives and sprite sheets are copied byte for byte.
"""
import hashlib
import json
from pathlib import Path
import shutil
import struct
import sys

ASSETS = Path(__file__).resolve().parents[2] / "app/src/main/assets"


def main():
    export = Path(sys.argv[1]).resolve()
    style = json.loads((export / "style.json").read_text(encoding="utf-8"))
    manifest_path = ASSETS / "map/cavite_basemap.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    archives = []
    for source in style["sources"].values():
        assert source["type"] == "vector" and source["url"].startswith("pmtiles://")
        path = export / source["url"].removeprefix("pmtiles://")
        header = path.read_bytes()[:127]
        assert header[:8] == b"PMTiles\x03", path
        destination = ASSETS / "pmtiles" / path.name
        shutil.copyfile(path, destination)
        archives.append({"archive": path.name, "sizeBytes": path.stat().st_size,
                         "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        source["url"] = "pmtiles://file://{ARCHIVE_DIRECTORY}/" + path.name
        if path.name == "layers.pmtiles":
            manifest.update(archives[-1], minTileZoom=header[100], maxTileZoom=header[101])
            manifest["coverage"] = [value / 1e7 for value in struct.unpack_from("<iiii", header, 102)]
    sprite = style["sprite"]
    assert isinstance(sprite, str) and Path(sprite).name == sprite
    for suffix in (".json", ".png", "@2x.json", "@2x.png"):
        shutil.copyfile(export / (sprite + suffix), ASSETS / "map" / (sprite + suffix))
    style["sprite"] = "asset://map/" + sprite
    style["glyphs"] = "asset://fonts/{fontstack}/{range}.pbf"
    # The exported boundary rule starts at a city-scale zoom, but the app
    # opens at the fitted province overview. Keep the export's line styling
    # and make the administrative divisions visible from that overview.
    for layer in style["layers"]:
        if layer.get("id") == "cavite_admin_boundary_uniform":
            layer["minzoom"] = 0
        if "osm_buildings" in layer.get("source-layer", ""):
            layer["minzoom"] = 16
    for layer in style["layers"]:
        for font in layer.get("layout", {}).get("text-font", []):
            assert (ASSETS / "fonts" / font / "0-255.pbf").is_file(), font
    manifest["version"] = manifest.get("version", 0) + 1
    manifest["archives"] = archives
    manifest["style"] = "cavite_mapsplat_style.json"
    manifest["styleOverrides"] = {
        "cavite_admin_boundary_uniform": {"minzoom": 0}
    }
    manifest["exportStyleSha256"] = hashlib.sha256((export / "style.json").read_bytes()).hexdigest()
    (ASSETS / "map/cavite_mapsplat_style.json").write_text(
        json.dumps(style, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(style['layers'])} unchanged layers, {len(archives)} archives, and both sprite resolutions.")


if __name__ == "__main__":
    main()
