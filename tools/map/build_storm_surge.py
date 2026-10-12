"""Build the Cavite-only Project NOAH SSA1-4 offline vector package.

Run with QGIS python-qgis.bat. Download inputs separately using curl and verify
the pinned SHA-256 values below. Never use the nationwide tiles or bbox clipping.
"""
import gzip
import hashlib
import json
import math
from pathlib import Path
import sys
import zipfile
from collections import Counter

from osgeo import gdal, ogr, osr
from shapely.geometry import shape, mapping, Polygon, MultiPolygon
from shapely import make_valid
from shapely.ops import unary_union, transform

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / 'tools/map/.cache'
sys.path.insert(0, str(CACHE / 'python'))
from pmtiles.reader import MmapSource, Reader, all_tiles
from mapbox_vector_tile.Mapbox import vector_tile_pb2

ASSETS = ROOT / 'app/src/main/assets'
REVISION = 'c62166a255068a47be2d017f748a9c5cbcdddc7e'
BASE_URL = f'https://huggingface.co/datasets/bettergovph/project-noah-hazard-maps/resolve/{REVISION}'
gdal.UseExceptions()
ogr.UseExceptions()


def polygons(geometry):
    if isinstance(geometry, Polygon):
        yield geometry
    elif hasattr(geometry, 'geoms'):
        for part in geometry.geoms:
            yield from polygons(part)


def mercator(x, y, z=None):
    return x * 20037508.342789244 / 180, math.log(math.tan(math.pi/4 + math.radians(y)/2)) * 6378137


