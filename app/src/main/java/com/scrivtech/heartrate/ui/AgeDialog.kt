package com.scrivtech.heartrate.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.data.maxHrForAge
import com.scrivtech.heartrate.ui.theme.Dimens
import com.scrivtech.heartrate.ui.theme.HrmType
import com.scrivtech.heartrate.ui.theme.TextMuted
import com.scrivtech.heartrate.ui.theme.TextPrimary

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
        title = { Text(text = "Your age") },
        text = {
            Column {
                Text(
                    text = "Heart rate zones are percentages of your maximum heart " +
                        "rate, estimated as 220 minus your age.",
                    style = HrmType.Body,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(Dimens.ScreenEdge))
                OutlinedTextField(
                    value = text,
                    onValueChange = { entered ->
                        // Digits only: the field drives an Int, and filtering here means
                        // the keyboard type is a convenience rather than the only defence.
                        text = entered.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Age") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(Dimens.Md))
                // The save button being disabled is what marks the entry invalid; this
                // line says what would make it valid.
                Text(
                    text = if (valid) {
                        "Maximum heart rate: ${maxHrForAge(age!!)} bpm"
                    } else {
                        "Enter an age between ${Storage.MIN_AGE} and ${Storage.MAX_AGE}"
                    },
                    style = HrmType.BodySm,
                    color = if (valid) TextPrimary else TextMuted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onSave(age!!) }, enabled = valid) {
                Text(text = "Save")
            }
        },
        dismissButton = { MutedTextButton(text = "Cancel", onClick = onDismiss) }
    )
}
