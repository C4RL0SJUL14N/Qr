package com.example.qrstudent

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                StudentQrScreen()
            }
        }
    }
}

data class StudentInfo(
    val documento: String,
    val carne: String,
    val nombres: String,
    val apellidos: String,
    val grado: String
)

@Composable
private fun StudentQrScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    var studentInfo by remember { mutableStateOf<StudentInfo?>(null) }
    var readingDateTime by remember { mutableStateOf("") }
    var observation by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("Escanea un codigo QR para ver los datos del estudiante") }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val rawContent = scanResult?.contents

        if (rawContent.isNullOrBlank()) {
            message = "No se pudo leer un QR valido"
            return@rememberLauncherForActivityResult
        }

        val parsed = parseStudentInfo(rawContent)
        if (parsed == null) {
            message = "QR leido, pero no contiene los campos requeridos"
            return@rememberLauncherForActivityResult
        }

        studentInfo = parsed
        observation = ""
        readingDateTime = currentDateTime()
        message = "Lectura exitosa"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Lector QR de Estudiantes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = {
                if (activity == null) {
                    message = "No se encontro una actividad para abrir la camara"
                    return@Button
                }

                val scanIntent = IntentIntegrator(activity)
                    .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    .setPrompt("Enfoca el codigo QR del estudiante")
                    .setBeepEnabled(true)
                    .setOrientationLocked(true)
                    .createScanIntent()

                scannerLauncher.launch(scanIntent)
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("Escanear QR")
        }

        Text(text = message, style = MaterialTheme.typography.bodyMedium)

        StudentField("Documento", studentInfo?.documento ?: "-")
        StudentField("Carné", studentInfo?.carne ?: "-")
        StudentField("Nombres", studentInfo?.nombres ?: "-")
        StudentField("Apellidos", studentInfo?.apellidos ?: "-")
        StudentField("Grado", studentInfo?.grado ?: "-")
        StudentField("Fecha y hora de lectura", if (readingDateTime.isBlank()) "-" else readingDateTime)

        OutlinedTextField(
            value = observation,
            onValueChange = { observation = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Observacion (opcional)") },
            placeholder = { Text("Agregar observacion") },
            minLines = 3
        )
    }
}

@Composable
private fun StudentField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun parseStudentInfo(raw: String): StudentInfo? {
    parseJsonPayload(raw)?.let { return it }
    return parseKeyValuePayload(raw)
}

private fun parseJsonPayload(raw: String): StudentInfo? {
    return runCatching {
        val json = JSONObject(raw)
        buildStudentInfo(
            mapOf(
                "documento" to json.optString("documento"),
                "carne" to (json.optString("carne").ifBlank { json.optString("carné") }),
                "nombres" to json.optString("nombres"),
                "apellidos" to json.optString("apellidos"),
                "grado" to json.optString("grado")
            )
        )
    }.getOrNull()
}

private fun parseKeyValuePayload(raw: String): StudentInfo? {
    val resultMap = mutableMapOf<String, String>()
    val entries = raw.split("\n", ";", "|")

    for (entry in entries) {
        val cleanEntry = entry.trim()
        if (cleanEntry.isBlank()) continue

        val separatorIndex = cleanEntry.indexOf(':').takeIf { it >= 0 } ?: cleanEntry.indexOf('=').takeIf { it >= 0 }
        if (separatorIndex == null) continue

        val key = normalizeKey(cleanEntry.substring(0, separatorIndex))
        val value = cleanEntry.substring(separatorIndex + 1).trim()

        resultMap[key] = value
    }

    return buildStudentInfo(resultMap)
}

private fun buildStudentInfo(values: Map<String, String>): StudentInfo? {
    val documento = values["documento"]
        .orEmpty()
        .ifBlank { values["identificacion"].orEmpty() }
        .trim()
    val carne = values["carne"]
        .orEmpty()
        .ifBlank { values["carnet"].orEmpty() }
        .trim()
    val nombres = values["nombres"].orEmpty().trim()
    val apellidos = values["apellidos"].orEmpty().trim()
    val grado = values["grado"].orEmpty().trim()

    if (documento.isBlank() || carne.isBlank() || nombres.isBlank() || apellidos.isBlank() || grado.isBlank()) {
        return null
    }

    return StudentInfo(
        documento = documento,
        carne = carne,
        nombres = nombres,
        apellidos = apellidos,
        grado = grado
    )
}

private fun normalizeKey(source: String): String {
    val noAccents = Normalizer.normalize(source.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")

    return noAccents
        .replace(" ", "")
        .replace("_", "")
}

private fun currentDateTime(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date())
}
