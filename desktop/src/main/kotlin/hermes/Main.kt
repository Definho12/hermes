package hermes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import java.io.File

fun main() {
    try {
        realMain()
    } catch (e: Throwable) {
        Log.error("abrir o Swallow", e)
        try {
            javax.swing.JOptionPane.showMessageDialog(
                null, "O Swallow não conseguiu abrir:\n${e::class.java.simpleName}: ${e.message}\n\nDetalhes em %APPDATA%\\Swallow\\log.txt",
                "Swallow", javax.swing.JOptionPane.ERROR_MESSAGE
            )
        } catch (_: Throwable) {}
        kotlin.system.exitProcess(1)
    }
}

object Win {
    var window: java.awt.Window? = null
    var state: androidx.compose.ui.window.WindowState? = null
    fun bringToFront() {
        state?.isMinimized = false
        window?.let { it.toFront(); it.requestFocus() }
    }
}

private fun realMain() {
    Log.w("iniciando (Java ${System.getProperty("java.version")}, ${System.getProperty("os.name")})")
    Thread.setDefaultUncaughtExceptionHandler { t, e -> Log.error("thread ${t.name}", e) }
    val shot = System.getenv("HERMES_SCREENSHOT")
    if (shot != null) System.setProperty("skiko.renderApi", "SOFTWARE")
    if (shot == null && !SingleInstance.claim { Win.bringToFront() }) return
    javax.swing.SwingUtilities.invokeAndWait {
        Settings.load()
        Engine.init()
        if (shot != null) Engine.demo()
    }

    application {
        val state = rememberWindowState(width = 1080.dp, height = 700.dp)
        fun quit() {
            Log.w("saindo")
            Settings.save(); Engine.cancelAll(); Engine.saveHistory()
            exitApplication(); kotlin.system.exitProcess(0)
        }
        Window(
            onCloseRequest = {
                val busy = Engine.items.count { it.active }
                if (busy == 0 || shot != null) quit()
                else {
                    val r = javax.swing.JOptionPane.showConfirmDialog(
                        Win.window, "Ainda tem $busy download(s) em andamento. Fechar e cancelar?",
                        "Swallow", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE,
                    )
                    if (r == javax.swing.JOptionPane.YES_OPTION) quit()
                }
            },
            title = "Swallow",
            icon = painterResource("img/icon.png"),
            state = state,
        ) {
            window.minimumSize = java.awt.Dimension(860, 540)
            SideEffect { Win.window = window; Win.state = state }
            LaunchedEffect(window) { TitleBar.applyDark(window) }
            SwallowTheme { App(window) }
            if (shot != null) ScreenshotRunner(shot, window) { quit() }
        }
    }
}

// ---------- screenshot check (build machine only) ----------

@Composable
private fun ScreenshotRunner(path: String, window: java.awt.Window, done: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000)
        capture(window, path.replace(".png", "-1.png"))
        UiState.text = "https://www.youtube.com/playlist?list=PLdemo"
        Settings.choice = Settings.choice
        delay(800)
        capture(window, path.replace(".png", "-2.png"))
        UiState.text = ""
        UiState.details = Engine.items.firstOrNull { it.state == State.Failed }
        delay(1000)
        capture(window, path.replace(".png", "-3.png"))
        UiState.details = null
        Engine.items.clear()
        delay(800)
        capture(window, path.replace(".png", "-4.png"))
        done()
    }
}

private fun capture(w: java.awt.Window, path: String) {
    try {
        val r = java.awt.Rectangle(w.locationOnScreen, w.size)
        val img = java.awt.Robot().createScreenCapture(r)
        javax.imageio.ImageIO.write(img, "png", File(path))
    } catch (e: Exception) {
        System.err.println("screenshot failed: $e")
    }
}
