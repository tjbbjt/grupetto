package com.spop.poverlay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HeartRateZoneSettings(stored: List<Int?>, onChange: (List<Int?>) -> Unit) {
    Text("Zone boundaries (bpm)", fontWeight = FontWeight.SemiBold)
    Text("Enter any one boundary and tap away: the others are worked out at 60/70/80/90% of max heart rate. Boxes you typed yourself are kept.", fontSize = 13.sp)
    val values = remember(stored) {
        mutableStateListOf(*stored.map { it?.toString() ?: "" }.toTypedArray())
    }
    var focusedIndex by remember { mutableStateOf(-1) }
    var valuesOnFocus by remember { mutableStateOf(stored) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        val columns = if (maxWidth >= 480.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.indices.toList().chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    indices.forEach { index ->
                        OutlinedTextField(values[index], { input ->
                            values[index] = input.filter(Char::isDigit).take(3)
                            onChange(values.map { it.toIntOrNull() })
                        // Use words: the bundled font does not contain the right-arrow glyph.
                        }, label = { Text("${index + 1} to ${index + 2}") }, singleLine = true,
                            modifier = Modifier.weight(1f).onFocusChanged { state ->
                                if (state.isFocused) {
                                    focusedIndex = index
                                    valuesOnFocus = values.map { it.toIntOrNull() }
                                } else if (focusedIndex == index) {
                                    focusedIndex = -1
                                    val filled = fillZoneBoundaries(
                                        valuesOnFocus, values.map { it.toIntOrNull() }, index
                                    )
                                    filled.forEachIndexed { i, v -> values[i] = v?.toString() ?: "" }
                                    onChange(filled)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
        }
    }
}
