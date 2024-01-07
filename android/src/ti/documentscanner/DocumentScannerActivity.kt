package ti.documentscanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import org.appcelerator.kroll.common.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageInfo
import androidx.core.content.ContextCompat
import ti.documentscanner.cameraview.*
import ti.documentscanner.constants.DefaultSetting
import ti.documentscanner.constants.DocumentScannerExtra
import ti.documentscanner.extensions.*
import ti.documentscanner.models.Analyzer
import ti.documentscanner.models.Document
import ti.documentscanner.models.Quad
import ti.documentscanner.ui.CircleTextButton
import ti.documentscanner.ui.CropView
import ti.documentscanner.ui.ImageCropView
import ti.documentscanner.utils.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.min
import android.view.LayoutInflater
import ti.documentscanner.DocumentDetector
import android.view.WindowInsetsController
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import ti.documentscanner.TiDocumentscannerModule
import android.os.Handler

/**
 * This class contains the main document scanner code. It opens the camera, lets the user
 * take a photo of a document (homework paper, business card, etc.), detects document corners,
 * allows user to make adjustments to the detected corners, depending on options, and saves
 * the cropped document. It allows the user to do this for 1 or more documents.
 *
 * @constructor creates document scanner activity
 */
class DocumentScannerActivity : AppCompatActivity() {

    private final val TAG = "DocumentScannerActivity"

    private var inPhotoMode = true

    private var livePreview = true
    private var liveCounter = 0
    private var cameraFadein = false

    private var takingPhoto = false

    /**
     * @property maxNumDocuments maximum number of documents a user can scan at a time
     */
    private var showColorFilters = DefaultSetting.SHOW_COLOR_FILTERS

    /**
     * @property maxNumDocuments maximum number of documents a user can scan at a time
     */
    private var maxNumDocuments = DefaultSetting.MAX_NUM_DOCUMENTS

    /**
     * @property maxNumSimultaneousDocuments maximum number of documents can be simultaneously scanned
     */
    private var maxNumSimultaneousDocuments = DefaultSetting.MAX_NUM_SIMULTANEOUS_DOCUMENTS

    /**
     * @property letUserAdjustCrop whether or not to let user move automatically detected corners
     */
    private var letUserAdjustCrop = DefaultSetting.LET_USER_ADJUST_CROP

    /**
     * @property croppedImageQuality the 0 - 100 quality of the cropped image
     */
    private var croppedImageQuality = DefaultSetting.CROPPED_IMAGE_QUALITY

    /**
     * @property customAnalyserClass a custom class to to analyse images
     */
    private var customAnalyserClass: String? = null

    /**
     * @property customAnalyser a custom analyzer
     */
    private var customAnalyzer: Analyzer? = null

    /**
     * @property flashMode the default flash mode
     */
    private var flashMode: CameraFlashMode = DefaultSetting.FLASH_MODE
    private var autoFocus: Boolean = DefaultSetting.AUTO_FOCUS

    /**
     * @property cropperOffsetWhenCornersNotFound if we can't find document corners, we set
     * corners to image size with a slight margin
     */
    private val cropperOffsetWhenCornersNotFound = 100.0

    /**
     * @property document This is the current document. Initially it's null. Once we capture
     * the photo, and find the corners we update document.
     */
    private var document: Document? = null

    /**
     * @property documents a list of documents (original photo file path, original photo
     * dimensions and 4 corner points)
     */
    private val documents = mutableListOf<Document>()

