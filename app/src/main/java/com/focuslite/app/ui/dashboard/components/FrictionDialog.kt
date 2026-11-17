package com.focuslite.app.ui.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val FRICTION_SENTENCES = listOf(
    "Ich entscheide mich bewusst dafür, meine Fokuszeit für den Algorithmus zu opfern.",
    "Mir ist bewusst, dass ein einziges Kurzvideo oft zu dreißig Minuten Scrollen führt.",
    "Ich schalte den Schutz jetzt auf eigene Verantwortung aus und bleibe achtsam.",
    "Meine Konzentration ist wertvoller als endloses Scrollen durch fremde Feeds."
)

@Composable
fun FrictionDialog(
    featureName: String,
    requireSentenceTyping: Boolean,
    countdownSeconds: Int = 15,
    onDismiss: () -> Unit,
    onConfirmBypass: () -> Unit
) {
    var remainingSeconds by remember { mutableIntStateOf(countdownSeconds) }
    val targetSentence = remember { FRICTION_SENTENCES.random() }
    var typedText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1_000L)
            remainingSeconds--
        }
    }

    val isSentenceValid = !requireSentenceTyping || typedText.trim() == targetSentence
    val canUnlock = remainingSeconds == 0 && isSentenceValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Impuls-Schutz aktiv",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Du möchtest „$featureName“ deaktivieren. Atme kurz durch – brauchst du das jetzt wirklich?",
                    style = MaterialTheme.typography.bodyMedium
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (remainingSeconds > 0) {
                            "Wartezeit: Noch $remainingSeconds Sekunden..."
                        } else {
                            "Wartezeit abgelaufen."
                        },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (remainingSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                if (requireSentenceTyping) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tippe folgenden Satz exakt ab, um fortzufahren:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = targetSentence,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    OutlinedTextField(
                        value = typedText,
                        onValueChange = { newValue ->
                            if (newValue.length - typedText.length <= 3) {
                                typedText = newValue
                            }
                        },
                        label = { Text("Bestätigungssatz eingeben") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmBypass,
                enabled = canUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(if (remainingSeconds > 0) "Gesperrt (${remainingSeconds}s)" else "Trotzdem deaktivieren")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fokus behalten (Abbrechen)")
            }
        }
    )
}
