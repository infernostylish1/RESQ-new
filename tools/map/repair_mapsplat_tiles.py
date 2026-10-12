"""Restore overview geometry from the original QGIS vectors, preserving styling.

Run using QGIS's python-qgis.bat (requires GDAL's PMTiles writer). The original
export's z14-16 tiles already have full precision and are retained after removing
the out-of-region building layer. Only z0-13 are regenerated; the export itself
is never overwritten.
"""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
import gzip
import hashlib
import json
from pathlib import Path
import sys
import sqlite3
import time

CACHE = Path(__file__).resolve().parent / ".cache"
sys.path.insert(0, str(CACHE / "python"))
from osgeo import gdal
from pmtiles.reader import Reader, MmapSource, all_tiles
from pmtiles.writer import Writer
from pmtiles.tile import zxy_to_tileid
from mapbox_vector_tile.Mapbox import vector_tile_pb2

gdal.UseExceptions()
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
LAST_REBUILT_ZOOM = 13
EXTENT = 4096
MAX_TILE_SIZE = 10_000_000
BUILDINGS_MIN_ZOOM = 16
OUT_OF_REGION_BUILDING_LAYER = "diff_bbox_gis_osm_buildings_a_free_1"
BUILDING_BATCH_SIZE = 100000
ROAD_BATCH_SIZE = 25000


def sources(root):
    result = {
        "land": (root / "corrections/bbox_land.gpkg", "land"),
        "bbox_boundary_cav_bbox": (root / "merged clipped and diff/clean_bbox_cav.gpkg", "bbox_boundary"),
        "cavite_admin_boundary": (root / "cavite_region_boundary.json", "cavite_region_boundary"),
        "cavite_admin_label_anchors": (root / "cavite_admin_fixed_labels_package/cavite_admin_label_anchors.geojson", None),
        "water_water_polygons": (root / "cavite_bbox input/water_water_polygons.gpkg", "water_water_polygons"),
        "landuse_label_anchors": (root / "landuse_poi_dedup_qgis42/progressive_landuse_anchor/landuse_label_anchors.gpkg", "landuse_label_anchors"),
    }
    for prefix, directory in (("clipped", "clipped"), ("diff_bbox", "difference")):
        for kind in ("buildings_a", "landuse_a", "roads", "water_a", "waterways"):
            name = f"{prefix}_gis_osm_{kind}_free_1"
            result[name] = (root / directory / (name + ".gpkg"), name)
    name = "clipped_gis_osm_pois_a_free_1"
    result[name] = (root / "clipped" / (name + ".gpkg"), name)
    poi = root / "poi_wrapped_labels_qgis42_mapsplat/poi_progressive_qgis42_mapsplat_package/progressive_poi_zoom_data/progressive_named_pois.gpkg"
    for kind in ("emergency", "everyday", "local_services", "public_services"):
        result[f"progressive_named_pois_poi_{kind}"] = (poi, f"poi_{kind}")
    return result


def tile_message(data):
    message = vector_tile_pb2.tile()
    message.ParseFromString(gzip.decompress(data))
    return message


def without_out_of_region_buildings(data):
    """Remove the bbox-context building layer while retaining all other data."""
    source = tile_message(data)
    if all(layer.name != OUT_OF_REGION_BUILDING_LAYER for layer in source.layers):
        return data
    result = vector_tile_pb2.tile()
    for layer in source.layers:
        if layer.name != OUT_OF_REGION_BUILDING_LAYER:
            result.layers.add().CopyFrom(layer)
    if not result.layers:
        return None
    return gzip.compress(result.SerializeToString(), mtime=0)


def cache_directory(export, source_root):
    original = export / "data/layers.pmtiles"
    identity = {"original": hashlib.sha256(original.read_bytes()).hexdigest(),
                "zoom": LAST_REBUILT_ZOOM, "extent": EXTENT, "buffer": 164, "simplification": 0,
                "buildingBatchSize": BUILDING_BATCH_SIZE, "roadBatchSize": ROAD_BATCH_SIZE,
                "maxTileSize": MAX_TILE_SIZE,
                "buildingsMinZoom": BUILDINGS_MIN_ZOOM,
                "inputs": [(name, str(path.resolve()), path.stat().st_size, path.stat().st_mtime_ns, layer)
                           for name, (path, layer) in sources(source_root).items()]}
    key = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()[:16]
    return CACHE / "mapsplat-repair" / key


