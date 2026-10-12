"""Add the four hidden, offline storm-surge layers to the active map style."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STYLE = ROOT / 'app/src/main/assets/map/cavite_mapsplat_style.json'
SOURCE_NAME = 'noah'
LAYER_IDS = [f'haz-ssa{i}' for i in range(1, 5)]

COLORS = ['#F6D64A', '#F28C28', '#D64545']


def layer(advisory):
    return {
        'id': f'haz-ssa{advisory}',
        'type': 'fill',
        'source': SOURCE_NAME,
        'source-layer': f'storm_surge_ssa{advisory}',
        'minzoom': 8,
        # Leave one zoom of headroom because a style maxzoom is exclusive.
        'maxzoom': 19,
        'layout': {'visibility': 'none'},
        'paint': {
            'fill-color': ['match', ['get', 'HAZ'], 1, COLORS[0], 2, COLORS[1], 3, COLORS[2], '#000000'],
            'fill-opacity': 0.58,
            'fill-outline-color': 'rgba(90,55,20,0.4)',
        },
    }


style = json.loads(STYLE.read_text(encoding='utf-8'))
style['sources'][SOURCE_NAME] = {
    'type': 'vector',
    'url': 'pmtiles://file://{ARCHIVE_DIRECTORY}/cavite_storm_surge.pmtiles',
    'maxzoom': 18,
}
style['layers'] = [item for item in style['layers'] if item.get('id') not in LAYER_IDS]
insert_at = next((i for i, item in enumerate(style['layers'])
                  if item.get('id') == 'cavite_admin_boundary_uniform'), len(style['layers']))
style['layers'][insert_at:insert_at] = [layer(i) for i in range(1, 5)]
STYLE.write_text(json.dumps(style, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Added {len(LAYER_IDS)} hidden storm-surge layers to {STYLE}')
