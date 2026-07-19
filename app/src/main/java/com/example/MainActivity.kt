package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import java.io.File

enum class ActiveScreen {
    HOME,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: ModManagerViewModel by viewModels()
    private var hasPermissionState by mutableStateOf(false)

    // Classic Android <11 Storage Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        hasPermissionState = readGranted || writeGranted
        if (hasPermissionState) {
            viewModel.triggerScan()
        } else {
            Toast.makeText(this, "Dosya erişim izni reddedildi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initSettings(this)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(ActiveScreen.HOME) }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "ScreenTransition"
                    ) { screen ->
                        when (screen) {
                            ActiveScreen.HOME -> HomeScreen(
                                hasPermission = hasPermissionState,
                                viewModel = viewModel,
                                onRequestPermission = { requestAppPermissions() },
                                onNavigateToSettings = { currentScreen = ActiveScreen.SETTINGS }
                            )
                            ActiveScreen.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { currentScreen = ActiveScreen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRefreshPermissions()
    }

    private fun checkAndRefreshPermissions() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        hasPermissionState = granted

        // Auto trigger scan on app launch/resume if permission is newly found
        if (granted) {
            val currentState = viewModel.scanState.value
            if (viewModel.autoScanEnabled.value && (currentState is ScanState.Idle || currentState is ScanState.Error)) {
                viewModel.triggerScan()
            }
        }
    }

    private fun requestAppPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // Open Settings window for All Files Access (Android 11+)
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            // Standard Permission request for older Android releases
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}

// Data holder for tab items
data class TabCategory(
    val title: String,
    val extension: String,
    val icon: ImageVector,
    val description: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    hasPermission: Boolean,
    viewModel: ModManagerViewModel,
    onRequestPermission: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val categories = remember {
        listOf(
            TabCategory("Eklentiler", "mcaddon", Icons.Default.Build, "Minecraft Eklentileri"),
            TabCategory("Paketler", "mcpack", Icons.Default.Layers, "Kaynak ve Davranış Paketleri"),
            TabCategory("Şablonlar", "mctemplate", Icons.Default.Category, "Dünya Şablonları"),
            TabCategory("Dünyalar", "mcworld", Icons.Default.Public, "Minecraft Dünyaları")
        )
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val activeCategory = categories[selectedTabIndex]

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (hasPermission) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.triggerScan() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("rescan_fab"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Modları Tara",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Modları Tara",
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }
                    
                    Text(
                        text = "COM.MOJANG.MINECRAFTPE HEDEFİ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Elegant Clean Minimalist Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Custom branding emblem
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Senden",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Mod Yöneticisi & Kurucu",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Settings Button
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!hasPermission) {
                // Display beautiful Permission Request Screen
                PermissionRationalCard(
                    onRequestPermission = onRequestPermission
                )
            } else {
                // Modern Segmented Pill Tabs Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    categories.forEachIndexed { index, tabCategory ->
                        val isSelected = selectedTabIndex == index
                        val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(background)
                                .clickable { selectedTabIndex = index }
                                .padding(vertical = 10.dp)
                                .testTag("tab_${tabCategory.extension}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (tabCategory.extension) {
                                    "mcaddon" -> "Addons"
                                    "mcpack" -> "Packs"
                                    "mctemplate" -> "Templates"
                                    "mcworld" -> "Worlds"
                                    else -> tabCategory.title
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar Input Card (clean outline style)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Mod adı ile ara...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("search_input"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Ara",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Temizle",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Heading/Label matching HTML design: recent label
                val headingText = when (activeCategory.extension) {
                    "mcaddon" -> "RECENT ADDONS"
                    "mcpack" -> "RECENT PACKS"
                    "mctemplate" -> "RECENT TEMPLATES"
                    "mcworld" -> "RECENT WORLDS"
                    else -> "RECENT FILES"
                }
                
                Text(
                    text = headingText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )

                // Render dynamic states: Scanning, Error, or Success
                when (val state = scanState) {
                    is ScanState.Idle -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is ScanState.Scanning -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Modlar taranıyor, lütfen bekleyin...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                    is ScanState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Hata",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.triggerScan() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Yeniden Dene")
                            }
                        }
                    }
                    is ScanState.Success -> {
                        val filteredList = remember(state.files, searchQuery, activeCategory) {
                            state.files.filter {
                                it.extension == activeCategory.extension &&
                                        it.name.contains(searchQuery, ignoreCase = true)
                            }
                        }

                        if (filteredList.isEmpty()) {
                            EmptyStatePlaceholder(
                                isSearchFilterActive = searchQuery.isNotEmpty(),
                                extension = activeCategory.extension,
                                categoryName = activeCategory.title
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("mod_list"),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredList) { modFile ->
                                    ModFileRowItem(
                                        modFile = modFile,
                                        onInstallClick = {
                                            viewModel.installMod(context, modFile)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Interactive row item for individual mod files
@Composable
fun ModFileRowItem(
    modFile: ModFile,
    onInstallClick: () -> Unit
) {
    val categoryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInstallClick() }
            .testTag("mod_file_card_${modFile.name}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Custom Icon Container with surfaceVariant background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (modFile.extension) {
                        "mcaddon" -> Icons.Default.Build
                        "mcpack" -> Icons.Default.Layers
                        "mctemplate" -> Icons.Default.Category
                        "mcworld" -> Icons.Default.Public
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = modFile.extension,
                    tint = categoryColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details: Name, File Size, Last Modified Date
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = modFile.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = modFile.formattedSize,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                    )

                    Text(
                        text = modFile.formattedDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chevron-right matching HTML design
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Detay",
                tint = categoryColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Gorgeous Minecraft styled empty state warning block
@Composable
fun EmptyStatePlaceholder(
    isSearchFilterActive: Boolean,
    extension: String,
    categoryName: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("empty_placeholder"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearchFilterActive) Icons.Default.SearchOff else Icons.Default.FolderOpen,
                contentDescription = "Boş Dosya",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isSearchFilterActive) "Arama sonucu bulunamadı!" else "Dosya Bulunamadı",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSearchFilterActive) {
                "Girdiğiniz kriterlere uygun hiçbir Minecraft dosyası eşleşmedi. Lütfen tekrar deneyin."
            } else {
                "Cihazınızda hiçbir .$extension ($categoryName) dosyası bulunamadı. Lütfen indirilenler klasörüne dosya ekleyin."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// Custom Rationale card when MANAGE_EXTERNAL_STORAGE permission is missing
@Composable
fun PermissionRationalCard(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .testTag("permission_rationale"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Klasör İzni",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Tam Dosya Erişimi Gerekli",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Minecraft modlarını (.mcworld, .mcaddon, .mctemplate, .mcpack) cihazınızda otomatik olarak bulup tek tıkla Minecraft'a kurabilmek için uygulamanın tüm dosyalara erişim iznine ihtiyacı vardır.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(52.dp)
                .testTag("grant_permission_button")
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "İzin Ver",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "İzin Ver ve Başla",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Highly customized Settings screen aligning with Turkish requests
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ModManagerViewModel,
    onNavigateBack: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val autoScanEnabled by viewModel.autoScanEnabled.collectAsState()
    val sortNewestFirst by viewModel.sortNewestFirst.collectAsState()
    
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            
            // 1. Theme Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tema Ayarı",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            Triple("SYSTEM", "Sistem", Icons.Default.SettingsSuggest),
                            Triple("DARK", "Karanlık", Icons.Default.DarkMode),
                            Triple("LIGHT", "Aydınlık", Icons.Default.LightMode)
                        )

                        modes.forEach { (mode, label, icon) ->
                            val isSelected = themeMode == mode
                            val buttonColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(buttonColor, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setThemeMode(mode) }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = contentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = contentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Extra Scan Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tarama Ayarları",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto Scan Switch Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Açılışta otomatik tara",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Uygulama açıldığında modları otomatik tarar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = autoScanEnabled,
                            onCheckedChange = { viewModel.setAutoScan(it) },
                            modifier = Modifier.testTag("auto_scan_switch")
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )

                    // Sort order: Newest First Switch Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dosyaları yeniden eskiye sırala",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "En son indirilen modları en başta gösterir",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = sortNewestFirst,
                            onCheckedChange = { viewModel.setSortNewestFirst(it) },
                            modifier = Modifier.testTag("sort_order_switch")
                        )
                    }
                }
            }

            // 3. About & Telegram Redirection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hakkında",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Minecraft Bedrock Edition Mod Yöneticisi ve Kurucu uygulaması, cihazınızdaki .mcaddon, .mcpack, .mctemplate ve .mcworld uzantılı dosyaları saniyeler içinde tarar ve tek bir tıklamayla oyuna aktarılmasını sağlar.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Yeni modlar, eklentiler, haritalar ve dlc paketleri indirmek için Telegram kanalımıza katılın!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            uriHandler.openUri("https://t.me/MC_DLC_FREE")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0088CC), // Telegram Brand Color
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(44.dp)
                            .testTag("telegram_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Telegram",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "@MC_DLC_FREE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
