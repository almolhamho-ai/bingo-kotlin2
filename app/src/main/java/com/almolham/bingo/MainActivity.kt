package com.almolham.bingo

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    // ---------- منطق اللعبة ----------
    private var gridNumbers = (1..25).toMutableList()
    private val marked = mutableSetOf<Int>()
    private var linesCompleted = 0
    private var gameStarted = false

    private var writer: PrintWriter? = null
    private var activeSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var myTurn = false

    // ---------- الشاشات ----------
    private lateinit var screenLoading: View
    private lateinit var screenWelcome: View
    private lateinit var screenSettings: View
    private lateinit var screenNetwork: View
    private lateinit var screenGame: View
    private lateinit var screenWin: View
    private var currentScreen: View? = null

    private lateinit var statusText: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var grid: GridLayout
    private lateinit var ipInput: EditText
    private lateinit var welcomeGreeting: TextView
    private lateinit var nameInput: EditText
    private lateinit var themeContainer: GridLayout
    private lateinit var winTitleText: TextView
    private lateinit var winBadge: View

    private lateinit var prefs: android.content.SharedPreferences

    private var backPressedOnce = false
    private val backHandler = Handler(Looper.getMainLooper())

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
        screenWin = findViewById(R.id.screenWin)

        statusText = findViewById(R.id.statusText)
        networkStatusText = findViewById(R.id.networkStatusText)
        grid = findViewById(R.id.gameGrid)
        ipInput = findViewById(R.id.ipInput)
        welcomeGreeting = findViewById(R.id.welcomeGreeting)
        nameInput = findViewById(R.id.nameInput)
        themeContainer = findViewById(R.id.themeContainer)
        winTitleText = findViewById(R.id.winTitleText)
        winBadge = findViewById(R.id.winBadge)

        ipInput.setText(prefs.getString("last_ip", ""))
        currentThemeIndex = prefs.getInt("theme_index", 0)

        setupLoadingScreen()
        setupWelcomeScreen()
        setupSettingsScreen()
        setupNetworkScreen()
        setupBackNavigation()

        val headlineTypeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        welcomeGreeting.typeface = headlineTypeface
        listOf(R.id.letterB, R.id.letterI, R.id.letterN, R.id.letterG, R.id.letterO).forEach { id ->
            findViewById<TextView>(id).typeface = headlineTypeface
        }

        applyTheme(currentThemeIndex)
        showOnly(screenLoading)
    }

    // ---------- زر الرجوع ----------
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    screenSettings -> showOnly(screenWelcome)
                    screenNetwork -> {
                        closeConnection()
                        showOnly(screenWelcome)
                    }
                    screenGame -> {
                        closeConnection()
                        showOnly(screenWelcome)
                    }
                    screenWin -> {
                        closeConnection()
                        showOnly(screenWelcome)
                    }
                    screenWelcome -> {
                        if (backPressedOnce) {
                            finish()
                        } else {
                            backPressedOnce = true
                            Toast.makeText(this@MainActivity, "اضغط رجوع مرة ثانية للخروج", Toast.LENGTH_SHORT).show()
                            backHandler.postDelayed({ backPressedOnce = false }, 2000)
                        }
                    }
                    else -> { /* شاشة التحميل: لا شي */ }
                }
            }
        })
    }

    // إغلاق الاتصال بشكل نظيف (بدون تسريب سوكيت)
    private fun closeConnection() {
        gameStarted = false
        Thread {
            try { writer?.close() } catch (e: Exception) { }
            try { activeSocket?.close() } catch (e: Exception) { }
            try { serverSocket?.close() } catch (e: Exception) { }
        }.start()
        writer = null
        activeSocket = null
        serverSocket = null
    }

    // إظهار شاشة وحدة بس وإخفاء الباقي
    private fun showOnly(screen: View) {
        listOf(screenLoading, screenWelcome, screenSettings, screenNetwork, screenGame, screenWin).forEach {
            it.visibility = if (it == screen) View.VISIBLE else View.GONE
        }
        currentScreen = screen
        if (screen == screenWin) {
            playWinCelebration()
        }
    }

    // ---------- تأثير ضغط بسيط على الأزرار/البطاقات ----------
    private fun addPressAnimation(view: View?) {
        view ?: return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false // يسمح بمرور حدث الضغط (onClick) بشكل طبيعي
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
        addPressAnimation(startTapBtn)

        val letters = listOf(letterB, letterI, letterN, letterG, letterO)
        val fromX = floatArrayOf(-600f, 0f, 0f, 0f, 600f)
        val fromY = floatArrayOf(0f, -600f, -600f, 600f, 0f)
        val fromRotation = floatArrayOf(-140f, 140f, -140f, 140f, -140f)
        val delays = longArrayOf(50, 180, 310, 440, 570)

        letters.forEachIndexed { i, tv ->
            tv.translationX = fromX[i]
            tv.translationY = fromY[i]
            tv.rotation = fromRotation[i]
            tv.alpha = 0f
            tv.animate()
                .translationX(0f).translationY(0f).rotation(0f).alpha(1f)
                .setInterpolator(OvershootInterpolator(2.2f))
                .setStartDelay(delays[i])
                .setDuration(650)
                .start()
        }

        startTapBtn.postDelayed({
            startTapBtn.visibility = View.VISIBLE
            startTapBtn.alpha = 0f
            startTapBtn.scaleX = 0.6f
            startTapBtn.scaleY = 0.6f
            startTapBtn.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setInterpolator(OvershootInterpolator(2f))
                .setDuration(450)
                .start()
        }, 1400)

        startTapBtn.setOnClickListener {
            welcomeGreeting.text = "أهلاً، ${prefs.getString("player_name", "لاعب")}"
            showOnly(screenWelcome)
        }
    }

    // ---------- شاشة 2: القائمة الرئيسية ----------
    private fun setupWelcomeScreen() {
        val settingsBtn = findViewById<Button>(R.id.settingsIconBtn)
        val cardNetwork = findViewById<Button>(R.id.cardNetwork)
        val playAgainBtn = findViewById<Button>(R.id.playAgainBtn)
        val winHomeBtn = findViewById<Button>(R.id.winHomeBtn)

        addPressAnimation(settingsBtn)
        addPressAnimation(cardNetwork)
        addPressAnimation(playAgainBtn)
        addPressAnimation(winHomeBtn)

        settingsBtn.setOnClickListener {
            nameInput.setText(prefs.getString("player_name", "لاعب"))
            showOnly(screenSettings)
        }
        cardNetwork.setOnClickListener {
            showOnly(screenNetwork)
        }
        val comingSoonIds = listOf(R.id.cardAI, R.id.cardFriends, R.id.cardLevels, R.id.cardDaily, R.id.cardTournament)
        comingSoonIds.forEach { id ->
            val btn = findViewById<Button>(id)
            addPressAnimation(btn)
            btn.setOnClickListener {
                Toast.makeText(this, "هاد النمط قريباً إن شاء الله 🙂", Toast.LENGTH_SHORT).show()
            }
        }

        playAgainBtn.setOnClickListener {
            closeConnection()
            showOnly(screenWelcome)
        }
        winHomeBtn.setOnClickListener {
            closeConnection()
            showOnly(screenWelcome)
        }
    }

    // ---------- شاشة 3: الإعدادات ----------
    private fun setupSettingsScreen() {
        val backBtn = findViewById<Button>(R.id.backFromSettingsBtn)
        val saveBtn = findViewById<Button>(R.id.saveSettingsBtn)
        addPressAnimation(backBtn)
        addPressAnimation(saveBtn)

        backBtn.setOnClickListener {
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
            addPressAnimation(dot)
            dot.setOnClickListener {
                currentThemeIndex = index
                applyTheme(index)
                setupSettingsScreen() // إعادة رسم النقاط لتحديث المؤشر عالمختار
            }
            themeContainer.addView(dot)
        }

        saveBtn.setOnClickListener {
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

        val buttonIds = listOf(R.id.startTapBtn, R.id.saveSettingsBtn, R.id.hostBtn, R.id.joinBtn, R.id.playAgainBtn)
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
        val backBtn = findViewById<Button>(R.id.backFromNetworkBtn)
        val hostBtn = findViewById<Button>(R.id.hostBtn)
        val joinBtn = findViewById<Button>(R.id.joinBtn)
        addPressAnimation(backBtn)
        addPressAnimation(hostBtn)
        addPressAnimation(joinBtn)

        backBtn.setOnClickListener {
            closeConnection()
            showOnly(screenWelcome)
        }
        hostBtn.setOnClickListener { startHost() }
        joinBtn.setOnClickListener { startJoin() }
    }

    private fun startHost() {
        val ip = getLocalIpAddress()
        networkStatusText.text = "بانتظار الاتصال... عنوانك: $ip"

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
                activeSocket = client
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
            runOnUiThread { if (currentScreen == screenGame) statusText.text = "انقطع الاتصال" }
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
            addPressAnimation(btn)
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
                runOnUiThread { if (currentScreen == screenGame) statusText.text = "فشل الإرسال: ${e.message}" }
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
                child.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120)
                    .withEndAction { child.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
                    .start()
            }
        }

        val lines = countCompletedLines()
        if (lines > linesCompleted) {
            linesCompleted = lines
        }

        if (linesCompleted >= 5) {
            gameStarted = false
            // اللي ضغط الرقم اللي كمّل الفوز هو الفائز الحقيقي:
            // fromRemote = false يعني أنا يلي ضغطت → أنا الفائز
            // fromRemote = true يعني الرسالة إجت من الطرف الآخر → هو الفائز، أنا خسرت
            val iWon = !fromRemote
            winTitleText.text = if (iWon) "!BINGO — فزت 🏆" else "خسرت — فاز الطرف الآخر"
            showOnly(screenWin)
            return
        }

        myTurn = fromRemote
        statusText.text = if (myTurn) "دورك — اضغط أي رقم (خطوط: $linesCompleted)"
                           else "دور الطرف الآخر (خطوط: $linesCompleted)"
    }

    // ---------- شاشة 6: احتفال الفوز ----------
    private fun playWinCelebration() {
        winBadge.scaleX = 0f
        winBadge.scaleY = 0f
        winBadge.animate().scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(3f))
            .setDuration(500)
            .start()

        winTitleText.alpha = 0f
        winTitleText.translationY = 30f
        winTitleText.animate().alpha(1f).translationY(0f)
            .setStartDelay(200)
            .setDuration(400)
            .start()

        val container = screenWin as? ViewGroup ?: return
        val emojis = listOf("🎉", "✨", "⭐", "🎊", "🥳")
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        for (i in 0 until 18) {
            val piece = TextView(this)
            piece.text = emojis[Random.nextInt(emojis.size)]
            piece.textSize = 20f
            piece.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            container.addView(piece)

            val startX = Random.nextFloat() * screenWidth - screenWidth / 2f
            piece.translationX = startX
            piece.translationY = -500f
            piece.alpha = 0f
            piece.rotation = Random.nextFloat() * 360f

            piece.animate()
                .translationY(1100f)
                .translationX(startX + Random.nextInt(-150, 150))
                .rotation(piece.rotation + 380f)
                .alpha(1f)
                .setStartDelay(Random.nextLong(0, 350))
                .setDuration(1500)
                .withEndAction {
                    piece.animate().alpha(0f).setDuration(250)
                        .withEndAction { container.removeView(piece) }
                        .start()
                }
                .start()
        }
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

    override fun onDestroy() {
        closeConnection()
        super.onDestroy()
    }
}
