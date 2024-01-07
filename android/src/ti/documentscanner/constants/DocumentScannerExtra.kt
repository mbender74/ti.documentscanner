package ti.documentscanner.constants

/**
 * This class contains constants meant to be used as intent extras
 */
class DocumentScannerExtra {
    companion object {
        const val EXTRA_FLASH_MODE = "flashMode"
        const val EXTRA_SHOW_COLOR_FILTERS = "showColorFilters"
        const val EXTRA_AUTO_FOCUS = "autoFocus"
        const val EXTRA_CROPPED_IMAGE_QUALITY = "croppedImageQuality"
        const val EXTRA_LET_USER_ADJUST_CROP = "letUserAdjustCrop"
        const val EXTRA_MAX_NUM_DOCUMENTS = "maxNumDocuments"
        const val EXTRA_MAX_NUM_SIMULTANEOUS_DOCUMENTS = "maxNumSimultaneousDocuments"
        const val EXTRA_CUSTOM_ANALYSER_CLASS = "customAnalyserClass"
    }
}