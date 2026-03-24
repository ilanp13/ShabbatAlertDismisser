package com.ilanp13.shabbatalertdismisser

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView

/**
 * Shared map tile configuration for both MapFragment and StatusFragment.
 *
 * Three styles:
 *   "minimal"   — CartoDB Dark Matter / Positron (clean, sparse)
 *   "grayscale" — OSM MAPNIK with desaturation filter
 *   "detailed"  — OSM MAPNIK with full color (no filter)
 */
object MapTileHelper {

    /** Apply the selected map style to a MapView */
    fun applyStyle(mapView: MapView, resources: Resources, style: String) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        when (style) {
            "minimal" -> {
                mapView.setTileSource(cartoTileSource(isDark))
                mapView.overlayManager.tilesOverlay.setColorFilter(null)
            }
            "grayscale" -> {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                val grayscale = ColorMatrix().apply { setSaturation(0f) }
                if (isDark) {
                    val invert = ColorMatrix(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    val combined = ColorMatrix()
                    combined.setConcat(invert, grayscale)
                    mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(combined))
                } else {
                    mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(grayscale))
                }
            }
            else -> { // "detailed"
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                if (isDark) {
                    val invert = ColorMatrix(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(invert))
                } else {
                    mapView.overlayManager.tilesOverlay.setColorFilter(null)
                }
            }
        }
    }

    private fun cartoTileSource(isDark: Boolean): OnlineTileSourceBase {
        val variant = if (isDark) "dark_all" else "light_all"
        return object : OnlineTileSourceBase(
            "CartoDB-$variant", 0, 19, 256, ".png",
            arrayOf("https://a.basemaps.cartocdn.com/$variant/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                val server = arrayOf("a", "b", "c")[(x % 3).toInt()]
                return "https://$server.basemaps.cartocdn.com/$variant/$z/$x/$y.png"
            }
        }
    }
}
