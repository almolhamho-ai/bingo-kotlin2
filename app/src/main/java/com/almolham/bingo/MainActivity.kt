package com.almolham.bingo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==========================================
// نظام الألوان (Color Palette & Gradients)
// -- ثابت، ممنوع تغييره --
// ==========================================
object BingoColors {
    val Background = Color(0xFF080812)
    val Surface = Color(0xFF10101E)
    val CardBackground = Color(0xFF16162A)
    val Border = Color(0xFF2A2A4A)
    val TextPrimary = Color(0xFFE8E8F8)
    val TextSecondary = Color(0xFF6B7280)

    val GoldPrimary = Color(0xFFF0B429)
    val GoldDark = Color(0xFFE67E22)
    val RedAccent = Color(0xFFFF4757)
    val CrossedCell = Color(0xFF1E1E38)

    // التدرجات (Gradients)
    val GoldGradient = Brush.linearGradient(listOf(GoldPrimary, GoldDark))
    val LogoGradient = Brush.linearGradient(listOf(GoldPrimary, GoldDark, RedAccent))
    val VsPhoneGradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF3D8BFF)))
    val VsFriendsGradient = Brush.linearGradient(listOf(Color(0xFF00C97A), Color(0xFF0099A8)))
    val NetworkGradient = Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF0369A1)))
    val LevelsGradient = Brush.linearGradient(listOf(GoldPrimary, GoldDark))
    val DailyGradient = Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFBE185D)))
    val TournamentGradient = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF6D28D9)))
    val BackgroundGradient = Brush.radialGradient(
        colors = listOf(Color(0x33F0B429), Color(0x333D8BFF), Background),
        radius = 1500f
    )
}

// ==========================================
// الخطوط (Typography)
// ==========================================
object BingoTypography {
    val Logo = TextStyle(fontSize = 80.sp, fontWeight = FontWeight.Black)
    val Title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black)
    val ButtonText = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black)
    val BodyPrimary = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val BodySecondary = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val CellText = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

// ==========================================
// المكونات الأساسية (Core Components)
// ==========================================
@Composable
fun BingoMainButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .shadow(20.dp, spotColor = BingoColors.GoldPrimary.copy(alpha = 0.4f), shape = RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BingoColors.GoldGradient)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, color = BingoColors.TextPrimary, style = BingoTypography.ButtonText)
        }
    }
}

@Composable
fun BingoMenuCard(title: String, subtitle: String, icon: String, gradient: Brush, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, BingoColors.Border, RoundedCornerShape(16.dp))
            .shadow(20.dp, spotColor = BingoColors.GoldPrimary.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BingoColors.CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(gradient, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = BingoColors.TextPrimary, style = BingoTypography.Title.copy(fontSize = 16.sp))
                Text(text = subtitle, color = BingoColors.TextSecondary, style = BingoTypography.BodySecondary)
            }
        }
    }
}

private enum class Screen { Splash, Welcome, Settings }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BingoApp()
            }
        }
    }
}

@Composable
fun BingoApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bingo_prefs", android.content.Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.Splash) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BingoColors.BackgroundGradient)
    ) {
        when (screen) {
            Screen.Splash -> SplashScreen(onDone = { screen = Screen.Welcome })
            Screen.Welcome -> WelcomeScreen(
                prefs = prefs,
                onOpenSettings = { screen = Screen.Settings },
                onLaunchMode = { mode -> launchGameActivity(context, mode) },
                onComingSoon = {
                    Toast.makeText(context, "هاد النمط قريباً إن شاء الله 🙂", Toast.LENGTH_SHORT).show()
                }
            )
            Screen.Settings -> SettingsScreen(
                prefs = prefs,
                onBack = { screen = Screen.Welcome }
            )
        }
    }
}

private fun launchGameActivity(context: android.content.Context, mode: String) {
    val intent = Intent(context, GameActivity::class.java)
    intent.putExtra("start_mode", mode)
    context.startActivity(intent)
}

