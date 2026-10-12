# Offline Cavite basemap

## Active MapSplat export (2026-10-12)

The Map Module now loads `map/cavite_mapsplat_style.json`, imported from the
user's `android_map_cavite_region_webmap` export. Its 187 layers are preserved
verbatim, including colors, filters, labels, widths, ordering, and zoom ranges.
The administrative boundary rule is intentionally promoted to `minzoom: 0` so
municipality divisions are visible in the fitted province overview; its line
styling remains unchanged. Only resource URLs otherwise change: six PMTiles sources resolve to private local files,
glyphs resolve to bundled fonts, and `poi_sprite` resolves to bundled 1x/2x
sprite sheets. The manifest records each archive's size and SHA-256, and the
copier verifies each file before replacing its cached copy.

Reimport an updated export from the Android project root:

```text
rtk proxy python tools/map/import_mapsplat.py "C:\Downloads\shp basemap\android_map_cavite_region_webmap"
```

The main export archive contains zooms 0–16 and can be overzoomed to 18.
The camera starts at the lowest zoom that fills the entire MapView with the
export. This crops the longer map dimension and preserves geographic proportions,
so the regional map does not appear as a box surrounded by blank bands.
Zoom-out controls and gestures cannot go below this floor. Re-entering the module
returns to the centered view; rotation recalculates the floor for the viewport.
`coverage` comes from the main archive's actual bounds, and panning keeps every
viewport corner inside that rectangle. The reset action restores this filled view.

Physical-device tests exercise the active export's native layer loading,
all on-screen municipality names, sprites, street geometry, fuel markers, touch gestures,
zoom bounds, both orientations, re-entry, and preservation of saved app data.
Use the physical-device runner below, passing `--java-home` for a compatible
JDK if needed (this workstation uses `C:\Program Files\Java\jdk-21`).

## Storm surge overlay

The first hazard family is the Project NOAH storm surge package. Four
province-specific Cavite archives (SSA 1–4) are intersected with the precise
23-region Cavite administrative union before PMTiles generation. Each advisory
uses the NOAH `HAZ` values: 1 low, 2 medium, and 3 high. The compact offline
archive is `pmtiles/cavite_storm_surge.pmtiles`; its checksums, source URLs,
revision, and ODC-ODbL license are recorded in `map/cavite_storm_surge.json`.

The map style keeps all four fill layers hidden until the user opens Storm
surge and selects an advisory. The Android control exposes four independent
chips, a shared opacity slider, and a low/medium/high legend. The overlay is
inserted below the administrative boundary and labels, so it remains readable
without obscuring the map context.

### Repairing overview geometry

The original MapSplat main archive was generated with GDAL's default compressed
tile budget (500,000 bytes). Crowded tiles at z3-13 were reduced to grids as
small as 128 units, affecting every layer in those tiles. Roads, river lines,
and polygons then looked angular or disappeared between their endpoints.
Waiting for another rendered frame cannot restore geometry discarded during export.

`repair_mapsplat_tiles.py` regenerates z0-13 from the original QGIS vectors with
the standard 4096-unit grid, no simplification, a 164-unit overlap buffer, and
a 10 MB tile ceiling. This is twenty times the export's precision-loss budget
while keeping individual tiles practical for a phone. Buildings are clipped to
the Cavite administrative union and begin at z16, near the maximum detail zoom,
so the opening and intermediate views remain responsive while roads, rivers,
water, and land render completely. It removes the out-of-region building layer
from every zoom and retains all other z14-16 tile data and
all 187 style layers. Its cache is keyed by
the original archive, source-file timestamps/sizes, and geometry settings.
The manifest records the repair and updated SHA-256; the installed app replaces
its old private archive on its next map load.

After importing an export, run the repair using QGIS Python, then validate it:

```text
rtk proxy "C:\Program Files\QGIS 4.2.3\bin\python-qgis.bat" tools/map/repair_mapsplat_tiles.py "C:\Downloads\shp basemap\android_map_cavite_region_webmap" "C:\Downloads\shp basemap"
rtk proxy python tools/map/build_geometry_fixture.py "C:\Downloads\shp basemap"
rtk proxy python tools/map/validate_mapsplat_tiles.py "C:\Downloads\shp basemap\android_map_cavite_region_webmap"
```

The validator checks every tile's precision, 2,490 positions along original
major roads and named rivers at z8-13, byte equality for z14-16, and exact style
layer equality. `--baseline` reports defects in the original archive for comparison.
The native device test also checks on-screen road/river checkpoints at the
opening minimum in portrait/landscape and at an intermediate zoom.

The following Protomaps pipeline documents the previous basemap and remains
available for rebuilding that older package. It is not the active map style.

The app bundles `app/src/main/assets/pmtiles/cavite_basemap.pmtiles`: a genuine
PMTiles v3 archive of gzip-compressed Mapbox vector tiles. MapLibre can overzoom
its z15 vectors to street viewing zooms without raster blur. The supplied
`cavite_region_boundary.json` contains all 23 Cavite municipalities; its precise
union determines the data focus. The original boundary remains unchanged.

## Sources and attribution

- Public Protomaps Version 4.15.2 build:
  <https://build.protomaps.com/20261003.pmtiles>.
- OpenStreetMap snapshot recorded in its metadata: `2026-10-03T04:00:00Z`.
- Basemap data: **© OpenStreetMap contributors**. Attribution and license:
  <https://www.openstreetmap.org/copyright>.
- Natural Earth context: public domain, <https://www.naturalearthdata.com/about/terms-of-use/>.
- Cavite boundary: the user-supplied project GeoJSON, preserving its coordinates
  and municipality properties. No new claim is made about its original producer.

