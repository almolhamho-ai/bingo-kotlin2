package com.almolham.bingo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private class ClientConn(val id: Int, val socket: Socket, val writer: PrintWriter)

    // ============ جسر اتصال مباشر بعنوان IP (بديل WebRTC) ============
    // بيسمح لكود اللعبة (bingo-v18.html) يستضيف/ينضم عبر Socket حقيقي،
    // بنفس واجهة اللعب والاحتفال الأصلية بالضبط — بدون رموز أو QR.
    private inner class AndroidBridge {

        private var serverSocket: ServerSocket? = null
        private val hostClients = mutableListOf<ClientConn>()
        private var nextClientId = 0

        private var joinSocket: Socket? = null
        private var joinWriter: PrintWriter? = null

        private fun evalJs(code: String) {
            runOnUiThread { webView.evaluateJavascript(code, null) }
        }

        private fun encode(s: String): String =
            Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        @JavascriptInterface
        fun getLocalIp(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (e: Exception) { }
            return ""
        }

        @JavascriptInterface
        fun startHost() {
            Thread {
                try {
                    val server = ServerSocket(8888)
                    serverSocket = server
                    while (true) {
                        val socket = server.accept()
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        val id = synchronized(hostClients) { nextClientId++ }
                        val conn = ClientConn(id, socket, writer)
                        synchronized(hostClients) { hostClients.add(conn) }
                        evalJs("window.onNetClientConnected && window.onNetClientConnected($id);")

                        Thread {
                            try {
                                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                                var line: String?
                                while (true) {
                                    line = reader.readLine() ?: break
                                    evalJs("window.onNetMessageFromClient && window.onNetMessageFromClient($id,'${encode(line)}');")
                                }
                            } catch (e: Exception) { }
                            synchronized(hostClients) { hostClients.remove(conn) }
                            evalJs("window.onNetClientDisconnected && window.onNetClientDisconnected($id);")
                        }.start()
                    }
                } catch (e: Exception) {
                    evalJs("window.onNetError && window.onNetError('startHost');")
                }
            }.start()
        }

        @JavascriptInterface
        fun sendToClient(id: Int, data: String) {
            synchronized(hostClients) { hostClients.find { it.id == id }?.writer?.println(data) }
        }

        @JavascriptInterface
        fun joinHost(ip: String) {
            Thread {
                try {
                    val socket = Socket(ip, 8888)
                    joinSocket = socket
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    joinWriter = writer
                    evalJs("window.onNetJoinConnected && window.onNetJoinConnected();")

                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    var line: String?
                    while (true) {
                        line = reader.readLine() ?: break
                        evalJs("window.onNetMessageFromHost && window.onNetMessageFromHost('${encode(line)}');")
                    }
                    // Host closed the connection cleanly (readLine returned null) — let the JS
                    // layer know so it can show reconnect UI / retry during a network tournament.
                    evalJs("window.onNetHostDisconnected && window.onNetHostDisconnected();")
                } catch (e: Exception) {
                    evalJs("window.onNetError && window.onNetError('joinHost');")
                    evalJs("window.onNetHostDisconnected && window.onNetHostDisconnected();")
                }
            }.start()
        }

        @JavascriptInterface
        fun sendToHost(data: String) {
            joinWriter?.println(data)
        }

        @JavascriptInterface
        fun closeNetwork() {
            Thread {
                try {
                    synchronized(hostClients) {
                        hostClients.forEach { try { it.writer.close(); it.socket.close() } catch (e: Exception) { } }
                        hostClients.clear()
                    }
                } catch (e: Exception) { }
                try { serverSocket?.close() } catch (e: Exception) { }
                try { joinWriter?.close() } catch (e: Exception) { }
                try { joinSocket?.close() } catch (e: Exception) { }
                serverSocket = null
                joinSocket = null
                joinWriter = null
            }.start()
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finish() }
        }
    }

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
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.loadUrl("file:///android_asset/bingo-v18.html")
    }

    // زر الرجوع: بنستدعي دالة goBack() الداخلية بالصفحة (يلي عندها منطق "الشاشة السابقة
    // منطقياً" الصحيح لكل حالة)، بدل الاعتماد على تاريخ التصفح الخام يلي كان يودّي لشاشات قديمة غلط.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("if(window.goBack)goBack();", null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
