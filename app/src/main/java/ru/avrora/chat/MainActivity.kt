package ru.avrora.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// Палитра «флюорит»: глубокий индиго и холодная бирюза
private val Ink = Color(0xFF151B36)
private val Deep = Color(0xFF1E2750)
private val AuroraBubble = Color(0xFF28346A)
private val UserBubble = Color(0xFF2B6E80)
private val Glow = Color(0xFF93D8EA)
private val TextMain = Color(0xFFE7ECFF)
private val TextDim = Color(0xFF9BA6D0)

private val AuroraColors = darkColorScheme(
    primary = Glow,
    onPrimary = Ink,
    background = Ink,
    onBackground = TextMain,
    surface = Deep,
    onSurface = TextMain,
    onSurfaceVariant = TextDim,
    primaryContainer = UserBubble,
    onPrimaryContainer = TextMain,
    error = Color(0xFFFF9A9A)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AuroraColors) { ChatScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(vm.token.isBlank()) }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.messages.size, vm.sending) {
        val count = vm.messages.size + if (vm.sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Аврора", fontSize = 22.sp) },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Text("⚙", fontSize = 22.sp, color = Glow)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Deep)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.messages.isEmpty() && !vm.sending) {
                    item {
                        Text(
                            "Здесь пока тихо. Напиши Авроре первым.",
                            color = TextDim,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    }
                }
                items(vm.messages) { Bubble(it) }
                if (vm.sending) item { TypingBubble() }
            }

            vm.error?.let { err ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::retry) { Text("Повторить") }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(Deep).padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Написать Авроре…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.send(input); input = "" },
                    enabled = input.isNotBlank() && !vm.sending,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(56.dp)
                ) { Text("➤", fontSize = 18.sp) }
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
fun Bubble(m: ChatMessage) {
    val mine = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (mine) UserBubble else AuroraBubble,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            SelectionContainer {
                Text(
                    m.content,
                    color = TextMain,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun TypingBubble() {
    Surface(color = AuroraBubble, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)) {
        Text(
            "Аврора печатает…",
            color = TextDim,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun SettingsDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(vm.serverUrl) }
    var token by remember { mutableStateOf(vm.token) }
    var confirmClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Deep,
        title = { Text("Подключение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Адрес сервера") }, singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("Токен приложения") }, singleLine = true)
                TextButton(onClick = {
                    if (confirmClear) { vm.clearChat(); confirmClear = false } else confirmClear = true
                }) {
                    Text(
                        if (confirmClear) "Точно стереть всю переписку?" else "Стереть переписку",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(url, token); onDismiss() }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
