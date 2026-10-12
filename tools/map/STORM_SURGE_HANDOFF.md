# Storm surge integration handoff

Updated: 2026-10-12. Workspace: `C:\Users\dom\AndroidStudioProjects\RESQ`.

## Request and constraints

- Add Project NOAH storm surge advisories 1, 2, 3, 4 as basemap overlays.
- Clip to the actual Cavite administrative union, not its bounding box.
- Collapsible hazard-family controls, advisory toggles, legible blending, small-screen support.
- Keep offline rendering efficient for lower-end devices and future overlays.
- Review https://hazards.bettercalapan.org/?family=storm-surge&layers=none&view=2d.
- Build, install with `adb install -r`, test physical USB phone, fix failures.
- Preserve saved data. Never use `connectedDebugAndroidTest`, uninstall, or clear data.
- Prefix all shell commands with `rtk proxy`. Read AGENTS.md and C:\Users\dom\.codex\RTK.md.

## Timeline

1. Inspected MapFragment, offline copier, layout, and existing dormant hazard model.
2. Reviewed Calapan reference: collapsible Map Controls, hazard family selection,
   separate Advisory 1-4 checkboxes, 2D map. Browser reference remains available during work.
3. Located BetterGov's attributed Project NOAH dataset mirror on Hugging Face.
   Cached its dataset card and directory listing under tools/map/.cache.
4. Downloaded and verified the four pinned Cavite ZIPs from the BetterGov PH
   Project NOAH mirror; generated a 2.54 MB Cavite-only PMTiles package.
5. Added four hidden MapLibre fill layers, collapsible Android controls, opacity,
   legend, metadata/checksum/license, and physical-device coverage tests.
6. Completed: validator passed 2,490 basemap checks; the physical TECNO test
   passed in 35.543s with saved data preserved.
7. Extended the storm-surge PMTiles archive from z16 to native z18 tiles
   (4.38 MB), kept one zoom of style headroom, and added a device assertion
   that the selected overlay remains visible at the map's z18 ceiling.
8. Fixed the hazard panel width at 272dp, made its slider fill the panel,
   kept the panel below the Cavite card in landscape, and moved the legend
   above the attribution footer. Final physical-device test passed in
   34.984s with all four advisory toggles and both orientations.
9. Replaced advisory checkable chips with fixed-size color-state buttons:
   selection changes only the chip colors, so no check icon changes chip
   dimensions or order. Removed the redundant Cavite/status card and added
   a dynamic metric scale bar that updates with camera zoom and latitude.
   The final physical-device test passed in 36.223s with saved data intact.

## Existing state (do not redo)

- Active basemap style: app/src/main/assets/map/cavite_mapsplat_style.json
  (187 basemap layers plus 4 hidden storm-surge layers).
- Main package v6: layers.pmtiles, 113792371 bytes, SHA256
  53b3d44cb9d71bd7d91e11e6b3b516077408a71fd3050ca9eeb3ffd19febc26b.
- Fitted viewport floor, admin lines at min zoom, buildings from z16, outside buildings removed.
- Last basemap-only device test passed in 34.458s. Preexisting .idea/misc.xml and
  gradle.properties changes must be preserved. No commits requested.
- Boundary input: C:\Downloads\shp basemap\cavite_region_boundary.json (23 regions).
- Offline PMTiles in app assets are ignored by Git but are present on disk.
- Storm-surge archive: app/src/main/assets/pmtiles/cavite_storm_surge.pmtiles,
  4,384,111 bytes, SHA256
  5881a35ad1c706d5acf308701ad5c99960d5679ab536b59e37ae0e7b6475e467,
  native tile range z8-z18.
- Map UI now has no top-left region/status card. The hazard panel starts at
  the top-left, advisory chips are fixed-size color buttons, and the scale
  bar is shown at the lower-right above the floating action button.

## Remaining work

None for the requested Storm surge SSA1-4 milestone. Future hazard families should
follow the same offline archive + manifest + hidden style-layer pattern and add
their own focused device assertions before being enabled.

## Commands and device

Physical serial: 1409025565032868, TECNO_CM7. Detect again before use.

```text
rtk proxy C:\Users\dom\AppData\Local\Android\Sdk\platform-tools\adb.exe devices -l
rtk proxy C:\Users\dom\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe tools/map/test_on_device.py --serial 1409025565032868 --adb C:\Users\dom\AppData\Local\Android\Sdk\platform-tools\adb.exe --java-home "C:\Program Files\Java\jdk-21"
```

QGIS GDAL writer: C:\Program Files\QGIS 4.2.3\bin\python-qgis.bat.
Bundled Python dependencies: tools/map/.cache/python (pmtiles, mapbox_vector_tile, shapely).
Instrumentation output: build/map-device/instrumentation.txt.