    /**
     * @property cameraUtil gets called with photo file path once user takes photo, or
     * exits camera
     */
    private fun onPhotoCaptureSuccess(file: File) {
        setPhotoMode(false)
        // if maxNumDocuments is 3 and this is the 3rd photo, hide the new photo button since
        // we reach the allowed limit
        if (documents.size == maxNumDocuments - 1) {
            val newPhotoButton: ImageButton = findViewById(R.id.new_photo_button)
            newPhotoButton.isClickable = false
            newPhotoButton.visibility = View.INVISIBLE
        }

        // get bitmap from photo file path
        val previewBitmap = runCatching {
            ImageUtil.getImageFromFile(file, maxWidth = screenWidth)
        }
            .onFailure { e ->
                e.printStackTrace()
                finishIntentWithError(e.message ?: "Error")
            }
            .getOrNull() ?: return

        // get document corners by detecting them, or falling back to photo corners with
        // slight margin if we can't find the corners
        var pointsList: List<List<Point>>?;
        var quads: List<Quad>?
        if (customAnalyzer != null) {
            pointsList = customAnalyzer!!.getDocumentCorners(previewBitmap)
        } else {
            pointsList = getDocumentCorners(previewBitmap)
        }
        if (pointsList != null) {
            quads = pointsList.map { points ->
                points.sortedBy { it.y }
                    .chunked(2)
                    .map { it.sortedBy { point -> point.x } }
                    .flatten()
            }.map { points -> Quad(points) }
            val maxItems = min(maxNumSimultaneousDocuments, quads.size)
            quads = quads.subList(0, maxItems)
        } else {
            quads = null;
        }
        if (quads == null) {
            finishIntentWithError(
                "unable to get document corners"
            )
        }

        document = Document(file.absolutePath, previewBitmap, null,null)

        if (letUserAdjustCrop) {
            // user is allowed to move corners to make corrections
            try {
                var buttonsOffset = 0
                if (!showColorFilters) {
                    buttonsOffset = filtersView.measuredHeight
                    filtersView.visibility = View.GONE
                }
                // set preview image height based off of photo dimensions
                imageView.setImagePreviewBounds(
                    previewBitmap,
                    screenWidth,
                    screenHeight,
                    buttonsOffset
                )

                // display original photo, so user can adjust detected corners
                imageView.setImage(previewBitmap)

                // document corner points are in original image coordinates, so we need to
                // scale and move the points to account for blank space (caused by photo and
                // photo container having different aspect ratios)

                val cornersInImagePreviewCoordinates = quads!!.map { quad ->
                    quad.mapOriginalToPreviewImageCoordinates(
                        imageView.imagePreviewBounds,
                        imageView.imagePreviewBounds.height() / previewBitmap.height
                    )
                }
                // display cropper, and allow user to move corners
                imageView.quads = (cornersInImagePreviewCoordinates)
            } catch (exception: Exception) {
                exception.printStackTrace()
                finishIntentWithError(
                    "unable get image preview ready: ${exception.message}"
                )
                return
            }
        } else {
            // user isn't allowed to move corners, so accept automatically the first detected corners
            document?.let { document ->
               // Log.d(TAG, "adding document " + documents)
                documents.add(Document(file.absolutePath, previewBitmap, quads!![0],null))
                updateCounterButton()
            }

            // create cropped document image, and return file path to complete document scan
            cropDocumentAndFinishIntent()
        }
    }

    /**
     * @property imageView container with original photo and cropper
     */
    private lateinit var imageView: ImageCropView

    /**
     * @property cameraView container for camera view
     */
    private lateinit var cameraView: CameraView

    /**
     * @property cameraView container for camera view
     */
    private lateinit var cropView: CropView

    /**
     * @property filtersView containerfor filters
     */
    private lateinit var filtersView: View
    private lateinit var cameraUIContainer: View

    private lateinit var flashModeButton: ImageButton
    private lateinit var newPhotoButton: ImageButton
    private lateinit var completeDocumentScanButton: ImageButton
    private lateinit var retakePhotoButton: ImageButton
    private lateinit var counterButton: CircleTextButton

    private lateinit var yuvToRgbConverter: YuvToRgbConverter
    private lateinit var scannerModule: TiDocumentscannerModule


    /**
     * called when activity is created
     *
     * @param savedInstanceState persisted data that maintains state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        StrictMode.setVmPolicy(
//            StrictMode.VmPolicy.Builder()
//                .detectLeakedClosableObjects()
//                .penaltyLog()
//                .build()
//        )
        yuvToRgbConverter = YuvToRgbConverter(this)




        // Show cropper, accept crop button, add new document button, and
        // retake photo button. Since we open the camera in a few lines, the user
        // doesn't see this until they finish taking a photo
/*
        window.setFlags(
            LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.addFlags(
            LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(
            LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        window.addFlags(
            LayoutParams.FLAG_LAYOUT_IN_OVERSCAN
        )        
*/
//        window.decorView.setAlpha(0f)
         scannerModule = TiDocumentscannerModule.getInstance()

