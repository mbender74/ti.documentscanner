package ti.documentscanner

import android.graphics.*
import ti.documentscanner.extensions.distance
import ti.documentscanner.models.Document
import ti.documentscanner.utils.ImageUtil
import java.io.File
import java.util.Vector

/**
 * This class uses OpenCV to find document corners.
 *
 * @constructor creates document detector
 */
class DocumentDetector {
    companion object {
        private external fun nativeScan(srcBitmap: Bitmap, shrunkImageHeight: Int, imageRotation: Int): Vector<Vector<Point>>
        private external fun nativeCrop(srcBitmap: Bitmap, points: Array<Point>, outBitmap: Bitmap)
        init {
            try {
                System.loadLibrary("document_detector")
            } catch (exception: Exception) {}
        }

        /**
         * take a photo with a document, and find the document's corners
         *
         * @param image a photo with a document
         * @return a list with document corners (top left, top right, bottom right, bottom left)
         */
        fun findDocumentCorners(image: Bitmap, shrunkImageHeight: Double = 500.0, imageRotation: Int= 0): List<List<Point>>? {
            val outPoints =  nativeScan(image, shrunkImageHeight.toInt(), imageRotation)
            if (outPoints.size > 0) {
                if (outPoints[0].size == 0) {
                    return null
                }
                return (outPoints)
                return (outPoints)
            }
            return null
        }
        /**
         * take a photo with a document, crop everything out but document, and force it to display
         * as a rectangle
         *
         * @param document with original image data
         * @param colorFilter for this image
         * @return bitmap with cropped and warped document
         */
        fun cropDocument(document: Document, colorFilter: ColorFilter?): Bitmap {
            val file = File(document.originalPhotoPath)
            val bitmap = ImageUtil.getImageFromFile(file, 4000)

            // convert corners from image preview coordinates to original photo coordinates
            // (original image is probably bigger than the preview image)
            val preview = document.preview
            val corners =if (preview != null)  document.quad!!.mapPreviewToOriginalImageCoordinates(
                RectF(0f, 0f, 1f * preview.width, 1f * preview.height),
                1f * preview.height / bitmap.height
            ) else document.quad!!
            // convert output image matrix to bitmap
            val cropWidth = ((corners.topLeftCorner.distance(corners.topRightCorner)
                    + corners.bottomLeftCorner.distance(corners.bottomRightCorner)) / 2).toInt()
            val cropHeight = ((corners.bottomLeftCorner.distance(corners.topLeftCorner)
                    + corners.bottomRightCorner.distance(corners.topRightCorner)) / 2).toInt()

            val cropBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
            nativeCrop(bitmap,  corners.cornersList, cropBitmap)
//
            if (colorFilter != null) {
                val canvas = Canvas(cropBitmap)
                val paint = Paint()
                paint.colorFilter = colorFilter
                canvas.drawBitmap(cropBitmap, 0f, 0f, paint)
            }
            return cropBitmap
        }
    }

}