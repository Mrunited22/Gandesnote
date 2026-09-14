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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════ MODEL ═══════════════
data class DocItem(
    val id: String,
    val name: String,
    val path: String,
    val createdAt: Long
)

data class NoteItem(
    val id: String,
    val text: String,
    val pinned: Boolean,
    val createdAt: Long
)

// ═══════════════ STORAGE ═══════════════
class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("gandes_notes", Context.MODE_PRIVATE)

    fun loadNotes(): List<NoteItem> {
        val raw = prefs.getString("notes", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val item = arr.get(i)
                if (item is String) {
                    NoteItem(UUID.randomUUID().toString(), item, false, System.currentTimeMillis())
                } else {
                    val obj = item as org.json.JSONObject
                    NoteItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.optString("text", ""),
                        pinned = obj.optBoolean("pinned", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveNotes(notes: List<NoteItem>) {
        val arr = JSONArray()
        notes.forEach { n ->
            val obj = org.json.JSONObject()
            obj.put("id", n.id)
            obj.put("text", n.text)
            obj.put("pinned", n.pinned)
            obj.put("createdAt", n.createdAt)
            arr.put(obj)
        }
        prefs.edit().putString("notes", arr.toString()).apply()
    }

    fun loadDocs(): List<DocItem> {
        val raw = prefs.getString("docs", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DocItem(obj.getString("id"), obj.getString("name"), obj.getString("path"), obj.getLong("createdAt"))
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

    fun isDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)
    fun setDarkMode(dark: Boolean) { prefs.edit().putBoolean("dark_mode", dark).apply() }
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

fun formatShortDate(millis: Long): String {
    val fmt = SimpleDateFormat("dd MMM", Locale("id", "ID"))
    return fmt.format(Date(millis))
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%.0f KB", kb)
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
        val destFile = File(docsDir, "scan_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile
    } catch (e: Exception) { null }
}

fun shareFile(context: Context, file: File, mime: String, title: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "com.gandes.note.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title — Gandes Note")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Bagikan ke…")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {}
}

fun openPdfExternally(context: Context, file: File) {
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

// ═══════════════ PDF OPS ═══════════════
suspend fun renderPdfThumbnail(pdf: File, maxWidth: Int = 300): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val page = renderer.openPage(0)
        val scale = maxWidth.toFloat() / page.width
        val w = maxWidth
        val h = (page.height * scale).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        bmp
    } catch (e: Exception) { null }
}

suspend fun renderPdfPages(pdf: File, scale: Float = 1.5f): List<Bitmap> = withContext(Dispatchers.IO) {
    val list = mutableListOf<Bitmap>()
    try {
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val w = (page.width * scale).roundToInt()
            val h = (page.height * scale).roundToInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            list.add(bmp)
        }
        renderer.close()
    } catch (e: Exception) {}
    list
}

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
            val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
            sb.append("─── Halaman ${i + 1} ───\n")
            sb.append(result.text.ifBlank { "(tidak ada teks)" })
            sb.append("\n\n")
            bmp.recycle()
        }
        renderer.close()
        recognizer.close()
    } catch (e: Exception) { sb.append("Error: ${e.message}") }
    sb.toString()
}

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

fun mergePdfs(inputs: List<File>, output: File): Boolean {
    return try {
        val merger = PDFMergerUtility()
        inputs.forEach { merger.addSource(it) }
        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(null)
        true
    } catch (e: Exception) { false }
}

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
            FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            bmp.recycle()
            files.add(f)
        }
        renderer.close()
    } catch (e: Exception) {}
    files
}

suspend fun imagesToPdf(context: Context, uris: List<Uri>, output: File): Boolean = withContext(Dispatchers.IO) {
    return@withContext try {
        val pdf = android.graphics.pdf.PdfDocument()
        uris.forEachIndexed { i, uri ->
            val input = context.contentResolver.openInputStream(uri)
            val bmp = android.graphics.BitmapFactory.decodeStream(input)
            input?.close()
            if (bmp != null) {
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                val page = pdf.startPage(info)
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

// ═══════════════ PREMIUM THEME ═══════════════
private val CyanPrimary = Color(0xFF06B6D4)

private val PremiumDark = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF001418),
    primaryContainer = Color(0xFF003D4A),
    onPrimaryContainer = Color(0xFFB0EBF8),
    background = Color(0xFF0A0E1A),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF141A26),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF1E2536),
    onSurfaceVariant = Color(0xFF9CA5B8),
    outline = Color(0xFF2E3648),
    error = Color(0xFFEF5350)
)