def generate_overviews(merged, work, names):
    """Independent GDAL datasets parallelize the million-polygon input scan."""
    options = ["MINZOOM=0", f"MAXZOOM={LAST_REBUILT_ZOOM}", f"EXTENT={EXTENT}",
               "BUFFER=164", "SIMPLIFICATION=0", "SIMPLIFICATION_MAX_ZOOM=0",
               f"MAX_SIZE={MAX_TILE_SIZE}", "MAX_FEATURES=2000000"]
    batches = []
    with sqlite3.connect(merged) as database:
        for name in names:
            # Buildings are retained from the export's full-precision z14-16
            # tiles only. This omits them from the overview rebuild entirely;
            # the style makes them visible only near the maximum zoom.
            if "osm_buildings" in name:
                continue
            if "osm_buildings" not in name and "osm_roads" not in name:
                batches.append((work / (name + ".pmtiles"), [name], None))
                continue
            batch_size = ROAD_BATCH_SIZE
            maximum = database.execute(f'SELECT MAX(fid) FROM "{name}"').fetchone()[0]
            for lower in range(0, maximum + 1, batch_size):
                path = work / f"{name}-{lower}.pmtiles"
                batches.append((path, [name], f"fid >= {lower} AND fid < {lower + batch_size}"))

    def generate(batch):
        path, layers, where = batch
        if path.exists():
            return path
        print(f"Generating {path.name}", flush=True)
        gdal.SetThreadLocalConfigOption("GDAL_NUM_THREADS", "1")
        temporary = path.with_name(path.stem + "-building.pmtiles")
        batch_options = list(options)
        dataset = gdal.VectorTranslate(str(temporary), str(merged), format="PMTiles",
            layers=layers, where=where, datasetCreationOptions=batch_options)
        assert dataset is not None, path
        dataset = None
        temporary.replace(path)
        print(f"Finished {path.name}", flush=True)
        return path

    with ThreadPoolExecutor(max_workers=6) as workers:
        futures = [workers.submit(generate, batch) for batch in batches]
        paths = [future.result() for future in as_completed(futures)]
    pieces = {}
    for path in sorted(paths):
        with path.open("rb") as stream:
            for xyz, data in all_tiles(MmapSource(stream)):
                pieces.setdefault(xyz, []).append(data)
    combined = []
    for xyz, data_parts in pieces.items():
        target = vector_tile_pb2.tile()
        for data in data_parts:
            for layer in tile_message(data).layers:
                existing = next((item for item in target.layers if item.name == layer.name), None)
                if existing is None:
                    target.layers.add().CopyFrom(layer)
                    continue
                assert existing.extent == layer.extent and existing.version == layer.version
                key_map = {key: index for index, key in enumerate(existing.keys)}
                for key in layer.keys:
                    if key not in key_map:
                        key_map[key] = len(existing.keys)
                        existing.keys.append(key)
                key_indices = [key_map[key] for key in layer.keys]
                value_offset = len(existing.values)
                existing.values.extend(layer.values)
                for feature in layer.features:
                    copied = existing.features.add()
                    copied.CopyFrom(feature)
                    copied.tags[:] = [key_indices[value] if index % 2 == 0 else value + value_offset
                                      for index, value in enumerate(feature.tags)]
        combined.append((xyz, gzip.compress(target.SerializeToString(), mtime=0)))
    return combined


