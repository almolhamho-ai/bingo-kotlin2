package com.almolham.bingo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private var gridNumbers = (1..25).toMutableList()
    private val marked = mutableSetOf<Int>()
    private var linesCompleted = 0
    private var gameStarted = false

    private var writer: PrintWriter? = null
    private var myTurn = false

    private lateinit var statusText: TextView
    private lateinit var grid: GridLayout
    private lateinit var ipInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        grid = findViewById(R.id.gameGrid)
        ipInput = findViewById(R.id.ipInput)
        val hostBtn = findViewById<Button>(R.id.hostBtn)
        val joinBtn = findViewById<Button>(R.id.joinBtn)

        hostBtn.setOnClickListener { startHost() }
        joinBtn.setOnClickListener { startJoin() }
    }

    // ---------- شبكة: استضافة ----------
    private fun startHost() {
        val ip = getLocalIpAddress()
        statusText.text = "بانتظار الاتصال... عنوانك: $ip"

        Thread {
            try {
                val serverSocket = ServerSocket(8888)
                val client = serverSocket.accept()
                writer = PrintWriter(client.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))

                gridNumbers = (1..25).toMutableList()
                gridNumbers.shuffle()
                writer?.println("GRID:" + gridNumbers.joinToString(","))

                runOnUiThread {
                    marked.clear()
                    linesCompleted = 0
                    gameStarted = true
                    myTurn = true
                    statusText.text = "متصل! دورك — اضغط أي رقم"
                    buildGrid()
                }
                listenLoop(reader)
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "خطأ بالاتصال: ${e.message}" }
            }
        }.start()
    }

    // ---------- شبكة: انضمام ----------
    private fun startJoin() {
        val ip = ipInput.text.toString().trim()
        if (ip.isEmpty()) {
            statusText.text = "اكتب عنوان IP المضيف أولاً"
            return
        }
        statusText.text = "جاري الاتصال بـ $ip ..."

        Thread {
            try {
                val client = Socket(ip, 8888)
                writer = PrintWriter(client.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))

                runOnUiThread { statusText.text = "متصل! بانتظار بدء اللعبة..." }
                listenLoop(reader)
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "فشل الاتصال: ${e.message}" }
            }
        }.start()
    }

    // ---------- استقبال الرسائل من الطرف الآخر ----------
    private fun listenLoop(reader: BufferedReader) {
        try {
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                when {
                    line.startsWith("GRID:") -> {
                        val numbers = line.removePrefix("GRID:").split(",").map { it.toInt() }.toMutableList()
                        runOnUiThread {
                            gridNumbers = numbers
                            marked.clear()
                            linesCompleted = 0
                            gameStarted = true
                            myTurn = false
                            statusText.text = "بدأت اللعبة — دور الطرف الآخر"
                            buildGrid()
                        }
                    }
                    line.startsWith("TAP:") -> {
                        val number = line.removePrefix("TAP:").toInt()
                        runOnUiThread { applyTap(number, fromRemote = true) }
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread { statusText.text = "انقطع الاتصال" }
        }
    }

    // ---------- بناء الشبكة على الشاشة ----------
    private fun buildGrid() {
        grid.removeAllViews()
        for (i in 0 until 25) {
            val btn = Button(this)
            btn.text = gridNumbers[i].toString()
            btn.textSize = 16f
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(i % 5, 1f)
            params.rowSpec = GridLayout.spec(i / 5)
            params.setMargins(4, 4, 4, 4)
            btn.layoutParams = params
            val number = gridNumbers[i]
            if (marked.contains(number)) {
                btn.isEnabled = false
                btn.alpha = 0.4f
            }
            btn.setOnClickListener { onCellTapped(number) }
            grid.addView(btn)
        }
    }

    private fun onCellTapped(number: Int) {
        if (!gameStarted || !myTurn || marked.contains(number)) return
        writer?.println("TAP:$number")
        applyTap(number, fromRemote = false)
    }

    private fun applyTap(number: Int, fromRemote: Boolean) {
        if (marked.contains(number)) return
        marked.add(number)

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i) as Button
            if (child.text.toString() == number.toString()) {
                child.isEnabled = false
                child.alpha = 0.4f
            }
        }

        val lines = countCompletedLines()
        if (lines > linesCompleted) {
            linesCompleted = lines
        }

        if (linesCompleted >= 5) {
            statusText.text = "🏆 اكتمل BINGO! (5 خطوط)"
            gameStarted = false
            return
        }

        // إذا الرقم إجاني من الطرف التاني -> صار دوري. إذا أنا يلي ضغطت -> صار دوره.
        myTurn = fromRemote
        statusText.text = if (myTurn) "دورك — اضغط أي رقم (خطوط: $linesCompleted)"
                           else "دور الطرف الآخر (خطوط: $linesCompleted)"
    }

    private fun countCompletedLines(): Int {
        var count = 0
        for (r in 0 until 5) {
            var full = true
            for (c in 0 until 5) {
                if (!marked.contains(gridNumbers[r * 5 + c])) { full = false; break }
            }
            if (full) count++
        }
        for (c in 0 until 5) {
            var full = true
            for (r in 0 until 5) {
                if (!marked.contains(gridNumbers[r * 5 + c])) { full = false; break }
            }
            if (full) count++
        }
        var d1 = true
        for (i in 0 until 5) if (!marked.contains(gridNumbers[i * 5 + i])) { d1 = false; break }
        if (d1) count++
        var d2 = true
        for (i in 0 until 5) if (!marked.contains(gridNumbers[i * 5 + (4 - i)])) { d2 = false; break }
        if (d2) count++
        return count
    }

    private fun getLocalIpAddress(): String {
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
        return "غير معروف"
    }
}
