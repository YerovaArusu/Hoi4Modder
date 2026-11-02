package at.yerova.hoi4_modder.v2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.engine.Hoi4ModderEngine
import at.yerova.hoi4_modder.v2.engine.model.AppPreferences
import at.yerova.hoi4_modder.v2.ui.*
import hoi4_modder.shared.generated.resources.*
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun ProjectSetupScreen(
    onEngineCreated: (Hoi4ModderEngine) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var vanillaPath by remember { mutableStateOf(AppPreferences.vanillaPath) }
    var modPath by remember { mutableStateOf(AppPreferences.lastModPath) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val invalidVanillaMsg = stringResource(Res.string.invalid_vanilla_path)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HMPanel(
            modifier = Modifier.width(680.dp).padding(16.dp),
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(Res.string.setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(Res.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HMTextField(
                        value = vanillaPath,
                        onValueChange = { vanillaPath = it },
                        label = stringResource(Res.string.vanilla_path_label),
                        modifier = Modifier.weight(1f)
                    )
                    HMOutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                chooseDirectory(vanillaPath)?.let { vanillaPath = it }
                            }
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        HMIcon("folder_open", size = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.browse_button))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HMTextField(
                        value = modPath,
                        onValueChange = { modPath = it },
                        label = stringResource(Res.string.mod_path_label),
                        modifier = Modifier.weight(1f)
                    )
                    HMOutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                chooseDirectory(modPath)?.let { modPath = it }
                            }
                        },
                        modifier = Modifier.height(52.dp)
                    ) {
                        HMIcon("folder_open", size = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.browse_button))
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HMButton(
                    onClick = {
                        errorMessage = null
                        try {
                            val vDir = File(vanillaPath)
                            val mDir = File(modPath)
                            require(File(vDir, "map/provinces.bmp").exists()) { invalidVanillaMsg }

                            AppPreferences.vanillaPath = vanillaPath
                            AppPreferences.lastModPath = modPath
                            AppPreferences.autoOpenLastProject = true

                            val engine = Hoi4ModderEngine(vDir, mDir)
                            engine.initializeAndLoadAsync()
                            onEngineCreated(engine)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Error initializing project"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    HMIcon("rocket_launch", size = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.open_create_mod),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun EngineLoadingScreen(
    modName: String,
    loadingStep: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HMPanel(
            modifier = Modifier.width(480.dp).padding(16.dp),
            contentPadding = PaddingValues(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HMIcon("public", size = 36.sp, color = MaterialTheme.colorScheme.primary)

                Text(
                    text = "Loading Mod: $modName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = loadingStep.ifBlank { stringResource(Res.string.loading_map) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

suspend fun chooseDirectory(initialPath: String): String? {
    return FileKit.openDirectoryPicker(PlatformFile(initialPath))?.path
}