def rebuild(export, source_root):
    work = cache_directory(export, source_root)
    work.mkdir(parents=True, exist_ok=True)
    original = export / "data/layers.pmtiles"
    with original.open("rb") as stream:
        reader = Reader(MmapSource(stream))
        metadata, header = reader.metadata(), reader.header()
    inputs = sources(source_root)
    assert set(inputs) == {layer["id"] for layer in metadata["vector_layers"]}
    merged = work / "overview.gpkg"
    prepared = work / "overview.ready"
    if merged.exists() and not prepared.exists():
        # An interrupted layer copy must not be mistaken for a complete input.
        assert merged.resolve().is_relative_to(CACHE.resolve())
        merged.unlink()
    for path, _ in inputs.values():
        assert path.is_file(), path
    existing = set()
    if merged.exists():
        dataset = gdal.OpenEx(str(merged), gdal.OF_VECTOR)
        existing = {layer.GetName() for layer in dataset}
        dataset = None
    for name, (path, layer) in inputs.items():
        if name in existing:
            continue
        print(f"Preparing {name}", flush=True)
        options = dict(format="GPKG", dstSRS="EPSG:3857", layerName=name,
                       accessMode="append" if merged.exists() else None)
        if layer:
            options["layers"] = [layer]
        dataset = gdal.VectorTranslate(str(merged), str(path), **options)
        assert dataset is not None, name
        dataset = None
    prepared.write_text("All original layers prepared.\n", encoding="utf-8")
    new_tiles = generate_overviews(merged, work, inputs)
    corrected = work / "layers.pmtiles"
    counts = Counter()
    largest = 0
    with original.open("rb") as old_stream, corrected.open("wb") as output:
        old = MmapSource(old_stream)
        old_tiles = []
        for xyz, data in all_tiles(old):
            if xyz[0] <= LAST_REBUILT_ZOOM:
                continue
            data = without_out_of_region_buildings(data)
            if data is not None:
                old_tiles.append((xyz, data))
        writer = Writer(output)
        for (z, x, y), data in sorted(new_tiles + old_tiles, key=lambda tile: zxy_to_tileid(*tile[0])):
            message = tile_message(data)
            minimum = EXTENT if z <= LAST_REBUILT_ZOOM else 4096
            assert all(layer.extent >= minimum for layer in message.layers), (z, x, y)
            for layer in message.layers:
                assert len(layer.features) < 2000000, (z, x, y, layer.name)
            largest = max(largest, len(data))
            counts[z] += 1
            writer.write_tile(zxy_to_tileid(z, x, y), data)
        writer.finalize(header, metadata)
    # Publish only after every tile passes precision checks. Update checksums
    # and package version so installed phones replace the old private archive.
    destination = ASSETS / "pmtiles/layers.pmtiles"
    import shutil
    shutil.copyfile(corrected, destination)
    manifest_path = ASSETS / "map/cavite_basemap.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    record = {"archive": "layers.pmtiles", "sizeBytes": destination.stat().st_size,
              "sha256": hashlib.sha256(destination.read_bytes()).hexdigest()}
    manifest.update(record)
    manifest["archives"] = [record if item["archive"] == record["archive"] else item for item in manifest["archives"]]
    manifest["version"] += 1
    manifest["exportStyleSha256"] = hashlib.sha256((export / "style.json").read_bytes()).hexdigest()
    manifest["geometryRepair"] = {"rebuiltZooms": [0, LAST_REBUILT_ZOOM], "extent": EXTENT,
        "buffer": 164, "simplification": 0, "maxTileSize": MAX_TILE_SIZE,
        "buildingsMinZoom": BUILDINGS_MIN_ZOOM,
        "excludedLayers": [OUT_OF_REGION_BUILDING_LAYER],
        "originalArchiveSha256": hashlib.sha256(original.read_bytes()).hexdigest()}
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    report = {"tilesByZoom": dict(counts), "largestCompressedTileBytes": largest,
              "sizeBytes": record["sizeBytes"], "sha256": record["sha256"]}
    (work / "validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", type=Path)
    parser.add_argument("source_root", type=Path)
    args = parser.parse_args()
    started = time.monotonic()
    rebuild(args.export.resolve(), args.source_root.resolve())
    print(f"Finished in {time.monotonic() - started:.1f}s", flush=True)
