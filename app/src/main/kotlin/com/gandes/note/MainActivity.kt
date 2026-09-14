package com.gandes.note

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ═══════════════ MODEL ═══════════════
data class DocItem(
    val id: String,
    val name: String,
    val path: String,
    val createdAt: Long
)

// ═══════════════ STORAGE ═══════════════
class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("gandes_notes", Context.MODE_PRIVATE)

    fun loadNotes(): List<String> {
        val raw = prefs.getString("notes", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveNotes(notes: List<String>) {
        val arr = JSONArray()
        notes.forEach { arr.put(it) }
        prefs.edit().putString("notes", arr.toString()).apply()
    }

    fun loadDocs(): List<DocItem> {
        val raw = prefs.getString("docs", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DocItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    path = obj.getString("path"),
                    createdAt = obj.getLong("createdAt")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveDocs(docs: List<DocItem>) {
        val arr = JSONArray()
        docs.forEach { d ->
            val obj = org.json.JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("path", d.path)
            obj.put("createdAt", d.createdAt)
            arr.put(obj)
        }
        prefs.edit().putString("docs", arr.toString()).apply()
    }
}

// ═══════════════ UTILS ═══════════════
fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    return fmt.format(Date(millis))
}

fun copyPdfToStorage(context: Context, sourceUri: Uri): File? {
    return try {
        val docsDir = File(context.filesDir, "docs")
        if (!docsDir.exists()) docsDir.mkdirs()
        val filename = "scan_${System.currentTimeMillis()}.pdf"
        val destFile = File(docsDir, filename)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destFile
    } catch (e: Exception) {
        null
    }
}

fun openPdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.gandes.note.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {}
}

// ★ FUNGSI BARU — SHARE PDF
fun sharePdf(context: Context, file: File, title: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.gandes.note.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title - dikirim dari Gandes Note")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Bagikan PDF ke…")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {}
}

// ═══════════════ THEME ═══════════════
private val GandesColors = darkColorScheme(
    primary = Color(0xFF00BCD4),
    onPrimary = Color.Black,
    background = Color(0xFF0F1115),
    onBackground = Color.White,
    surface = Color(0xFF161A22),
    onSurface = Color.White,
    error = Color(0xFFEF5350)
)

// ═══════════════ SCREEN ═══════════════
enum class Screen { HOME, SCANNER }

@Composable
fun HomeScreen(onScanClick: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore(context) }
    var notes by remember { mutableStateOf(store.loadNotes()) }
    var docs by remember { mutableStateOf(store.loadDocs()) }
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Gandes Note",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan Buku / Dokumen", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tulis catatan…") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = {
                if (input.isNotBlank()) {
                    val newList = notes + input.trim()
                    notes = newList
                    store.saveNotes(newList)
                    input = ""
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (docs.isNotEmpty()) {
                item {
                    Text(
                        "Dokumen Scan (${docs.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(docs) { doc ->
                    DocCard(
                        doc = doc,
                        onOpen = {
                            val f = File(doc.path)
                            if (f.exists()) openPdf(context, f)
                        },
                        onShare = {
                            val f = File(doc.path)
                            if (f.exists()) sharePdf(context, f, doc.name)
                        },
                        onDelete = {
                            try { File(doc.path).delete() } catch (e: Exception) {}
                            val newList = docs.filter { it.id != doc.id }
                            docs = newList
                            store.saveDocs(newList)
                        }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (notes.isNotEmpty()) {
                item {
                    Text(
                        "Catatan (${notes.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(notes.withIndex().toList()) { indexed ->
                    val index = indexed.index
                    val note = indexed.value
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                note,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = {
                                val newList = notes.toMutableList()
                                newList.removeAt(index)
                                notes = newList
                                store.saveNotes(newList)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350))
                            }
                        }
                    }
                }
            }

            if (docs.isEmpty() && notes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Belum ada catatan atau dokumen", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun DocCard(
    doc: DocItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    formatDate(doc.createdAt),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            // Tombol Buka
            IconButton(onClick = onOpen) {
                Text("Buka", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            // ★ Tombol Share
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Bagikan",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Tombol Hapus
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350))
            }
        }
    }
}

// ═══════════════ SCANNER ═══════════════
@Composable
fun ScannerScreen(
    onCancel: () -> Unit,
    onSuccess: (pdfPath: String) -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            if (pdfUri != null) {
                val saved = copyPdfToStorage(context, pdfUri)
                if (saved != null) {
                    onSuccess(saved.absolutePath)
                } else {
                    error = "Gagal simpan PDF"
                }
            } else {
                onCancel()
            }
        } else {
            onCancel()
        }
    }

    fun startScan() {
        error = null
        loading = true
        val activity = context.findActivity()
        if (activity == null) {
            error = "Activity tidak ditemukan"
            loading = false
            return
        }
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(50)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()

            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                    loading = false
                }
                .addOnFailureListener { e ->
                    error = "Gagal buka scanner: ${e.message}"
                    loading = false
                }
        } catch (e: Exception) {
            error = "Error: ${e.message}"
            loading = false
        }
    }

    LaunchedEffect(Unit) { startScan() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Membuka scanner…")
        }
        if (error != null) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { startScan() }) { Text("Coba Lagi") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCancel) { Text("Kembali") }
        }
    }
}

// ═══════════════ MAIN ═══════════════
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = GandesColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val store = remember { NoteStore(context) }
                    var screen by remember { mutableStateOf(Screen.HOME) }
                    var refreshKey by remember { mutableStateOf(0) }

                    when (screen) {
                        Screen.HOME -> {
                            key(refreshKey) {
                                HomeScreen(onScanClick = { screen = Screen.SCANNER })
                            }
                        }
                        Screen.SCANNER -> ScannerScreen(
                            onCancel = { screen = Screen.HOME },
                            onSuccess = { pdfPath ->
                                val newDoc = DocItem(
                                    id = UUID.randomUUID().toString(),
                                    name = "Scan ${formatDate(System.currentTimeMillis())}",
                                    path = pdfPath,
                                    createdAt = System.currentTimeMillis()
                                )
                                val updated = store.loadDocs() + newDoc
                                store.saveDocs(updated)
                                refreshKey++
                                screen = Screen.HOME
                            }
                        )
                    }
                }
            }
        }
    }
}
