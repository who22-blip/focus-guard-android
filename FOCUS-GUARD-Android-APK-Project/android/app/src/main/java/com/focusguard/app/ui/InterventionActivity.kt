package com.focusguard.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.MainActivity
import com.focusguard.app.data.FocusGuardPrefs
import kotlinx.coroutines.delay

class InterventionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reason = intent.getStringExtra("EXTRA_TRIGGER_REASON") ?: "Protection Shield Triggered"

        setContent {
            InterventionScreen(
                reason = reason,
                lifeGoal = FocusGuardPrefs.getLifeGoal(this),
                careerGoal = FocusGuardPrefs.getCareerGoal(this),
                familyExpectations = FocusGuardPrefs.getFamilyExpectations(this),
                motivationalMessage = FocusGuardPrefs.getMotivationalMessage(this),
                onGoBackToSafety = {
                    FocusGuardPrefs.incrementUrgesManaged(this)
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                },
                onOpenHeartfulness = {
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        putExtra("OPEN_TAB", "heartfulness")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(mainIntent)
                    finish()
                }
            )
        }
    }
}

@Composable
fun InterventionScreen(
    reason: String,
    lifeGoal: String,
    careerGoal: String,
    familyExpectations: String,
    motivationalMessage: String,
    onGoBackToSafety: () -> Unit,
    onOpenHeartfulness: () -> Unit
) {
    var stage by remember { mutableIntStateOf(1) }
    var reflectionSeconds by remember { mutableIntStateOf(60) }
    var focusSeconds by remember { mutableIntStateOf(600) }
    var focusRunning by remember { mutableStateOf(false) }

    LaunchedEffect(stage) {
        if (stage == 2) {
            reflectionSeconds = 60
            while (reflectionSeconds > 0) {
                delay(1000L)
                reflectionSeconds--
            }
        }
    }

    LaunchedEffect(focusRunning) {
        while (focusRunning && focusSeconds > 0) {
            delay(1000L)
            focusSeconds--
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090D16)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FOCUS GUARD INTERVENTION · STAGE $stage OF 3",
                color = Color(0xFFFBBF24),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = reason, color = Color(0xFF94A3B8), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(24.dp))

            if (stage == 1) {
                Text(
                    text = "STOP. Think before you make this decision.",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You installed FOCUS GUARD because you wanted to change your life.\n\nIs a few minutes of temporary pleasure worth sacrificing the goals you promised yourself?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onGoBackToSafety,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("GO BACK TO SAFETY", fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { stage = 2 },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("I STILL WANT TO CONTINUE", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }

            if (stage == 2) {
                Text(
                    text = "Remember your future.",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Life Goal: $lifeGoal", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    Text("Career Goal: $careerGoal", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    Text("Family & Sacrifices: $familyExpectations", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                    Text("\"$motivationalMessage\"", color = Color(0xFF67E8F9), fontSize = 13.sp, fontStyle = FontStyle.Italic)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E2922), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text("HEARTFULNESS ALTERNATIVE", color = Color(0xFF6EE7B7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Pause for a moment. You don't need to react to every thought or impulse. Give yourself a little time to become calm and reconnect with your inner self.",
                        color = Color(0xFFD1FAE5),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onOpenHeartfulness,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Take a Moment for Heartfulness", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (reflectionSeconds > 0) "Mandatory Reflection: ${reflectionSeconds}s" else "Reflection Complete",
                    color = Color(0xFF22D3EE),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onGoBackToSafety,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("RETURN TO SAFETY", fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    enabled = reflectionSeconds == 0,
                    onClick = { stage = 3 },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (reflectionSeconds > 0) "Wait ${reflectionSeconds}s to Continue" else "Open 10-Min Focus Screen",
                        fontSize = 12.sp
                    )
                }
            }

            if (stage == 3) {
                Text(
                    text = "10-Minute Focus & Discipline Reset",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                val mins = focusSeconds / 60
                val secs = focusSeconds % 60
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    color = Color(0xFF22D3EE),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { focusRunning = !focusRunning },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (focusRunning) "Pause 10-Min Focus" else "Start 10-Min Focus Session", color = Color.White)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenHeartfulness,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Open Heartfulness Sanctuary", color = Color.White)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onGoBackToSafety,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("I Choose Self-Mastery (Return Safely)", color = Color.White)
                }
            }
        }
    }
}
