package com.calory.swapcal.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineStart
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView
//    lateinit var swipeRefresh : SwipeRefreshLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            if (uri != null) {
                filePathCallback?.onReceiveValue(arrayOf(uri))
            } else {
                filePathCallback?.onReceiveValue(null)
            }

            filePathCallback = null
        }

    private lateinit var cameraLauncher: ActivityResultLauncher<Void?>
    private lateinit var imageUri: Uri


    // 3. JS-callable interface — JS can call this anytime after login
    inner class AndroidBridge {
        @JavascriptInterface
        fun requestFcmToken() {
        }

        @JavascriptInterface
        fun showInterstitialAd() {
            Log.d("jsads", "showInterstitial: ")
            runOnUiThread {
                AdManager.showInterstitial(this@MainActivity)
            }
        }

        @JavascriptInterface
        fun showRewardedAd() {
            Log.d("jsads", "showRewardedAd: ")
            runOnUiThread {
                AdManager.showRewarded(this@MainActivity) {
                    webView.evaluateJavascript("window.rewardUser()", null)
                }
            }
        }

        @JavascriptInterface
        fun openCheckoutUrl(url: String) {


        }

        @SuppressLint("InlinedApi")
        @JavascriptInterface
        fun findPermissionStatus(): Int {
            return ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Permission automatically granted on < API 33
            }
        }

        @JavascriptInterface
        fun openCamera() {
            launchCamera()

        }

        @JavascriptInterface
        fun requestPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }


        @JavascriptInterface
        fun onUserLoggedIn() {
            Log.d("FCM", "onUserLoggedIn: ")
            // Alternative: let JS explicitly signal login instead of relying on cookies
        }
    }


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted —
            } else {
                // Permission denied
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this)
        AdManager.initialize(this)

        imageUri = createImageUri()
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                val stream = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
                sendToWebView(base64)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContentView(R.layout.activity_main) // 👈 REQUIRED

        webView = findViewById(R.id.webView)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.webChromeClient = object : WebChromeClient() {

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                Log.d("FileChoose", "onShowFileChooser: pick media")
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                fileChooserLauncher.launch("image/*")
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(
                        "WebViewConsole",
                        "JS: ${it.message()} (Line: ${it.lineNumber()}, Source: ${it.sourceId()})"
                    )
                }
                return true
            }
        }
        setupWebView()
        // In onCreate:
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")


        // 1. Fix: set webViewClient ONCE, after setupWebView()
        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                false

        }

        webView.loadUrl(getString(R.string.url))

    }

    fun launchCamera() {
        cameraLauncher.launch(null)
    }

    private fun createImageUri(): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            // Required for Android 10+
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/SwapCal")
        }

        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create new MediaStore record.")
    }

    private fun uriToBase64(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream!!.readBytes()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun sendToWebView(base64Data: String) {
        val dataUri = "data:image/jpeg;base64,$base64Data"
        webView.evaluateJavascript(
            "window.onCameraResult(${JSONObject.quote(dataUri)})",
            null
        )
    }

    private fun createTempImageUri(): Uri {
        val file = File(cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
//            setWebContentsDebuggingEnabled(false)
            userAgentString = userAgentString + " MVP_ANDROID"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                return false // keep navigation inside app
            }
        }

    }


    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush() // write cookies to disk when app backgrounds
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }


}