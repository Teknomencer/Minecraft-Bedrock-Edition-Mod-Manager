package com.example

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(val files: List<ModFile>) : ScanState()
    data class Error(val message: String) : ScanState()
}

class ModManagerViewModel : ViewModel() {

    private lateinit var settingsManager: SettingsManager

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Preferences states for Compose UI observation
    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _autoScanEnabled = MutableStateFlow(true)
    val autoScanEnabled: StateFlow<Boolean> = _autoScanEnabled.asStateFlow()

    private val _sortNewestFirst = MutableStateFlow(true)
    val sortNewestFirst: StateFlow<Boolean> = _sortNewestFirst.asStateFlow()

    fun initSettings(context: Context) {
        if (!::settingsManager.isInitialized) {
            settingsManager = SettingsManager(context.applicationContext)
            _themeMode.value = settingsManager.themeMode
            _autoScanEnabled.value = settingsManager.autoScan
            _sortNewestFirst.value = settingsManager.sortNewestFirst
        }
    }

    fun setThemeMode(mode: String) {
        if (::settingsManager.isInitialized) {
            settingsManager.themeMode = mode
            _themeMode.value = mode
        }
    }

    fun setAutoScan(enabled: Boolean) {
        if (::settingsManager.isInitialized) {
            settingsManager.autoScan = enabled
            _autoScanEnabled.value = enabled
        }
    }

    fun setSortNewestFirst(enabled: Boolean) {
        if (::settingsManager.isInitialized) {
            settingsManager.sortNewestFirst = enabled
            _sortNewestFirst.value = enabled
            
            // Re-apply sorting if success state is present
            val currentState = _scanState.value
            if (currentState is ScanState.Success) {
                _scanState.value = ScanState.Success(sortFiles(currentState.files, enabled))
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun triggerScan() {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            try {
                val foundFiles = ModScanner.scanForMods()
                val sorted = sortFiles(foundFiles, _sortNewestFirst.value)
                _scanState.value = ScanState.Success(sorted)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.localizedMessage ?: "Dosyalar taranırken bir hata oluştu.")
            }
        }
    }

    private fun sortFiles(files: List<ModFile>, newestFirst: Boolean): List<ModFile> {
        return if (newestFirst) {
            files.sortedByDescending { it.lastModified }
        } else {
            files.sortedBy { it.lastModified }
        }
    }

    fun installMod(context: Context, modFile: ModFile) {
        try {
            val file = modFile.file
            if (!file.exists()) {
                Toast.makeText(context, "Hata: Dosya bulunamadı!", Toast.LENGTH_SHORT).show()
                return
            }

            // Create shareable URI using FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            // Setup View Intent targeted to Minecraft package name
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                setPackage("com.mojang.minecraftpe")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Show informative toast
            Toast.makeText(context, context.getString(R.string.toast_file_opened_minecraft), Toast.LENGTH_SHORT).show()
            context.startActivity(intent)

        } catch (e: ActivityNotFoundException) {
            // Minecraft Bedrock PE not installed, fallback or alert
            Toast.makeText(context, context.getString(R.string.toast_minecraft_not_found), Toast.LENGTH_LONG).show()
            
            // Optional: propose opening using generic file explorer so user can select an app
            try {
                val file = modFile.file
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, file)
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(genericIntent, "Modu açmak için uygulama seçin"))
            } catch (ex: Exception) {
                Toast.makeText(context, "Uygulama açılamadı: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Mod kurulamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
