package at.yerova.hoi4_modder.v2.ui.screens.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.ui.*

@Composable
fun FocusTreeWorkspaceView(
    modName: String
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Linker Bereich: Zukünftiges Node-Grid / Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            HMPanel(
                modifier = Modifier.width(520.dp),
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HMIcon(
                        iconName = "account_tree",
                        size = 40.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Focus Tree Studio ($modName)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This workspace tab is ready for the upcoming National Focus node graph editor (common/national_focus/*.txt).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HMVerticalDivider()

        // Rechter Bereich: Zukünftiger Focus-Node-Inspector
        Surface(
            modifier = Modifier.width(440.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Focus Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HMHorizontalDivider()
                Text(
                    text = "Select or create a Focus node on the canvas to edit its ID, icon, cost, prerequisites, and completion rewards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}