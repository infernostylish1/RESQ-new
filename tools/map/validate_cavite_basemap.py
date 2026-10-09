"""Check the packaged vector archive and its focused Cavite data contract."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import time

from build_cavite_basemap import ASSETS, FOCUSED_LAYERS, CONTEXT_LAYERS, decode, prepare_boundary, tile_focus
from pmtiles.reader import MmapSource, Reader, all_tiles
from pmtiles.tile import Compression, TileType
from shapely.geometry import shape


def validate(path):
    started = time.monotonic()
    manifest = json.loads((ASSETS / "map/cavite_basemap.json").read_text(encoding="utf-8"))
    assert path.stat().st_size == manifest["sizeBytes"], "archive size differs from manifest"
    assert hashlib.sha256(path.read_bytes()).hexdigest() == manifest["sha256"], "archive hash differs from manifest"
    province, world_boundary = prepare_boundary()
    assert list(province.bounds) == manifest["boundary"], "province extent differs from supplied boundary"
    counts, feature_counts = Counter(), Counter()
    empty_quantized_features = 0
    largest = (0, None)
    with path.open("rb") as archive:
        source = MmapSource(archive)
        header = Reader(source).header()
        assert header["tile_type"] == TileType.MVT
        assert header["tile_compression"] == Compression.GZIP
        assert header["min_zoom"] == manifest["minTileZoom"] == 7
        assert header["max_zoom"] == manifest["maxTileZoom"] == 15
        for index, ((z, x, y), data) in enumerate(all_tiles(source), 1):
            layers = decode(data)
            assert set(layers).issubset(FOCUSED_LAYERS | CONTEXT_LAYERS), "unexpected or transit layer"
            assert "earth" in layers or "water" in layers, f"missing continuous land/water context at {z}/{x}/{y}"
            counts[z] += 1
            largest = max(largest, (len(data), (z, x, y)))
            clip = tile_focus(world_boundary, z, x, y).buffer(2)
            for name, layer in layers.items():
                feature_counts[name] += len(layer["features"])
                for feature in layer["features"]:
                    properties = feature["properties"]
                    assert properties.get("kind") != "ferry", "ferry sea lane found"
                    if name == "boundaries":
                        assert properties.get("maritime") not in (True, 1, "yes", "true", "1"), "maritime boundary found"
                    if name in FOCUSED_LAYERS:
                        assert properties.get("resq_focus") == 1, "detail feature lacks focus marker"
                        geometry = shape(feature["geometry"])
                        # A subpixel polygon may collapse during integer MVT
                        # quantization. It has no rendered area to test.
                        if geometry.is_empty:
                            empty_quantized_features += 1
                            continue
                        assert clip.covers(geometry), f"detail outside supplied Cavite polygon in {name} {z}/{x}/{y}"
            if index % 10000 == 0:
                print(f"validated {index} tiles ({time.monotonic()-started:.1f}s)", flush=True)
    assert feature_counts["roads"] > 1000 and feature_counts["buildings"] > 1000 and feature_counts["pois"] > 100, "street detail absent"
    assert dict(counts) == {int(z): count for z, count in manifest["tilesByZoom"].items()}
    print(json.dumps({"valid": True, "tiles": sum(counts.values()), "tilesByZoom": dict(counts),
        "featuresByLayer": dict(feature_counts), "largestCompressedTileBytes": largest[0],
        "largestTile": largest[1], "emptyQuantizedFeatures": empty_quantized_features,
        "seconds": round(time.monotonic()-started, 1)}, indent=2), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", nargs="?", type=Path, default=ASSETS / "pmtiles/cavite_basemap.pmtiles")
    validate(parser.parse_args().archive)
