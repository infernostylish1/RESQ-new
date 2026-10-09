"""Build a focused, offline Cavite map from public Protomaps PMTiles.

See README.md for downloads and reproducible commands. Coordinates in MVT are
kept in tile space, so vector detail stays crisp when MapLibre overzooms.
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
import math
from pathlib import Path
import sys
import time

CACHE = Path(__file__).resolve().parent / ".cache"
if (CACHE / "python").exists():
    sys.path.insert(0, str(CACHE / "python"))

import mapbox_vector_tile
from pmtiles.reader import MmapSource, Reader, all_tiles
from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer
from shapely import make_valid
from shapely.affinity import affine_transform
from shapely.geometry import box, mapping, shape, MultiPolygon, MultiLineString
from shapely.ops import transform, unary_union

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
DEFAULT_COVERAGE = [119.4, 13.2, 122.0, 15.2]
SOURCE_URL = "https://build.protomaps.com/20261003.pmtiles"
FOCUSED_LAYERS = {"buildings", "roads", "pois", "landuse", "landcover", "natural"}
CONTEXT_LAYERS = {"earth", "water", "boundaries", "places"}
EXTENT = 4096
BUFFER = 64


def world_coordinates(lon, lat, _z=None):
    return (lon + 180) / 360, (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2


def json_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def prepare_boundary():
    original = json.loads((ASSETS / "map/cavite_region_boundary.json").read_text(encoding="utf-8"))
    municipalities = [make_valid(shape(feature["geometry"])) for feature in original["features"]]
    province = unary_union(municipalities)
    # About 11 m at Cavite's latitude; detailed data uses the unsimplified union.
    display = province.simplify(0.0001, preserve_topology=True)
    json_write(ASSETS / "map/cavite_outline.geojson", {
        "type": "FeatureCollection", "features": [{"type": "Feature", "properties": {"name": "Cavite"},
        "geometry": mapping(display)}]})
    label_features = []
    for feature, geometry in zip(original["features"], municipalities):
        name = feature["properties"]["adm3_en"].replace("City of ", "")
        if name == "Cavite":
            name = "Cavite City"
        if name == "Gen. Mariano Alvarez":
            name = "General Mariano Alvarez"
        # Cavite City also administers Corregidor and smaller offshore islands.
        # Anchor its municipal label on the largest polygon (the mainland city).
        label_geometry = max(geometry.geoms, key=lambda part: part.area) if geometry.geom_type == "MultiPolygon" else geometry
        point = label_geometry.representative_point()
        label_features.append({"type": "Feature", "properties": {"name": name,
            "psgc": feature["properties"]["adm3_psgc"]}, "geometry": mapping(point)})
    json_write(ASSETS / "map/cavite_municipalities.geojson", {"type": "FeatureCollection", "features": label_features})
    return province, transform(world_coordinates, province)


def dimensional_geometry(geometry, original):
    """Discard intersection fragments of a different dimension."""
    if geometry.is_empty:
        return None
    prefix = "Polygon" if "Polygon" in original.geom_type else "LineString" if "LineString" in original.geom_type else "Point"
    if geometry.geom_type == "GeometryCollection":
        parts = [part for part in geometry.geoms if prefix in part.geom_type]
        if not parts:
            return None
        if len(parts) == 1:
            geometry = parts[0]
        elif prefix == "Polygon":
            geometry = unary_union(parts)
        elif prefix == "LineString":
            geometry = unary_union(parts)
        else:
            geometry = unary_union(parts)
    return geometry if prefix in geometry.geom_type else None


def intersect_geometry(geometry, clip):
    if geometry.is_empty or clip.is_empty or not geometry.intersects(clip):
        return None
    if clip.covers(geometry):
        return geometry
    try:
        return dimensional_geometry(geometry.intersection(clip), geometry)
    except Exception:
        repaired = make_valid(geometry)
        return dimensional_geometry(repaired.intersection(clip), geometry)


def tile_focus(world_boundary, z, x, y):
    n = 1 << z
    padding = BUFFER / EXTENT
    local_world = world_boundary.intersection(box((x-padding)/n, (y-padding)/n, (x+1+padding)/n, (y+1+padding)/n))
    return affine_transform(local_world, [n*EXTENT, 0, 0, n*EXTENT, -x*EXTENT, -y*EXTENT])


def decode(data):
    layers = mapbox_vector_tile.decode(gzip.decompress(data), default_options={"y_coord_down": True})
    if any(layer.get("extent") != EXTENT for layer in layers.values()):
        raise ValueError("Expected the Protomaps MVT extent of 4096")
    return layers


def focus_tile(layers, focus):
    result = []
    for name, layer in layers.items():
        if name not in FOCUSED_LAYERS | CONTEXT_LAYERS:
            continue
        if name in FOCUSED_LAYERS and focus.is_empty:
            continue
        features = []
        for feature in layer["features"]:
            properties = dict(feature["properties"])
            kind = properties.get("kind", "")
            if name == "roads" and (kind == "ferry" or properties.get("kind_detail") == "ferry"):
                continue
            if name == "boundaries" and properties.get("maritime") in (True, 1, "yes", "true", "1"):
                continue
            geometry = shape(feature["geometry"])
            if name in FOCUSED_LAYERS:
                geometry = intersect_geometry(geometry, focus)
                if geometry is None:
                    continue
                properties["resq_focus"] = 1
            elif name == "places":
                inside = not focus.is_empty and focus.covers(geometry)
                if inside:
                    properties["resq_focus"] = 1
                elif kind not in ("region", "country") and properties.get("kind_detail") not in ("city", "town", "province", "state"):
                    continue
            elif name == "water" and "LineString" in geometry.geom_type:
                geometry = intersect_geometry(geometry, focus)
                if geometry is None:
                    continue
                properties["resq_focus"] = 1
            features.append({"geometry": geometry, "properties": properties, "id": feature.get("id", 0)})
        if features:
            result.append({"name": name, "features": features})
    return result


def subdivide_context(layers, dx, dy):
    """Overzoom subdued context one level without downloading outer buildings."""
    result = []
    clip = box(-BUFFER, -BUFFER, EXTENT + BUFFER, EXTENT + BUFFER)
    for layer in layers:
        if layer["name"] not in CONTEXT_LAYERS:
            continue
        features = []
        for feature in layer["features"]:
            # Precise street tiles provide every Cavite feature at z15.
            if feature["properties"].get("resq_focus") == 1:
                continue
            geometry = affine_transform(feature["geometry"], [2, 0, 0, 2, -dx * EXTENT, -dy * EXTENT])
            geometry = intersect_geometry(geometry, clip)
            if geometry is not None:
                features.append({**feature, "geometry": geometry})
        if features:
            result.append({"name": layer["name"], "features": features})
    return result


def encode(layers):
    return gzip.compress(mapbox_vector_tile.encode(layers, default_options={"y_coord_down": True,
        "extents": EXTENT}), compresslevel=6, mtime=0)


def build(args):
    started = time.monotonic()
    province, world_boundary = prepare_boundary()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    tile_counts, feature_counts = Counter(), Counter()
    pending = {}
    with open(args.detail, "rb") as detail_file, open(args.context, "rb") as context_file:
        detail_source, context_source = MmapSource(detail_file), MmapSource(context_file)
        context_reader = Reader(context_source)
        detail_tiles = dict(all_tiles(detail_source))
        metadata = context_reader.metadata()
        header = context_reader.header()
        for index, ((z, x, y), data) in enumerate(all_tiles(context_source), 1):
            layers = focus_tile(decode(data), tile_focus(world_boundary, z, x, y))
            pending[zxy_to_tileid(z, x, y)] = encode(layers)
            tile_counts[z] += 1
            feature_counts.update({layer["name"]: len(layer["features"]) for layer in layers})
            if z == 14:
                for dx in (0, 1):
                    for dy in (0, 1):
                        child = (15, x * 2 + dx, y * 2 + dy)
                        if child in detail_tiles:
                            child_layers = focus_tile(decode(detail_tiles.pop(child)), tile_focus(world_boundary, *child))
                        else:
                            child_layers = subdivide_context(layers, dx, dy)
                        pending[zxy_to_tileid(*child)] = encode(child_layers)
                        tile_counts[15] += 1
                        feature_counts.update({layer["name"]: len(layer["features"]) for layer in child_layers})
            if index % 1000 == 0:
                print(f"processed {index} context tiles ({time.monotonic()-started:.1f}s)", flush=True)
        if detail_tiles:
            raise RuntimeError(f"{len(detail_tiles)} street tiles fell outside context coverage")
        metadata.update({"name": "RESQ Cavite focused basemap", "description": "Detailed Cavite; subdued neighboring context. Ferry and maritime boundary lines excluded.",
            "bounds": ",".join(map(str, DEFAULT_COVERAGE)), "minzoom": 7, "maxzoom": 15,
            "resq:source_url": SOURCE_URL, "resq:context_zoom15": "subdivided from zoom14", "resq:focus": "supplied Cavite GeoJSON union"})
        metadata["vector_layers"] = [layer for layer in metadata.get("vector_layers", []) if layer["id"] in FOCUSED_LAYERS | CONTEXT_LAYERS]
        for layer in metadata["vector_layers"]:
            layer.setdefault("fields", {})["resq_focus"] = "Number"
        header.update({"min_lon_e7": round(DEFAULT_COVERAGE[0]*1e7), "min_lat_e7": round(DEFAULT_COVERAGE[1]*1e7),
            "max_lon_e7": round(DEFAULT_COVERAGE[2]*1e7), "max_lat_e7": round(DEFAULT_COVERAGE[3]*1e7),
            "center_zoom": 9, "center_lon_e7": round(province.centroid.x*1e7), "center_lat_e7": round(province.centroid.y*1e7),
            "tile_compression": Compression.GZIP, "tile_type": TileType.MVT})
        with open(output, "wb") as target:
            writer = Writer(target)
            for tile_id, tile in sorted(pending.items()):
                writer.write_tile(tile_id, tile)
            writer.finalize(header, metadata)
    manifest = {"version": 1, "archive": output.name, "boundary": list(province.bounds), "coverage": DEFAULT_COVERAGE,
        "minTileZoom": 7, "maxTileZoom": 15, "sizeBytes": output.stat().st_size,
        "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "source": SOURCE_URL,
        "osmSnapshot": metadata.get("planetiler:osm:osmosisreplicationtime"), "tilesByZoom": dict(sorted(tile_counts.items())),
        "featuresByLayer": dict(feature_counts)}
    json_write(ASSETS / "map/cavite_basemap.json", manifest)
    print(json.dumps(manifest, indent=2), flush=True)
    print(f"Built archive in {time.monotonic()-started:.1f}s", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--context", default=str(CACHE / "cavite_context_raw.pmtiles"))
    parser.add_argument("--detail", default=str(CACHE / "cavite_detail_raw.pmtiles"))
    parser.add_argument("--output", default=str(ASSETS / "pmtiles/cavite_basemap.pmtiles"))
    build(parser.parse_args())
