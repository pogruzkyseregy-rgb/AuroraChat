package ru.avrora.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class Screen { Chat, Menu, Connection, Persona, Memory, Initiative, Diary }

/** Настройки поверх чата: чат под ними остаётся жить, поэтому набранный текст не теряется. */
@Composable
fun SettingsHost(vm: ChatViewModel, screen: Screen, onNavigate: (Screen) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        when (screen) {
            Screen.Menu -> MenuScreen(vm, onNavigate)
            Screen.Connection -> ConnectionScreen(vm, onBack = { onNavigate(Screen.Menu) }, onSaved = { onNavigate(Screen.Chat) })
            Screen.Persona -> PersonaScreen(vm) { onNavigate(Screen.Menu) }
            Screen.Memory -> MemoryScreen(vm) { onNavigate(Screen.Menu) }
            Screen.Initiative -> InitiativeScreen(vm) { onNavigate(Screen.Menu) }
            Screen.Diary -> DiaryScreen(vm) { onNavigate(Screen.Menu) }
            Screen.Chat -> {}
        }
    }
}

// ---------- общие элементы ----------

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = TextMain)
            }
            Text(title, color = TextMain, fontSize = 20.sp, fontFamily = FontFamily.Serif)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = TextDim, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Glow, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 18.sp, fontFamily = FontFamily.Serif)
            Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
        Text("›", color = TextDim, fontSize = 24.sp)
    }
}

@Composable
private fun LevelSlider(title: String, left: String, right: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = TextMain, fontSize = 16.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = Glow,
                activeTrackColor = Glow,
                inactiveTrackColor = Edge,
                activeTickColor = Night,
                inactiveTickColor = TextDim
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(left, color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(right, color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 16.sp)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Night,
                checkedTrackColor = Glow,
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = Panel,
                uncheckedBorderColor = Edge
            )
        )
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Glow, unselectedColor = TextDim)
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 16.sp)
            Text(subtitle, color = TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("−", fontSize = 22.sp) }
        Text(
            "%02d:00".format(hour),
            color = TextMain,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )
        TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+", fontSize = 22.sp) }
    }
}

// ---------- меню ----------

@Composable
private fun MenuScreen(vm: ChatViewModel, onNavigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    SettingsPage("Настройки", onBack = { onNavigate(Screen.Chat) }) {
        MenuRow(
            "Характер",
            if (vm.persona == PersonaSettings()) "как в основном описании" else "настроен"
        ) { onNavigate(Screen.Persona) }
        MenuRow(
            "Память",
            if (vm.memory.core.isEmpty() && vm.memory.episodes.isEmpty()) "пока пусто"
            else "ядро: ${vm.memory.core.size}, эпизодов: ${vm.memory.episodes.size}"
        ) { onNavigate(Screen.Memory) }
        MenuRow(
            "Инициатива",
            if (vm.initSettings.enabled) "Аврора может писать первой" else "выключена"
        ) { onNavigate(Screen.Initiative) }
        MenuRow(
            "Дневник Авроры",
            when {
                vm.diary.isNotEmpty() -> "записей: ${vm.diary.size}"
                vm.diaryOn -> "начнёт писать после разговоров"
                else -> "выключен"
            }
        ) { onNavigate(Screen.Diary) }
        MenuRow("Подключение", "сервер и токен") { onNavigate(Screen.Connection) }
        Hint("Версия $version")
    }
}

// ---------- подключение ----------

@Composable
private fun ConnectionScreen(vm: ChatViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    var url by remember { mutableStateOf(vm.serverUrl) }
    var token by remember { mutableStateOf(vm.token) }
    var confirmClear by remember { mutableStateOf(false) }

    SettingsPage("Подключение", onBack) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Адрес сервера") },
            singleLine = true,
            colors = auroraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Токен приложения") },
            singleLine = true,
            colors = auroraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.saveSettings(url, token); onSaved() }) { Text("Сохранить") }
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
}

