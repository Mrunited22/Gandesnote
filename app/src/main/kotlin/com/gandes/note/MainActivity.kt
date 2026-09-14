package com.gandes.note

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
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

fun copyUriToFile(context: Context, uri: Uri, target: File): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        true
    } catch (e: Exception) { false }
}

fun copyPdfToStorage(context: Context, sourceUri: Uri): File? {
    return try {
        val docsDir = File(context.filesDir, "docs")
        if (!docsDir.exists()) docsDir.mkdirs()
        val filename = "scan_${System.currentTimeMillis()}.pdf"
        val destFile = File(docsDir, filename)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile
    } catch (e: Exception) { null }
}

fun openPdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "com.gandes.note.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {}
}

fun shareFile(context: Context, file: File, mime: String, title: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "com.gandes.note.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title - dikirim dari Gandes Note")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Bagikan ke…")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {}
}

// ═══════════════ PDF OPERATIONS ═══════════════

// OCR — render PDF ke bitmap lalu extract teks tiap halaman
suspend fun runOcrOnPdf(context: Context, pdfFile: File): String = withContext(Dispatchers.IO) {
    val sb = StringBuilder()
    try {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val image = InputImage.fromBitmap(bmp, 0)
            val result = recognizer.process(image).await()
            sb.append("─── Halaman ${i + 1} ───\n")
            sb.append(result.text.ifBlank { "(tidak ada teks terdeteksi)" })
            sb.append("\n\n")
            bmp.recycle()
        }

        renderer.close()
        recognizer.close()
    } catch (e: Exception) {
        sb.append("Error OCR: ${e.message}")
    }
    sb.toString()
}

// Pisah PDF dari halaman X ke Y
fun splitPdfRange(input: File, output: File, fromPage: Int, toPage: Int): Boolean {
    return try {
        val src = PDDocument.load(input)
        val total = src.numberOfPages
        val safeFrom = fromPage.coerceIn(1, total)
        val safeTo = toPage.coerceIn(safeFrom, total)

        val splitter = Splitter()
        splitter.setStartPage(safeFrom)
        splitter.setEndPage(safeTo)
        splitter.setSplitAtPage(safeTo - safeFrom + 1)

        val docs = splitter.split(src)
        if (docs.isNotEmpty()) {
            docs[0].save(output)
            docs.forEach { it.close() }
        }
        src.close()
        true
    } catch (e: Exception) { false }
}

// Gabung beberapa PDF
fun mergePdfs(inputs: List<File>, output: File): Boolean {
    return try {
        val merger = PDFMergerUtility()
        inputs.forEach { merger.addSource(it) }
        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(null)
        true
    } catch (e: Exception) { false }
}

// PDF → JPG
suspend fun pdfToJpg(context: Context, pdfFile: File, outDir: File): List<File> = withContext(Dispatchers.IO) {
    val files = mutableListOf<File>()
    try {
        if (!outDir.exists()) outDir.mkdirs()
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val f = File(outDir, "page_${i + 1}.jpg")
            FileOutputStream(f).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bmp.recycle()
            files.add(f)
        }
        renderer.close()
    } catch (e: Exception) {}
    files
}

// JPG → PDF
suspend fun imagesToPdf(context: Context, uris: List<Uri>, output: File): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val pdf = android.graphics.pdf.PdfDocument()
        uris.forEachIndexed { i, uri ->
            val input = context.contentResolver.openInputStream(uri)
            val bmp = android.graphics.BitmapFactory.decodeStream(input)
            input?.close()
            if (bmp != null) {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                    bmp.width, bmp.height, i + 1
                ).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                pdf.finishPage(page)
                bmp.recycle()
            }
        }
        FileOutputStream(output).use { pdf.writeTo(it) }
        pdf.close()
        true
    } catch (e: Exception) { false }
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

// ═══════════════ SCREEN ENUM ═══════════════
enum class Screen {
    HOME, SCANNER, PDF_TOOLS,
    OCR_RESULT, SPLIT, MERGE, CONVERT
}

// ═══════════════ HOME ═══════════════
@Composable
fun HomeScreen(onScanClick: () -> Unit, onToolsClick: () -> Unit) {
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

        Row {
            Button(
                onClick = onScanClick,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onToolsClick,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Alat PDF", fontWeight = FontWeight.SemiBold)
            }
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
                            if (f.exists()) shareFile(context, f, "application/pdf", doc.name)
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
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
fun DocCard(doc: DocItem, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(formatDate(doc.createdAt), fontSize = 11.sp, color = Color.Gray)
            }
            IconButton(onClick = onOpen) {
                Text("Buka", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Bagikan", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350))
            }
        }
    }
}

