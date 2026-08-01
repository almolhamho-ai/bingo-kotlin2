package com.almolham.bingo

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

// شاشة لعب شبكي مستقلة بالكامل (بدون أي اعتماد على ملفات XML/موارد) —
// بتتصل مباشرة بعنوان IP، بدون رموز أو نسخ ولصق.
class NetworkActivity : AppCompatActivity() {

    private val BG = Color.parseColor("#080812")
    private val CARD = Color.parseColor("#16162A")
    private val BORDER = Color.parseColor("#2A2A4A")
    private val GOLD = Color.parseColor("#F0B429")
    private val GOLD2 = Color.parseColor("#E67E22")
    private val TEXT_MAIN = Color.parseColor("#E8E8F8")
    private val MUTED = Color.parseColor("#6B7280")
    private val MARKED_BG = Color.parseColor("#1E1E38")
    private val MARKED_TEXT = Color.parseColor("#4A4A6A")

    private lateinit var screenSetup: LinearLayout
    private lateinit var screenGame: LinearLayout
    private lateinit var screenWin: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var netStatusText: TextView
    private lateinit var ipInput: EditText
    private lateinit var grid: GridLayout
    private lateinit var winText: TextView

    private var gridNumbers = (1..25).toMutableList()
    private val marked = mutableSetOf<Int>()
    private var linesCompleted = 0
    private var gameStarted = false
    private var myTurn = false

    private var writer: PrintWriter? = null
    private var activeSocket: Socket? = null
    private var serverSocket: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayoutSafe()
        setContentView(root)

        screenSetup = buildSetupScreen()
        screenGame = buildGameScreen()
        screenWin = buildWinScreen()

        root.addView(screenSetup)
        root.addView(screenGame)
        root.addView(screenWin)

