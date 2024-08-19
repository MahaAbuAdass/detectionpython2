package com.example.seconddetectionpython


import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import android.media.ExifInterface
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val REQUEST_CAMERA_PERMISSION = 100
    private lateinit var photoUri: Uri
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var resultTextView: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.resultTextView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    // Correct orientation of the image and save it
                    correctImageOrientationAndSave()
                    // Process the image
                    CoroutineScope(Dispatchers.IO).launch {
                        processImage()
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    Log.e("BitmapFactory", "Unable to decode stream: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.takePictureButton).setOnClickListener {
            dispatchTakePictureIntent()
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val photoFile = File(externalCacheDir, "photo.jpg")
        photoUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", photoFile)

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        cameraLauncher.launch(takePictureIntent)
    }

    private fun correctImageOrientationAndSave() {
        try {
            // Load the image as a Bitmap
            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(photoUri))

            // Check the orientation and correct it
            val correctedBitmap = correctImageOrientation(bitmap)

            // Save the corrected bitmap
            val imageFile = File(cacheDir, "temp_image.jpg")
            FileOutputStream(imageFile).use { out ->
                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            Log.d("ImageCorrection", "Image successfully saved at ${imageFile.absolutePath}")

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ImageCorrection", "Failed to correct and save image: ${e.message}")
        }
    }

    private fun correctImageOrientation(bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(contentResolver.openInputStream(photoUri)!!)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        val finalWidth: Int
        val finalHeight: Int
        if (ratioMax > 1) {
            finalWidth = (maxHeight * ratioBitmap).toInt()
            finalHeight = maxHeight
        } else {
            finalWidth = maxWidth
            finalHeight = (maxWidth / ratioBitmap).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private suspend fun processImage() {
        val python = Python.getInstance()
        val pythonModule = python.getModule("emotion")

        if (pythonModule == null) {
            Log.e("PythonError", "Failed to load Python module")
            return
        }

        val imageFile = File(cacheDir, "temp_image.jpg")
        val resizedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        val resizedImageFile = File(cacheDir, "temp_image_resized.jpg")

        try {
            FileOutputStream(resizedImageFile).use { out ->
                resizeBitmap(resizedBitmap, 800, 600).compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("processImage", "Failed to save resized bitmap to file: ${e.message}")
            return
        }

        val encodingFile = File(filesDir, "encodings.pkl")
        copyAssetToFile("encodings.pkl", encodingFile)

        if (resizedImageFile.exists()) {
            Log.d("FileCheck", "Image file exists at ${resizedImageFile.absolutePath}")
        } else {
            Log.e("FileCheck", "Image file does not exist at ${resizedImageFile.absolutePath}")
            return
        }

        val fileSize = resizedImageFile.length()
        Log.d("FileCheck", "Image file size: $fileSize bytes")
        if (fileSize == 0L) {
            Log.e("FileCheck", "Image file is empty")
            return
        }

        try {
            Log.d("PythonExecution", "Starting Python function execution")
            val result: PyObject = withContext(Dispatchers.IO) {
                pythonModule.callAttr("process_image", resizedImageFile.absolutePath, encodingFile.absolutePath)
            }

            val resultJson = result.toString() // Ensure the result is a JSON string
            Log.d("PythonResult", "Received result: $resultJson")

            runOnUiThread {
                try {
                    val jsonObject = JSONObject(resultJson)
                    val status = jsonObject.optString("status", "unknown")
                    val message = jsonObject.optString("message", "No message")
                    val name = jsonObject.optString("name", "No name")
                    val emotion = jsonObject.optString("emotion", "No emotion")
                    val time = jsonObject.optString("time", "No time")

                    resultTextView.text = """
                        Status: $status
                        Message: $message
                        Name: $name
                        Emotion: $emotion
                        Time: $time
                    """.trimIndent()
                } catch (e: Exception) {
                    Log.e("JsonParsingError", "Failed to parse JSON result: ${e.message}")
                    resultTextView.text = "Error: Failed to parse JSON result."
                }
            }

            Log.d("PythonExecution", "Python function execution completed")

        } catch (e: Exception) {
            Log.e("PythonError", "Python function execution failed: ${e.message}")
            runOnUiThread {
                resultTextView.text = "Error: Python function execution failed."
            }
        }
    }

    private fun copyAssetToFile(assetName: String, outFile: File) {
        try {
            assets.open(assetName).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("FileCopyError", "Failed to copy asset file: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent()
            } else {
                Log.e("PermissionError", "Camera permission not granted")
            }
        }
    }
}
