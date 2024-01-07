package ti.documentscanner

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.*
import ti.documentscanner.constants.DefaultSetting
import ti.documentscanner.constants.DocumentScannerExtra
import ti.documentscanner.constants.ResponseType
import ti.documentscanner.extensions.toBase64
import ti.documentscanner.utils.ImageUtil
import java.io.File
import org.appcelerator.kroll.common.Log

/**
 * This class is used to start a document scan. It accepts parameters used to customize
 * the document scan, and callback parameters.
 *
 * @param activity the current activity
 * @param successHandler event handler that gets called on document scan success
 * @param errorHandler event handler that gets called on document scan error
 * @param cancelHandler event handler that gets called when a user cancels the document scan
 * @param responseType the cropped image gets returned in this format
 * @param letUserAdjustCrop whether or not the user can change the auto detected document corners
 * @param maxNumDocuments the maximum number of documents a user can scan at once
 * @param croppedImageQuality the 0 - 100 quality of the cropped image
 * @constructor creates document scanner
 */

class DocumentScanner @JvmOverloads constructor(
    private val activity: ComponentActivity,
    val successHandler: ((documentScanResults: ArrayList<String>) -> Unit),
    val errorHandler: ((errorMessage: String) -> Unit)? = null,
    val cancelHandler: (() -> Unit)? = null,
    var responseType: String? = null,
    var letUserAdjustCrop: Boolean? = null,
    var flashMode: Int? = null,
    var autoFocus: Boolean? = null,
    var showColorFilters: Boolean? = null,
    var maxNumDocuments: Int? = null,
    var maxNumSimultaneousDocuments: Int? = null,
    var croppedImageQuality: Int? = null,
    var customAnalyserClass: String? = null,
    //var getContent: ActivityResultLauncher<Intent>? = null
) {
    init {
        //getContent = getContent ?: activity.registerForActivityResult(
        //    StartActivityForResult()
       // ) { result: ActivityResult -> handleDocumentScanIntentResult(result) }
        croppedImageQuality = croppedImageQuality ?: DefaultSetting.CROPPED_IMAGE_QUALITY
        responseType = responseType ?: DefaultSetting.RESPONSE_TYPE
    }

    /**
     * create intent to launch document scanner and set custom options
     */
    fun createDocumentScanIntent(): Intent {
        val documentScanIntent = Intent(activity, DocumentScannerActivity::class.java)
        if (croppedImageQuality != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_CROPPED_IMAGE_QUALITY,
                croppedImageQuality
            )
        }
        if (letUserAdjustCrop != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_LET_USER_ADJUST_CROP,
                letUserAdjustCrop
            )
        }
        if (maxNumDocuments != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_MAX_NUM_DOCUMENTS,
                maxNumDocuments
            )
        }
        if (maxNumSimultaneousDocuments != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_MAX_NUM_SIMULTANEOUS_DOCUMENTS,
                maxNumSimultaneousDocuments
            )
        }
        if (flashMode != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_FLASH_MODE,
                flashMode
            )
        }
        if (autoFocus != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_AUTO_FOCUS,
                autoFocus
            )
        }
        if (showColorFilters != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_SHOW_COLOR_FILTERS,
                showColorFilters
            )
        }
        if (customAnalyserClass != null) {
            documentScanIntent.putExtra(
                DocumentScannerExtra.EXTRA_CUSTOM_ANALYSER_CLASS,
                customAnalyserClass
            )
        }

        return documentScanIntent
    }

    /**
     * handle response from document scanner
     *
     * @param result the document scanner activity result
     */
     fun handleDocumentScanIntentResult(result: Intent,resultCode:Int) {
        try {
            // make sure responseType is valid
            if (!arrayOf(
                    ResponseType.BASE64
                    //ResponseType.IMAGE_FILE_PATH
                ).contains(responseType)
            ) {
                throw Exception(
                    "responseType must be either ${ResponseType.BASE64} " +
                            "or ${ResponseType.IMAGE_FILE_PATH}"
                )
            }


            when (resultCode) {
                Activity.RESULT_OK -> {
                    // check for errors
                    val error = result?.extras?.getString("error")
                    if (error != null) {
                        throw Exception("error - $error")
                    }

                    // get an array with scanned document file paths
                    val croppedImageResults: ArrayList<String> =
                        result?.getStringArrayListExtra(
                            "croppedImageResults"
                        ) ?: throw Exception("No cropped images returned")

                    // if responseType is imageFilePath return an array of file paths
                    var successResponse: ArrayList<String> = croppedImageResults

                    // if responseType is base64 return an array of base64 images
                    if (responseType == ResponseType.BASE64) {
                        val base64CroppedImages =
                            croppedImageResults.map { croppedImagePath ->
                                // read cropped image from file path, and convert to base 64
                                val base64Image = ImageUtil.readBitmapFromFileUriString(
                                    croppedImagePath,
                                    activity.contentResolver
                                ).toBase64(croppedImageQuality!!)

                                // delete cropped image from android device to avoid
                                // accumulating photos
                                File(croppedImagePath).delete()

                                base64Image
                            }

                        successResponse = base64CroppedImages as ArrayList<String>
                    }

                    // trigger the success event handler with an array of cropped images
                    successHandler?.let { it(successResponse) }
                }
                Activity.RESULT_CANCELED -> {
                    // user closed camera
                    cancelHandler?.let { it() }
                }
            }
        } catch (exception: Exception) {
            // trigger the error event handler
            errorHandler?.let { it(exception.localizedMessage ?: exception.toString()) }
        }
    }

    /**
     * add document scanner result handler and launch the document scanner
     */
    fun startScan() {
       // getContent?.launch(createDocumentScanIntent())
    }
}