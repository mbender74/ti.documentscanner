package ti.documentscanner.models

import android.graphics.Bitmap
import android.graphics.Point

abstract class Analyzer {
    abstract fun getDocumentCorners(photo: Bitmap, shrunkImageHeight: Double = 500.0, imageRotation: Int= 0, returnDefault: Boolean = true): List<List<Point>>?
}