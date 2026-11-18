package com.focuslite.app.ui.dashboard.components
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OneUiBatteryCard(onFixClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(text = "Samsung OneUI Hintergrund-Schutz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text(text = "Damit OneUI den FocusLite-Blocker nicht im Hintergrund beendet, muss die Akku-Optimierung für diese App auf „Nicht eingeschränkt“ gesetzt werden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Button(onClick = onFixClick, modifier = Modifier.align(Alignment.End)) { Text("Jetzt dauerhaft erlauben") }
        }
    }
}
