package hermes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.guilherme.hermes.shared.Choice
import org.json.JSONObject
import java.io.File

object Settings {
    private val file get() = File(AppDirs.data, "settings.json")
    private val defaultFolder = File(System.getProperty("user.home"), "Downloads${File.separator}Hermes")

    /** Where finished downloads go (Downloads\Hermes unless the person picks another folder). */
    var folder by mutableStateOf(defaultFolder)
    var choice by mutableStateOf(Choice())

    fun load() {
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            o.optString("folder", "").takeIf { it.isNotBlank() }?.let { folder = File(it) }
            choice = Choice.decode(o.optString("choice", ""))
        } catch (e: Exception) { Log.error("ler settings.json", e) }
    }

    fun save() {
        try {
            file.writeText(JSONObject().put("folder", folder.path).put("choice", choice.encode()).toString(2))
        } catch (e: Exception) { Log.error("salvar settings.json", e) }
    }
}
