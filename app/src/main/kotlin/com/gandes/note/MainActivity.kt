package com.gandes.note

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.json.JSONArray

// ═══════════════ STORAGE ═══════════════
class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("gandes_notes", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString("notes", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(notes: List<String>) {
        val arr = JSONArray()
        notes.forEach { arr.put(it) }
        prefs.edit().putString("notes", arr.toString()).apply()
    }
}

// ═══════════════ THEME ═══════════════
private val GandesColors = darkColorScheme(
    primary = Color(0xFF00BCD4),
    onPrimary = Color.Black,
    background = Color(0xFF0F1115),
    onBackground = Color.White,
    surface = Color(0xFF161A22),
    onSurface = Color.White
)

// ═══════════════ SCREEN ═══════════════
@Composable
fun NoteScreen() {
    val context = LocalContext.current
    val store = remember { NoteStore(context) }
    var notes by remember { mutableStateOf(store.load()) }
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Gandes Note",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

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
                    store.save(newList)
                    input = ""
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada catatan", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                store.save(newList)
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Hapus",
                                    tint = Color(0xFFEF5350)
                                )
                            }
                        }
                    }
                }
            }
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
                    NoteScreen()
                }
            }
        }
    }
}
