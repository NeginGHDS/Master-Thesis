package com.example.offline_first

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import com.example.offline_first.ui.PlatformActions
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * Strong references to in-flight picker delegates: `UIImagePickerController.delegate` is a `weak`
 * Objective-C property, so without holding onto the delegate elsewhere, ARC would deallocate it
 * before the user finishes picking.
 */
@OptIn(ExperimentalForeignApi::class)
private val activePickerDelegates = mutableSetOf<NSObject>()

@OptIn(ExperimentalForeignApi::class)
private class ImagePickerDelegate(
    private val onResult: (String?) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        finish(picker, image?.let(::saveImageToDocuments))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        finish(picker, null)
    }

    private fun finish(picker: UIImagePickerController, path: String?) {
        picker.dismissViewControllerAnimated(true, completion = null)
        activePickerDelegates.remove(this)
        onResult(path)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun saveImageToDocuments(image: UIImage): String? {
    val data = UIImageJPEGRepresentation(image, 0.85) ?: return null
    val documentsUrl = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL ?: return null
    val fileUrl = documentsUrl.URLByAppendingPathComponent("photo_${NSUUID().UUIDString}.jpg") ?: return null
    val path = fileUrl.path ?: return null
    return if (NSFileManager.defaultManager.createFileAtPath(path, contents = data, attributes = null)) path else null
}

@OptIn(ExperimentalForeignApi::class)
private fun presentPicker(
    rootViewController: UIViewController,
    sourceType: UIImagePickerControllerSourceType,
    onResult: (String?) -> Unit
) {
    val picker = UIImagePickerController()
    picker.sourceType = sourceType
    val delegate = ImagePickerDelegate(onResult)
    activePickerDelegates += delegate
    picker.delegate = delegate
    rootViewController.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
@Composable
fun rememberIosPlatformActions(): PlatformActions {
    val rootViewController = LocalUIViewController.current
    return remember(rootViewController) {
        PlatformActions(
            pickPhotoFromGallery = { onResult ->
                presentPicker(rootViewController, UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary, onResult)
            },
            takePhoto = { onResult ->
                if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
                    presentPicker(rootViewController, UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera, onResult)
                } else {
                    onResult(null)
                }
            }
            // Google Sign-In stays the default "not available" stub on iOS — same treatment as the
            // existing Twitter sign-in placeholder (see ui.App.PlatformActions / migration plan Phase 5).
        )
    }
}