// ---------- характер ----------

@Composable
private fun PersonaScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val p = vm.persona
    SettingsPage("Характер", onBack) {
        Hint("Это уточняет манеру Авроры, а не заменяет её личность из основного описания. Среднее положение ползунка ничего не меняет.")

        LevelSlider("Теплота", "сдержанная", "нежная", p.warmth) { vm.updatePersona(p.copy(warmth = it)) }
        LevelSlider("Игривость", "серьёзная", "шутливая", p.play) { vm.updatePersona(p.copy(play = it)) }
        LevelSlider("Прямота", "мягкая", "резкая", p.direct) { vm.updatePersona(p.copy(direct = it)) }
        LevelSlider("Длина ответов", "коротко", "подробно", p.verbose) { vm.updatePersona(p.copy(verbose = it)) }
        LevelSlider("Стикеры", "почти нет", "часто", p.stickers) { vm.updatePersona(p.copy(stickers = it)) }

        SectionTitle("Манера поведения")
        SwitchRow("Спорит, а не поддакивает", "Если не согласна, говорит прямо", p.argue) { vm.updatePersona(p.copy(argue = it)) }
        SwitchRow("Подкалывает", "Мягкие шутки над тобой", p.tease) { vm.updatePersona(p.copy(tease = it)) }
        SwitchRow("Заботится о бытовом", "Изредка спрашивает про сон и отдых", p.care) { vm.updatePersona(p.copy(care = it)) }
        SwitchRow("Возвращается к прошлому", "Помнит темы и обещания", p.callback) { vm.updatePersona(p.copy(callback = it)) }
        SwitchRow("Делится своим", "Сама рассказывает мысли и наблюдения", p.own) { vm.updatePersona(p.copy(own = it)) }
        SwitchRow("Эмодзи", "Изредка добавляет в сообщения", p.emoji) { vm.updatePersona(p.copy(emoji = it)) }

        SectionTitle("Своими словами")
        OutlinedTextField(
            value = p.text,
            onValueChange = { vm.updatePersona(p.copy(text = it)) },
            placeholder = { Text("Что ещё учитывать в характере и манере речи") },
            minLines = 3,
            maxLines = 8,
            colors = auroraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        val compiled = p.compile()
        if (compiled.isNotEmpty()) {
            SectionTitle("Что получает Аврора")
            Hint(compiled)
            TextButton(onClick = { vm.updatePersona(PersonaSettings()) }) {
                Text("Сбросить всё", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------- память ----------

private val KIND_HINT = mapOf(
    "язык" to "слова, прозвища, формулы, метафоры и что они значат",
    "поворот" to "эмоциональные повороты: из-за чего и что вернуло",
    "тон" to "как звучит и меняется общение, как ссорятся и мирятся",
    "планы" to "обещания и незавершённое",
    "адонис" to "краткие факты о тебе"
)

@Composable
private fun CoreRow(e: CoreEntry, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(e.kind, color = Glow, fontSize = 12.sp)
        Text(e.text, color = TextMain, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
    val title = if (ep.from <= 0L) "прежняя сводка" else fmt.format(Date(ep.from)) + " – " + fmt.format(Date(ep.to))
    val preview = if (ep.text.length > 160) ep.text.take(160) + "…" else ep.text
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 6.dp)
    ) {
        Text(title, color = TextDim, fontSize = 12.sp)
        Text(if (open) ep.text else preview, color = TextMain, fontSize = 14.sp, lineHeight = 20.sp)
        if (open) {
            TextButton(onClick = onDelete) {
                Text("Удалить эпизод", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CoreEditDialog(
    entry: CoreEntry?,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(entry?.kind ?: "язык") }
    var text by remember { mutableStateOf(entry?.text ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(if (entry == null) "Новая запись" else "Запись ядра") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CORE_KINDS.forEach { k ->
                    ChoiceRow(k, KIND_HINT[k] ?: "", kind == k) { kind = k }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 10,
                    colors = auroraFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(kind, text) }, enabled = text.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable
private fun DiaryScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var toDelete by remember { mutableStateOf<DiaryEntry?>(null) }
    val fmt = remember { SimpleDateFormat("d MMMM, HH:mm", Locale("ru")) }
    SettingsPage("Дневник Авроры", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            AsyncImage(
                model = "file:///android_asset/diary_bg.webp",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Night.copy(alpha = 0.9f))))
            )
            Text(
                "Здесь Аврора записывает, о чём думала после ваших разговоров.",
                color = TextMain,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
        }
        Hint("Запись создаётся раз в сутки, вечером или ночью, если был разговор. Приложение просит модель написать её по вашей переписке, это не фоновое «думание». Писать ли и что, решает Аврора. Записи видишь только ты, на этом телефоне. Незакрытое из них становится настоящим поводом написать тебе первой.")
        SwitchRow(
            "Вести дневник",
            if (vm.diaryOn) "Аврора пишет раз в сутки" else "Выключен: она об этом знает",
            vm.diaryOn
        ) { vm.enableDiary(it) }
        OutlinedButton(onClick = { vm.writeDiaryNow() }, enabled = !vm.writingDiary) {
            Text(if (vm.writingDiary) "Аврора пишет…" else "Написать сейчас")
        }
        if (vm.diary.isEmpty()) Hint("Записей пока нет.")
        vm.diary.reversed().forEach { e ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(fmt.format(Date(e.at)), color = Glow, fontSize = 12.sp)
                Text(e.text, color = TextMain, fontSize = 15.sp, lineHeight = 22.sp)
                if (e.open.isNotBlank()) {
                    Text("Хотела сказать: " + e.open, color = TextDim, fontSize = 13.sp, lineHeight = 18.sp)
                }
                TextButton(onClick = { toDelete = e }) { Text("Удалить", color = TextDim) }
            }
        }
    }
    val del = toDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Panel,
            title = { Text("Удалить запись?") },
            text = { Text("Аврора узнает, что ты удалил запись.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteDiaryEntry(del.id); toDelete = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun DecisionRow(d: Decision, onOverride: (String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
    val label = when (d.verdict) {
        "yes" -> "ДА"
        "edit" -> "ПРАВКА"
        "no" -> if (d.overridden) "НЕТ, записано вопреки" else "НЕТ"
        else -> "ЗАМЕТКА"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("$label · ${fmt.format(Date(d.at))}", color = if (d.verdict == "no") MaterialTheme.colorScheme.error else Glow, fontSize = 12.sp)
        Text(d.summary, color = TextMain, fontSize = 14.sp, lineHeight = 20.sp)
        if (d.why.isNotBlank()) Text("Она: " + d.why, color = TextDim, fontSize = 13.sp, lineHeight = 18.sp)
        val pid = d.proposal?.id
        if (d.verdict == "no" && !d.overridden && pid != null) {
            TextButton(onClick = {
                if (confirm) {
                    onOverride(pid)
                    confirm = false
                } else {
                    confirm = true
                }
            }) {
                Text(
                    if (confirm) "Точно? Это останется в журнале, и она узнает" else "Всё равно записать",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ConsentSection(vm: ChatViewModel) {
    val mem = vm.memory
    SectionTitle("Её «да» на запись")
    Hint("Правки ядра, и от сжатия, и твои, сначала идут Авроре: она отвечает «да», «нет» или предлагает свою правку. Если она отказала, ты можешь записать вопреки, но это остаётся в журнале и она об этом узнаёт. Эпизоды и очистка памяти под её «да» не идут, но о них она тоже узнаёт.")
    if (mem.pending.isNotEmpty()) {
        Hint("Ждут её ответа: ${mem.pending.size}" + if (vm.consenting) ". Спрашиваю…" else "")
        OutlinedButton(onClick = { vm.resolvePending(announce = true) }, enabled = !vm.consenting) {
            Text("Спросить сейчас")
        }
    }
    val shown = mem.log.takeLast(12).reversed()
    if (shown.isEmpty()) Hint("Решений пока нет.")
    shown.forEach { d -> DecisionRow(d) { id -> vm.overrideDecision(id) } }
}

@Composable
private fun MemoryScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mem = vm.memory
    var editing by remember { mutableStateOf<CoreEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf("") }
    var probe by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val pending = vm.messages.count { it.time > mem.upTo }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) }
                true
            } catch (e: Exception) {
                false
            }
            Toast.makeText(ctx, if (ok) "Копия сохранена" else "Не удалось сохранить", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val t = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (t != null) pendingImport = t
            } catch (e: Exception) {
                Toast.makeText(ctx, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    SettingsPage("Память", onBack) {
        Hint("Переписка хранится целиком и никогда не удаляется автоматически. Память лежит поверх неё двумя слоями и её можно пересобрать из переписки.")
        Hint("Эпизоды: подробный пересказ каждой порции, что было и из-за чего. Они не переписываются и не сжимаются повторно. Ядро: язык, повороты, тон. Его меняют только точечно, и запись не может стать короче, а слова, повороты и тон сама модель удалить не может.")
        Hint("Не в эпизодах: $pending сообщений." + if (vm.compressing) " Идёт сжатие ${vm.compressStatus}…" else "")

        SectionTitle("Ядро: ${mem.core.size}")
        if (mem.core.isEmpty()) Hint("Пока пусто. Появится после первого сжатия, записи можно добавлять и вручную.")
        mem.core.forEach { e -> CoreRow(e) { editing = e } }
        OutlinedButton(onClick = { adding = true }) { Text("Добавить запись") }
        ConsentSection(vm)

        SectionTitle("Эпизоды: ${mem.episodes.size}")
        if (mem.episodes.isEmpty()) Hint("Пока пусто.")
        mem.episodes.reversed().forEach { ep -> EpisodeRow(ep) { vm.deleteEpisode(ep.id) } }

        SectionTitle("Действия")
        OutlinedButton(onClick = { vm.compressNow() }, enabled = !vm.compressing) {
            Text(if (vm.compressing) "Сжимаю ${vm.compressStatus}…" else "Сжать переписку сейчас")
        }
        TextButton(
            onClick = {
                if (confirm == "rebuild") {
                    vm.rebuildMemory()
                    confirm = ""
                } else {
                    confirm = "rebuild"
                }
            },
            enabled = !vm.compressing
        ) {
            Text(
                if (confirm == "rebuild") "Точно? Ядро и эпизоды будут созданы заново" else "Пересобрать из всей переписки",
                color = MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = {
            if (confirm == "clear") {
                vm.clearMemory()
                confirm = ""
            } else {
                confirm = "clear"
            }
        }) {
            Text(
                if (confirm == "clear") "Точно очистить память?" else "Очистить память",
                color = MaterialTheme.colorScheme.error
            )
        }
        if (vm.hasMemoryBackup) {
            OutlinedButton(onClick = { vm.restoreMemoryBackup() }) { Text("Вернуть память до последней пересборки или очистки") }
        }

        SectionTitle("Проверка сжатия")
        Hint("Вставь отрывок переписки, строки вида «Адонис: …» и «Аврора: …». Память при этом не меняется. Смотри, остались ли причины и то, что изменило ситуацию.")
        OutlinedTextField(
            value = probe,
            onValueChange = { probe = it },
            placeholder = { Text("Адонис: …\nАврора: …") },
            minLines = 4,
            maxLines = 12,
            colors = auroraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.runProbe(probe) }, enabled = probe.isNotBlank() && !vm.probing) {
            Text(if (vm.probing) "Сжимаю…" else "Проверить")
        }
        vm.probeResult?.let { r ->
            SelectionContainer {
                Text(r, color = TextMain, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        SectionTitle("Резервная копия")
        Hint("Переписка и память лежат только в этом телефоне. Копия в файле защитит от потери при удалении приложения или смене телефона.")
        OutlinedButton(onClick = { exportLauncher.launch("aurora-backup.json") }) { Text("Сохранить копию в файл") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
            Text("Загрузить копию из файла")
        }
    }

    if (adding) {
        CoreEditDialog(
            entry = null,
            onSave = { kind, text -> vm.saveCore(null, kind, text); adding = false },
            onDelete = null,
            onDismiss = { adding = false }
        )
    }
    editing?.let { e ->
        CoreEditDialog(
            entry = e,
            onSave = { kind, text -> vm.saveCore(e.id, kind, text); editing = null },
            onDelete = { vm.deleteCore(e.id); editing = null },
            onDismiss = { editing = null }
        )
    }
    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = Panel,
            title = { Text("Заменить копией?") },
            text = { Text("Текущая переписка и память будут заменены. Текущие сохранятся в приложении на случай ошибки.") },
            confirmButton = {
                TextButton(onClick = { vm.importBackup(text); pendingImport = null }) { Text("Заменить") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } }
        )
    }
}

// ---------- инициатива ----------

@Composable
private fun InitiativeScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.initSettings

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateInit(vm.initSettings.copy(enabled = true))
        if (!granted) {
            Toast.makeText(ctx, "Без разрешения уведомлений сообщения будут только в чате", Toast.LENGTH_LONG).show()
        }
    }

    SettingsPage("Инициатива", onBack) {
        Hint("Аврора может написать первой: если разговор остался незаконченным, если ей есть что рассказать или если тебя давно не было. Есть ли повод, она решает сама и молчит, когда ты занят или разговор закончился. Она не упрекает за молчание. Если ты не отвечаешь, пишет всё реже и после трёх сообщений подряд перестаёт, пока ты сам не напишешь.")

        SwitchRow("Аврора может писать первой", "", s.enabled) { on ->
            if (on) {
                val needPermission = Build.VERSION.SDK_INT >= 33 &&
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needPermission) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.updateInit(s.copy(enabled = true))
            } else {
                vm.updateInit(s.copy(enabled = false))
            }
        }

        SectionTitle("Как часто")
        ChoiceRow("Редко", "не больше одного сообщения в день", s.level == 0) { vm.updateInit(s.copy(level = 0)) }
        ChoiceRow("Обычно", "не больше двух в день", s.level == 1) { vm.updateInit(s.copy(level = 1)) }
        ChoiceRow("Чаще", "не больше четырёх в день", s.level == 2) { vm.updateInit(s.copy(level = 2)) }

        SectionTitle("Не беспокоить")
        HourStepper("С", s.quietFrom) { vm.updateInit(s.copy(quietFrom = it)) }
        HourStepper("До", s.quietTo) { vm.updateInit(s.copy(quietTo = it)) }

        SectionTitle("Проверка")
        Hint("Кнопка обходит паузы, лимиты и тихие часы, чтобы ты мог убедиться, что всё работает.")
        Button(onClick = { vm.testInitiative() }, enabled = !vm.testing) {
            Text(if (vm.testing) "Аврора думает…" else "Пусть Аврора напишет сейчас")
        }

        SectionTitle("Если сообщения не приходят")
        Hint("Приложению нужен интернет, а пока домен блокируется, ещё и VPN. Некоторые телефоны усыпляют фоновые задачи: разреши приложению работать в фоне без ограничений батареи и включи автозапуск.")
        OutlinedButton(onClick = {
            try {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Открой настройки батареи в системе вручную", Toast.LENGTH_LONG).show()
            }
        }) { Text("Настройки батареи") }
    }
}