Protomaps documents its basemap as an Open Database License Produced Work:
<https://docs.protomaps.com/basemaps/downloads>. Generation code, schema, and
tools are documented at <https://docs.protomaps.com/basemaps/layers>,
<https://docs.protomaps.com/pmtiles/cli>, and
<https://github.com/protomaps/go-pmtiles>.

## Coverage and data policy

The province's exact bbox is approximately
`120.562081789,14.056549670,121.077498833,14.500089418`. The continuous context
coverage is `119.4,13.2,122.0,15.2`, sufficiently generous for phone portrait and
landscape overview fitting. The map view controls its initial/minimum zoom from
the measured viewport and the province bbox; archive minimum tile zoom is 7.

- `earth` and polygon `water` remain throughout the context extent. Thus
  coastlines, neighboring land, Manila Bay, and Taal/Laguna lakes continue to the
  viewport edges, including when a province fit has extra width or height.
- Cavite `roads`, `buildings`, `pois`, `natural`, `landuse`, and `landcover` are
  geometrically clipped to the precise supplied province union. Focused content
  has numeric `resq_focus=1`. Watercourse lines are also focused.
- Places inside Cavite have `resq_focus=1`. Outside Cavite, only region/country
  and city/town/province/state names remain. Land and water provide quiet
  surroundings; outer streets/buildings/POIs are removed before packaging,
  saving storage and device work. Retained Cavite building layers are visible
  only from z16.
- Ferry routes are removed and the transit layer is omitted. Boundary vectors
  explicitly marked maritime are filtered where the source provides that tag;
  the source schema does not promise a maritime flag on all administrative
  lines. The app therefore leaves the broad `boundaries` vector layer unstyled.
  It highlights only the supplied Cavite province/municipality GeoJSON outlines,
  preventing distracting administrative sea lines under future hazards. The
  retained vector boundary layer is available for future uses.
- Genuine z15 data provides individual buildings and street information within
  Cavite. The neighboring z15 context is derived by subdividing z14 geometry,
  giving continuous coverage without downloading high-detail outer buildings.
- Protomaps is a curated OSM basemap, not an exhaustive export of every OSM tag
  or feature. Data availability follows that upstream dataset.

Auxiliary assets are `map/cavite_outline.geojson` (dissolved external boundary,
display simplification approximately 11 m) and `map/cavite_municipalities.geojson`
(23 names at representative points). Data clipping uses the precise union,
independently of the display simplification. The manifest
`map/cavite_basemap.json` records bounds, size, SHA-256, source, zooms, and counts.
Its `featuresByLayer` counts are before final MVT integer quantization; they
include overlapping tile buffers and repeated features at different zooms.

The packaged archive is 28,115,495 bytes (26.8 MiB), covering 60,481 addressed
tiles at zooms 7–15. The full validator passed on all tiles, as did `pmtiles
verify`. Its largest compressed tile is 126,730 bytes. Fourteen subpixel
features quantized to empty geometry, with zero visible area; the validator
records those separately and checks all nonempty focused geometry against the
precise province boundary using a two-MVT-unit rounding tolerance.

## Rebuild

Download the `pmtiles` CLI from
<https://github.com/protomaps/go-pmtiles/releases> (the build used 1.31.2).
Create a local `.cache` directory under `tools/map`; it is ignored by Git.
These commands run from the project root. On this project's Windows setup,
replace `python` and `pmtiles` with the runtime Python and CLI executable paths.
Prefix shell commands with `rtk proxy` as required by project instructions.

```text
rtk proxy python -m pip install --target tools/map/.cache/python -r tools/map/requirements.txt
rtk proxy pmtiles extract https://build.protomaps.com/20261003.pmtiles tools/map/.cache/cavite_context_raw.pmtiles --bbox=119.4,13.2,122.0,15.2 --minzoom=7 --maxzoom=14
rtk proxy pmtiles extract https://build.protomaps.com/20261003.pmtiles tools/map/.cache/cavite_detail_raw.pmtiles --bbox=120.54,14.04,121.1,14.52 --minzoom=15 --maxzoom=15
rtk proxy python tools/map/build_cavite_basemap.py
rtk proxy python tools/map/validate_cavite_basemap.py
rtk proxy pmtiles verify app/src/main/assets/pmtiles/cavite_basemap.pmtiles
```

The public daily source URL may eventually expire. To update to another retained
Version 4 build, use its exact URL for both extracts, update `SOURCE_URL` in the
pipeline, rebuild, and validate. Do not silently reuse the city-only archive.
If a source is instead supplied as MBTiles, use `pmtiles convert input.mbtiles
output.pmtiles` before this processing stage; the bundled output is always
PMTiles v3.

The validator decodes every packaged tile, checks the manifest/hash and zooms,
checks land/water coverage, rejects sea lanes, and verifies focused geometry is
within Cavite to the MVT quantization tolerance. Android builds and physical
device interaction tests remain required separately.

## Physical-device testing

Select the physical serial from `rtk proxy adb devices -l`, then run:

```text
rtk proxy python tools/map/test_on_device.py --serial <physical-serial>
```

The runner saves current private app data under ignored `build/map-device`,
builds unit tests and APKs, installs both APKs with `adb install -r`, and calls
`am instrument` directly. It never uninstalls the app or clears data. The map
test checks native rendering, fitted zoom limits, street detail, quiet neighboring
context, drag/pinch gestures, rotation, and saved-data equality. Screenshots and
render timings are written to the device's app-specific external files directory;
instrumentation output is saved to `build/map-device/instrumentation.txt`.

Avoid Gradle's `connectedDebugAndroidTest` on a user's device: Android Gradle
Plugin's default UTP cleanup removes installed APKs and their private data.
`gradle.properties` also sets
`android.injected.androidTest.leaveApksInstalledAfterRun=true` as a safeguard.
