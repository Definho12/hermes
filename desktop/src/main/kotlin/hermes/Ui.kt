package hermes

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guilherme.hermes.shared.AudioFormat
import com.guilherme.hermes.shared.Choice
import com.guilherme.hermes.shared.Formats
import com.guilherme.hermes.shared.Ico
import com.guilherme.hermes.shared.Icon
import com.guilherme.hermes.shared.Mode
import com.guilherme.hermes.shared.Quality
import java.awt.Desktop
import java.io.File

/** Things the build machine's screenshots (and the window) can switch from outside. */
object UiState {
    var text by mutableStateOf("")
    var details by mutableStateOf<Item?>(null)
}

@Composable
fun App(window: java.awt.Window) {
    var choice by remember { mutableStateOf(Settings.choice) }
    fun go() {
        if (Engine.enqueue(UiState.text, choice) > 0) UiState.text = ""
    }
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        // top bar: the mark, and where files go
        Row(
            Modifier.fillMaxWidth().background(C.Bar).padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ico.Mark, C.Accent, 22.dp)
            Spacer(Modifier.width(10.dp))
            Text("Hermes", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter)
            Spacer(Modifier.weight(1f))
            Text("Salvando em  ", color = C.Faint, fontSize = 13.sp, fontFamily = Inter)
            Text(prettyPath(Settings.folder), color = C.Sub, fontSize = 13.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 360.dp))
            Spacer(Modifier.width(8.dp))
            Action("Abrir pasta", Ico.Open, C.Text) { openFolder(Settings.folder) }
            Action("Trocar", Ico.Folder) { pickFolder(window) }
        }
        Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(400.dp).fillMaxHeight()) {
                InputCard(UiState.text, { UiState.text = it }, choice, { choice = it }, onGo = { go() })
                Spacer(Modifier.weight(1f))
                Footer()
            }
            DownloadList(Modifier.weight(1f).fillMaxHeight())
        }
    }

    UiState.details?.let { d ->
        val clip = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { UiState.details = null },
            containerColor = C.Surface,
            title = { Text(d.message, color = C.Text, fontSize = 17.sp, fontFamily = Inter) },
            text = {
                Column(Modifier.width(560.dp).heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(d.source, color = C.Sub, fontSize = 13.sp, fontFamily = Inter)
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer {
                        Text(d.detail.ifBlank { "Sem mais detalhes." }, color = C.Faint, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { Engine.retry(d); UiState.details = null }) { Text("Tentar de novo", color = C.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { clip.setText(AnnotatedString(d.source + "\n\n" + d.detail)) }) { Text("Copiar detalhes", color = C.Sub) }
            },
        )
    }
}

@Composable
private fun DownloadList(modifier: Modifier) {
    val items = Engine.items
    val state = rememberLazyListState()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val running = items.count { it.active }
            Text(
                if (running > 0) "Baixando · $running" else "Downloads",
                color = C.Sub, fontSize = 13.sp, fontFamily = Inter, modifier = Modifier.weight(1f),
            )
            if (items.any { !it.active }) Action("Limpar lista", Ico.Close, C.Faint) { Engine.clearFinished() }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = state, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (items.isEmpty()) item { EmptyHint() }
                items(items, key = { it.id }) { DownloadRow(it) }
            }
            VerticalScrollbar(
                rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style = ScrollbarStyle(
                    minimalHeight = 32.dp, thickness = 6.dp, shape = RoundedCornerShape(3.dp), hoverDurationMillis = 200,
                    unhoverColor = C.Border.copy(alpha = 0.6f), hoverColor = C.Faint,
                ),
            )
        }
    }
}

