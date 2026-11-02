package at.yerova.hoi4_modder.v2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.ui.*
import at.yerova.hoi4_modder.v2.ui.navigation.AvailableStudioViews
import at.yerova.hoi4_modder.v2.ui.navigation.WorkspaceRoute
import at.yerova.hoi4_modder.v2.ui.navigation.descriptor
import at.yerova.hoi4_modder.v2.ui.state.WorkspaceTabManager

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HMWorkspaceTabBar(
    tabManager: WorkspaceTabManager,
    modifier: Modifier = Modifier
) {
    val openTabs = tabManager.openTabs
    val activeTab = tabManager.activeTab

    Surface(
        modifier = modifier.fillMaxWidth().height(36.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            openTabs.forEach { route ->
                val desc = route.descriptor()
                val isSelected = activeTab == route

                Column(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(HMDefaults.ComponentShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        // Mittelklick schließt den Tab wie in Eclipse / VS Code / Browsern
                        .onPointerEvent(PointerEventType.Release) { event ->
                            if (event.button == PointerButton.Tertiary) {
                                tabManager.closeTab(route)
                            }
                        }
                        .clickable { tabManager.openOrFocusTab(route) },
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HMIcon(
                            iconName = desc.iconName,
                            size = 15.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = desc.defaultTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Schließen-Button (X)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(HMDefaults.ComponentShape)
                                .clickable { tabManager.closeTab(route) },
                            contentAlignment = Alignment.Center
                        ) {
                            HMIcon(
                                iconName = "close",
                                size = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Aktiver Unterstrich-Indikator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                }
            }
        }
    }
}

/**
 * Wird angezeigt, wenn der Benutzer alle offenen Arbeitsbereich-Tabs geschlossen hat.
 */
@Composable
fun EmptyWorkspaceViewsPlaceholder(
    onOpenView: (WorkspaceRoute) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        HMPanel(
            modifier = Modifier.width(460.dp),
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HMIcon("tab_unselected", size = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "No Views Open",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Reopen any tool via 'View -> Show View' in the top menu bar or pick one below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AvailableStudioViews.forEach { view ->
                        HMOutlinedButton(
                            onClick = { onOpenView(view.route) },
                            modifier = Modifier.weight(1f)
                        ) {
                            HMIcon(view.iconName, size = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(view.defaultTitle, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}