// ==========================================
// شاشة الافتتاحية (Splash)
// ==========================================
private val OvershootEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val letters = listOf("B", "I", "N", "G", "O")
    val fromX = listOf(-260f, 0f, 0f, 0f, 260f)
    val fromY = listOf(0f, -260f, -260f, 260f, 0f)
    val delays = listOf(50L, 180L, 310L, 440L, 570L)

    val offsetXs = remember { letters.indices.map { Animatable(fromX[it]) } }
    val offsetYs = remember { letters.indices.map { Animatable(fromY[it]) } }
    val alphas = remember { letters.indices.map { Animatable(0f) } }
    var showButton by remember { mutableStateOf(false) }
    val buttonAlpha = remember { Animatable(0f) }
    val buttonScale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        letters.indices.forEach { i ->
            launch {
                delay(delays[i])
                launch { offsetXs[i].animateTo(0f, tween(650, easing = OvershootEasing)) }
                launch { offsetYs[i].animateTo(0f, tween(650, easing = OvershootEasing)) }
                launch { alphas[i].animateTo(1f, tween(650)) }
            }
        }
        delay(1400)
        showButton = true
        launch { buttonAlpha.animateTo(1f, tween(450)) }
        launch { buttonScale.animateTo(1f, tween(450, easing = OvershootEasing)) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row {
            letters.indices.forEach { i ->
                Text(
                    text = letters[i],
                    style = BingoTypography.Logo,
                    color = BingoColors.GoldPrimary,
                    modifier = Modifier
                        .offset(x = offsetXs[i].value.dp, y = offsetYs[i].value.dp)
                        .alpha(alphas[i].value)
                )
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        if (showButton) {
            Box(
                modifier = Modifier
                    .alpha(buttonAlpha.value)
                    .scale(buttonScale.value)
            ) {
                BingoMainButton(text = "اضغط للبدء") { onDone() }
            }
        }
    }
}

// ==========================================
// شاشة القائمة الرئيسية (Welcome)
// ==========================================
@Composable
fun WelcomeScreen(
    prefs: SharedPreferences,
    onOpenSettings: () -> Unit,
    onLaunchMode: (String) -> Unit,
    onComingSoon: () -> Unit
) {
    val name = prefs.getString("player_name", "لاعب") ?: "لاعب"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "⚙ الإعدادات",
                color = BingoColors.GoldPrimary,
                style = BingoTypography.BodyPrimary,
                modifier = Modifier.clickable { onOpenSettings() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BINGO", style = BingoTypography.Logo.copy(fontSize = 44.sp), color = BingoColors.GoldPrimary)
            Text("أهلاً، $name", style = BingoTypography.Title, color = BingoColors.TextPrimary)
        }

        Spacer(modifier = Modifier.height(28.dp))

        BingoMenuCard("🌐 لعب شبكي", "بين جهازين على نفس الشبكة", "🌐", BingoColors.NetworkGradient) {
            onLaunchMode("network")
        }
        BingoMenuCard("🤖 ضد الذكاء الاصطناعي", "تحدَّ الذكاء الاصطناعي", "🤖", BingoColors.VsPhoneGradient) {
            onLaunchMode("ai")
        }
        BingoMenuCard("👥 اللعب مع الأصدقاء", "تمرير الهاتف بينكم", "👥", BingoColors.VsFriendsGradient) {
            onLaunchMode("friends")
        }
        BingoMenuCard("🏆 مستويات", "قريباً", "🏆", BingoColors.LevelsGradient) { onComingSoon() }
        BingoMenuCard("📅 تحدي يومي", "قريباً", "📅", BingoColors.DailyGradient) { onComingSoon() }
        BingoMenuCard("🏅 بطولة", "قريباً", "🏅", BingoColors.TournamentGradient) { onComingSoon() }
    }
}

// ==========================================
// شاشة الإعدادات (Settings)
// ==========================================
private val themePresets = listOf(
    Color(0xFFF0B429), Color(0xFF00C97A), Color(0xFF3D8BFF), Color(0xFF8B5CF6), Color(0xFFFF4757),
    Color(0xFFFB7185), Color(0xFF0EA5E9), Color(0xFFF97316), Color(0xFFEAB308), Color(0xFF94A3B8)
)

@Composable
fun SettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    var name by remember { mutableStateOf(prefs.getString("player_name", "") ?: "") }
    var selectedTheme by remember { mutableStateOf(prefs.getInt("theme_index", 0)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "→ رجوع",
            color = BingoColors.TextSecondary,
            style = BingoTypography.BodyPrimary,
            modifier = Modifier.clickable { onBack() }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("الاسم", color = BingoColors.TextSecondary, style = BingoTypography.BodySecondary)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BingoColors.TextPrimary,
                unfocusedTextColor = BingoColors.TextPrimary,
                focusedBorderColor = BingoColors.GoldPrimary,
                unfocusedBorderColor = BingoColors.Border,
                cursorColor = BingoColors.GoldPrimary,
                focusedContainerColor = BingoColors.Surface,
                unfocusedContainerColor = BingoColors.Surface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("اللون", color = BingoColors.TextSecondary, style = BingoTypography.BodySecondary)
        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            themePresets.forEachIndexed { index, color ->
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(36.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (selectedTheme == index) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable { selectedTheme = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        BingoMainButton(text = "حفظ") {
            prefs.edit()
                .putString("player_name", name.ifBlank { "لاعب" })
                .putInt("theme_index", selectedTheme)
                .apply()
            onBack()
        }
    }
}
