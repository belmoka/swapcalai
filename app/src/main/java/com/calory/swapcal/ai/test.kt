//package com.calory.swapcal.ai
//
//package com.calory.swapcal.ai
//
//import android.graphics.Bitmap
//import android.net.Uri
//import android.os.Bundle
//import android.util.Base64
//import android.util.Log
//import android.webkit.WebView
//import androidx.activity.result.ActivityResultLauncher
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.FileProvider
//import java.io.ByteArrayOutputStream
//import java.io.File
//
//class MainActivity : AppCompatActivity() {
//
//    private lateinit var webView: WebView
//    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
//    private var tempImageUri: Uri? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        webView = findViewById(R.id.webview)
//        webView.settings.javaScriptEnabled = true
//        webView.loadUrl("http://192.168.11.100:3000/scan") // your Next.js URL
//
//        // Initialize camera launcher with TakePicture()
//        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
//            if (success && tempImageUri != null) {
//                // Convert URI to Base64 safely
//                val base64 = uriToBase64(tempImageUri!!)
//                if (base64 != null) sendToWebView(base64)
//            }
//        }
//
//        // Example: JS can call AndroidBridge.openCamera()
//        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
//    }
//
//    // Convert URI to Base64 safely
//    private fun uriToBase64(uri: Uri): String? {
//        return try {
//            contentResolver.openInputStream(uri)?.use { input ->
//                val bytes = input.readBytes()
//                Base64.encodeToString(bytes, Base64.DEFAULT)
//            }
//        } catch (e: Exception) {
//            Log.e("Camera", "Failed to read URI", e)
//            null
//        }
//    }
//
//    // Send Base64 string to WebView
//    private fun sendToWebView(base64: String) {
//        val jsString = org.json.JSONObject.quote("data:image/jpeg;base64,$base64")
//        val jsCode = "window.onCameraResult && window.onCameraResult($jsString);"
//        webView.post { webView.evaluateJavascript(jsCode, null) }
//    }
//
//    // Create a temporary URI in app cache
//    private fun createTempImageUri(): Uri {
//        val file = File(cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
//        tempImageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
//        return tempImageUri!!
//    }
//
//    // Public function for JS to call
//    fun openCamera() {
//        // If you prefer in-memory, you can switch to TakePicturePreview()
//        cameraLauncher.launch(createTempImageUri())
//    }
//
//    // Optional: in-memory version (no file saved)
//    fun openCameraPreview() {
//        val previewLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
//            bitmap?.let {
//                val stream = ByteArrayOutputStream()
//                it.compress(Bitmap.CompressFormat.JPEG, 100, stream)
//                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
//                sendToWebView(base64)
//            }
//        }
//        previewLauncher.launch(null)
//    }
//
//    inner class AndroidBridge {
//        @android.webkit.JavascriptInterface
//        fun openCamera() {
//            this@MainActivity.openCamera()
//        }
//    }
//}