        showOnly(screenSetup)
    }

    // FrameLayout بسيط بدون أي حاجة لملف XML
    private fun FrameLayoutSafe(): android.widget.FrameLayout {
        val fl = android.widget.FrameLayout(this)
        fl.setBackgroundColor(BG)
        return fl
    }

    private fun showOnly(screen: LinearLayout) {
        listOf(screenSetup, screenGame, screenWin).forEach {
            it.visibility = if (it == screen) View.VISIBLE else View.GONE
        }
    }

    private fun goldButton(text: String): Button {
        val btn = Button(this)
        btn.text = text
        btn.setTextColor(Color.parseColor("#1A1000"))
        btn.isAllCaps = false
        val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(GOLD, GOLD2))
        gradient.cornerRadius = 40f
        btn.background = gradient
        return btn
    }

    private fun sectionPadding(v: View) {
        v.setPadding(40, 40, 40, 40)
    }

    // ---------- شاشة الإعداد ----------
    private fun buildSetupScreen(): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        sectionPadding(layout)

        val backBtn = TextView(this)
        backBtn.text = "→ رجوع"
        backBtn.setTextColor(MUTED)
        backBtn.setPadding(0, 0, 0, 30)
        backBtn.setOnClickListener { finish() }
        layout.addView(backBtn)

        netStatusText = TextView(this)
        netStatusText.text = "اختر: استضافة أو انضمام"
        netStatusText.setTextColor(GOLD)
        netStatusText.textSize = 15f
        netStatusText.gravity = Gravity.CENTER
        netStatusText.setPadding(0, 0, 0, 30)
        layout.addView(netStatusText)

        ipInput = EditText(this)
        ipInput.hint = "عنوان IP المضيف (للانضمام فقط)"
        ipInput.setHintTextColor(MUTED)
        ipInput.setTextColor(TEXT_MAIN)
        val inputBg = GradientDrawable()
        inputBg.setColor(CARD)
        inputBg.setStroke(2, BORDER)
        inputBg.cornerRadius = 20f
        ipInput.background = inputBg
        ipInput.setPadding(30, 24, 30, 24)
        layout.addView(ipInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = 30 })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val hostBtn = goldButton("استضافة (Host)")
        val joinBtn = goldButton("انضمام (Join)")
        row.addView(hostBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = 10 })
        row.addView(joinBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(row)

        hostBtn.setOnClickListener { startHost() }
        joinBtn.setOnClickListener { startJoin() }

        return layout
    }

    // ---------- شاشة اللعب ----------
    private fun buildGameScreen(): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        sectionPadding(layout)

        statusText = TextView(this)
        statusText.setTextColor(GOLD)
        statusText.textSize = 15f
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 0, 0, 30)
        layout.addView(statusText)

        grid = GridLayout(this)
        grid.columnCount = 5
        grid.rowCount = 5
        layout.addView(grid)

        return layout
    }

    // ---------- شاشة الفوز ----------
    private fun buildWinScreen(): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        sectionPadding(layout)

        winText = TextView(this)
        winText.textSize = 26f
        winText.setTextColor(GOLD)
        winText.gravity = Gravity.CENTER
        winText.setPadding(0, 0, 0, 40)
        layout.addView(winText)

        val homeBtn = goldButton("القائمة الرئيسية")
        homeBtn.setOnClickListener { finish() }
        layout.addView(homeBtn)

        return layout
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

    private fun startHost() {
        val ip = getLocalIpAddress()
        netStatusText.text = "بانتظار الاتصال... عنوانك: $ip"

        Thread {
            try {
                val server = ServerSocket(8888)
                serverSocket = server
                val client = server.accept()
                activeSocket = client
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
                    showOnly(screenGame)
                }
                listenLoop(reader)
            } catch (e: Exception) {
                runOnUiThread { netStatusText.text = "خطأ بالاتصال: ${e.message}" }
            }
        }.start()
    }

    private fun startJoin() {
        val ip = ipInput.text.toString().trim()
        if (ip.isEmpty()) {
            netStatusText.text = "اكتب عنوان IP المضيف أولاً"
            return
        }
        netStatusText.text = "جاري الاتصال بـ $ip ..."

        Thread {
            try {
                val client = Socket(ip, 8888)
                activeSocket = client
                writer = PrintWriter(client.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))

                runOnUiThread { netStatusText.text = "متصل! بانتظار بدء اللعبة..." }
                listenLoop(reader)
            } catch (e: Exception) {
                runOnUiThread { netStatusText.text = "فشل الاتصال: ${e.message}" }
            }
        }.start()
    }

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
                            showOnly(screenGame)
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
            val cellBg = GradientDrawable()
            cellBg.cornerRadius = 20f
            if (marked.contains(number)) {
                btn.text = "✗"
                btn.isEnabled = false
                cellBg.setColor(MARKED_BG)
                cellBg.setStroke(2, Color.parseColor("#3A3A5A"))
                btn.setTextColor(MARKED_TEXT)
            } else {
                cellBg.setColor(CARD)
                cellBg.setStroke(2, BORDER)
                btn.setTextColor(TEXT_MAIN)
            }
            btn.background = cellBg
            btn.setOnClickListener { onCellTapped(number) }
            grid.addView(btn)
        }
    }

    private fun onCellTapped(number: Int) {
        if (!gameStarted || !myTurn || marked.contains(number)) return
        sendMessage("TAP:$number")
        applyTap(number, fromRemote = false)
    }

    private fun sendMessage(msg: String) {
        Thread {
            try { writer?.println(msg) } catch (e: Exception) { }
        }.start()
    }

    private fun applyTap(number: Int, fromRemote: Boolean) {
        if (marked.contains(number)) return
        marked.add(number)

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i) as Button
            if (child.text.toString() == number.toString()) {
                child.isEnabled = false
                child.text = "✗"
                val cellBg = GradientDrawable()
                cellBg.cornerRadius = 20f
                cellBg.setColor(MARKED_BG)
                cellBg.setStroke(2, Color.parseColor("#3A3A5A"))
                child.background = cellBg
                child.setTextColor(MARKED_TEXT)
            }
        }

        val lines = countCompletedLines()
        if (lines > linesCompleted) linesCompleted = lines

        if (linesCompleted >= 5) {
            gameStarted = false
            val iWon = !fromRemote
            winText.text = if (iWon) "!BINGO — فزت 🏆" else "خسرت — فاز الطرف الآخر"
            showOnly(screenWin)
            return
        }

        myTurn = fromRemote
        statusText.text = if (myTurn) "دورك — اضغط أي رقم (خطوط: $linesCompleted)"
                           else "دور الطرف الآخر (خطوط: $linesCompleted)"
    }

    private fun countCompletedLines(): Int {
        var count = 0
        for (r in 0 until 5) {
            var full = true
            for (c in 0 until 5) if (!marked.contains(gridNumbers[r * 5 + c])) { full = false; break }
            if (full) count++
        }
        for (c in 0 until 5) {
            var full = true
            for (r in 0 until 5) if (!marked.contains(gridNumbers[r * 5 + c])) { full = false; break }
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

    private fun closeConnection() {
        gameStarted = false
        Thread {
            try { writer?.close() } catch (e: Exception) { }
            try { activeSocket?.close() } catch (e: Exception) { }
            try { serverSocket?.close() } catch (e: Exception) { }
        }.start()
    }

    override fun onDestroy() {
        closeConnection()
        super.onDestroy()
    }
}
