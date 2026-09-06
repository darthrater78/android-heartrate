package com.scrivtech.heartrate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.data.maxHrForAge

/**
 * Collects the one number the zone model needs.
 *
 * Age is asked for rather than maximum heart rate directly because almost nobody knows
 * their measured maximum, and 220 minus age is the same estimate Google Health shows.
 * The derived maximum is displayed as it is typed so the number driving every zone is
 * never hidden from the person it describes.
 */
@Composable
fun AgeDialog(
    initialAge: Int?,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialAge?.toString() ?: "") }
    val age = text.toIntOrNull()
    val valid = age != null && age in Storage.MIN_AGE..Storage.MAX_AGE

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Text(text = "Your age", color = Color.White, fontWeight = FontWeight.Medium)
        },
        text = {
            Column {
                Text(
                    text = "Heart rate zones are percentages of your maximum heart " +
                        "rate, estimated as 220 minus your age.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { entered ->
                        // Digits only: the field drives an Int, and filtering here means
                        // the keyboard type is a convenience rather than the only defence.
                        text = entered.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Age") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE53935),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color(0xFFE53935),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color(0xFFE53935)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (valid) {
                        "Maximum heart rate: ${maxHrForAge(age!!)} bpm"
                    } else {
                        "Enter an age between ${Storage.MIN_AGE} and ${Storage.MAX_AGE}"
                    },
                    fontSize = 13.sp,
                    color = if (valid) {
                        Color.White.copy(alpha = 0.6f)
                    } else {
                        Color(0xFFE53935).copy(alpha = 0.8f)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(age!!) },
                enabled = valid
            ) {
                Text(
                    text = "Save",
                    color = if (valid) Color(0xFFE53935) else Color.White.copy(alpha = 0.3f)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color.White.copy(alpha = 0.5f))
            }
        }
    )
}