def main(boundary_file):
    work = CACHE / 'storm-surge'
    work.mkdir(parents=True, exist_ok=True)
    boundary_json = json.loads(boundary_file.read_text(encoding='utf-8'))
    boundary = unary_union([make_valid(shape(item['geometry'])) for item in boundary_json['features']])
    assert boundary.is_valid and len(boundary_json['features']) == 23
    gpkg = work / 'cavite_storm_surge.gpkg'
    if gpkg.exists():
        gpkg.unlink()  # Only this reproducible file within the verified cache is replaced.
    output = ogr.GetDriverByName('GPKG').CreateDataSource(str(gpkg))
    srs = osr.SpatialReference()
    srs.ImportFromEPSG(4326)
    srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
    reports = []
    checkpoints = []
    for advisory in range(1, 5):
        archive = CACHE / f'cavite-ssa{advisory}.zip'
        tree = json.loads((CACHE / f'noah-ssa{advisory}-tree.json').read_text())
        source_entry = next(x for x in tree if x['path'].endswith('/Cavite.zip'))
        checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
        assert checksum == source_entry['lfs']['oid'], f'SSA{advisory} input checksum failed'
        folder = work / f'ssa{advisory}'
        folder.mkdir(exist_ok=True)
        with zipfile.ZipFile(archive) as zipped:
            assert all(Path(entry.filename).name == entry.filename for entry in zipped.infolist())
            zipped.extractall(folder)
        dataset = ogr.Open(str(next(folder.glob('*.shp'))))
        source = dataset.GetLayer(0)
        assert source.GetSpatialRef().IsSame(srs), source.GetSpatialRef().ExportToWkt()
        name = f'storm_surge_ssa{advisory}'
        target = output.CreateLayer(name, srs, ogr.wkbPolygon)
        target.CreateField(ogr.FieldDefn('HAZ', ogr.OFTInteger))
        counts = Counter()
        areas = {}
        for feature in source:
            hazard = int(feature.GetField('HAZ'))
            assert hazard in (1, 2, 3)
            geometry = make_valid(shape(json.loads(feature.GetGeometryRef().ExportToJson())))
            clipped = geometry.intersection(boundary)
            assert clipped.difference(boundary).area < 1e-12
            parts = list(polygons(clipped))
            areas[hazard] = sum(transform(mercator, part).area for part in parts)
            largest = max(parts, key=lambda part: part.area)
            point = largest.representative_point()
            checkpoints.append(dict(advisory=advisory, HAZ=hazard, longitude=point.x, latitude=point.y))
            for part in parts:
                if part.area <= 0:
                    continue
                item = ogr.Feature(target.GetLayerDefn())
                item.SetField('HAZ', hazard)
                item.SetGeometry(ogr.CreateGeometryFromJson(json.dumps(mapping(part))))
                target.CreateFeature(item)
                counts[hazard] += 1
        reports.append(dict(advisory=advisory, sourceLayer=name,
                            url=BASE_URL + f'/Storm%20Surge/StormSurgeAdvisory{advisory}/Cavite.zip',
                            sourceSha256=checksum, polygonPartsByHazard=dict(counts), areaM2ByHazard=areas))
        print(f'SSA{advisory}: clipped {sum(counts.values())} polygon parts', flush=True)
    output = None
    tile_file = work / 'cavite_storm_surge.pmtiles'
    if tile_file.exists():
        tile_file.unlink()
    result = gdal.VectorTranslate(str(tile_file), str(gpkg), format='PMTiles',
        datasetCreationOptions=['MINZOOM=8', 'MAXZOOM=18', 'EXTENT=8192', 'BUFFER=64',
                                'SIMPLIFICATION=0', 'SIMPLIFICATION_MAX_ZOOM=0',
                                'MAX_SIZE=10000000', 'MAX_FEATURES=2000000'])
    assert result is not None
    result = None
    largest_bytes = 0
    tiles = Counter()
    with tile_file.open('rb') as stream:
        source = MmapSource(stream)
        header = Reader(source).header()
        for (z, x, y), data in all_tiles(source):
            tile = vector_tile_pb2.tile()
            tile.ParseFromString(gzip.decompress(data))
            assert all(layer.extent == 8192 for layer in tile.layers), (z, x, y)
            largest_bytes = max(largest_bytes, len(data))
            tiles[z] += 1
    assert largest_bytes < 512000, largest_bytes
    destination = ASSETS / 'pmtiles/cavite_storm_surge.pmtiles'
    import shutil
    shutil.copyfile(tile_file, destination)
    metadata = dict(version=1, archive=destination.name, sizeBytes=destination.stat().st_size,
                    sha256=hashlib.sha256(destination.read_bytes()).hexdigest(),
                    minTileZoom=8, maxTileZoom=18, extent=8192,
                    source='Project NOAH / UP / PAGASA',
                    mirror='BetterGov PH', revision=REVISION, license='ODC-ODbL-1.0',
                    licenseUrl='https://opendatacommons.org/licenses/odbl/1.0/',
                    primarySource='https://noah.up.edu.ph/',
                    sourceMetadataUrl=BASE_URL + '/Storm%20Surge/metadata_stormsurge.txt',
                    boundarySha256=hashlib.sha256(boundary_file.read_bytes()).hexdigest(),
                    boundaryRegions=23, clipping='Exact Cavite administrative polygon union before tiling',
                    advisoryHeightLabels=['up to 2 m', 'up to 3 m', 'up to 4 m', 'more than 4 m'],
                    hazardValues={'1':'Low', '2':'Medium', '3':'High'},
                    advisories=reports, tilesByZoom=dict(tiles), largestCompressedTileBytes=largest_bytes)
    (ASSETS / 'map/cavite_storm_surge.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    (ROOT / 'app/src/androidTest/assets/map/storm_surge_checkpoints.json').write_text(
        json.dumps(checkpoints, indent=2) + '\n', encoding='utf-8')
    # The precise clipping input is included with the derived open database for
    # reproducibility and validation; it is not loaded into the Android renderer.
    shutil.copyfile(boundary_file, ASSETS / 'map/storm_surge_clip_boundary.geojson')
    manifest_path = ASSETS / 'map/cavite_basemap.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    manifest['version'] += 1
    manifest['hazards'] = [{key: metadata[key] for key in ('archive', 'sizeBytes', 'sha256')}]
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(sizeBytes=metadata['sizeBytes'], largestCompressedTileBytes=largest_bytes,
                          tilesByZoom=dict(tiles)), indent=2), flush=True)


if __name__ == '__main__':
    main(Path(sys.argv[1]).resolve())
