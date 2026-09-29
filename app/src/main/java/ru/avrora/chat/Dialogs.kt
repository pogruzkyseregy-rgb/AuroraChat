package ru.avrora.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun SettingsDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(vm.serverUrl) }
    var token by remember { mutableStateOf(vm.token) }
    var confirmClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Подключение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Адрес сервера") }, singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("Токен приложения") }, singleLine = true)
                TextButton(onClick = {
                    if (confirmClear) {
                        vm.clearChat()
                        confirmClear = false
                    } else {
                        confirmClear = true
                    }
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

/** Добавление своего стикера или фото в общую библиотеку. Описание нужно, чтобы Аврора понимала, когда это отправлять. */
@Composable
fun AddItemDialog(
    isSticker: Boolean,
    uri: Uri,
    onConfirm: (desc: String, cutBlack: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var cut by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (isSticker) "Новый стикер" else "Новое фото в альбом") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Когда это отправлять") },
                    placeholder = { Text("Например: радуется, смеётся") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isSticker) {
                    Row(
                        modifier = Modifier.clickable { cut = !cut },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = cut, onCheckedChange = { cut = it })
                        Text("Убрать чёрный фон")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(desc.trim(), cut) }, enabled = desc.isNotBlank()) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