private val PremiumLight = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCEEF8),
    onPrimaryContainer = Color(0xFF003543),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEAF0F6),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFCBD5E0),
    error = Color(0xFFDC2626)
)

// ═══════════════ PASTEL COLORS ═══════════════
val PastelColors = listOf(
    Color(0xFFFFF4C4), // kuning
    Color(0xFFFFD7E0), // pink
    Color(0xFFD8F0D8), // hijau
    Color(0xFFD5E7FF), // biru
    Color(0xFFE8DFFF), // ungu
    Color(0xFFFFE0CE), // oranye
    Color(0xFFCDEFF5), // cyan
    Color(0xFFFFE5B4)  // peach
)

fun colorForId(id: String): Color {
    val hash = abs(id.hashCode())
    return PastelColors[hash % PastelColors.size]
}

// ═══════════════ SCREEN ENUM ═══════════════
enum class Tab { HOME, DOCS, NOTES, TOOLS }
enum class SubScreen { NONE, SCANNER, OCR, SPLIT, MERGE, CONVERT, PREVIEW }

// ═══════════════ MAIN ═══════════════
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { PDFBoxResourceLoader.init(applicationContext) } catch (e: Exception) {}

        setContent {
            val context = LocalContext.current
            val store = remember { NoteStore(context) }
            var isDark by remember { mutableStateOf(store.isDarkMode()) }

            MaterialTheme(colorScheme = if (isDark) PremiumDark else PremiumLight) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GandesRoot(
                        store = store,
                        isDark = isDark,
                        onToggleTheme = {
                            isDark = !isDark
                            store.setDarkMode(isDark)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GandesRoot(
    store: NoteStore,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var subScreen by remember { mutableStateOf(SubScreen.NONE) }
    var refreshKey by remember { mutableStateOf(0) }
    var previewDoc by remember { mutableStateOf<DocItem?>(null) }
    var fABPressed by remember { mutableStateOf(false) }

    val fabScale by animateFloatAsState(
        targetValue = if (fABPressed) 0.92f else 1f,
        animationSpec = tween(120),
        label = "fab"
    )

    Scaffold(
        floatingActionButton = {
            if (subScreen == SubScreen.NONE && tab == Tab.HOME) {
                FloatingActionButton(
                    onClick = {
                        fABPressed = !fABPressed
                        subScreen = SubScreen.SCANNER
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Outlined.DocumentScanner,
                        contentDescription = "Scan",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        bottomBar = {
            if (subScreen == SubScreen.NONE) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = tab == Tab.HOME,
                        onClick = { tab = Tab.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == Tab.DOCS,
                        onClick = { tab = Tab.DOCS },
                        icon = { Icon(Icons.Filled.Description, contentDescription = "Dokumen") },
                        label = { Text("Dokumen", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == Tab.NOTES,
                        onClick = { tab = Tab.NOTES },
                        icon = { Icon(Icons.Filled.Notes, contentDescription = "Catatan") },
                        label = { Text("Catatan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == Tab.TOOLS,
                        onClick = { tab = Tab.TOOLS },
                        icon = { Icon(Icons.Outlined.Build, contentDescription = "Alat") },
                        label = { Text("Alat", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = navItemColors()
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = subScreen to tab,
                transitionSpec = {
                    (fadeIn(tween(220)) togetherWith fadeOut(tween(180)))
                },
                label = "screen"
            ) { (ss, tb) ->
                when (ss) {
                    SubScreen.NONE -> {
                        when (tb) {
                            Tab.HOME -> key(refreshKey) {
                                HomeTab(
                                    store = store,
                                    isDark = isDark,
                                    onToggleTheme = onToggleTheme,
                                    onOpenDoc = { doc ->
                                        previewDoc = doc
                                        subScreen = SubScreen.PREVIEW
                                    }
                                )
                            }
                            Tab.DOCS -> key(refreshKey) {
                                DocsTab(
                                    store = store,
                                    onOpenDoc = { doc ->
                                        previewDoc = doc
                                        subScreen = SubScreen.PREVIEW
                                    }
                                )
                            }
                            Tab.NOTES -> key(refreshKey) {
                                NotesTab(store = store)
                            }
                            Tab.TOOLS -> ToolsTab(onPick = { tool ->
                                subScreen = when (tool) {
                                    "OCR" -> SubScreen.OCR
                                    "SPLIT" -> SubScreen.SPLIT
                                    "MERGE" -> SubScreen.MERGE
                                    else -> SubScreen.CONVERT
                                }
                            })
                        }
                    }
                    SubScreen.SCANNER -> ScannerScreen(
                        onCancel = { subScreen = SubScreen.NONE },
                        onSuccess = { pdfPath ->
                            val newDoc = DocItem(
                                id = UUID.randomUUID().toString(),
                                name = "Scan ${formatDate(System.currentTimeMillis())}",
                                path = pdfPath,
                                createdAt = System.currentTimeMillis()
                            )
                            store.saveDocs(store.loadDocs() + newDoc)
                            refreshKey++
                            subScreen = SubScreen.NONE
                        }
                    )
                    SubScreen.PREVIEW -> {
                        val doc = previewDoc
                        if (doc != null) {
                            PreviewScreen(
                                doc = doc,
                                onBack = {
                                    previewDoc = null
                                    refreshKey++
                                    subScreen = SubScreen.NONE
                                }
                            )
                        } else {
                            subScreen = SubScreen.NONE
                        }
                    }
                    SubScreen.OCR -> OcrScreen(onBack = { subScreen = SubScreen.NONE })
                    SubScreen.SPLIT -> SplitScreen(
                        onBack = { subScreen = SubScreen.NONE },
                        onSaved = { refreshKey++ }
                    )
                    SubScreen.MERGE -> MergeScreen(
                        onBack = { subScreen = SubScreen.NONE },
                        onSaved = { refreshKey++ }
                    )
                    SubScreen.CONVERT -> ConvertScreen(
                        onBack = { subScreen = SubScreen.NONE },
                        onSaved = { refreshKey++ }
                    )
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

// ═══════════════ TAB 1 — HOME ═══════════════
@Composable
fun HomeTab(
    store: NoteStore,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDoc: (DocItem) -> Unit
) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(store.loadNotes()) }
    var docs by remember { mutableStateOf(store.loadDocs()) }
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    val totalStorage = docs.sumOf { File(it.path).length() }
    val recentDocs = docs.sortedByDescending { it.createdAt }.take(2)

    val filteredDocs = if (query.isBlank()) recentDocs
        else docs.filter { it.name.contains(query, ignoreCase = true) }.take(4)
    val filteredNotes = (if (query.isBlank()) notes
        else notes.filter { it.text.contains(query, ignoreCase = true) })
        .sortedByDescending { it.pinned }
        .take(3)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header gradient
        GradientHeader(
            isDark = isDark,
            docCount = docs.size,
            noteCount = notes.size,
            storageStr = formatSize(totalStorage),
            onToggleTheme = onToggleTheme
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari dokumen atau catatan…", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            Spacer(Modifier.height(20.dp))

            // Quick input
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Catatan cepat…", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(10.dp))
                Card(
                    modifier = Modifier.size(52.dp),
                    onClick = {
                        if (input.isNotBlank()) {
                            val newList = notes + NoteItem(
                                UUID.randomUUID().toString(),
                                input.trim(),
                                false,
                                System.currentTimeMillis()
                            )
                            notes = newList
                            store.saveNotes(newList)
                            input = ""
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Tambah",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Section: Dokumen terbaru
            if (filteredDocs.isNotEmpty()) {
                SectionHeader(
                    title = if (query.isBlank()) "Dokumen Terbaru" else "Hasil Pencarian",
                    subtitle = "${filteredDocs.size} item"
                )
                Spacer(Modifier.height(12.dp))

                LazyVerticalGridHorizontalPlaceholder(docs = filteredDocs, onOpen = onOpenDoc)

                Spacer(Modifier.height(24.dp))
            }

            // Section: Catatan terbaru
            if (filteredNotes.isNotEmpty()) {
                SectionHeader(
                    title = if (query.isBlank()) "Catatan Terbaru" else "Catatan Cocok",
                    subtitle = "${filteredNotes.size} item"
                )
                Spacer(Modifier.height(12.dp))

                filteredNotes.forEach { note ->
                    PastelNoteCard(
                        note = note,
                        onTogglePin = {
                            val newList = notes.map {
                                if (it.id == note.id) it.copy(pinned = !it.pinned) else it
                            }
                            notes = newList
                            store.saveNotes(newList)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(24.dp))
            }

            // Empty state
            if (filteredDocs.isEmpty() && filteredNotes.isEmpty()) {
                EmptyState(
                    title = if (query.isBlank()) "Mulai Perjalanan Kamu" else "Tidak ada hasil",
                    message = if (query.isBlank())
                        "Tap tombol scan buat rekam dokumen, atau tulis catatan pertama kamu."
                    else "Coba kata kunci lain."
                )
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun GradientHeader(
    isDark: Boolean,
    docCount: Int,
    noteCount: Int,
    storageStr: String,
    onToggleTheme: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = if (isDark) listOf(
                        Color(0xFF0A2540),
                        Color(0xFF062A38),
                        Color(0xFF0A0E1A)
                    ) else listOf(
                        Color(0xFF0891B2),
                        Color(0xFF06B6D4),
                        Color(0xFF22D3EE)
                    )
                )
            )
    ) {
        // Glow
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .alpha(0.4f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF06B6D4).copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Gandes Note",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDark) Color(0xFFE8ECF2) else Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Capture ideas. Preserve moments.",
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF9CA5B8) else Color.White.copy(alpha = 0.85f)
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.08f)
                            else Color.White.copy(alpha = 0.18f),
                            CircleShape
                        )
                ) {
                    Text(if (isDark) "🌙" else "☀️", fontSize = 20.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill(value = "$docCount", label = "Dokumen", isDark = isDark)
                StatPill(value = "$noteCount", label = "Catatan", isDark = isDark)
                StatPill(value = storageStr, label = "Terpakai", isDark = isDark)
            }
        }
    }
}

@Composable
fun StatPill(value: String, label: String, isDark: Boolean) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color.White.copy(alpha = 0.06f)
            else Color.White.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = if (isDark) Color(0xFF06B6D4) else Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 10.sp,
                color = if (isDark) Color(0xFF9CA5B8) else Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-0.3).sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LazyVerticalGridHorizontalPlaceholder(docs: List<DocItem>, onOpen: (DocItem) -> Unit) {
    // Grid horizontal sederhana pakai Row (biar gak nested scroll)
    Column {
        docs.chunked(2).forEach { rowDocs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowDocs.forEach { doc ->
                    Box(modifier = Modifier.weight(1f)) {
                        DocThumbCard(doc = doc, onOpen = { onOpen(doc) })
                    }
                }
                if (rowDocs.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun DocThumbCard(doc: DocItem, onOpen: () -> Unit) {
    val context = LocalContext.current
    val file = remember(doc.id) { File(doc.path) }

    val thumbnail by produceState<Bitmap?>(initialValue = null, doc.id) {
        value = renderPdfThumbnail(file)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .background(Color.White)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumbnail
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    doc.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatShortDate(doc.createdAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PastelNoteCard(note: NoteItem, onTogglePin: () -> Unit) {
    val pastelBg = colorForId(note.id)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = pastelBg)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                note.text,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = Color(0xFF1F2937),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onTogglePin) {
                Icon(
                    if (note.pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Pin",
                    tint = if (note.pinned) Color(0xFFF59E0B) else Color(0xFF6B7280),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════ TAB 2 — DOCS ═══════════════
@Composable
fun DocsTab(store: NoteStore, onOpenDoc: (DocItem) -> Unit) {
    val context = LocalContext.current
    var docs by remember { mutableStateOf(store.loadDocs()) }
    var query by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<DocItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filtered = if (query.isBlank()) docs
        else docs.filter { it.name.contains(query, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Dokumen",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${docs.size} file tersimpan",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari dokumen…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )
        }

        if (filtered.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) "Belum ada dokumen" else "Tidak ada hasil",
                message = if (query.isBlank())
                    "Tap tombol scan di Home buat rekam dokumen pertama."
                else "Coba kata kunci lain."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { doc ->
                    Box {
                        DocThumbCard(doc = doc, onOpen = { onOpenDoc(doc) })

                        // Menu tombol
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            SmallCircleButton(
                                icon = Icons.Filled.Edit,
                                onClick = {
                                    renameTarget = doc
                                    renameText = doc.name
                                }
                            )
                            SmallCircleButton(
                                icon = Icons.Filled.Share,
                                onClick = {
                                    val f = File(doc.path)
                                    if (f.exists()) shareFile(context, f, "application/pdf", doc.name)
                                }
                            )
                            SmallCircleButton(
                                icon = Icons.Filled.Delete,
                                tint = Color(0xFFEF5350),
                                onClick = {
                                    try { File(doc.path).delete() } catch (e: Exception) {}
                                    val newList = docs.filter { it.id != doc.id }
                                    docs = newList
                                    store.saveDocs(newList)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Ganti Nama", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Nama dokumen") },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        val newList = docs.map {
                            if (it.id == target.id) it.copy(name = renameText.trim()) else it
                        }
                        docs = newList
                        store.saveDocs(newList)
                    }
                    renameTarget = null
                }) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun SmallCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.size(30.dp),
        onClick = onClick,
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
    }
}

// ═══════════════ TAB 3 — NOTES ═══════════════
@Composable
fun NotesTab(store: NoteStore) {
    var notes by remember { mutableStateOf(store.loadNotes()) }
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<NoteItem?>(null) }
    var editText by remember { mutableStateOf("") }

    val filtered = (if (query.isBlank()) notes
        else notes.filter { it.text.contains(query, ignoreCase = true) })
        .sortedByDescending { it.pinned }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Catatan",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${notes.size} catatan",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari catatan…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tulis catatan baru…", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(10.dp))
                Card(
                    modifier = Modifier.size(52.dp),
                    onClick = {
                        if (input.isNotBlank()) {
                            val newList = notes + NoteItem(
                                UUID.randomUUID().toString(),
                                input.trim(),
                                false,
                                System.currentTimeMillis()
                            )
                            notes = newList
                            store.saveNotes(newList)
                            input = ""
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) "Belum ada catatan" else "Tidak ada hasil",
                message = if (query.isBlank()) "Tulis catatan pertama kamu di atas."
                else "Coba kata kunci lain."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { note ->
                    PastelNoteCardFull(
                        note = note,
                        onEdit = {
                            editTarget = note
                            editText = note.text
                        },
                        onTogglePin = {
                            val newList = notes.map {
                                if (it.id == note.id) it.copy(pinned = !it.pinned) else it
                            }
                            notes = newList
                            store.saveNotes(newList)
                        },
                        onDelete = {
                            val newList = notes.filter { it.id != note.id }
                            notes = newList
                            store.saveNotes(newList)
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Edit Catatan", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text("Teks catatan") },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank()) {
                        val newList = notes.map {
                            if (it.id == target.id) it.copy(text = editText.trim()) else it
                        }
                        notes = newList
                        store.saveNotes(newList)
                    }
                    editTarget = null
                }) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
fun PastelNoteCardFull(
    note: NoteItem,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val pastelBg = colorForId(note.id)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = pastelBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                note.text,
                fontSize = 14.sp,
                color = Color(0xFF1F2937),
                fontWeight = FontWeight.Medium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatShortDate(note.createdAt),
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (note.pinned) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Pin",
                        tint = if (note.pinned) Color(0xFFF59E0B) else Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ═══════════════ TAB 4 — TOOLS ═══════════════
@Composable
fun ToolsTab(onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Alat PDF",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Semua alat untuk kelola PDF kamu",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            ToolCard(Icons.Outlined.TextFields, "OCR — Ambil Teks", "Extract teks dari PDF pakai AI", Color(0xFF06B6D4)) { onPick("OCR") }
            Spacer(Modifier.height(10.dp))
            ToolCard(Icons.Outlined.ContentCut, "Pisah PDF", "Ambil halaman tertentu aja", Color(0xFFF59E0B)) { onPick("SPLIT") }
            Spacer(Modifier.height(10.dp))
            ToolCard(Icons.Outlined.CallMerge, "Gabung PDF", "Satukan beberapa PDF jadi satu", Color(0xFF10B981)) { onPick("MERGE") }
            Spacer(Modifier.height(10.dp))
            ToolCard(Icons.Outlined.Image, "Konversi", "PDF ke JPG, atau Gambar ke PDF", Color(0xFF8B5CF6)) { onPick("CONVERT") }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun ToolCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════ EMPTY STATE ═══════════════
@Composable
fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ilustrasi — lingkaran konsentrik dengan icon di tengah
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ═══════════════ PREVIEW PDF ═══════════════
@Composable
fun PreviewScreen(doc: DocItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember(doc.id) { File(doc.path) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(doc.id) {
        loading = true
        pages = renderPdfPages(file)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                pages.forEach { it.recycle() }
                onBack()
            }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${pages.size} halaman", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { shareFile(context, file, "application/pdf", doc.name) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { openPdfExternally(context, file) }) {
                Icon(Icons.Filled.Description, contentDescription = "Buka eksternal", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Memuat halaman…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(pages.size) { i ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            Text(
                                "Hal. ${i + 1}",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(8.dp)
                            )
                            Image(
                                bitmap = pages[i].asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().aspectRatio(
                                    pages[i].width.toFloat() / pages[i].height.toFloat()
                                ),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════ OCR ═══════════════
@Composable
fun OcrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                result = ""
                try {
                    val tmp = File(context.cacheDir, "ocr_input.pdf")
                    copyUriToFile(context, uri, tmp)
                    result = runOcrOnPdf(context, tmp)
                } catch (e: Exception) { result = "Error: ${e.message}" }
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
        ) { Text("Pilih PDF", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(16.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Menjalankan OCR…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (result.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Text(result, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("OCR", result))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Copy") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, result)
                        }
                        context.startActivity(Intent.createChooser(intent, "Bagikan teks…"))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Bagikan") }
            }
        }
    }
}

// ═══════════════ SPLIT ═══════════════
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

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        ) { Text(if (pickedPdf == null) "Pilih PDF" else "Ganti PDF", fontWeight = FontWeight.SemiBold) }
        if (pickedPdf != null) {
            Spacer(Modifier.height(8.dp))
            Text(pickedName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it.filter { c -> c.isDigit() } },
                    label = { Text("Dari") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it.filter { c -> c.isDigit() } },
                    label = { Text("Sampai") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
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
                            status = if (ok) { onSaved(); "✓ Berhasil" } else "Gagal pisah PDF"
                        } catch (e: Exception) { status = "Error: ${e.message}" }
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

// ═══════════════ MERGE ═══════════════
@Composable
fun MergeScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked = remember { mutableStateListOf<Uri>() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
        ) { Text("Pilih 2+ PDF", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        if (picked.isNotEmpty()) {
            Text("${picked.size} PDF dipilih:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        status = if (ok) { onSaved(); "✓ Berhasil" } else "Gagal gabung PDF"
                    } catch (e: Exception) { status = "Error: ${e.message}" }
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

// ═══════════════ CONVERT ═══════════════
@Composable
fun ConvertScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = ""
                try {
                    val src = File(context.cacheDir, "convert_input.pdf")
                    copyUriToFile(context, uri, src)
                    val outDir = File(context.filesDir, "images/jpg_${System.currentTimeMillis()}")
                    val files = pdfToJpg(context, src, outDir)
                    status = if (files.isNotEmpty()) "✓ ${files.size} JPG disimpan" else "Gagal convert"
                } catch (e: Exception) { status = "Error: ${e.message}" }
                busy = false
            }
        }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                status = ""
                try {
                    val outDir = File(context.filesDir, "docs")
                    if (!outDir.exists()) outDir.mkdirs()
                    val out = File(outDir, "from_images_${System.currentTimeMillis()}.pdf")
                    val ok = imagesToPdf(context, uris, out)
                    status = if (ok) { onSaved(); "✓ PDF dibuat dari ${uris.size} gambar" } else "Gagal convert"
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
        ) { Text("PDF → JPG", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { pickImages.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !busy,
            shape = RoundedCornerShape(14.dp)
        ) { Text("Gambar → PDF", fontWeight = FontWeight.SemiBold) }
        if (busy) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
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
