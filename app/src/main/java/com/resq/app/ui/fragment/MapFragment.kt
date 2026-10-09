package com.resq.app.ui.fragment

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.resq.app.R
import com.resq.app.databinding.FragmentMapBinding
import com.resq.app.map.BasemapPackage
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
import kotlin.math.abs
import kotlin.math.ceil

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

    private val cameraMoveListener = MapLibreMap.OnCameraMoveListener { constrainCamera() }
    private val cameraIdleListener = MapLibreMap.OnCameraIdleListener { onCameraIdle() }
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
        binding.mapView.onCreate(savedInstanceState)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupAttribution()
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
        currentBinding.tvMapStatus.setText(R.string.map_status_loading)
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
        val padding = MapCameraPolicy.overviewPadding(metadata.boundary, metadata.coverage, width, height)
        val unconstrainedFit = MapCameraPolicy.fit(metadata.boundary, width, height, padding)
        require(unconstrainedFit.zoom >= metadata.minTileZoom && unconstrainedFit.zoom < MAXIMUM_ZOOM)
        require(unconstrainedFit.zoom > MapCameraPolicy.coverageZoom(metadata.coverage, width + 2.0, height + 2.0)) {
            "Bundled map coverage is too small to display the complete province on this screen"
        }
        val fitted = MapCameraPolicy.constrain(unconstrainedFit,
            MapCameraPolicy.targetBounds(metadata.coverage, metadata.boundary, width, height, unconstrainedFit.zoom),
            unconstrainedFit.zoom, MAXIMUM_ZOOM)
        require(MapCameraPolicy.fitsWithinPadding(fitted, metadata.boundary, width, height, padding)) {
            "Bundled map coverage cannot contain the province and this viewport"
        }
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

    private fun onCameraIdle() {
        val currentMap = map ?: return
        val fitted = overview ?: return
        if (mapReady && cameraConfigured && !constrainingCamera &&
            currentMap.cameraPosition.zoom <= minimumZoom + 0.001) {
            val target = currentMap.cameraPosition.target
            // Returning to the zoom floor also returns to the complete province overview.
            if (target != null && (abs(target.longitude - fitted.longitude) > 0.0000001 ||
                abs(target.latitude - fitted.latitude) > 0.0000001)) {
                applyCamera(fitted)
            }
        }
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
        if (enabled && !currentBinding.progressMap.isVisible) {
            currentBinding.tvMapStatus.setText(when {
                zoom <= minimumZoom + 0.05 -> R.string.map_status_overview
                zoom >= 15.0 -> R.string.map_status_detail
                else -> R.string.map_status_exploring
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
            it.tvMapStatus.setText(R.string.map_load_error)
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
        private const val MAXIMUM_ZOOM = 18.0
    }
}
