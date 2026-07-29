package com.almolham.bingo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

// STEP 2: testing raw IP socket connection only — no game UI yet on purpose.
// One phone taps "Host", shows its IP. The other phone types that IP and taps "Join".
// If both phones show a success message with an exchanged text, the network layer works
// and we can safely build the real game on top of it next.
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var ipText: TextView
    private var serverSocket: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)
        val hostBtn = findViewById<Button>(R.id.hostBtn)
        val joinBtn = findViewById<Button>(R.id.joinBtn)
        val ipInput = findViewById<EditText>(R.id.ipInput)

        ipText.text = "عنوان جهازك: " + getLocalIpAddress()

        hostBtn.setOnClickListener {
            statusText.text = "بانتظار اتصال..."
            startHostServer()
        }

        joinBtn.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            if (ip.isEmpty()) {
                statusText.text = "اكتب عنوان IP أولاً"
            } else {
                statusText.text = "جاري الاتصال..."
                startJoinClient(ip)
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        return addr.hostAddress ?: "?"
                    }
                }
            }
        } catch (e: Exception) { }
        return "غير معروف"
    }

    private fun startHostServer() {
        Thread {
            try {
                val server = ServerSocket(8888)
                serverSocket = server
                val client = server.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)
                val received = reader.readLine()
                writer.println("مرحباً من المضيف! استلمت: $received")
                runOnUiThread {
                    statusText.text = "✅ اتصل لاعب! الرسالة: $received"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "خطأ: ${e.message}"
                }
            }
        }.start()
    }

    private fun startJoinClient(ip: String) {
        Thread {
            try {
                val socket = Socket(ip, 8888)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println("مرحباً من اللاعب الثاني!")
                val reply = reader.readLine()
                runOnUiThread {
                    statusText.text = "✅ اتصلت بالمضيف! رد: $reply"
                }
                socket.close()
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "فشل الاتصال: ${e.message}"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { serverSocket?.close() } catch (e: Exception) { }
    }
}
