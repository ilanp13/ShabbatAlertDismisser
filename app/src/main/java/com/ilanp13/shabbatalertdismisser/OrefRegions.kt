package com.ilanp13.shabbatalertdismisser

object OrefRegions {
    // Derive from OrefRegionCoords to ensure region selector and map stay in sync
    val all: List<String> = OrefRegionCoords.coords.keys.sorted()
}
