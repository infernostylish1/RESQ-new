import json
from pathlib import Path
import zipfile
from osgeo import ogr

cache = Path(__file__).resolve().parent / '.cache'
for advisory in range(1, 5):
    folder = cache / f'ssa{advisory}'
    folder.mkdir(exist_ok=True)
    with zipfile.ZipFile(cache / f'cavite-ssa{advisory}.zip') as zipped:
        for item in zipped.infolist():
            assert Path(item.filename).name == item.filename
        zipped.extractall(folder)
    dataset = ogr.Open(str(next(folder.glob('*.shp'))))
    layer = dataset.GetLayer(0)
    definition = layer.GetLayerDefn()
    print(advisory, layer.GetFeatureCount(), layer.GetExtent(), layer.GetSpatialRef().ExportToProj4())
    print([(definition.GetFieldDefn(i).GetName(), definition.GetFieldDefn(i).GetTypeName())
           for i in range(definition.GetFieldCount())])
    print(layer.GetNextFeature().ExportToJson()[:350])
