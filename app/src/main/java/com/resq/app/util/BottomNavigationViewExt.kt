package com.resq.app.util

import com.google.android.material.bottomnavigation.BottomNavigationView

fun BottomNavigationView.clearSelection() {
    menu.setGroupCheckable(0, true, false) // temporarily disable exclusive checking
    for (i in 0 until menu.size()) {
        menu.getItem(i).isChecked = false
    }
    menu.setGroupCheckable(0, true, true)  // restore it
}