       // setContentView(scannerModule.overlayView())

        
        setContentView(R.layout.activity_image_crop)
        
        cameraUIContainer  = findViewById(R.id.camera_ui_container)
        
        cameraUIContainer.setVisibility(View.INVISIBLE)
        cameraUIContainer.setAlpha(0f)

    //    imageView = scannerModule.findView("image_view") as ImageCropView
        imageView = findViewById(R.id.image_view)
//        cameraView = scannerModule.findView("camera_view") as CameraView
//        cropView = scannerModule.findView("crop_view") as CropView
 //       filtersView = scannerModule.findView("filters_view") as View


        cameraView = findViewById(R.id.camera_view)
        cropView = findViewById(R.id.crop_view)
        filtersView = findViewById(R.id.filters_view)
        

        //cameraView.cameraPreviewView().setAlpha(0f)
        
//         setFullscreen()

 //       window.decorView.rootView.setVisibility(View.INVISIBLE)
//        window.decorView.rootView.setAlpha(0f)
//        cameraView.setVisibility(View.INVISIBLE)
//        cameraView.setAlpha(0f)

        
        if (inPhotoMode) {
            requestCameraPermission()
            cameraView.autoFocus = autoFocus
        }
        if (showColorFilters) {
            val filtersIds = listOf(R.id.filter0, R.id.filter1, R.id.filter2, R.id.filter3)
            val filters = filtersIds.map { findViewById<View>(it) }
            filters.forEach { v ->
                v.setOnClickListener { _ ->
                    if (v.isSelected) return@setOnClickListener

                    filters.forEach { it.isSelected = (it === v) }
                    val colorFilter = when (v.id) {
                        R.id.filter1 -> ImageUtil.getColorMatrixFilter(saturation = 0f)
                        R.id.filter2 -> ImageUtil.getColorMatrixFilter(
                            contrast = 1.4f,
                            brightness = 0.65f,
                            saturation = 0f
                        )
                        R.id.filter3 -> ImageUtil.getColorMatrixFilter(contrast = 2f)
                        else -> null
                    }
                    imageView.colorFilter = colorFilter
                }
            }
            filters.first().performClick()
        }

        try {
            if (intent.extras != null) {
                customAnalyserClass = intent.extras!!.getString(
                    DocumentScannerExtra.EXTRA_CUSTOM_ANALYSER_CLASS,
                    customAnalyserClass
                )
                maxNumDocuments = intent.extras!!.getInt(
                    DocumentScannerExtra.EXTRA_MAX_NUM_DOCUMENTS,
                    maxNumDocuments
                )
                maxNumSimultaneousDocuments = intent.extras!!.getInt(
                    DocumentScannerExtra.EXTRA_MAX_NUM_SIMULTANEOUS_DOCUMENTS,
                    maxNumSimultaneousDocuments
                )
                letUserAdjustCrop = intent.extras!!.getBoolean(
                    DocumentScannerExtra.EXTRA_LET_USER_ADJUST_CROP,
                    letUserAdjustCrop
                )
                showColorFilters = intent.extras!!.getBoolean(
                    DocumentScannerExtra.EXTRA_SHOW_COLOR_FILTERS,
                    showColorFilters
                )
                autoFocus =
                    intent.extras!!.getBoolean(DocumentScannerExtra.EXTRA_AUTO_FOCUS, autoFocus)
                croppedImageQuality = intent.extras!!.getInt(
                    DocumentScannerExtra.EXTRA_CROPPED_IMAGE_QUALITY,
                    croppedImageQuality
                )
                flashMode = CameraFlashMode.from(
                    intent.extras!!.getInt(
                        DocumentScannerExtra.EXTRA_FLASH_MODE,
                        flashMode.value
                    )
                ) as CameraFlashMode

            }

            // if we don't want user to move corners, we can let the user only take 1 photo
            if (!letUserAdjustCrop) {
                maxNumDocuments = 1
            }

        } catch (exception: Exception) {
            exception.printStackTrace()
            finishIntentWithError(
                "invalid extra: ${exception.message}"
            )
            return
        }

