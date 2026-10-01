package ru.avrora.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
fun Avatar(size: Dp) {
    AsyncImage(
        model = "file:///android_asset/avatar.webp",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Glow.copy(alpha = 0.55f), CircleShape)
    )
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(96.dp)
        Spacer(Modifier.height(16.dp))
        Text("Здесь пока тихо", color = TextMain, fontSize = 20.sp, fontFamily = FontFamily.Serif)
        Spacer(Modifier.height(4.dp))
        Text("Напиши Авроре первым", color = TextDim, fontSize = 14.sp)
    }
}

@Composable
fun TypingRow() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.Bottom) {
        Avatar(32.dp)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = BubbleAurora,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            border = BorderStroke(1.dp, Glow.copy(alpha = 0.18f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(3) { i ->
                    val a by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = i * 160),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$i"
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Glow.copy(alpha = a))
                    )
                }
            }
        }
    }
}

@Composable
fun MessageRow(
    m: ChatMessage,
    showAvatar: Boolean,
    stickers: List<CatalogItem>,
    photos: List<CatalogItem>,
    sentDir: File,
    revealing: Boolean,
    onOpen: (Any) -> Unit
) {
    val mine = m.role == "user"
    val parts = remember(m.content) { parseParts(m.content) }
    // Её паузы: части идут по очереди, только пока сообщение свежее. Где пауза и на сколько, решила она
    val steps = remember(parts) { splitSteps(parts) }
    var visible by remember(m.time) { mutableIntStateOf(if (revealing) 1 else steps.size) }
    LaunchedEffect(m.time, revealing) {
        if (revealing) {
            while (visible < steps.size) {
                delay(steps[visible].first)
                visible += 1
            }
        } else {
            visible = steps.size
        }
    }
    val shown = steps.take(visible).flatMap { it.second }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!mine) {
            if (showAvatar) Avatar(32.dp) else Spacer(Modifier.width(32.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            shown.forEach { p ->
                when (p) {
                    is Part.Text -> TextBubble(p.s, mine)
                    is Part.Pause -> {}
                    is Part.Silence -> Text(
                        "Аврора прочитала и промолчала",
                        color = TextDim,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                    is Part.Sticker -> {
                        val item = stickers.firstOrNull { s -> s.id == p.id }
                        if (item != null) {
                            AsyncImage(
                                model = item.model,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .height(150.dp)
                                    .aspectRatio(item.aspect.coerceIn(0.3f, 3f))
                            )
                        }
                    }
                    is Part.Photo -> {
                        val item = photos.firstOrNull { s -> s.id == p.id }
                        if (item != null) PhotoImage(item.model, item.aspect, onOpen)
                    }
                    is Part.Snap -> {
                        val file = File(sentDir, p.file)
                        val aspect = remember(file) { imageAspect(file) }
                        PhotoImage(file, aspect, onOpen)
                    }
                }
            }
            if (parts.isNotEmpty()) {
                Text(
                    timeFormat.format(Date(m.time)),
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/** Делит части сообщения по её паузам: (задержка перед группой в мс, части группы). */
private fun splitSteps(parts: List<Part>): List<Pair<Long, List<Part>>> {
    val steps = ArrayList<Pair<Long, List<Part>>>()
    var cur = ArrayList<Part>()
    var delayMs = 0L
    for (p in parts) {
        if (p is Part.Pause) {
            if (cur.isNotEmpty()) {
                steps.add(Pair(delayMs, cur))
                cur = ArrayList()
            }
            delayMs = p.seconds * 1000L
        } else {
            cur.add(p)
        }
    }
    if (cur.isNotEmpty()) steps.add(Pair(delayMs, cur))
    return steps
}

@Composable
private fun PhotoImage(model: Any, aspect: Float, onOpen: (Any) -> Unit) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(240.dp)
            .aspectRatio(aspect.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen(model) }
    )
}

@Composable
private fun TextBubble(text: String, mine: Boolean) {
    Surface(
        color = if (mine) BubbleUser else BubbleAurora,
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (mine) 18.dp else 4.dp,
            bottomEnd = if (mine) 4.dp else 18.dp
        ),
        border = if (mine) null else BorderStroke(1.dp, Glow.copy(alpha = 0.18f)),
        modifier = Modifier.widthIn(max = 290.dp)
    ) {
        SelectionContainer {
            Text(
                text,
                color = TextMain,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
