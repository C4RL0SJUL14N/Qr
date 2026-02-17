package com.example.qrstudent

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
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

@Composable
private fun StudentQrScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val tcpClient = remember { TcpClient() }

    var documento by rememberSaveable { mutableStateOf("") }
    var carne by rememberSaveable { mutableStateOf("") }
    var nombres by rememberSaveable { mutableStateOf("") }
    var apellidos by rememberSaveable { mutableStateOf("") }
    var grado by rememberSaveable { mutableStateOf("") }
    var readingDateTime by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf("") }
    var operatorName by rememberSaveable { mutableStateOf("") }
    var punctualityEnabled by rememberSaveable { mutableStateOf(true) }
    var expectedEntryTime by rememberSaveable { mutableStateOf("07:00") }
    var observation by rememberSaveable { mutableStateOf("") }
    var serverHost by rememberSaveable { mutableStateOf("192.168.1.10") }
    var serverPort by rememberSaveable { mutableStateOf("5050") }
    var shouldStayConnected by rememberSaveable { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Escanea QR o llena los datos manualmente") }

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

        documento = parsed.documento
        carne = parsed.carne
        nombres = parsed.nombres
        apellidos = parsed.apellidos
        grado = parsed.grado
        readingDateTime = currentDateTime()
        message = "Lectura exitosa"
    }

    LaunchedEffect(shouldStayConnected, serverHost, serverPort) {
        while (shouldStayConnected) {
            if (!isConnected) {
                val host = serverHost.trim()
                val port = serverPort.trim().toIntOrNull()
                if (host.isBlank() || port == null || port !in 1..65535) {
                    message = "Configura IP/puerto validos para reconectar"
                    delay(3000)
                    continue
                }

                val result = withContext(Dispatchers.IO) { tcpClient.connect(host, port) }
                if (result.isSuccess) {
                    isConnected = true
                    message = "Conectado a $host:$port"
                } else {
                    message = "Reintentando conexion..."
                }
            } else if (!tcpClient.isActive()) {
                isConnected = false
                message = "Conexion perdida, reconectando..."
            }
            delay(3000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tcpClient.close()
        }
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

        ConnectionBadge(isConnected = isConnected)

        OutlinedTextField(
            value = serverHost,
            onValueChange = { serverHost = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP del servidor") },
            placeholder = { Text("Ejemplo: 192.168.1.20") },
            singleLine = true
        )

        OutlinedTextField(
            value = serverPort,
            onValueChange = { serverPort = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Puerto del servidor") },
            placeholder = { Text("5050") },
            singleLine = true
        )

        Button(
            onClick = {
                if (shouldStayConnected) {
                    shouldStayConnected = false
                    tcpClient.close()
                    isConnected = false
                    message = "Conexion cerrada"
                    return@Button
                }

                val host = serverHost.trim()
                val port = serverPort.trim().toIntOrNull()
                if (host.isBlank()) {
                    message = "Ingresa la IP del servidor"
                    return@Button
                }
                if (port == null || port !in 1..65535) {
                    message = "Puerto invalido"
                    return@Button
                }

                shouldStayConnected = true
                message = "Conexion activa con reconexion automatica"
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text(if (shouldStayConnected) "Detener conexion" else "Conectar")
        }

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre del dispositivo") },
            placeholder = { Text("Ejemplo: Porteria 1") },
            singleLine = true
        )

        OutlinedTextField(
            value = operatorName,
            onValueChange = { operatorName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Docente/Funcionario") },
            placeholder = { Text("Nombre de quien registra") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Control de puntualidad")
            Switch(
                checked = punctualityEnabled,
                onCheckedChange = { punctualityEnabled = it }
            )
        }

        if (punctualityEnabled) {
            OutlinedTextField(
                value = expectedEntryTime,
                onValueChange = { expectedEntryTime = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hora de ingreso esperada (HH:mm)") },
                placeholder = { Text("07:00") },
                singleLine = true
            )
        }

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

        OutlinedTextField(
            value = documento,
            onValueChange = { documento = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Documento") },
            singleLine = true
        )

        OutlinedTextField(
            value = carne,
            onValueChange = { carne = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Carné") },
            singleLine = true
        )

        OutlinedTextField(
            value = nombres,
            onValueChange = { nombres = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombres") },
            singleLine = true
        )

        OutlinedTextField(
            value = apellidos,
            onValueChange = { apellidos = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Apellidos") },
            singleLine = true
        )

        OutlinedTextField(
            value = grado,
            onValueChange = { grado = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Grado") },
            singleLine = true
        )

        OutlinedTextField(
            value = readingDateTime,
            onValueChange = { readingDateTime = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Fecha y hora de lectura (yyyy-MM-dd HH:mm:ss)") },
            placeholder = { Text(currentDateTime()) },
            singleLine = true
        )

        OutlinedTextField(
            value = observation,
            onValueChange = { observation = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Observacion (opcional)") },
            placeholder = { Text("Agregar observacion") },
            minLines = 3
        )

        Text(text = message, style = MaterialTheme.typography.bodyMedium)

        Button(
            onClick = {
                if (!shouldStayConnected || !isConnected) {
                    message = "Primero conectate al servidor"
                    return@Button
                }

                val student = buildStudentInfo(
                    mapOf(
                        "documento" to documento,
                        "carne" to carne,
                        "nombres" to nombres,
                        "apellidos" to apellidos,
                        "grado" to grado
                    )
                )

                if (student == null) {
                    message = "Completa documento, carné, nombres, apellidos y grado"
                    return@Button
                }

                val finalReadingTime = readingDateTime.trim().ifBlank { currentDateTime() }
                readingDateTime = finalReadingTime

                val payload = buildServerPayload(
                    student = student,
                    readingDateTime = finalReadingTime,
                    deviceName = deviceName,
                    operatorName = operatorName,
                    expectedEntryTime = expectedEntryTime,
                    punctualityEnabled = punctualityEnabled,
                    observation = observation
                )

                scope.launch {
                    val sendResult = withContext(Dispatchers.IO) { tcpClient.sendLine(payload) }
                    if (sendResult.isSuccess) {
                        message = "Informacion enviada al servidor"
                    } else {
                        isConnected = false
                        message = "Conexion perdida, reconectando..."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("Enviar informacion")
        }
    }
}

@Composable
private fun ConnectionBadge(isConnected: Boolean) {
    val text = if (isConnected) "Conectado" else "Desconectado"
    val background = if (isConnected) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    val foreground = Color.White

    Text(
        text = "Estado: $text",
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

data class StudentInfo(
    val documento: String,
    val carne: String,
    val nombres: String,
    val apellidos: String,
    val grado: String
)

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

private fun resolveEntryStatus(
    readingDateTime: String,
    punctualityEnabled: Boolean,
    expectedEntryTime: String
): String {
    if (!punctualityEnabled) {
        return "Funcion deshabilitada"
    }

    if (readingDateTime.isBlank()) {
        return "-"
    }

    val expectedMinutes = parseHourMinute(expectedEntryTime) ?: return "Hora esperada invalida"
    val readHourMinute = readingDateTime.takeIf { it.length >= 16 }?.substring(11, 16) ?: return "-"
    val readMinutes = parseHourMinute(readHourMinute) ?: return "-"

    return if (readMinutes <= expectedMinutes) "A tiempo" else "Tarde"
}

private fun parseHourMinute(value: String): Int? {
    val match = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(value.trim()) ?: return null
    val hours = match.groupValues[1].toInt()
    val minutes = match.groupValues[2].toInt()
    return (hours * 60) + minutes
}

private fun buildServerPayload(
    student: StudentInfo,
    readingDateTime: String,
    deviceName: String,
    operatorName: String,
    expectedEntryTime: String,
    punctualityEnabled: Boolean,
    observation: String
): String {
    return JSONObject().apply {
        put("documento", student.documento)
        put("carne", student.carne)
        put("nombres", student.nombres)
        put("apellidos", student.apellidos)
        put("grado", student.grado)
        put("fecha_hora_lectura", readingDateTime)
        put("dispositivo", deviceName)
        put("docente_funcionario", operatorName)
        put(
            "estado_ingreso",
            resolveEntryStatus(
                readingDateTime = readingDateTime,
                punctualityEnabled = punctualityEnabled,
                expectedEntryTime = expectedEntryTime
            )
        )
        put("observacion", observation)
    }.toString()
}

private class TcpClient {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    @Synchronized
    fun connect(host: String, port: Int): Result<Unit> {
        close()
        return runCatching {
            val createdSocket = Socket()
            createdSocket.connect(InetSocketAddress(host, port), 5000)
            val output = OutputStreamWriter(createdSocket.getOutputStream(), StandardCharsets.UTF_8)
            writer = BufferedWriter(output)
            socket = createdSocket
        }
    }

    @Synchronized
    fun sendLine(payload: String): Result<Unit> {
        val currentWriter = writer ?: return Result.failure(IllegalStateException("Sin conexion activa"))
        return runCatching {
            currentWriter.write(payload)
            currentWriter.newLine()
            currentWriter.flush()
        }.onFailure {
            close()
        }
    }

    @Synchronized
    fun isActive(): Boolean {
        val currentSocket = socket ?: return false
        return currentSocket.isConnected && !currentSocket.isClosed
    }

    @Synchronized
    fun close() {
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
    }
}