// ═══════════════ PDF TOOLS MENU ═══════════════
@Composable
fun PdfToolsScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Alat PDF",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(16.dp))

        ToolCard(
            icon = Icons.Outlined.TextFields,
            title = "OCR — Ambil Teks",
            desc = "Extract teks dari PDF pakai AI",
            onClick = { onPick("OCR") }
        )
        Spacer(Modifier.height(10.dp))
        ToolCard(
            icon = Icons.Outlined.ContentCut,
            title = "Pisah PDF",
            desc = "Ambil halaman tertentu aja",
            onClick = { onPick("SPLIT") }
        )
        Spacer(Modifier.height(10.dp))
        ToolCard(
            icon = Icons.Outlined.CallMerge,
            title = "Gabung PDF",
            desc = "Satukan beberapa PDF jadi satu",
            onClick = { onPick("MERGE") }
        )
        Spacer(Modifier.height(10.dp))
        ToolCard(
            icon = Icons.Outlined.Image,
            title = "Konversi",
            desc = "PDF ke JPG, atau Gambar ke PDF",
            onClick = { onPick("CONVERT") }
        )
    }
}

@Composable
fun ToolCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

// ═══════════════ OCR SCREEN ═══════════════
@Composable
fun OcrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var pickedName by remember { mutableStateOf("") }

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                result = ""
                pickedName = "Memproses…"
                try {
                    val tmp = File(context.cacheDir, "ocr_input.pdf")
                    copyUriToFile(context, uri, tmp)
                    result = runOcrOnPdf(context, tmp)
                    pickedName = "Hasil OCR"
                } catch (e: Exception) {
                    result = "Error: ${e.message}"
                }
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("OCR Teks", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Pilih PDF", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Menjalankan OCR…", color = Color.Gray)
                }
            }
        }

        if (result.isNotBlank()) {
            Text(pickedName, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(result, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("OCR", result))
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Copy")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan teks…"))
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Bagikan")
                }
            }
        }
    }
}

