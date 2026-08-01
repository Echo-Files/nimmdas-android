package at.nimmdas.app.ui.screens.coins

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ── Slot Machine ──
@Composable
fun AnimatedSlotMachine(enabled: Boolean, loading: Boolean, onPlay: () -> Unit) {
    var spinning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val symbols = listOf("🍒", "🍋", "🔔", "⭐", "💎", "7️⃣")
    val reel1 = remember { Animatable(0f) }
    val reel2 = remember { Animatable(0f) }
    val reel3 = remember { Animatable(0f) }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🎰 Münz-Jackpot", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Einsatz: 5 Münzen · Bis zu 250 Münzen gewinnen", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            
            // Slots display
            Row(Modifier.fillMaxWidth().height(80.dp).background(Color.Black.copy(0.1f), RoundedCornerShape(12.dp)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf(reel1, reel2, reel3).forEachIndexed { i, reel ->
                    val index = ((reel.value / 100f).toInt() + i) % symbols.size
                    Box(Modifier.size(60.dp).background(Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text(symbols[if(index < 0) index + symbols.size else index], fontSize = 32.sp, modifier = Modifier.graphicsLayer {
                            translationY = (reel.value % 100f) - 50f
                        })
                    }
                }
            }

            Button(
                onClick = {
                    if (spinning) return@Button
                    spinning = true
                    scope.launch {
                        launch { reel1.animateTo(reel1.value + 1000f + Random.nextInt(500), tween(1500, easing = LinearOutSlowInEasing)) }
                        launch { reel2.animateTo(reel2.value + 1200f + Random.nextInt(500), tween(2000, easing = LinearOutSlowInEasing)) }
                        launch { reel3.animateTo(reel3.value + 1500f + Random.nextInt(500), tween(2500, easing = LinearOutSlowInEasing)) }
                        delay(2500)
                        spinning = false
                        onPlay()
                    }
                },
                enabled = enabled && !loading && !spinning,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (spinning || loading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text(if(enabled) "🕹️ Drehen (5 🪙)" else "❌ Nicht verfügbar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Treasure Hunt ──
@Composable
fun AnimatedTreasureHunt(enabled: Boolean, loading: Boolean, onPlay: () -> Unit) {
    var selectedChest by remember { mutableStateOf<Int?>(null) }
    val chests = listOf(1, 2, 3)

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🎁 Schatzsuche", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("1x täglich kostenlos! Wähle eine Truhe.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                chests.forEach { idx ->
                    val scale by animateFloatAsState(if (selectedChest == idx) 1.2f else 1f, label = "scale")
                    Box(Modifier.size(80.dp).graphicsLayer(scaleX = scale, scaleY = scale).clickable(enabled = enabled && !loading && selectedChest == null) {
                        selectedChest = idx
                        onPlay()
                    }, contentAlignment = Alignment.Center) {
                        Text(if (selectedChest == idx) "✨" else "📦", fontSize = 48.sp)
                    }
                }
            }
            if (selectedChest != null) {
                Button(onClick = { selectedChest = null }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Neu laden", fontWeight = FontWeight.Bold)
                }
            } else if (!enabled) {
                Text("❌ Heute schon gespielt", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Memory Match ──
@Composable
fun AnimatedMemoryMatch(enabled: Boolean, loading: Boolean, onPlay: () -> Unit) {
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var flipped1 by remember { mutableStateOf(false) }
    var flipped2 by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🧠 Memory Match", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Finde das Pärchen (1x täglich)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MemoryCard(flipped1) { if(enabled && !playing) { flipped1 = true; if(flipped2) { playing=true; scope.launch { delay(500); onPlay() } } } }
                MemoryCard(flipped2) { if(enabled && !playing) { flipped2 = true; if(flipped1) { playing=true; scope.launch { delay(500); onPlay() } } } }
            }
            
            if (!enabled) Text("❌ Heute schon gespielt", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MemoryCard(flipped: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(500), label = "")
    Box(Modifier.size(80.dp).graphicsLayer(rotationY = rotation, cameraDistance = 12f).clickable { onClick() }
        .background(if(rotation > 90f) Color.White else MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        if (rotation > 90f) Text("🃏", fontSize = 32.sp, modifier = Modifier.graphicsLayer(rotationY = 180f))
        else Text("?", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Coin Toss ──
@Composable
fun AnimatedCoinToss(enabled: Boolean, loading: Boolean, onPlay: (String) -> Unit) {
    var spinning by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🪙 Münzwurf", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Einsatz: 10 Münzen → Gewinn: 20", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            
            Box(Modifier.size(100.dp).graphicsLayer(rotationY = rotation.value, cameraDistance = 15f)
                .background(Color(0xFFFFC107), CircleShape), contentAlignment = Alignment.Center) {
                Text(if (rotation.value % 360 > 90 && rotation.value % 360 < 270) "Zahl" else "Kopf", 
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if(!spinning) {
                        spinning = true
                        scope.launch {
                            rotation.animateTo(rotation.value + 1800f, tween(2000, easing = FastOutSlowInEasing))
                            spinning = false
                            onPlay("heads")
                        }
                    }
                }, enabled = enabled && !loading && !spinning, modifier = Modifier.weight(1f)) { Text("Kopf") }
                
                Button(onClick = {
                    if(!spinning) {
                        spinning = true
                        scope.launch {
                            rotation.animateTo(rotation.value + 1980f, tween(2000, easing = FastOutSlowInEasing))
                            spinning = false
                            onPlay("tails")
                        }
                    }
                }, enabled = enabled && !loading && !spinning, modifier = Modifier.weight(1f)) { Text("Zahl") }
            }
        }
    }
}

// ── Lucky Wheel ──
@Composable
fun AnimatedLuckyWheel(enabled: Boolean, loading: Boolean, onPlay: () -> Unit) {
    var spinning by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🎡 Glücksrad", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("1x täglich drehen!", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            
            Box(Modifier.size(120.dp).graphicsLayer(rotationZ = rotation.value)
                .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Red)), CircleShape), 
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(100.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text("🎡", fontSize = 48.sp)
                }
            }

            Button(onClick = {
                if(!spinning) {
                    spinning = true
                    scope.launch {
                        rotation.animateTo(rotation.value + 3600f + Random.nextInt(360), tween(3000, easing = FastOutSlowInEasing))
                        spinning = false
                        onPlay()
                    }
                }
            }, enabled = enabled && !loading && !spinning, modifier = Modifier.fillMaxWidth()) { Text("Drehen") }
        }
    }
}

// ── Scratch Card ──
@Composable
fun AnimatedScratchCard(enabled: Boolean, loading: Boolean, onPlay: () -> Unit) {
    var scratched by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🎫 Rubbellos", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Rubbeln und gewinnen!", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            
            Box(Modifier.fillMaxWidth().height(120.dp)
                .background(if (scratched) Color(0x334CAF50) else Color.LightGray, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled && !loading && !scratched) { 
                    scratched = true
                    onPlay()
                }, contentAlignment = Alignment.Center) {
                if (scratched) Text("🎉 Gewonnen!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                else Text("Klicken zum Rubbeln", color = Color.DarkGray, fontWeight = FontWeight.Bold)
            }
            if (scratched) {
                Button(onClick = { scratched = false }, modifier = Modifier.fillMaxWidth()) { Text("Neues Los") }
            } else if (!enabled) {
                Text("❌ Keine Lose mehr", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}