@Composable
private fun InputCard(text: String, setText: (String) -> Unit, choice: Choice, setChoice: (Choice) -> Unit, onGo: () -> Unit) {
    val clip = LocalClipboardManager.current
    val looksLikeList = Regex("list=|/playlist|/sets/|/album/|/channel/|/@[^/]+/?(videos)?$").containsMatchIn(text)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Field)
                .border(1.dp, C.Line, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (text.isEmpty()) Text("Cole o link aqui (ou vários, um por linha), ou escreva para buscar", color = C.Faint, fontSize = 15.sp, fontFamily = Inter)
                BasicTextField(
                    value = text,
                    onValueChange = setText,
                    textStyle = TextStyle(color = C.Text, fontSize = 15.sp, fontFamily = Inter),
                    cursorBrush = SolidColor(C.Accent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                        // Enter downloads, Shift+Enter adds a new line (to paste several links)
                        if (it.key == Key.Enter && !it.isShiftPressed) {
                            if (it.type == KeyEventType.KeyDown) onGo()
                            true
                        } else false
                    },
                )
            }
            if (text.isEmpty()) {
                Pill("Colar", Ico.Paste) { clip.getText()?.text?.let(setText) }
            } else {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { setText("") }, contentAlignment = Alignment.Center) {
                    Icon(Ico.Close, C.Sub, 16.dp)
                }
            }
        }

        // video or audio
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Field).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Segment("Vídeo", Ico.Video, choice.mode == Mode.Video, Modifier.weight(1f)) { setChoice(choice.copy(mode = Mode.Video)) }
            Segment("Áudio", Ico.Audio, choice.mode == Mode.Audio, Modifier.weight(1f)) { setChoice(choice.copy(mode = Mode.Audio)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (choice.mode == Mode.Video) {
                for (q in Quality.entries) Chip(q.label, choice.quality == q, Modifier.weight(1f)) { setChoice(choice.copy(quality = q)) }
            } else {
                Chip("MP3", choice.audio == AudioFormat.Mp3, Modifier.weight(1f)) { setChoice(choice.copy(audio = AudioFormat.Mp3)) }
                Chip("Original", choice.audio == AudioFormat.Original, Modifier.weight(1f)) { setChoice(choice.copy(audio = AudioFormat.Original)) }
            }
        }
        if (choice.mode == Mode.Audio) {
            Text(
                if (choice.audio == AudioFormat.Mp3) "MP3 toca em qualquer lugar." else "Original: o áudio como veio do site, sem converter (melhor qualidade, arquivo menor).",
                color = C.Faint, fontSize = 12.sp, fontFamily = Inter, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (looksLikeList || choice.playlist) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Baixar a lista inteira", color = C.Text, fontSize = 14.sp, fontFamily = Inter)
                    Text("Desligado: só o vídeo do link", color = C.Faint, fontSize = 12.sp, fontFamily = Inter)
                }
                Switch(
                    checked = choice.playlist, onCheckedChange = { setChoice(choice.copy(playlist = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = C.OnAccent, checkedTrackColor = C.Accent,
                        uncheckedThumbColor = C.Sub, uncheckedTrackColor = C.Field, uncheckedBorderColor = C.Border,
                    ),
                )
            }
        }

        val enabled = text.isNotBlank()
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp))
                .background(if (enabled) C.Accent else C.Raised)
                .clickable(enabled = enabled) { onGo() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (text.isNotBlank() && Formats.linkIn(text) == null) "Buscar e baixar" else "Baixar",
                color = if (enabled) C.OnAccent else C.Faint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter,
            )
        }
    }
}

@Composable
private fun Segment(label: String, icon: Ico, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(42.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) C.Raised else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, if (selected) C.Accent else C.Sub, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) C.Text else C.Sub, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontFamily = Inter)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(9.dp))
            .background(if (selected) C.AccentDim else Color.Transparent)
            .border(1.dp, if (selected) C.Accent.copy(alpha = 0.6f) else C.Line, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) C.Accent else C.Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)
    }
}

@Composable
private fun Pill(label: String, icon: Ico, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(C.Raised).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, C.Sub, 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = C.Text, fontSize = 13.sp, fontFamily = Inter)
    }
}

@Composable
private fun Action(label: String, icon: Ico, color: Color = C.Sub, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, color, 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 13.sp, fontFamily = Inter)
    }
}

