package ru.avrora.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

/** Стикер или фото из альбома, прикреплённые к черновику. Уходят вместе с текстом по кнопке отправки. */
private data class Pending(val tag: String, val model: Any, val isSticker: Boolean)

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var input by rememberSaveable { mutableStateOf("") }
    var screen by remember { mutableStateOf(if (vm.token.isBlank()) Screen.Connection else Screen.Chat) }
    var showPanel by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Any?>(null) }
    var pendingSnap by remember { mutableStateOf<Uri?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var addingSticker by remember { mutableStateOf(true) }
    var addUri by remember { mutableStateOf<Uri?>(null) }
    var toDelete by remember { mutableStateOf<CatalogItem?>(null) }

    val pickSnap = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingSnap = uri
            pending = null
        }
    }
    val pickForLibrary = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addUri = uri
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    LaunchedEffect(vm.messages.size, vm.sending) {
        val count = vm.messages.size + if (vm.sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(vm.notice) {
        val n = vm.notice
        if (n != null) {
            Toast.makeText(ctx, n, Toast.LENGTH_SHORT).show()
            vm.clearNotice()
        }
    }

    BackHandler(enabled = showPanel) { showPanel = false }
    BackHandler(enabled = screen != Screen.Chat) {
        screen = if (screen == Screen.Menu) Screen.Chat else Screen.Menu
    }

    val canSend = !vm.sending && (input.isNotBlank() || pendingSnap != null || pending != null)
    fun submit() {
        val snap = pendingSnap
        val att = pending
        when {
            snap != null -> vm.sendSnap(snap, input)
            att != null -> vm.sendWithTag(input, att.tag)
            else -> vm.send(input)
        }
        input = ""
        pendingSnap = null
        pending = null
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        AsyncImage(
            model = "file:///android_asset/bg.webp",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.fillMaxSize()) {
            Header(sending = vm.sending, onSettings = { screen = Screen.Menu })

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (vm.messages.isEmpty() && !vm.sending) item { EmptyState() }
                itemsIndexed(vm.messages) { i, m ->
                    val showAvatar = m.role != "user" && (i == 0 || vm.messages[i - 1].role == "user")
                    MessageRow(
                        m = m,
                        showAvatar = showAvatar,
                        stickers = vm.stickers,
                        photos = vm.photos,
                        sentDir = vm.library.sentDir,
                        onOpen = { viewer = it }
                    )
                }
                if (vm.sending) item { TypingRow() }
            }

            vm.error?.let { err ->
                Row(
                    Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::retry) { Text("Повторить") }
                }
            }

            val chipModel: Any? = pendingSnap ?: pending?.model
            if (chipModel != null) {
                val isSticker = pending?.isSticker == true
                Row(
                    Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = chipModel,
                        contentDescription = null,
                        contentScale = if (isSticker) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isSticker) "Стикер прикреплён. Напиши сообщение или сразу отправь."
                        else "Фото прикреплено. Можно добавить подпись.",
                        color = TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { pendingSnap = null; pending = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Убрать вложение", tint = TextDim)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { pickSnap.launch(imageOnly) }) {
                    Icon(Icons.Default.Add, contentDescription = "Отправить фото", tint = TextDim)
                }
                IconButton(onClick = {
                    showPanel = !showPanel
                    if (showPanel) keyboard?.hide()
                }) {
                    Icon(
                        Icons.Default.Face,
                        contentDescription = "Стикеры и альбом",
                        tint = if (showPanel) Glow else TextDim
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) showPanel = false },
                    placeholder = { Text("Написать Авроре…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    colors = auroraFieldColors()
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Glow else Edge)
                        .clickable(enabled = canSend) { submit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (canSend) Night else TextDim,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showPanel) {
                LibraryPanel(
                    stickers = vm.stickers,
                    photos = vm.photos,
                    onSticker = {
                        pending = Pending("[стикер:${it.id}]", it.model, true)
                        pendingSnap = null
                        showPanel = false
                    },
                    onPhoto = {
                        pending = Pending("[фото:${it.id}]", it.model, false)
                        pendingSnap = null
                        showPanel = false
                    },
                    onAddSticker = {
                        addingSticker = true
                        pickForLibrary.launch(imageOnly)
                    },
                    onAddPhoto = {
                        addingSticker = false
                        pickForLibrary.launch(imageOnly)
                    },
                    onDelete = { toDelete = it }
                )
            }
        }

        if (screen != Screen.Chat) {
            SettingsHost(vm = vm, screen = screen, onNavigate = { screen = it })
        }
    }

    val newUri = addUri
    if (newUri != null) {
        AddItemDialog(
            isSticker = addingSticker,
            uri = newUri,
            onConfirm = { desc, cut ->
                if (addingSticker) vm.addSticker(newUri, desc, cut) else vm.addPhoto(newUri, desc)
                addUri = null
            },
            onDismiss = { addUri = null }
        )
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Panel,
            title = { Text("Удалить?") },
            text = { Text(item.desc) },
            confirmButton = {
                TextButton(onClick = { vm.removeItem(item); toDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }

    viewer?.let { model ->
        Dialog(
            onDismissRequest = { viewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xF2000000))
                    .clickable { viewer = null }
            ) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun Header(sending: Boolean, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel.copy(alpha = 0.94f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Аврора", color = TextMain, fontSize = 21.sp, fontFamily = FontFamily.Serif)
            Text(if (sending) "печатает…" else "fluorite blue", color = TextDim, fontSize = 12.sp)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = TextDim)
        }
    }
}
