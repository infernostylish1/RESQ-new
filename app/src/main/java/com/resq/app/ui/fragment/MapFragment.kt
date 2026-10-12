package com.resq.app.ui.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.resq.app.R
import com.resq.app.databinding.FragmentMapBinding
import com.resq.app.map.BasemapPackage
import com.resq.app.map.MapLayerController
import com.resq.app.map.MapCameraPolicy
import com.resq.app.map.PmtilesAssetCopier
import com.resq.app.util.setupSettingsAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@AndroidEntryPoint
class MapFragment : Fragment() {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var map: MapLibreMap? = null
    private var mapPackage: BasemapPackage? = null
    private var mapReady = false
    private var cameraConfigured = false
    private var restoringCamera = false
    private var restoringOverview = false
    private var constrainingCamera = false
    private var minimumZoom = 0.0
    private var overview: MapCameraPolicy.Camera? = null
    private var targetBounds: MapCameraPolicy.Bounds? = null
    private var loadJob: Job? = null
    private var timeoutJob: Job? = null
    private var loadGeneration = 0
    private val selectedHazards = linkedSetOf<String>()
    private var hazardOpacity = 0.58f

    private val cameraMoveListener = MapLibreMap.OnCameraMoveListener { constrainCamera() }
    private val cameraIdleListener = MapLibreMap.OnCameraIdleListener { updateControls() }
    private val loadErrorListener = MapView.OnDidFailLoadingMapListener { error ->
        if (_binding != null) showMapError(IllegalStateException(error))
    }
    private val renderListener = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
        if (fully && mapReady && cameraConfigured && _binding != null) {
            timeoutJob?.cancel()
            binding.progressMap.isVisible = false
            updateControls()
        }
    }
    private val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom,
                                                               oldLeft, oldTop, oldRight, oldBottom ->
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
            // Native MapView sizing is updated by its own layout listener first.
            _binding?.mapView?.post {
                if (_binding != null && mapReady) {
                    try {
                        configureCamera(reset = !cameraConfigured)
                    } catch (error: Exception) {
                        showMapError(error)
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        restoringCamera = savedInstanceState?.getBoolean(STATE_HAS_CAMERA) == true
        restoringOverview = savedInstanceState?.getBoolean(STATE_AT_OVERVIEW) == true
        savedInstanceState?.getStringArrayList(STATE_HAZARDS)?.let {
            selectedHazards.clear()
            selectedHazards.addAll(it)
        }
        hazardOpacity = savedInstanceState?.getFloat(STATE_HAZARD_OPACITY, hazardOpacity) ?: hazardOpacity
        binding.mapView.onCreate(savedInstanceState)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupAttribution()
        setupHazardControls()
        binding.btnMapOverview.setOnClickListener { showOverview() }
        binding.btnMapZoomIn.setOnClickListener { changeZoom(1.0) }
        binding.btnMapZoomOut.setOnClickListener { changeZoom(-1.0) }
        binding.btnMapRetry.setOnClickListener { loadBasemap() }
        binding.mapView.addOnLayoutChangeListener(layoutListener)
        binding.mapView.addOnDidFailLoadingMapListener(loadErrorListener)
        binding.mapView.addOnDidFinishRenderingFrameListener(renderListener)
        val currentBinding = binding
        currentBinding.mapView.getMapAsync { mapLibreMap ->
            if (_binding !== currentBinding) return@getMapAsync
            map = mapLibreMap
            mapLibreMap.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false // Attribution remains visible in the map's own footer.
            }
            mapLibreMap.setMaxZoomPreference(MAXIMUM_ZOOM)
            mapLibreMap.addOnCameraMoveListener(cameraMoveListener)
            mapLibreMap.addOnCameraIdleListener(cameraIdleListener)
            loadBasemap()
        }
    }

    private fun loadBasemap() {
        val currentBinding = _binding ?: return
        val currentMap = map ?: return
        val context = requireContext().applicationContext
        loadJob?.cancel()
        timeoutJob?.cancel()
        val generation = ++loadGeneration
        mapReady = false
        currentBinding.progressMap.isVisible = true
        currentBinding.mapErrorContainer.isVisible = false
        currentBinding.mapScaleContainer.isVisible = false
        updateControls()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val metadata = BasemapPackage.load(context)
                    val archive = PmtilesAssetCopier.ensureCopied(context, metadata)
                    metadata to metadata.loadStyle(context, archive)
                }
                if (_binding !== currentBinding || generation != loadGeneration) return@launch
                mapPackage = loaded.first
                timeoutJob = viewLifecycleOwner.lifecycleScope.launch {
                    // Rendering pauses with the view; only foreground time can indicate a stalled map.
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        delay(30_000)
                        if (_binding === currentBinding && generation == loadGeneration &&
                            currentBinding.progressMap.isVisible) {
                            showMapError(IllegalStateException("Offline map rendering timed out"))
                        }
                    }
                }
                currentMap.setStyle(Style.Builder().fromJson(loaded.second)) {
                    if (_binding !== currentBinding || generation != loadGeneration) return@setStyle
                    mapReady = true
                    applyHazardState()
                    try {
                        configureCamera(reset = !restoringCamera || restoringOverview)
                        restoringCamera = false
                        restoringOverview = false
                    } catch (error: Exception) {
                        showMapError(error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_binding === currentBinding && generation == loadGeneration) showMapError(error)
            }
        }
    }

    private fun configureCamera(reset: Boolean) {
        val currentBinding = _binding ?: return
        val currentMap = map ?: return
        val metadata = mapPackage ?: return
        if (currentBinding.mapView.width <= 0 || currentBinding.mapView.height <= 0) return
        val wasOverview = cameraConfigured && currentMap.cameraPosition.zoom <= minimumZoom + 0.05
        val (width, height) = viewportSize()
        val unconstrainedFit = MapCameraPolicy.cover(metadata.coverage, width, height)
        require(unconstrainedFit.zoom >= metadata.minTileZoom && unconstrainedFit.zoom < MAXIMUM_ZOOM)
        val fitted = MapCameraPolicy.constrain(unconstrainedFit,
            MapCameraPolicy.targetBounds(metadata.coverage, metadata.boundary, width, height, unconstrainedFit.zoom),
            unconstrainedFit.zoom, MAXIMUM_ZOOM)
        minimumZoom = fitted.zoom
        overview = fitted
        currentMap.setMinZoomPreference(minimumZoom)
        cameraConfigured = true
        if (reset || wasOverview) {
            currentMap.cancelTransitions()
            applyCamera(fitted)
        } else {
            constrainCamera()
        }
        updateControls()
    }

    private fun viewportSize(): Pair<Double, Double> {
        val view = binding.mapView
        val density = resources.displayMetrics.density.toDouble()
        // MapLibre Native rounds its logical viewport dimensions upward.
        return ceil(view.width / density) to ceil(view.height / density)
    }

    private fun applyCamera(camera: MapCameraPolicy.Camera) {
        val currentMap = map ?: return
        val metadata = mapPackage ?: return
        val (width, height) = viewportSize()
        val bounds = MapCameraPolicy.targetBounds(metadata.coverage, metadata.boundary, width, height, camera.zoom)
        val constrained = MapCameraPolicy.constrain(camera, bounds, minimumZoom, MAXIMUM_ZOOM)
        constrainingCamera = true
        try {
            currentMap.setLatLngBoundsForCameraTarget(null)
            targetBounds = null
            currentMap.moveCamera(CameraUpdateFactory.newCameraPosition(constrained.toPosition()))
            installTargetBounds(bounds)
        } finally {
            constrainingCamera = false
        }
    }

    private fun constrainCamera() {
        if (!mapReady || !cameraConfigured || constrainingCamera || _binding == null) return
        val currentMap = map ?: return
        val metadata = mapPackage ?: return
        val position = currentMap.cameraPosition
        val target = position.target ?: return
        val zoom = position.zoom.coerceIn(minimumZoom, MAXIMUM_ZOOM)
        val (width, height) = viewportSize()
        val bounds = MapCameraPolicy.targetBounds(metadata.coverage, metadata.boundary, width, height, zoom)
        val constrained = MapCameraPolicy.constrain(
            MapCameraPolicy.Camera(target.longitude, target.latitude, zoom), bounds, minimumZoom, MAXIMUM_ZOOM
        )
        constrainingCamera = true
        try {
            installTargetBounds(bounds)
            if (abs(target.longitude - constrained.longitude) > 0.0000001 ||
                abs(target.latitude - constrained.latitude) > 0.0000001 ||
                abs(position.zoom - constrained.zoom) > 0.00001 ||
                position.bearing != 0.0 || position.tilt != 0.0) {
                currentMap.moveCamera(CameraUpdateFactory.newCameraPosition(constrained.toPosition()))
            }
        } finally {
            constrainingCamera = false
        }
    }

    private fun installTargetBounds(bounds: MapCameraPolicy.Bounds) {
        if (targetBounds == bounds) return
        map?.setLatLngBoundsForCameraTarget(LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west))
        targetBounds = bounds
    }

    private fun showOverview() {
        if (!mapReady || !cameraConfigured) return
        map?.cancelTransitions()
        overview?.let(::applyCamera)
        updateControls()
    }

    private fun changeZoom(delta: Double) {
        val currentMap = map ?: return
        if (!mapReady || !cameraConfigured) return
        val position = currentMap.cameraPosition
        val target = position.target ?: return
        val zoom = (position.zoom + delta).coerceIn(minimumZoom, MAXIMUM_ZOOM)
        if (zoom <= minimumZoom + 0.001) showOverview()
        else {
            currentMap.cancelTransitions()
            applyCamera(MapCameraPolicy.Camera(target.longitude, target.latitude, zoom))
            updateControls()
        }
    }

    private fun updateControls() {
        val currentBinding = _binding ?: return
        val zoom = map?.cameraPosition?.zoom ?: minimumZoom
        val enabled = mapReady && cameraConfigured && !currentBinding.mapErrorContainer.isVisible
        currentBinding.btnMapOverview.isEnabled = enabled
        currentBinding.btnMapZoomIn.isEnabled = enabled && zoom < MAXIMUM_ZOOM - 0.001
        currentBinding.btnMapZoomOut.isEnabled = enabled && zoom > minimumZoom + 0.001
        updateScaleBar()
    }

    private fun setupHazardControls() {
        binding.chipHazardStormSurge.setOnClickListener {
            binding.layoutHazardAdvisories.isVisible = !binding.layoutHazardAdvisories.isVisible
        }
        listOf(binding.chipSsa1, binding.chipSsa2, binding.chipSsa3, binding.chipSsa4).forEachIndexed { index, chip ->
            val id = "haz-ssa${index + 1}"
            val selected = selectedHazards.contains(id)
            updateHazardChip(chip, selected)
            chip.setOnClickListener {
                val checked = !selectedHazards.contains(id)
                if (checked) selectedHazards.add(id) else selectedHazards.remove(id)
                updateHazardChip(chip, checked)
                setHazardVisibility(id, checked)
                updateHazardLegend()
            }
        }
        binding.sliderHazardOpacity.value = hazardOpacity * 100f
        binding.sliderHazardOpacity.addOnChangeListener { _, value, _ ->
            hazardOpacity = value / 100f
            MapLayerController.layers.filter { it.hazardType == com.resq.app.map.HazardType.STORM_SURGE }
                .forEach { layer ->
                    map?.style?.getLayer(layer.id)?.setProperties(PropertyFactory.fillOpacity(hazardOpacity))
                }
        }
    }

    private fun applyHazardState() {
        val chips = listOf(binding.chipSsa1, binding.chipSsa2, binding.chipSsa3, binding.chipSsa4)
        MapLayerController.layers.filter { it.hazardType == com.resq.app.map.HazardType.STORM_SURGE }
            .forEachIndexed { index, layer ->
                val selected = selectedHazards.contains(layer.id)
                updateHazardChip(chips[index], selected)
                setHazardVisibility(layer.id, selected)
                map?.style?.getLayer(layer.id)?.setProperties(PropertyFactory.fillOpacity(hazardOpacity))
            }
        updateHazardLegend()
    }

    private fun updateHazardChip(chip: com.google.android.material.chip.Chip, selected: Boolean) {
        chip.isCheckable = false
        chip.isSelected = selected
        chip.chipBackgroundColor = ColorStateList.valueOf(
            Color.parseColor(if (selected) "#D9ECE4" else "#F1F4F1")
        )
        chip.chipStrokeColor = ColorStateList.valueOf(
            Color.parseColor(if (selected) "#4C766B" else "#D5DED9")
        )
        chip.setTextColor(Color.parseColor(if (selected) "#24473D" else "#334C46"))
    }

    private fun updateScaleBar() {
        val currentBinding = _binding ?: return
        val currentMap = map ?: return
        if (!mapReady || !cameraConfigured || currentBinding.mapView.width <= 0) {
            currentBinding.mapScaleContainer.isVisible = false
            return
        }
        val target = currentMap.cameraPosition.target ?: return
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(target.latitude)) /
            2.0.pow(currentMap.cameraPosition.zoom)
        val distanceMeters = chooseScaleDistance(metersPerPixel)
        val widthPixels = (distanceMeters / metersPerPixel).roundToInt()
            .coerceIn((64 * resources.displayMetrics.density).roundToInt(),
                (168 * resources.displayMetrics.density).roundToInt())
        currentBinding.tvMapScale.text = formatScaleDistance(distanceMeters)
        currentBinding.mapScaleLine.layoutParams = currentBinding.mapScaleLine.layoutParams.apply {
            width = widthPixels
        }
        currentBinding.mapScaleContainer.isVisible = true
    }

    private fun chooseScaleDistance(metersPerPixel: Double): Double {
        val desired = (metersPerPixel * 110.0).coerceAtLeast(1.0)
        val magnitude = 10.0.pow(floor(log10(desired)))
        val normalized = desired / magnitude
        val step = when {
            normalized >= 5.0 -> 5.0
            normalized >= 2.0 -> 2.0
            else -> 1.0
        }
        return step * magnitude
    }

    private fun formatScaleDistance(distanceMeters: Double): String {
        if (distanceMeters >= 1000.0) {
            val kilometers = distanceMeters / 1000.0
            return if (kilometers >= 10.0 || kilometers % 1.0 == 0.0) {
                "${kilometers.roundToInt()} km"
            } else {
                "${(kilometers * 10.0).roundToInt() / 10.0} km"
            }
        }
        return "${distanceMeters.roundToInt()} m"
    }

    private fun setHazardVisibility(id: String, visible: Boolean) {
        map?.style?.getLayer(id)?.setProperties(
            PropertyFactory.visibility(if (visible) "visible" else "none")
        )
    }

    private fun updateHazardLegend() {
        val shown = selectedHazards.isNotEmpty()
        binding.cardLegend.isVisible = shown
        if (!shown) return
        binding.tvLegendTitle.setText(R.string.map_legend_storm_surge)
        binding.layoutLegendRows.removeAllViews()
        listOf(
            R.string.map_legend_low to 0xFFF6D64A.toInt(),
            R.string.map_legend_medium to 0xFFF28C28.toInt(),
            R.string.map_legend_high to 0xFFD64545.toInt()
        ).forEach { (label, color) ->
            binding.layoutLegendRows.addView(TextView(requireContext()).apply {
                text = "●  ${getString(label)}"
                setTextColor(color)
                textSize = 12f
            })
        }
    }

    private fun showMapError(error: Exception) {
        Log.e(TAG, "Unable to load Cavite offline basemap", error)
        mapReady = false
        timeoutJob?.cancel()
        _binding?.let {
            it.progressMap.isVisible = false
            it.mapErrorContainer.isVisible = true
            updateControls()
        }
    }

    private fun MapCameraPolicy.Camera.toPosition(): CameraPosition = CameraPosition.Builder()
        .target(LatLng(latitude, longitude)).zoom(zoom).bearing(0.0).tilt(0.0).build()

    private fun setupToolbar() {
        binding.toolbar.setupSettingsAction(this)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                findNavController().navigate(R.id.nav_settings)
                true
            } else false
        }
    }

    private fun setupAttribution() {
        val attribution = SpannableString(getString(R.string.map_basemap_attribution))
        listOf("OpenStreetMap contributors" to "https://www.openstreetmap.org/copyright",
            "Protomaps" to "https://protomaps.com").forEach { (label, url) ->
            val start = attribution.toString().indexOf(label)
            if (start >= 0) attribution.setSpan(URLSpan(url), start, start + label.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvAttribution.text = attribution
        binding.tvAttribution.movementMethod = LinkMovementMethod.getInstance()
        binding.tvAttribution.setLinkTextColor(binding.tvAttribution.currentTextColor)
    }

    override fun onStart() { super.onStart(); _binding?.mapView?.onStart() }
    override fun onResume() { super.onResume(); _binding?.mapView?.onResume() }
    override fun onPause() { _binding?.mapView?.onPause(); super.onPause() }
    override fun onStop() { _binding?.mapView?.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); _binding?.mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_HAS_CAMERA, cameraConfigured)
        outState.putBoolean(STATE_AT_OVERVIEW,
            cameraConfigured && (map?.cameraPosition?.zoom ?: Double.MAX_VALUE) <= minimumZoom + 0.05)
        outState.putStringArrayList(STATE_HAZARDS, ArrayList(selectedHazards))
        outState.putFloat(STATE_HAZARD_OPACITY, hazardOpacity)
        _binding?.mapView?.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        ++loadGeneration
        loadJob?.cancel()
        timeoutJob?.cancel()
        map?.removeOnCameraMoveListener(cameraMoveListener)
        map?.removeOnCameraIdleListener(cameraIdleListener)
        _binding?.mapView?.apply {
            removeOnLayoutChangeListener(layoutListener)
            removeOnDidFailLoadingMapListener(loadErrorListener)
            removeOnDidFinishRenderingFrameListener(renderListener)
            onDestroy()
        }
        map = null
        mapPackage = null
        mapReady = false
        cameraConfigured = false
        overview = null
        targetBounds = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CaviteMap"
        private const val STATE_HAS_CAMERA = "cavite_map_has_camera"
        private const val STATE_AT_OVERVIEW = "cavite_map_at_overview"
        private const val STATE_HAZARDS = "cavite_map_hazards"
        private const val STATE_HAZARD_OPACITY = "cavite_map_hazard_opacity"
        private const val MAXIMUM_ZOOM = 18.0
    }
}