@Composable
private fun DownloadRow(item: Item) {
    val running = item.state == State.Running
    val failed = item.state == State.Failed
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface)
            .clickable {
                when (item.state) {
                    State.Done -> openFile(item)
                    State.Failed -> UiState.details = item
                    else -> {}
                }
            }
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (running) C.AccentDim else C.Raised),
                contentAlignment = Alignment.Center,
            ) {
                val ico = when {
                    item.state == State.Done -> Ico.Check
                    failed -> Ico.Alert
                    item.choice.mode == Mode.Video -> Ico.Video
                    else -> Ico.Audio
                }
                Icon(ico, when { running -> C.Accent; failed -> C.Bad; else -> C.Sub }, 18.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 6.dp)) {
                Text(item.shownTitle, color = C.Text, fontSize = 15.sp, fontFamily = Inter, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${item.site} · ${item.choice.label}", color = C.Faint, fontSize = 12.sp, fontFamily = Inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    item.message,
                    color = when { failed -> C.Bad; running -> C.Accent; else -> C.Sub },
                    fontSize = 13.sp, fontFamily = Inter, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (running) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { item.pct / 100f },
                modifier = Modifier.fillMaxWidth().padding(end = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = C.Accent, trackColor = C.Raised, gapSize = 0.dp, drawStopIndicator = {},
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            when (item.state) {
                State.Queued, State.Running -> Action("Cancelar", Ico.Stop) { Engine.cancel(item) }
                State.Done -> {
                    Action("Tirar da lista", Ico.Close, C.Faint) { Engine.remove(item) }
                    Action("Mostrar na pasta", Ico.Open) { showInFolder(item) }
                    Action("Abrir", Ico.Video.takeIf { item.choice.mode == Mode.Video } ?: Ico.Audio, C.Text) { openFile(item) }
                }
                State.Failed, State.Canceled -> {
                    Action("Tirar da lista", Ico.Close, C.Faint) { Engine.remove(item) }
                    if (failed) Action("Detalhes", Ico.Alert) { UiState.details = item }
                    Action("Tentar de novo", Ico.Retry, C.Text) { Engine.retry(item) }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Funciona com YouTube, Instagram, TikTok, X, Facebook, Reddit, Kwai, SoundCloud, Vimeo, Twitch e centenas de outros sites.",
            color = C.Sub, fontSize = 14.sp, fontFamily = Inter, lineHeight = 20.sp)
        Text("Dica: dá para colar vários links de uma vez, um por linha.",
            color = C.Faint, fontSize = 13.sp, fontFamily = Inter, lineHeight = 18.sp)
    }
}

@Composable
private fun Footer() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Motor de download: " + (Engine.version ?: "preparando…") + (Engine.updateStatus?.let { " · $it" } ?: ""),
            color = C.Faint, fontSize = 12.sp, fontFamily = Inter, modifier = Modifier.weight(1f))
        TextButton(onClick = { Engine.updateYtDlp() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Atualizar", color = C.Sub, fontSize = 12.sp, fontFamily = Inter)
        }
    }
}

// ---------- files and folders ----------

fun prettyPath(f: File): String {
    val home = System.getProperty("user.home")
    return if (f.path.startsWith(home)) f.path.removePrefix(home).trimStart('\\', '/') else f.path
}

private fun openFile(item: Item) {
    val f = item.files.firstOrNull { it.exists() }
    if (f == null) { UiState.details = null; item.message = "O arquivo não está mais na pasta"; return }
    if (item.files.size > 1) { openFolder(f.parentFile); return }
    try { Desktop.getDesktop().open(f) } catch (e: Exception) { Log.error("abrir arquivo", e); showInFolder(item) }
}

private fun showInFolder(item: Item) {
    val f = item.files.firstOrNull { it.exists() } ?: return openFolder(Settings.folder)
    try {
        if (isWindows) ProcessBuilder("explorer.exe", "/select,", f.path).start()
        else Desktop.getDesktop().open(f.parentFile)
    } catch (e: Exception) { Log.error("mostrar na pasta", e) }
}

fun openFolder(dir: File) {
    try { dir.mkdirs(); Desktop.getDesktop().open(dir) } catch (e: Exception) { Log.error("abrir pasta", e) }
}

private fun pickFolder(window: java.awt.Window) {
    val ch = javax.swing.JFileChooser(Settings.folder.takeIf { it.exists() } ?: File(System.getProperty("user.home")))
    ch.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    ch.dialogTitle = "Onde salvar os downloads"
    if (ch.showDialog(window, "Usar esta pasta") == javax.swing.JFileChooser.APPROVE_OPTION) {
        Settings.folder = ch.selectedFile
        Settings.save()
    }
}
