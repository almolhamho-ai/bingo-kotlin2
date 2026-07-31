package com.almolham.bingo

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
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
import android.widget.LinearLayout
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

    private enum class GameMode { NETWORK, AI }
    private var gameMode = GameMode.NETWORK

    // ---------- منطق الشبكة (لعب اونلاين بين جهازين) ----------
    private var gridNumbers = (1..25).toMutableList()
    private val marked = mutableSetOf<Int>()
    private var linesCompleted = 0
    private var gameStarted = false

    private var writer: PrintWriter? = null
    private var activeSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var myTurn = false

    // ---------- منطق اللعب ضد الذكاء الاصطناعي (متعدد الخصوم) ----------
    private class GamePlayer(val name: String, val isAI: Boolean, val grid: MutableList<Int>) {
        var lineCount = 0
    }

    private var players = mutableListOf<GamePlayer>()
    private var currentIdx = 0
    private val aiScratched = mutableSetOf<Int>()
    private var lastWinWasByHuman = true

    private var aiOpponentCount = 1
    private var aiDifficulty = "medium" // easy / medium / hard / expert
    private var aiSpeedIndex = 1 // 0 بطيء، 1 وسط، 2 سريع
    private val speedFactors = floatArrayOf(1.7f, 1f, 0.5f)

    private var pendingArrangement: MutableList<Int>? = null
    private val arrangingTaps = mutableListOf<Int>()
    private val aiNameInputs = mutableListOf<EditText>()
    private lateinit var diffButtons: List<Button>
    private lateinit var speedButtons: List<Button>

    private val allLinesIdx: List<List<Int>> by lazy {
        val lines = mutableListOf<List<Int>>()
        for (r in 0 until 5) lines.add((0 until 5).map { c -> r * 5 + c })
        for (c in 0 until 5) lines.add((0 until 5).map { r -> r * 5 + c })
        lines.add((0 until 5).map { it * 5 + it })
        lines.add((0 until 5).map { it * 5 + (4 - it) })
        lines
    }

    // ---------- الشاشات ----------
    private lateinit var screenLoading: View
    private lateinit var screenWelcome: View
    private lateinit var screenSettings: View
    private lateinit var screenNetwork: View
    private lateinit var screenGame: View
    private lateinit var screenWin: View
    private lateinit var screenAiSetup: View
    private lateinit var screenArrange: View
    private var currentScreen: View? = null

    private lateinit var statusText: TextView
    private lateinit var networkStatusText: TextView
    private lateinit var grid: GridLayout
    private lateinit var ipInput: EditText
    private lateinit var welcomeGreeting: TextView
    private lateinit var nameInput: EditText
    private lateinit var themeContainer: GridLayout
    private lateinit var winTitleText: TextView
    private lateinit var winSubtitleText: TextView
    private lateinit var winBadge: TextView

    private lateinit var aiCountText: TextView
    private lateinit var aiNamesContainer: LinearLayout
    private lateinit var arrangeStatusText: TextView
    private lateinit var arrangeGrid: GridLayout

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
        screenAiSetup = findViewById(R.id.screenAiSetup)
        screenArrange = findViewById(R.id.screenArrange)

        statusText = findViewById(R.id.statusText)
        networkStatusText = findViewById(R.id.networkStatusText)
        grid = findViewById(R.id.gameGrid)
        ipInput = findViewById(R.id.ipInput)
        welcomeGreeting = findViewById(R.id.welcomeGreeting)
        nameInput = findViewById(R.id.nameInput)
        themeContainer = findViewById(R.id.themeContainer)
        winTitleText = findViewById(R.id.winTitleText)
        winSubtitleText = findViewById(R.id.winSubtitleText)
        winBadge = findViewById(R.id.winBadge)

        aiCountText = findViewById(R.id.aiCountText)
        aiNamesContainer = findViewById(R.id.aiNamesContainer)
        arrangeStatusText = findViewById(R.id.arrangeStatusText)
        arrangeGrid = findViewById(R.id.arrangeGrid)

        ipInput.setText(prefs.getString("last_ip", ""))
        currentThemeIndex = prefs.getInt("theme_index", 0)

        setupLoadingScreen()
        setupWelcomeScreen()
        setupSettingsScreen()
        setupNetworkScreen()
        setupAiSetupScreen()
        setupArrangeScreen()
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
                    screenAiSetup -> showOnly(screenWelcome)
                    screenArrange -> showOnly(screenAiSetup)
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

    // إغلاق الاتصال بشكل نظيف (بدون تسريب سوكيت) وإيقاف أي لعبة جارية
    private fun closeConnection() {
        gameStarted = false
        gameMode = GameMode.NETWORK
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
        listOf(
            screenLoading, screenWelcome, screenSettings, screenNetwork,
            screenGame, screenWin, screenAiSetup, screenArrange
        ).forEach {
            it.visibility = if (it == screen) View.VISIBLE else View.GONE
        }
        currentScreen = screen
        if (screen == screenWin) {
            if (lastWinWasByHuman) playWinCelebration() else playLoseTransition()
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

        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()

        val letters = listOf(letterB, letterI, letterN, letterG, letterO)
        val fromX = floatArrayOf(-screenW, 0f, 0f, 0f, screenW)
        val fromY = floatArrayOf(0f, -screenH, -screenH, screenH, 0f)
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
        val cardAI = findViewById<Button>(R.id.cardAI)
        addPressAnimation(cardAI)
        cardAI.setOnClickListener { openAiSetup() }

        val comingSoonIds = listOf(R.id.cardFriends, R.id.cardLevels, R.id.cardDaily, R.id.cardTournament)
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

        val buttonIds = listOf(R.id.startTapBtn, R.id.saveSettingsBtn, R.id.hostBtn, R.id.joinBtn, R.id.playAgainBtn, R.id.startAiSetupBtn)
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
        gameMode = GameMode.NETWORK
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
        gameMode = GameMode.NETWORK
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

    // ---------- شاشة 5: اللعب الشبكي ----------
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
        if (gameMode == GameMode.NETWORK) sendMessage("TAP:$number")
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
            lastWinWasByHuman = iWon
            winBadge.text = if (iWon) "🏆" else "😔"
            winTitleText.text = if (iWon) "!BINGO — فزت 🏆" else "خسرت — فاز الطرف الآخر"
            winSubtitleText.text = ""
            playOutcomeSound(iWon)
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

    // ================================================================
    // ========== شاشة 7: إعدادات اللعب ضد الذكاء الاصطناعي ==========
    // ================================================================
    private fun setupAiSetupScreen() {
        val backBtn = findViewById<Button>(R.id.backFromAiSetupBtn)
        val minusBtn = findViewById<Button>(R.id.aiCountMinusBtn)
        val plusBtn = findViewById<Button>(R.id.aiCountPlusBtn)
        val arrangeManualBtn = findViewById<Button>(R.id.arrangeManualBtn)
        val arrangeRandomBtn = findViewById<Button>(R.id.arrangeRandomBtn)
        val startBtn = findViewById<Button>(R.id.startAiSetupBtn)

        diffButtons = listOf(
            findViewById(R.id.diffEasyBtn), findViewById(R.id.diffMediumBtn),
            findViewById(R.id.diffHardBtn), findViewById(R.id.diffExpertBtn)
        )
        speedButtons = listOf(
            findViewById(R.id.speedSlowBtn), findViewById(R.id.speedMediumBtn), findViewById(R.id.speedFastBtn)
        )

        listOf(backBtn, minusBtn, plusBtn, arrangeManualBtn, arrangeRandomBtn, startBtn).forEach { addPressAnimation(it) }
        (diffButtons + speedButtons).forEach { addPressAnimation(it) }

        backBtn.setOnClickListener { showOnly(screenWelcome) }

        minusBtn.setOnClickListener {
            if (aiOpponentCount > 1) { aiOpponentCount--; updateAiCountUI() }
        }
        plusBtn.setOnClickListener {
            if (aiOpponentCount < 5) { aiOpponentCount++; updateAiCountUI() }
        }

        val difficulties = listOf("easy", "medium", "hard", "expert")
        diffButtons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                aiDifficulty = difficulties[i]
                highlightGroup(diffButtons, i)
            }
        }
        speedButtons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                aiSpeedIndex = i
                highlightGroup(speedButtons, i)
            }
        }

        arrangeManualBtn.setOnClickListener {
            arrangingTaps.clear()
            buildArrangeGrid()
            showOnly(screenArrange)
        }
        arrangeRandomBtn.setOnClickListener {
            pendingArrangement = (1..25).shuffled().toMutableList()
            arrangeStatusText.text = "✅ ترتيب عشوائي جاهز (اضغط الزر تاني لإعادة الترتيب)"
        }

        startBtn.setOnClickListener {
            val arrangement = pendingArrangement
            if (arrangement == null) {
                Toast.makeText(this, "لازم ترتب شبكتك أولاً (يدوي أو عشوائي)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startAiGame(arrangement)
        }
    }

    private fun highlightGroup(buttons: List<Button>, selectedIndex: Int) {
        buttons.forEachIndexed { i, b ->
            if (i == selectedIndex) {
                b.setBackgroundResource(R.drawable.bg_button_gold)
                b.setTextColor(getColor(R.color.dark_text_on_gold))
            } else {
                b.setBackgroundResource(R.drawable.bg_input)
                b.setTextColor(getColor(R.color.text_main))
            }
        }
    }

    private fun updateAiCountUI() {
        aiCountText.text = aiOpponentCount.toString()
        rebuildAiNameInputs()
    }

    private fun rebuildAiNameInputs() {
        aiNamesContainer.removeAllViews()
        aiNameInputs.clear()
        for (i in 1..aiOpponentCount) {
            val e = EditText(this)
            e.hint = "اسم الخصم $i (اختياري)"
            e.setHintTextColor(getColor(R.color.muted))
            e.setTextColor(getColor(R.color.text_main))
            e.setBackgroundResource(R.drawable.bg_input)
            e.setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 8)
            e.layoutParams = lp
            aiNamesContainer.addView(e)
            aiNameInputs.add(e)
        }
    }

    private fun openAiSetup() {
        aiOpponentCount = 1
        aiDifficulty = "medium"
        aiSpeedIndex = 1
        pendingArrangement = null
        arrangingTaps.clear()
        updateAiCountUI()
        highlightGroup(diffButtons, 1)
        highlightGroup(speedButtons, 1)
        arrangeStatusText.text = "⚠️ لسا لازم ترتب شبكتك (يدوي أو عشوائي)"
        showOnly(screenAiSetup)
    }

    // ================================================================
    // ========== شاشة 8: ترتيب الشبكة يدوياً ==========
    // ================================================================
    private fun setupArrangeScreen() {
        val backBtn = findViewById<Button>(R.id.backFromArrangeBtn)
        addPressAnimation(backBtn)
        backBtn.setOnClickListener { showOnly(screenAiSetup) }
    }

    private fun buildArrangeGrid() {
        arrangeGrid.removeAllViews()
        for (i in 1..25) {
            val btn = Button(this)
            btn.text = i.toString()
            btn.textSize = 14f
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec((i - 1) % 5, 1f)
            params.rowSpec = GridLayout.spec((i - 1) / 5)
            params.setMargins(4, 4, 4, 4)
            btn.layoutParams = params
            btn.setBackgroundResource(R.drawable.bg_cell)
            btn.setTextColor(getColor(R.color.text_main))
            addPressAnimation(btn)
            btn.setOnClickListener {
                if (arrangingTaps.contains(i)) return@setOnClickListener
                arrangingTaps.add(i)
                btn.isEnabled = false
                btn.setBackgroundResource(R.drawable.bg_cell_marked)
                btn.setTextColor(getColor(R.color.marked_text))
                if (arrangingTaps.size == 25) {
                    pendingArrangement = arrangingTaps.toMutableList()
                    arrangeStatusText.text = "✅ ترتيب يدوي جاهز"
                    showOnly(screenAiSetup)
                }
            }
            arrangeGrid.addView(btn)
        }
    }

    // ================================================================
    // ========== محرك اللعب ضد الذكاء الاصطناعي (لعبة فعلية) ==========
    // ================================================================
    private fun startAiGame(arrangement: MutableList<Int>) {
        gameMode = GameMode.AI
        aiScratched.clear()
        players = mutableListOf()

        val humanName = prefs.getString("player_name", "لاعب") ?: "لاعب"
        players.add(GamePlayer(humanName, false, arrangement))

        for (i in 0 until aiOpponentCount) {
            val typed = aiNameInputs.getOrNull(i)?.text?.toString()?.trim()
            val nm = if (typed.isNullOrEmpty()) "الذكاء الاصطناعي ${i + 1}" else typed
            players.add(GamePlayer(nm, true, (1..25).shuffled().toMutableList()))
        }

        currentIdx = 0 // أنا يلي بلعب أول دايماً
        gameStarted = true
        buildAiGrid()
        updateAiStatusText()
        showOnly(screenGame)
    }

    private fun buildAiGrid() {
        grid.removeAllViews()
        val human = players[0]
        for (i in 0 until 25) {
            val number = human.grid[i]
            val btn = Button(this)
            btn.text = number.toString()
            btn.textSize = 16f
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(i % 5, 1f)
            params.rowSpec = GridLayout.spec(i / 5)
            params.setMargins(4, 4, 4, 4)
            btn.layoutParams = params
            if (aiScratched.contains(number)) {
                btn.isEnabled = false
                btn.setBackgroundResource(R.drawable.bg_cell_marked)
                btn.setTextColor(getColor(R.color.marked_text))
            } else {
                btn.setBackgroundResource(R.drawable.bg_cell)
                btn.setTextColor(getColor(R.color.text_main))
            }
            addPressAnimation(btn)
            btn.setOnClickListener { onAiCellTapped(number) }
            grid.addView(btn)
        }
    }

    private fun onAiCellTapped(number: Int) {
        if (!gameStarted || gameMode != GameMode.AI) return
        if (currentIdx != 0) return // مش دورك
        if (aiScratched.contains(number)) return
        applyCall(number)
    }

    private fun applyCall(number: Int) {
        if (aiScratched.contains(number)) return
        aiScratched.add(number)

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

        var winnerIdx = -1
        for ((idx, p) in players.withIndex()) {
            val lines = countLines(p.grid, aiScratched)
            if (lines > p.lineCount) p.lineCount = lines
            if (p.lineCount >= 5 && winnerIdx == -1) winnerIdx = idx
        }

        if (winnerIdx != -1) {
            finishAiGame(winnerIdx)
            return
        }

        nextAiTurn()
    }

    private fun nextAiTurn() {
        currentIdx = (currentIdx + 1) % players.size
        updateAiStatusText()
        val p = players[currentIdx]
        if (p.isAI) {
            val delayMs = (900 * speedFactors[aiSpeedIndex]).toLong()
            backHandler.postDelayed({
                if (!gameStarted || gameMode != GameMode.AI) return@postDelayed
                val avail = p.grid.filter { !aiScratched.contains(it) }
                if (avail.isEmpty()) { nextAiTurn(); return@postDelayed }
                val choice = pickAiNumberFor(p, avail)
                applyCall(choice)
            }, delayMs)
        }
    }

    private fun updateAiStatusText() {
        val p = players[currentIdx]
        statusText.text = if (!p.isAI) "دورك — اضغط أي رقم من شبكتك" else "دور ${p.name}..."
    }

    // يفضّل رقم يكمّل خط، غير هيك بيعتمد على مستوى الصعوبة المختار
    private fun pickAiNumberFor(p: GamePlayer, avail: List<Int>): Int {
        return when (aiDifficulty) {
            "easy" -> avail.random()
            "medium" -> aiMediumPick(p, avail)
            else -> aiExpertPick(p, avail) // صعب وخبير كلاهما يستخدما نفس المنطق الأذكى
        }
    }

    private fun aiMediumPick(p: GamePlayer, avail: List<Int>): Int {
        var best = avail.first()
        var bestScore = -1
        for (n in avail) {
            val i = p.grid.indexOf(n)
            var score = 0
            for (line in allLinesIdx) {
                if (i in line) {
                    val filled = line.count { aiScratched.contains(p.grid[it]) }
                    if (filled > score) score = filled
                }
            }
            if (score > bestScore || (score == bestScore && Random.nextFloat() < 0.4f)) {
                bestScore = score
                best = n
            }
        }
        return best
    }

    private fun aiExpertPick(p: GamePlayer, avail: List<Int>): Int {
        var best = avail.first()
        var bestScore = -1
        for (n in avail) {
            val i = p.grid.indexOf(n)
            var score = 0
            for (line in allLinesIdx) {
                if (i !in line) continue
                val remaining = line.count { !aiScratched.contains(p.grid[it]) }
                score += if (remaining <= 1) 1000 else (5 - remaining) * 25
            }
            if (score > bestScore) { bestScore = score; best = n }
        }
        return best
    }

    private fun finishAiGame(winnerIdx: Int) {
        gameStarted = false
        val winner = players[winnerIdx]
        val humanWon = winnerIdx == 0
        lastWinWasByHuman = humanWon
        val losers = players.filterIndexed { idx, _ -> idx != winnerIdx }.map { it.name }

        winBadge.text = if (humanWon) "🏆" else "😔"
        winTitleText.text = if (humanWon) "!BINGO — فزت 🏆" else "فاز ${winner.name} 🏆"
        winSubtitleText.text = if (losers.isNotEmpty()) "خسر: ${losers.joinToString("، ")}" else ""

        playOutcomeSound(humanWon)
        showOnly(screenWin)
    }

    private fun countLines(gridArr: List<Int>, scratched: Set<Int>): Int {
        var count = 0
        for (r in 0 until 5) {
            var full = true
            for (c in 0 until 5) { if (!scratched.contains(gridArr[r * 5 + c])) { full = false; break } }
            if (full) count++
        }
        for (c in 0 until 5) {
            var full = true
            for (r in 0 until 5) { if (!scratched.contains(gridArr[r * 5 + c])) { full = false; break } }
            if (full) count++
        }
        var d1 = true
        for (i in 0 until 5) if (!scratched.contains(gridArr[i * 5 + i])) { d1 = false; break }
        if (d1) count++
        var d2 = true
        for (i in 0 until 5) if (!scratched.contains(gridArr[i * 5 + (4 - i)])) { d2 = false; break }
        if (d2) count++
        return count
    }

    // ---------- شاشة 6: الفوز/الخسارة ----------
    private fun playOutcomeSound(won: Boolean) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            if (won) {
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                backHandler.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 150) }, 160)
                backHandler.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_ACK, 300) }, 320)
                backHandler.postDelayed({ tg.release() }, 650)
            } else {
                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                backHandler.postDelayed({ tg.release() }, 450)
            }
        } catch (e: Exception) { }
    }

    // احتفال كامل (شارة + كونفيتي) — يظهر فقط إذا أنا الفائز
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

        winSubtitleText.alpha = 0f
        winSubtitleText.animate().alpha(1f).setStartDelay(300).setDuration(400).start()

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

    // انتقال بسيط بدون احتفال — لما ما أكون أنا الفائز
    private fun playLoseTransition() {
        winBadge.alpha = 0f
        winBadge.animate().alpha(1f).setDuration(400).start()
        winTitleText.alpha = 0f
        winTitleText.animate().alpha(1f).setDuration(400).start()
        winSubtitleText.alpha = 0f
        winSubtitleText.animate().alpha(1f).setStartDelay(150).setDuration(400).start()
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