        // set click event handlers for new document button, accept and crop document button,
        // and retake document photo button
        flashModeButton = findViewById(R.id.flash_button)
        newPhotoButton = findViewById(R.id.new_photo_button)
        completeDocumentScanButton = findViewById(
            R.id.complete_document_scan_button
        )
        retakePhotoButton = findViewById(R.id.retake_photo_button)
        counterButton = findViewById(R.id.document_counter_button)


        newPhotoButton.onClick { onClickNew() }
        completeDocumentScanButton.onClick { onClickDone() }
        retakePhotoButton.onClick { onClickRetake() }
        flashModeButton.onClick { swithFlashMode() }

        updateFlashMode()
        // open camera, so user can snap document photo
        try {
            setPhotoMode(inPhotoMode)
        } catch (exception: Exception) {
            exception.printStackTrace()
            finishIntentWithError(
                "error opening camera: ${exception.message}"
            )
        }
    }





private fun setFullscreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
}


    private fun onCameraPermission(granted: Boolean) {
                                                       // Log.e(TAG, "onCameraPermission ")

        if (granted) {
            startCameraPreview()
            if (customAnalyzer == null && customAnalyserClass != null) {
                customAnalyzer =
                    Class.forName(customAnalyserClass).kotlin.objectInstance as Analyzer
            }
            
            cameraView.listener = object : CameraEventListener {
            override fun onReady() {
                                      //  Log.e(TAG, "onReady ")

            }

            override fun onCameraOpen() {         
               // Log.e(TAG, "onCameraOpen ")
                runOnUiThread {
                        //cameraView.setVisibility(View.VISIBLE)
                        //cameraUIContainer.animate().alpha(1f).setDuration(350).start()
                }

            }

            override fun onCameraStreaming() {
                    if (cameraFadein == false){
                         cameraFadein = true 
                       // Log.e(TAG, "onCameraStreaming ")
                        Handler().postDelayed({
                            runOnUiThread {
                                cameraUIContainer.setVisibility(View.VISIBLE)
                                cameraUIContainer.animate().alpha(1f).setDuration(300).start()
                            }
                        }, 20)
                    }
                }

            override fun onCameraClose() {
                   // Log.e(TAG, "onCameraClose ")
            }

            override fun onCameraPhoto(file: File?) {
               // Log.d(TAG, "onCameraPhoto " + file.toString())
                runOnUiThread {
                    takingPhoto = false
                    livePreview = true
                   // cameraView.listener = null
                    if (file != null) {
                        try {
                            onPhotoCaptureSuccess(file)
                        } catch (exception: Exception) {
                            exception.printStackTrace()
                        }
                    }
                }
            }

            override fun onCameraPhotoImage(
                image: Image?,
                info: ImageInfo,
                processor: ImageAsyncProcessor
            ) {
               // Log.d(TAG, "onCameraPhotoImage " + image?.width!! + " " + image?.height!!)
                runOnUiThread {
                    takingPhoto = false
                    livePreview = true
                   // cameraView.listener = null
                }
            }

            override fun onCameraVideo(file: File?) {
                   // Log.e(TAG, "onCameraVideo ")
            }

            override fun onCameraAnalysis(analysis: ImageAnalysis) {
            }

            override fun onCameraError(message: String, ex: java.lang.Exception) {
                                                                  //      Log.e(TAG, "onCameraError ")

                runOnUiThread {
                    takingPhoto = false
                }
            }

            override fun onCameraVideoStart() {
                                    //    Log.e(TAG, "onCameraVideoStart ")

            }

        }

            
            
            
            
            cameraView.analyserCallback = object : ImageAnalysisCallback {
                override fun process(
                    image: Image,
                    info: ImageInfo,
                    processor: ImageAsyncProcessor
                ) {
                    if (!livePreview) {
                        processor.finished()
                        return
                    }
                    try {
                        var previewBitmap =
                            Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                        yuvToRgbConverter.yuvToRgb(image, previewBitmap)
                        var pointsList: List<List<Point>>?;
                        if (customAnalyzer != null) {
                            pointsList = customAnalyzer!!.getDocumentCorners(
                                previewBitmap,
                                500.0,
                                info.rotationDegrees,
                                false
                            )
                            
                        } else {
                            pointsList = getDocumentCorners(
                                previewBitmap,
                                500.0,
                                info.rotationDegrees,
                                false
                            )
                        }
                        if (pointsList != null) {
                            var photoHeight: Int;
                            if (info.rotationDegrees == 180 || info.rotationDegrees == 0) {
                                photoHeight = image.height
                            } else {
                                photoHeight = image.width
                            }
                            val ratio = cropView.height.toFloat() / photoHeight.toFloat();
                            val quads = pointsList.map { points ->
                                points.sortedBy { it.y }
                                    .chunked(2)
                                    .map { it.sortedBy { point -> point.x } }
                                    .flatten()
                            }
                            val maxItems = min(maxNumSimultaneousDocuments, quads.size)
                            cropView.quads = quads!!.subList(0, maxItems).map { points -> Quad(points) }.map { quad ->
                                quad.applyRatio(ratio)
                            }
                            
                            if (liveCounter == 100){
                                    runOnUiThread {
                                        takePhoto()
                                    }
                            }
                            else {
                                liveCounter = liveCounter + 1
                            }
                            
                        } else {
                            cropView.quads = null;
                        }
                        cropView.invalidate()
                        previewBitmap.recycle()
                        processor.finished()
                    } catch (exception: Exception) {
                        exception.printStackTrace()
//                        Log.e(TAG, exception.localizedMessage)
                    }
                }
            }
        }
    }

    private fun takePhoto() {
        if (takingPhoto) {
            return
        }
        liveCounter = 0
        takingPhoto = true
      //  Log.d(TAG, "takePhoto ")
        livePreview = false
        cameraView.takePhoto()
    }
    
    
    
    

    private fun requestCameraPermission() {
                               //                                 Log.e(TAG, "requestCameraPermission ")

        val permission = Manifest.permission.CAMERA;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(permission!!) == PackageManager.PERMISSION_GRANTED) {
                onCameraPermission(true)
            } else {
                requestPermissions(
                    arrayOf(
                        permission
                    ), 701
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults!!)
                                              //                          Log.e(TAG, "onRequestPermissionsResult ")

        if (requestCode == 701) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (p in permissions) {
                    if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) {
                        onCameraPermission(true)
                    }
                }
            }
        }
    }

    /**
     * Pass in a photo of a document, and get back 4 corner points (top left, top right, bottom
     * right, bottom left). This tries to detect document corners, but falls back to photo corners
     * with slight margin in case we can't detect document corners.
     *
     * @param photo the original photo with a rectangular document
     * @return a List of 4 OpenCV points (document corners)
     */
    private fun getDocumentCorners(
        photo: Bitmap,
        shrunkImageHeight: Double = 500.0,
        imageRotation: Int = 0,
        returnDefault: Boolean = true
    ): List<List<Point>>? {
        val cornerPoints: List<List<Point>>? =
            DocumentDetector.findDocumentCorners(photo, shrunkImageHeight, imageRotation)
        // if cornerPoints is null then default the corners to the photo bounds with a margin
        var default = if (returnDefault) listOf(
            Point(
                cropperOffsetWhenCornersNotFound.toInt(),
                cropperOffsetWhenCornersNotFound.toInt()
            ),
            Point(
                (photo.width.toDouble() - cropperOffsetWhenCornersNotFound).toInt(),
                cropperOffsetWhenCornersNotFound.toInt()
            ),
            Point(
                cropperOffsetWhenCornersNotFound.toInt(),
                (photo.height.toDouble() - cropperOffsetWhenCornersNotFound.toInt()).toInt()
            ),
            Point(
                (photo.width.toDouble() - cropperOffsetWhenCornersNotFound).toInt(),
                (photo.height.toDouble() - cropperOffsetWhenCornersNotFound).toInt()
            )
        ) else null
        return if (cornerPoints != null) cornerPoints else (if (default != null) listOf(default) else null)
    }

    /**
     * Set document to null since we're capturing a new document, and open the camera. If the
     * user captures a photo successfully document gets updated.
     */
    private fun openCamera() {
                                    //            Log.e(TAG, "openCamera ")

        document = null
        inPhotoMode = true
        startCameraPreview()

    }

    /**
     * Once user accepts by pressing check button, or by pressing add new document button, add
     * original photo path and 4 document corners to documents list. If user isn't allowed to
     * adjust corners, call this automatically.
     */
    private fun addSelectedCornersAndOriginalPhotoPathToDocuments() {
        // only add document it's not null (the current document photo capture, and corner
        // detection are successful)
        document?.let { document ->
            // convert corners from image preview coordinates to original photo coordinates
            // (original image is probably bigger than the preview image)

            val cornersInImagePreviewCoordinatesList = imageView.quads!!.map { quad ->
                quad.mapPreviewToOriginalImageCoordinates(
                    imageView.imagePreviewBounds,
                    imageView.imagePreviewBounds.height() / document.preview!!.height
                )
            }

        //    Log.d(
        //        TAG,
        //        "adding addSelectedCornersAndOriginalPhotoPathToDocuments " + cornersInImagePreviewCoordinatesList.size
        //    )
            for (cornersInImagePreviewCoordinates in cornersInImagePreviewCoordinatesList) {
                documents.add(
                    Document(
                        document.originalPhotoPath,
                        document.preview,
                        cornersInImagePreviewCoordinates,
                        imageView.colorFilter
                    )
                )
            }
        //    Log.d(
         //       TAG,
        //        "adding addSelectedCornersAndOriginalPhotoPathToDocuments1 " + documents.size
        //    )
            updateCounterButton()
        }
    }

    /**
     * This gets called when a user presses the new document button. Store current photo path
     * with document corners. Then open the camera, so user can take a photo of the next
     * page or document
     */
    private fun onClickNew() {
        addSelectedCornersAndOriginalPhotoPathToDocuments()
        setPhotoMode(true)
    }

    /**
     * This gets called when a user presses the done button. Store current photo path with
     * document corners. Then crop document using corners, and return cropped image paths
     */
    private fun onClickDone() {
        if (inPhotoMode) {
            takePhoto()
        } else {
            addSelectedCornersAndOriginalPhotoPathToDocuments()
            cropDocumentAndFinishIntent()
        }
    }

    private fun setPhotoMode(enabled: Boolean) {
        inPhotoMode = enabled
        if (inPhotoMode) {
            if (!showColorFilters) {
                filtersView.visibility = (View.INVISIBLE)
            }
            imageView.visibility = View.GONE
            cameraView.visibility = View.VISIBLE
            flashModeButton.visibility = View.VISIBLE
            cropView.visibility = View.VISIBLE
            completeDocumentScanButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.photo_camera_24
                )
            )
            newPhotoButton.visibility = View.INVISIBLE
            
            //retakePhotoButton.visibility = View.INVISIBLE
            
            if (showColorFilters) {
                filtersView.visibility = View.INVISIBLE
            }
            //startCameraPreview()
        } else {
            filtersView.visibility = if (showColorFilters) View.VISIBLE else (View.INVISIBLE)
            //cameraView.stopPreview()
            cropView.quads = null
            cropView.visibility = View.GONE
            flashModeButton.visibility = View.GONE
            completeDocumentScanButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.ic_baseline_check_24
                )
            )
            cameraView.visibility = View.GONE
            imageView.visibility = View.VISIBLE
            newPhotoButton.visibility = View.VISIBLE
            retakePhotoButton.visibility = View.VISIBLE
            if (showColorFilters) {
                filtersView.visibility = View.VISIBLE
            }
        }
        updateCounterButton()
    }


    /**
     * This gets called when a user presses the retake photo button. The user presses this in
     * case the original document photo isn't good, and they need to take it again.
     */
    private fun onClickRetake() {
        // we're going to retake the photo, so delete the one we just took
       if (inPhotoMode) {
            if (documents.size == 0) {
                  onClickCancel()
            }
            else {
                cropDocumentAndFinishIntent()
            }
       }
       else {
          document?.preview?.recycle()
 //           if (document.preview != null && !document.preview.isRecycled) {
 //               document.preview.isRecycled
//            }

          setPhotoMode(true)    
      }
    }

    private fun swithFlashMode() {
        when (flashMode) {
            CameraFlashMode.AUTO -> flashMode = CameraFlashMode.ON
            CameraFlashMode.ON -> flashMode = CameraFlashMode.TORCH
            CameraFlashMode.TORCH -> flashMode = CameraFlashMode.OFF
            else -> flashMode = CameraFlashMode.AUTO
        }
        updateFlashMode()
    }

    private fun updateFlashMode() {
        cameraView.flashMode = flashMode
        when (flashMode) {
            CameraFlashMode.AUTO -> flashModeButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.flash_auto_24
                )
            )
            CameraFlashMode.ON -> flashModeButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.flash_on_24
                )
            )
            CameraFlashMode.TORCH -> flashModeButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.highlight_24
                )
            )
            else -> flashModeButton.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.flash_off_24
                )
            )
        }
    }

    private fun updateCounterButton() {
     //   Log.d(TAG, "updateCounterButton " + documents.size)
        if (documents.size == 0) {
            counterButton.visibility = View.GONE
        } else {
            counterButton.visibility = View.VISIBLE
            counterButton.text = (documents.size ?: 0).toString()
            counterButton.invalidate()
        }
    }

    /**
     * This gets called when a user doesn't want to complete the document scan after starting.
     * For example a user can quit out of the camera before snapping a photo of the document.
     */
    private fun onClickCancel() {
        setResult(Activity.RESULT_CANCELED)
        
        livePreview = false
                        onStop()
        
                        Handler().postDelayed({
                            runOnUiThread {
                                    finish()
                                    
                            }
                        }, 150)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inPhotoMode) {            
            if (documents.size == 0) {
                  onClickCancel()
            }
            else {
                cropDocumentAndFinishIntent()
            }
        } else {
            onClickRetake()
        }
    }

    /**
     * This crops original document photo, saves cropped document photo, deletes original
     * document photo, and returns cropped document photo file path. It repeats that for
     * all document photos.
     */
    private fun cropDocumentAndFinishIntent() {
        val croppedImageResults = arrayListOf<String>()
        for ((pageNumber, document) in documents.withIndex()) {
            // crop document photo by using corners
            var colorFilter = document.colorFilter

            val croppedImage: Bitmap = try {
                DocumentDetector.cropDocument(
                    document,
                    colorFilter
                )
            } catch (exception: Exception) {
                exception.printStackTrace()
                finishIntentWithError("unable to crop image: ${exception.message}")
                continue
            } finally {
                File(document.originalPhotoPath).delete()
            }

//            if (croppedImage !== document.bitmap) {
//                document.bitmap.recycle()
//            }

            // save cropped document photo
            try {
                val croppedImageFile = FileUtil().createImageFile(this, pageNumber)
                croppedImage.saveToFile(croppedImageFile, croppedImageQuality)
                croppedImageResults.add(Uri.fromFile(croppedImageFile).toString())
                if (!needStoragePermission) {
                    GlobalScope.launch {
                        runCatching {
                            insertMedia(croppedImageFile, Environment.DIRECTORY_PICTURES)
                        }
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
                finishIntentWithError(
                    "unable to save cropped image: ${exception.message}"
                )
                return
            }
            croppedImage.recycle()
        }
        for (document in documents) {
            if (document.preview != null && !document.preview.isRecycled) {
                document.preview.isRecycled
            }
        }
        document = null
        // return array of cropped document photo file paths
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra("croppedImageResults", croppedImageResults)
        )
                livePreview = false

                                onStop()

                        Handler().postDelayed({
                            runOnUiThread {
                                    finish()
                            }
                        }, 150)
    }

    /**
     * This ends the document scanner activity, and returns an error message that can be
     * used to debug error
     *
     * @param errorMessage an error message
     */
    private fun finishIntentWithError(errorMessage: String) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra("error", errorMessage)
        )
                livePreview = false

                                onStop()

                        Handler().postDelayed({
                            runOnUiThread {
                                    finish()
                            }
                        }, 150)


    }

    override fun onResume() {
       // Log.e(TAG, "onResume ")
        super.onResume()
        startCameraPreview()
    }

    override fun onStop() {
        super.onStop()
        cameraView.stopPreview()
        cameraView.stop()
        cameraView.listener = null
     //   Log.e(TAG, "onStop ")
    }

    override fun onPause() {
       // Log.e(TAG, "onPause ")
        super.onPause()
        cameraView.stopPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        yuvToRgbConverter.destroy()
        cameraFadein = true
        cameraView.release()
       // Log.e(TAG, "onDestroy ")
    }

    private fun startCameraPreview() {
       // Log.e(TAG, "startCameraPreview ")
        if (inPhotoMode) {
            cameraView.startPreview()
        }
    }
}