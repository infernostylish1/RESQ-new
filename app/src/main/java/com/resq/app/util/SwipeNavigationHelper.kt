package com.resq.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.NavController
import com.resq.app.R
import kotlin.math.abs

/**
 * A ConstraintLayout that intercepts horizontal swipe flings and
 * delegates them to the NavController for tab switching / back navigation.
 *
 * Replaces the plain ConstraintLayout wrapping nav_host_fragment
 * and bottom_navigation in activity_main.xml.
 */
class SwipeNavigationLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var navController: NavController? = null
    private var onTabSwiped: ((Int) -> Unit)? = null

    private var gestureDetector: GestureDetector? = null

    // Set to true once we decide THIS touch sequence is a horizontal swipe
    private var intercepting = false

    // Track initial touch point to decide whether to intercept
    private var startX = 0f
    private var startY = 0f
    private val touchSlop by lazy {
        android.view.ViewConfiguration.get(context).scaledTouchSlop
    }

    companion object {
        private val TAB_ORDER = listOf(
            R.id.nav_home,
            R.id.nav_disasters,
            R.id.nav_first_aid,
            R.id.nav_map
        )

        private val SWIPE_DISABLED_DESTINATIONS = setOf(
            R.id.nav_onboarding,
            R.id.nav_global_search,
            R.id.nav_map
        )

        private const val SWIPE_THRESHOLD = 100
        private const val VELOCITY_THRESHOLD = 200
    }

    fun attach(
        navController: NavController,
        onTabSwiped: ((destinationId: Int) -> Unit)? = null
    ) {
        this.navController = navController
        this.onTabSwiped = onTabSwiped
        this.gestureDetector = GestureDetector(context, FlingListener())
    }

    // ── Touch interception ────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (navController == null) return super.onInterceptTouchEvent(ev)

        val currentId = navController?.currentDestination?.id
        if (currentId != null && currentId in SWIPE_DISABLED_DESTINATIONS) {
            return super.onInterceptTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                intercepting = false
                startX = ev.x
                startY = ev.y
                // Feed the DOWN to the gesture detector so it can track
                gestureDetector?.onTouchEvent(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!intercepting) {
                    val dx = abs(ev.x - startX)
                    val dy = abs(ev.y - startY)
                    if (dx > touchSlop && dx > dy * 1.5f) {
                        // Horizontal drag detected — but first check if a
                        // horizontally-scrollable child should own this gesture
                        if (isHorizontalScrollableChildUnder(startX, startY)) {
                            return false
                        }
                        intercepting = true
                        // Send a CANCEL to children so they release pressed states
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                intercepting = false
            }
        }

        return intercepting
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)

        // Must return true once we are intercepting, or we won't get
        // further MOVE / UP events in this sequence.
        return if (intercepting || event.actionMasked == MotionEvent.ACTION_DOWN) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    // ── Horizontal-scrollable child check ─────────────────────────────

    private fun isHorizontalScrollableChildUnder(x: Float, y: Float): Boolean {
        val target = findChildUnder(this, x.toInt(), y.toInt())
        var v: View? = target
        while (v != null && v !== this) {
            if (v.canScrollHorizontally(-1) || v.canScrollHorizontally(1)) {
                return true
            }
            v = v.parent as? View
        }
        return false
    }

    private fun findChildUnder(parent: ViewGroup, x: Int, y: Int): View? {
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue

            val lx = x - child.left + child.scrollX
            val ly = y - child.top + child.scrollY

            if (lx in 0..child.width && ly in 0..child.height) {
                return if (child is ViewGroup) {
                    findChildUnder(child, lx, ly) ?: child
                } else {
                    child
                }
            }
        }
        return null
    }

    // ── Fling listener ────────────────────────────────────────────────

    private inner class FlingListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val nav = navController ?: return false

            val dx = e2.x - e1.x
            val dy = e2.y - e1.y

            if (abs(dx) < abs(dy)) return false
            if (abs(dx) < SWIPE_THRESHOLD || abs(velocityX) < VELOCITY_THRESHOLD) return false

            val currentId = nav.currentDestination?.id ?: return false
            if (currentId in SWIPE_DISABLED_DESTINATIONS) return false

            val currentTabIndex = TAB_ORDER.indexOf(currentId)
            val isTopLevel = currentTabIndex != -1

            if (isTopLevel) {
                if (dx < 0) {
                    // Swipe LEFT → next tab
                    val next = currentTabIndex + 1
                    if (next < TAB_ORDER.size) {
                        nav.navigate(TAB_ORDER[next])
                        onTabSwiped?.invoke(TAB_ORDER[next])
                        return true
                    }
                } else {
                    // Swipe RIGHT → previous tab
                    val prev = currentTabIndex - 1
                    if (prev >= 0) {
                        nav.navigate(TAB_ORDER[prev])
                        onTabSwiped?.invoke(TAB_ORDER[prev])
                        return true
                    }
                }
            } else {
                // Detail screen: swipe RIGHT = go back
                if (dx > 0) {
                    nav.popBackStack()
                    return true
                }
            }
            return false
        }
    }
}