// ═══════════════ SPLIT SCREEN ═══════════════
@Composable
fun SplitScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedPdf by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("") }
    var fromText by remember { mutableStateOf("1") }
    var toText by remember { mutableStateOf("1") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedPdf = uri
            pickedName = uri.lastPathSegment ?: "PDF"
            status = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Pisah PDF", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (pickedPdf == null) "Pilih PDF" else "Ganti PDF", fontWeight = FontWeight.SemiBold)
        }

        if (pickedPdf != null) {
            Spacer(Modifier.height(8.dp))
            Text(pickedName, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))

            Row {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it.filter { c -> c.isDigit() } },
                    label = { Text("Dari halaman") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it.filter { c -> c.isDigit() } },
                    label = { Text("Sampai") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        status = ""
                        try {
                            val src = File(context.cacheDir, "split_input.pdf")
                            copyUriToFile(context, pickedPdf!!, src)
                            val outDir = File(context.filesDir, "docs")
                            if (!outDir.exists()) outDir.mkdirs()
                            val out = File(outDir, "split_${System.currentTimeMillis()}.pdf")
                            val ok = withContext(Dispatchers.IO) {
                                splitPdfRange(src, out, fromText.toIntOrNull() ?: 1, toText.toIntOrNull() ?: 1)
                            }
                            status = if (ok) {
                                onSaved()
                                "✓ Berhasil disimpan"
                            } else "Gagal pisah PDF"
                        } catch (e: Exception) {
                            status = "Error: ${e.message}"
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black)
                else Text("Pisah Sekarang", fontWeight = FontWeight.SemiBold)
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(status, color = if (status.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ═══════════════ MERGE SCREEN ═══════════════
@Composable
fun MergeScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val pickPdfs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            picked.clear()
            picked.addAll(uris)
            status = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Gabung PDF", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { pickPdfs.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Pilih 2+ PDF", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        if (picked.isNotEmpty()) {
            Text("${picked.size} PDF dipilih:", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            picked.forEachIndexed { i, uri ->
                Text("${i + 1}. ${uri.lastPathSegment ?: "PDF"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    status = ""
                    try {
                        val files = mutableListOf<File>()
                        picked.forEachIndexed { i, uri ->
                            val f = File(context.cacheDir, "merge_${i}.pdf")
                            copyUriToFile(context, uri, f)
                            files.add(f)
                        }
                        val outDir = File(context.filesDir, "docs")
                        if (!outDir.exists()) outDir.mkdirs()
                        val out = File(outDir, "merged_${System.currentTimeMillis()}.pdf")
                        val ok = withContext(Dispatchers.IO) { mergePdfs(files, out) }
                        status = if (ok) {
                            onSaved()
                            "✓ Berhasil digabung"
                        } else "Gagal gabung PDF"
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !busy && picked.size >= 2,
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black)
            else Text("Gabung Sekarang", fontWeight = FontWeight.SemiBold)
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status, color = if (status.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
        }
    }
}

// ═══════════════ CONVERT SCREEN ═══════════════
@Composable
fun ConvertScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = ""
                try {
                    val src = File(context.cacheDir, "convert_input.pdf")
                    copyUriToFile(context, uri, src)
                    val outDir = File(context.filesDir, "images/jpg_${System.currentTimeMillis()}")
                    val files = pdfToJpg(context, src, outDir)
                    status = if (files.isNotEmpty()) "✓ ${files.size} JPG disimpan di folder app" else "Gagal convert"
                } catch (e: Exception) { status = "Error: ${e.message}" }
                busy = false
            }
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                status = ""
                try {
                    val outDir = File(context.filesDir, "docs")
                    if (!outDir.exists()) outDir.mkdirs()
                    val out = File(outDir, "from_images_${System.currentTimeMillis()}.pdf")
                    val ok = imagesToPdf(context, uris, out)
                    status = if (ok) {
                        onSaved()
                        "✓ PDF dibuat dari ${uris.size} gambar"
                    } else "Gagal convert"
                } catch (e: Exception) { status = "Error: ${e.message}" }
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Konversi", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !busy,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("PDF → JPG", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { pickImages.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !busy,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Gambar → PDF", fontWeight = FontWeight.SemiBold)
        }

        if (busy) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(status, color = if (status.startsWith("✓")) Color(0xFF10B981) else MaterialTheme.colorScheme.error)
        }
    }
}

// ═══════════════ SCANNER ═══════════════
@Composable
fun ScannerScreen(onCancel: () -> Unit, onSuccess: (pdfPath: String) -> Unit) {
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
                if (saved != null) onSuccess(saved.absolutePath)
                else error = "Gagal simpan PDF"
            } else onCancel()
        } else onCancel()
    }

    fun startScan() {
        error = null
        loading = true
        val activity = context.findActivity()
        if (activity == null) { error = "Activity tidak ditemukan"; loading = false; return }
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
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(error ?: "", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
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
        try { PDFBoxResourceLoader.init(applicationContext) } catch (e: Exception) {}

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
                        Screen.HOME -> key(refreshKey) {
                            HomeScreen(
                                onScanClick = { screen = Screen.SCANNER },
                                onToolsClick = { screen = Screen.PDF_TOOLS }
                            )
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
                                store.saveDocs(store.loadDocs() + newDoc)
                                refreshKey++
                                screen = Screen.HOME
                            }
                        )
                        Screen.PDF_TOOLS -> PdfToolsScreen(
                            onBack = { screen = Screen.HOME },
                            onPick = { tool ->
                                screen = when (tool) {
                                    "OCR" -> Screen.OCR_RESULT
                                    "SPLIT" -> Screen.SPLIT
                                    "MERGE" -> Screen.MERGE
                                    else -> Screen.CONVERT
                                }
                            }
                        )
                        Screen.OCR_RESULT -> OcrScreen(onBack = { screen = Screen.PDF_TOOLS })
                        Screen.SPLIT -> SplitScreen(
                            onBack = { screen = Screen.PDF_TOOLS },
                            onSaved = { refreshKey++ }
                        )
                        Screen.MERGE -> MergeScreen(
                            onBack = { screen = Screen.PDF_TOOLS },
                            onSaved = { refreshKey++ }
                        )
                        Screen.CONVERT -> ConvertScreen(
                            onBack = { screen = Screen.PDF_TOOLS },
                            onSaved = { refreshKey++ }
                        )
                    }
                }
            }
        }
    }
}
