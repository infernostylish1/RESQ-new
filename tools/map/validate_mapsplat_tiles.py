"""Check tile precision, original road/river paths, and unchanged exported style."""
import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
sys.path.insert(0, str(Path(__file__).resolve().parent / ".cache/python"))
from pmtiles.reader import Reader, MmapSource, all_tiles
from mapbox_vector_tile.Mapbox import vector_tile_pb2
from mapbox_vector_tile.decoder import TileData
from shapely.geometry import Point, shape

OUT_OF_REGION_BUILDING_LAYER = "diff_bbox_gis_osm_buildings_a_free_1"


def message(data):
    tile = vector_tile_pb2.tile()
    tile.ParseFromString(gzip.decompress(data))
    return tile


def main(export, baseline):
    original_style = json.loads((export / "style.json").read_text(encoding="utf-8"))
    bundled_style = json.loads((ASSETS / "map/cavite_mapsplat_style.json").read_text(encoding="utf-8"))
    bundled_basemap_layers = [layer for layer in bundled_style["layers"]
                              if not layer.get("id", "").startswith("haz-ssa")]
    assert original_style["layers"] == bundled_basemap_layers, "Basemap layer styling must remain identical"
    if not baseline:
        manifest = json.loads((ASSETS / "map/cavite_basemap.json").read_text(encoding="utf-8"))
        for archive in manifest["archives"] + manifest.get("hazards", []):
            asset = ASSETS / "pmtiles" / archive["archive"]
            assert asset.stat().st_size == archive["sizeBytes"], asset
            assert hashlib.sha256(asset.read_bytes()).hexdigest() == archive["sha256"], asset
        primary = next(archive for archive in manifest["archives"] if archive["archive"] == manifest["archive"])
        assert all(manifest[key] == primary[key] for key in ("archive", "sizeBytes", "sha256"))
    path = export / "data/layers.pmtiles" if baseline else ASSETS / "pmtiles/layers.pmtiles"
    checkpoints = json.loads((ROOT / "app/src/androidTest/assets/map/geometry_checkpoints.json").read_text())
    coarse = Counter()
    errors = []
    total = 0
    with path.open("rb") as stream:
        source = MmapSource(stream)
        reader = Reader(source)
        for (z, x, y), data in all_tiles(source):
            expected = 4096
            tile = message(data)
            assert all(layer.name != OUT_OF_REGION_BUILDING_LAYER for layer in tile.layers), \
                (z, x, y, "out-of-region building layer is still packaged")
            if any(layer.extent < expected for layer in tile.layers):
                coarse[z] += 1
        for z in range(8, 14):
            groups = defaultdict(list)
            for checkpoint in checkpoints:
                wx = (checkpoint["longitude"] + 180) / 360 * 2**z
                wy = (1 - math.asinh(math.tan(math.radians(checkpoint["latitude"]))) / math.pi) / 2 * 2**z
                groups[int(wx), int(wy)].append((checkpoint, wx % 1, wy % 1))
            for (x, y), checks in groups.items():
                data = reader.get(z, x, y)
                wanted = defaultdict(set)
                for checkpoint, _, _ in checks:
                    wanted[checkpoint["layer"]].add(checkpoint["osm_id"])
                geometries = defaultdict(list)
                if data:
                    tile = message(data)
                    decoder = TileData(b"")
                    for layer in tile.layers:
                        if layer.name not in wanted or "osm_id" not in layer.keys:
                            continue
                        key_index = list(layer.keys).index("osm_id")
                        for feature in layer.features:
                            osm_id = next((str(decoder.parse_value(layer.values[v])) for k, v in
                                zip(feature.tags[::2], feature.tags[1::2]) if k == key_index), None)
                            if osm_id not in wanted[layer.name]:
                                continue
                            geometry = decoder.parse_geometry(feature.geometry, feature.type, layer.extent, True, None)
                            geometries[layer.name, osm_id].append((layer.extent, shape(geometry)))
                for checkpoint, fx, fy in checks:
                    candidates = geometries[checkpoint["layer"], checkpoint["osm_id"]]
                    # Always measure error on the same 8192-unit grid. A tile
                    # with collapsed/discarded paths cannot pass by declaring
                    # a smaller extent and allowing a correspondingly huge error.
                    distance = min((geometry.distance(Point(fx * extent, fy * extent)) * 8192 / extent
                                    for extent, geometry in candidates), default=math.inf)
                    total += 1
                    if distance > 1.5:
                        errors.append((z, checkpoint["layer"], checkpoint["osm_id"], round(distance, 2)))
            print(f"Checked original geometry at z{z}", flush=True)
    print(json.dumps({"geometryChecks": total, "missingOrDisplacedPaths": len(errors),
                      "insufficientPrecisionTiles": dict(coarse), "firstErrors": errors[:10]}, indent=2))
    if not baseline:
        assert not coarse, "Some tiles still have reduced geometry precision"
        assert not errors, "Some original roads or rivers are missing/displaced"
        with (export / "data/layers.pmtiles").open("rb") as old_stream, path.open("rb") as new_stream:
            old, new = MmapSource(old_stream), Reader(MmapSource(new_stream))
            for (z, x, y), data in all_tiles(old):
                if z < 14:
                    continue
                old_tile = message(data)
                retained = [layer for layer in old_tile.layers
                            if layer.name != OUT_OF_REGION_BUILDING_LAYER]
                new_data = new.get(z, x, y)
                new_tile = message(new_data) if new_data else None
                assert (new_tile is not None) == bool(retained), (z, x, y)
                if retained:
                    expected_tile = vector_tile_pb2.tile()
                    for layer in retained:
                        expected_tile.layers.add().CopyFrom(layer)
                    assert new_tile.SerializeToString() == expected_tile.SerializeToString(), (z, x, y)
        print("All geometry checks passed; out-of-region buildings are removed and all 187 style layers remain defined.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", type=Path)
    parser.add_argument("--baseline", action="store_true", help="Report original export defects without asserting")
    args = parser.parse_args()
    main(args.export, args.baseline)
