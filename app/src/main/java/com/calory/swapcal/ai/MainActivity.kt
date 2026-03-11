package com.calory.swapcal.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineStart
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64


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
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            this@MainActivity.startActivityForResult(intent, 101)
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
//        enableEdgeToEdge()

        MobileAds.initialize(this);
        AdManager.initialize(this)

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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val photo = data.extras!!["data"] as Bitmap?

            val stream = ByteArrayOutputStream()
            photo!!.compress(Bitmap.CompressFormat.JPEG, 90, stream)

            val base64: String = Base64.encodeToString(stream.toByteArray(), CoroutineStart.DEFAULT)

            webView.evaluateJavascript(
                "window.onCameraResult('data:image/jpeg;base64,$base64')",
                null
            )
        }
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
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(fcmReceiver)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }


}