package com.almolham.bingo

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class MainActivity : AppCompatActivity() {

    // ---------- منطق اللعبة ----------
    private var gridNumbers = (1..25).toMutableList()
    private val marked = mutableSetOf<Int>()
    private var linesCompleted = 0
    private var gameStarted = false

    private var writer: PrintWriter? = null
    private var myTurn = false

    // ---------- الشاشات ----------
    private lateinit var screenLoading: View
    private lateinit var screenWelcome: View
    private lateinit var screenSettings: View
    private lateinit var screenNetwork: View
    private lateinit var screenGame: View

    private lateinit var statusText: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var grid: GridLayout
    private lateinit var ipInput: EditText
    private lateinit var welcomeGreeting: TextView
    private lateinit var nameInput: EditText
    private lateinit var themeContainer: GridLayout

    private lateinit var prefs: android.content.SharedPreferences

    // كل ثيم = لون أساسي + لون ثانوي (للتدرّج)، نفس أسلوب الذهبي الافتراضي
    private val themePresets = listOf(
        Pair("#F0B429", "#E67E22"), // ذهبي
        Pair("#00C97A", "#0099A8"), // أخضر
        Pair("#3D8BFF", "#0057D8"), // أزرق
        Pair("#8B5CF6", "#5B21B6"), // بنفسجي
        Pair("#FF4757", "#C0392B"), // أحمر
        Pair("#FB7185", "#BE185D"), // وردي
        Pair("#0EA5E9", "#0369A1"), // سماوي
        Pair("#F97316", "#C2410C"), // برتقالي
        Pair("#EAB308", "#A16207"), // كهرماني
        Pair("#94A3B8", "#475569")  // فضي
    )
    private var currentThemeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("bingo_prefs", MODE_PRIVATE)

        screenLoading = findViewById(R.id.screenLoading)
        screenWelcome = findViewById(R.id.screenWelcome)
        screenSettings = findViewById(R.id.screenSettings)
        screenNetwork = findViewById(R.id.screenNetwork)
        screenGame = findViewById(R.id.screenGame)

        statusText = findViewById(R.id.statusText)
        networkStatusText = findViewById(R.id.networkStatusText)
        grid = findViewById(R.id.gameGrid)
        ipInput = findViewById(R.id.ipInput)
        welcomeGreeting = findViewById(R.id.welcomeGreeting)
        nameInput = findViewById(R.id.nameInput)
        themeContainer = findViewById(R.id.themeContainer)

        ipInput.setText(prefs.getString("last_ip", ""))
        currentThemeIndex = prefs.getInt("theme_index", 0)

        setupLoadingScreen()
        setupWelcomeScreen()
        setupSettingsScreen()
        setupNetworkScreen()

        applyTheme(currentThemeIndex)
        showOnly(screenLoading)
    }

    // إظهار شاشة وحدة بس وإخفاء الباقي
    private fun showOnly(screen: View) {
        listOf(screenLoading, screenWelcome, screenSettings, screenNetwork, screenGame).forEach {
            it.visibility = if (it == screen) View.VISIBLE else View.GONE
        }
    }

    // ---------- شاشة 1: الافتتاحية ----------
    private fun setupLoadingScreen() {
        val letterB = findViewById<TextView>(R.id.letterB)
        val letterI = findViewById<TextView>(R.id.letterI)
        val letterN = findViewById<TextView>(R.id.letterN)
        val letterG = findViewById<TextView>(R.id.letterG)
        val letterO = findViewById<TextView>(R.id.letterO)
        val startTapBtn = findViewById<Button>(R.id.startTapBtn)

        val letters = listOf(letterB, letterI, letterN, letterG, letterO)
        val fromX = floatArrayOf(-600f, 0f, 0f, 0f, 600f)
        val fromY = floatArrayOf(0f, -600f, -600f, 600f, 0f)
        val delays = longArrayOf(50, 180, 310, 440, 570)

        letters.forEachIndexed { i, tv ->
            tv.translationX = fromX[i]
            tv.translationY = fromY[i]
            tv.alpha = 0f
            tv.animate()
                .translationX(0f).translationY(0f).alpha(1f)
                .setStartDelay(delays[i])
                .setDuration(500)
                .start()
        }

        startTapBtn.postDelayed({
            startTapBtn.visibility = View.VISIBLE
            startTapBtn.alpha = 0f
            startTapBtn.animate().alpha(1f).setDuration(400).start()
        }, 1300)

        startTapBtn.setOnClickListener {
            welcomeGreeting.text = "أهلاً، ${prefs.getString("player_name", "لاعب")}"
            showOnly(screenWelcome)
        }
    }

    // ---------- شاشة 2: القائمة الرئيسية ----------
    private fun setupWelcomeScreen() {
        findViewById<Button>(R.id.settingsIconBtn).setOnClickListener {
            nameInput.setText(prefs.getString("player_name", "لاعب"))
            showOnly(screenSettings)
        }
        findViewById<Button>(R.id.cardNetwork).setOnClickListener {
            showOnly(screenNetwork)
        }
        val comingSoonIds = listOf(R.id.cardAI, R.id.cardFriends, R.id.cardLevels, R.id.cardDaily, R.id.cardTournament)
        comingSoonIds.forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                Toast.makeText(this, "هاد النمط قريباً إن شاء الله 🙂", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- شاشة 3: الإعدادات ----------
    private fun setupSettingsScreen() {
        findViewById<Button>(R.id.backFromSettingsBtn).setOnClickListener {
            showOnly(screenWelcome)
        }

        themeContainer.removeAllViews()
        themePresets.forEachIndexed { index, colors ->
            val dot = Button(this)
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(android.graphics.Color.parseColor(colors.first))
            if (index == currentThemeIndex) {
                drawable.setStroke(6, android.graphics.Color.WHITE)
            }
            dot.background = drawable
            val size = (48 * resources.displayMetrics.density).toInt()
            val params = GridLayout.LayoutParams()
            params.width = size
            params.height = size
            params.setMargins(8, 8, 8, 8)
            params.columnSpec = GridLayout.spec(index % 5)
            params.rowSpec = GridLayout.spec(index / 5)
            dot.layoutParams = params
            dot.setOnClickListener {
                currentThemeIndex = index
                applyTheme(index)
                setupSettingsScreen() // إعادة رسم النقاط لتحديث المؤشر عالمختار
            }
            themeContainer.addView(dot)
        }

        findViewById<Button>(R.id.saveSettingsBtn).setOnClickListener {
            val name = nameInput.text.toString().trim().ifEmpty { "لاعب" }
            prefs.edit()
                .putString("player_name", name)
                .putInt("theme_index", currentThemeIndex)
                .apply()
            welcomeGreeting.text = "أهلاً، $name"
            showOnly(screenWelcome)
        }
    }

    // تطبيق لون الثيم على كل الأزرار الرئيسية بالتطبيق
    private fun applyTheme(index: Int) {
        val (startHex, endHex) = themePresets[index]
        val startColor = android.graphics.Color.parseColor(startHex)
        val endColor = android.graphics.Color.parseColor(endHex)

        val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(startColor, endColor))
        gradient.cornerRadius = 200f

        val buttonIds = listOf(R.id.startTapBtn, R.id.cardNetwork, R.id.saveSettingsBtn, R.id.hostBtn, R.id.joinBtn)
        buttonIds.forEach { id ->
            findViewById<Button>(id)?.background = gradient.constantState?.newDrawable()
        }

        val accentTextIds = listOf(R.id.welcomeGreeting, R.id.statusText, R.id.networkStatusText)
        accentTextIds.forEach { id ->
            findViewById<TextView>(id)?.setTextColor(startColor)
        }

        letterViewsAccent(startColor)
    }

    private fun letterViewsAccent(color: Int) {
        listOf(R.id.letterB, R.id.letterI, R.id.letterN, R.id.letterG, R.id.letterO).forEach { id ->
            findViewById<TextView>(id)?.setTextColor(color)
        }
    }

    // ---------- شاشة 4: الشبكة ----------
    private fun setupNetworkScreen() {
        findViewById<Button>(R.id.backFromNetworkBtn).setOnClickListener {
            showOnly(screenWelcome)
        }
        findViewById<Button>(R.id.hostBtn).setOnClickListener { startHost() }
        findViewById<Button>(R.id.joinBtn).setOnClickListener { startJoin() }
    }

    private fun startHost() {
        val ip = getLocalIpAddress()
        networkStatusText.text = "بانتظار الاتصال... عنوانك: $ip"

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
                    showOnly(screenGame)
                }
                listenLoop(reader)
            } catch (e: Exception) {
                runOnUiThread { networkStatusText.text = "خطأ بالاتصال: ${e.message}" }
            }
        }.start()
    }

    private fun startJoin() {
        val ip = ipInput.text.toString().trim()
        if (ip.isEmpty()) {
            networkStatusText.text = "اكتب عنوان IP المضيف أولاً"
            return
        }

        prefs.edit().putString("last_ip", ip).apply()
        networkStatusText.text = "جاري الاتصال بـ $ip ..."

        Thread {
            try {
                val client = Socket(ip, 8888)
                writer = PrintWriter(client.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))

                runOnUiThread { networkStatusText.text = "متصل! بانتظار بدء اللعبة..." }
                listenLoop(reader)
            } catch (e: Exception) {
                runOnUiThread { networkStatusText.text = "فشل الاتصال: ${e.message}" }
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

    // ---------- شاشة 5: اللعب ----------
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
                btn.setBackgroundResource(R.drawable.bg_cell_marked)
                btn.setTextColor(getColor(R.color.marked_text))
            } else {
                btn.setBackgroundResource(R.drawable.bg_cell)
                btn.setTextColor(getColor(R.color.text_main))
            }
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
            try {
                writer?.println(msg)
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "فشل الإرسال: ${e.message}" }
            }
        }.start()
    }

    private fun applyTap(number: Int, fromRemote: Boolean) {
        if (marked.contains(number)) return
        marked.add(number)

        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i) as Button
            if (child.text.toString() == number.toString()) {
                child.isEnabled = false
                child.setBackgroundResource(R.drawable.bg_cell_marked)
                child.setTextColor(getColor(R.color.marked_text))
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
