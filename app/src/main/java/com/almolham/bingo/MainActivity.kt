package com.almolham.bingo

import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// STEP 3: core single-device game logic (grid + scratch + win detection) — proven in
// isolation before we wire the networking from Step 2 into it.
class MainActivity : AppCompatActivity() {

    private val gridNumbers = (1..25).toMutableList()
    private val marked = mutableSetOf<Int>()
    private var linesCompleted = 0
    private var gameStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startBtn = findViewById<Button>(R.id.startBtn)
        val statusText = findViewById<TextView>(R.id.statusText)
        val grid = findViewById<GridLayout>(R.id.gameGrid)

        startBtn.setOnClickListener {
            gridNumbers.shuffle()
            marked.clear()
            linesCompleted = 0
            gameStarted = true
            statusText.text = "دورك — اضغط أي رقم"
            buildGrid(grid, statusText)
        }
    }

    private fun buildGrid(grid: GridLayout, statusText: TextView) {
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
            btn.setOnClickListener {
                onCellTapped(number, btn, statusText)
            }
            grid.addView(btn)
        }
    }

    private fun onCellTapped(number: Int, btn: Button, statusText: TextView) {
        if (!gameStarted || marked.contains(number)) return
        marked.add(number)
        btn.isEnabled = false
        btn.alpha = 0.4f

        val lines = countCompletedLines()
        if (lines > linesCompleted) {
            linesCompleted = lines
            statusText.text = "أكملت $linesCompleted خطوط!"
        }
        if (linesCompleted >= 5) {
            statusText.text = "🏆 فزت! أكملت 5 خطوط"
            gameStarted = false
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
}
