package com.almolham.bingo

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true // ضروري عشان localStorage (الإحصائيات + الإنجازات + الإعدادات)
        webView.settings.mediaPlaybackRequiresUserGesture = false // عشان الموسيقى المولّدة تشتغل بدون قيود زيادة
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.allowFileAccess = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

        webView.webChromeClient = WebChromeClient() // يسمح بتنبيهات JS وطلبات الصلاحيات لو احتجناها لاحقاً

        webView.loadUrl("file:///android_asset/bingo-v18.html")
    }

    // زر الرجوع: يرجع بصفحات الويب (شاشات اللعبة) قبل ما يطلع من التطبيق فعلياً
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
