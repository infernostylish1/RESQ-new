"""Create native rendering checkpoints directly from original road/river lines."""
import argparse
from collections import defaultdict
import json
from pathlib import Path
import sqlite3
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent / ".cache/python"))
from shapely import from_wkb


def lines(path, condition):
    with sqlite3.connect(path) as database:
        table, column = database.execute("SELECT table_name,column_name FROM gpkg_geometry_columns").fetchone()
        for osm_id, blob in database.execute(f'SELECT osm_id,"{column}" FROM "{table}" WHERE {condition}'):
            envelope = (blob[3] >> 1) & 7
            geometry = from_wkb(blob[8 + (0, 32, 48, 48, 64)[envelope]:])
            if geometry.length >= 0.005:
                yield str(osm_id), geometry


def main(source_root):
    checks = []
    for kind, condition in (("roads", "fclass IN ('motorway','motorway_link','trunk','trunk_link','primary','primary_link')"),
                            ("waterways", "fclass='river' AND name IS NOT NULL AND name!=''")):
        layer = f"clipped_gis_osm_{kind}_free_1"
        # Spread checks across the province rather than only the longest route.
        cells = defaultdict(list)
        for osm_id, geometry in lines(source_root / "clipped" / (layer + ".gpkg"), condition):
            point = geometry.interpolate(0.5, normalized=True)
            cell = (int(point.x / 0.05), int(point.y / 0.05))
            cells[cell].append((osm_id, geometry))
        for cell in sorted(cells):
            osm_id, geometry = max(cells[cell], key=lambda item: item[1].length)
            for fraction in (0.15, 0.35, 0.55, 0.75, 0.95):
                point = geometry.interpolate(fraction, normalized=True)
                checks.append({"layer": layer, "osm_id": osm_id, "longitude": point.x, "latitude": point.y})
    destination = ROOT / "app/src/androidTest/assets/map/geometry_checkpoints.json"
    destination.write_text(json.dumps(checks, indent=2) + "\n", encoding="utf-8")
    print(f"Saved {len(checks)} original-geometry checkpoints: {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_root", type=Path)
    main(parser.parse_args().source_root)
