package ti.documentscanner.constants

import ti.documentscanner.cameraview.CameraFlashMode


/**
 * This class contains default document scanner options
 */
class DefaultSetting {
    companion object {
        const val CROPPED_IMAGE_QUALITY = 100
        const val LET_USER_ADJUST_CROP = true
        const val SHOW_COLOR_FILTERS = true
        const val AUTO_FOCUS = true
        val FLASH_MODE = CameraFlashMode.OFF
        const val MAX_NUM_DOCUMENTS = 90
        const val MAX_NUM_SIMULTANEOUS_DOCUMENTS = 1
        const val RESPONSE_TYPE = ResponseType.BASE64
    }
}