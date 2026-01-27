package iwakura.lain.wheelvpn.ui.screens

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import iwakura.lain.wheelvpn.R
import iwakura.lain.wheelvpn.util.WheelUtils
import iwakura.lain.wheelvpn.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    var selectedItem by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleConnection()
        }
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(stringResource(R.string.nav_home), Icons.Default.Home, 0),
                    Triple(stringResource(R.string.nav_configs), Icons.Default.List, 1),
                    Triple(stringResource(R.string.nav_settings), Icons.Default.Settings, 2)
                )
                
                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = selectedItem,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { targetIndex ->
                when (targetIndex) {
                    0 -> HomeContent(viewModel, vpnPermissionLauncher)
                    1 -> ConfigsContent(viewModel)
                    2 -> SettingsContent()
                }
            }
        }
    }
}

@Composable
fun HomeContent(
    viewModel: MainViewModel,
    vpnPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val context = LocalContext.current
    val isConnected by viewModel.isConnected.collectAsState()
    val ping by viewModel.ping.collectAsState()
    val selectedId by viewModel.selectedConfigId.collectAsState()
    val configs by viewModel.configs.collectAsState()
    val currentConfig = configs.find { it.id == selectedId }
    
    val scale by animateFloatAsState(if (isConnected) 1.05f else 1f, label = "buttonScale")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isConnected) stringResource(R.string.vpn_connected).uppercase() 
                       else stringResource(R.string.vpn_disconnected).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            
            AnimatedVisibility(
                visible = isConnected && ping != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = ping ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ping == "Error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Box(contentAlignment = Alignment.Center) {
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            Surface(
                onClick = { 
                    val intent = VpnService.prepare(context)
                    if (intent != null) {
                        vpnPermissionLauncher.launch(intent)
                    } else {
                        viewModel.toggleConnection()
                    }
                },
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale),
                shape = CircleShape,
                color = if (isConnected) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 12.dp,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Lock else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = if (isConnected) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (currentConfig != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Share, 
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                stringResource(R.string.home_active_config), 
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                currentConfig.name, 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.home_no_config_selected),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigsContent(viewModel: MainViewModel) {
    val configs by viewModel.configs.collectAsState()
    val selectedId by viewModel.selectedConfigId.collectAsState()
    val context = LocalContext.current
    var showImportMenu by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    
    val subscriptions by viewModel.subscriptions.collectAsState()
    var selectedSubId by remember { mutableStateOf<String?>(null) } // null means "All"

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { 
                Text(
                    stringResource(R.string.nav_configs),
                    fontWeight = FontWeight.Bold
                ) 
            },
            actions = {
                Box {
                    IconButton(onClick = { showImportMenu = true }) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = stringResource(R.string.configs_import),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_import_clipboard)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showImportMenu = false
                                val content = WheelUtils.getClipboard(context)
                                viewModel.importFromClipboard(content)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_import_qr)) },
                            leadingIcon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                            onClick = {
                                showImportMenu = false
                                // TODO:Implement QR scanner
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_add_manual)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showImportMenu = false
                                showManualDialog = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_add_subscription)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showImportMenu = false
                                showSubDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.configs_update_subs)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showImportMenu = false
                                viewModel.updateSubscriptions()
                            }
                        )
                    }
                }
            }
        )

        if (subscriptions.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = if (selectedSubId == null) 0 else subscriptions.indexOfFirst { it.id == selectedSubId } + 1,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = selectedSubId == null,
                    onClick = { selectedSubId = null },
                    text = { Text("ALL") }
                )
                subscriptions.forEach { sub ->
                    Tab(
                        selected = selectedSubId == sub.id,
                        onClick = { selectedSubId = sub.id },
                        text = { Text(sub.name.uppercase()) }
                    )
                }
            }
        }

        if (showManualDialog) {
            ManualConfigDialog(
                onDismiss = { showManualDialog = false },
                onSave = { name, url ->
                    viewModel.importFromClipboard(url)
                    showManualDialog = false
                }
            )
        }

        if (showSubDialog) {
            SubscriptionDialog(
                onDismiss = { showSubDialog = false },
                onSave = { name, url ->
                    viewModel.addSubscription(name, url)
                    showSubDialog = false
                }
            )
        }

        val filteredConfigs = if (selectedSubId == null) configs else configs.filter { it.subId == selectedSubId }

        if (filteredConfigs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.configs_no_configs_yet), 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.Top
            ) {
                if (selectedSubId == null && subscriptions.isNotEmpty()) {
                    subscriptions.forEach { sub ->
                        val subConfigs = filteredConfigs.filter { it.subId == sub.id }
                        if (subConfigs.isNotEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = sub.name.uppercase(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            
                                            sub.userinfo?.let { info ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = info.split(";").firstOrNull() ?: "",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        
                                        if (sub.announce != null) {
                                            Text(
                                                text = sub.announce ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            items(subConfigs) { config ->
                                ConfigItem(
                                    config = config,
                                    isSelected = selectedId == config.id,
                                    subName = sub.name,
                                    onSelect = { viewModel.selectConfig(config.id) },
                                    onDelete = { viewModel.deleteConfig(config.id) }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    
                    val noSubConfigs = filteredConfigs.filter { it.subId == null }
                    if (noSubConfigs.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "MANUAL",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        items(noSubConfigs) { config ->
                            ConfigItem(
                                config = config,
                                isSelected = selectedId == config.id,
                                subName = null,
                                onSelect = { viewModel.selectConfig(config.id) },
                                onDelete = { viewModel.deleteConfig(config.id) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    items(filteredConfigs) { config ->
                        ConfigItem(
                            config = config,
                            isSelected = selectedId == config.id,
                            subName = null, // dont't show sub name in specific tab
                            onSelect = { viewModel.selectConfig(config.id) },
                            onDelete = { viewModel.deleteConfig(config.id) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigItem(
    config: iwakura.lain.wheelvpn.model.VpnConfig,
    isSelected: Boolean,
    subName: String?,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                
                val maskedAddress = remember(config.rawUrl) {
                    try {
                        val uri = android.net.Uri.parse(config.rawUrl)
                        val host = uri.host ?: ""
                        if (host.contains(":")) {
                            host.split(":").take(2).joinToString(":", postfix = ":***")
                        } else {
                            host.split(".").dropLast(1).joinToString(".", postfix = ".***")
                        }
                    } catch (e: Exception) { "" }
                }
                
                if (maskedAddress.isNotEmpty()) {
                    Text(
                        text = maskedAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = config.type.uppercase(),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    if (subName != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = subName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete, 
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }
            
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent() {
    val context = LocalContext.current
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.Bold) }
        )
        
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_general), 
                style = MaterialTheme.typography.labelLarge, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingItem(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_app_version),
                        value = appVersion
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    SettingItem(
                        icon = Icons.Default.Build,
                        title = stringResource(R.string.settings_xray_core),
                        value = "v24.11.11"
                    )
                }
            }
        }
    }
}

@Composable
fun SettingItem(icon: ImageVector, title: String, value: String) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(value) },
        leadingContent = { 
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) 
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ManualConfigDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.configs_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.configs_manual_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.configs_manual_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("vless://...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, url) },
                enabled = url.isNotBlank()
            ) {
                Text(stringResource(R.string.configs_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.configs_cancel))
            }
        }
    )
}

@Composable
fun SubscriptionDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.configs_subscriptions)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.configs_sub_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.configs_sub_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, url) },
                enabled = url.isNotBlank()
            ) {
                Text(stringResource(R.string.configs_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.configs_cancel))
            }
        }
    )
}
