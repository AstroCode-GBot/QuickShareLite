package com.novacode.quicksharelite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdView
import com.novacode.quicksharelite.data.*
import com.novacode.quicksharelite.transfer.NearbyTransferManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

private object Routes {
    const val Splash = "splash"
    const val Home = "home"
    const val Profile = "profile"
    const val Send = "send"
    const val Review = "review"
    const val Discover = "discover"
    const val Receive = "receive"
    const val Progress = "progress"
    const val Complete = "complete"
    const val History = "history"
    const val Settings = "settings"
    const val Pro = "pro"
    const val About = "about"
}

class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val app = application as App
    val profile = app.container.profileStore.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val adFree = app.container.settingsStore.adFree.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val theme = app.container.settingsStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val devices = app.container.transferManager.devices
    val transferState = app.container.transferManager.state
    val currentFile = app.container.transferManager.currentFileName
    val bytes = app.container.transferManager.bytesTransferred
    val total = app.container.transferManager.totalBytes
    val speed = app.container.transferManager.speedBytesPerSecond
    val eta = app.container.transferManager.remainingSeconds
    val incoming = app.container.transferManager.pendingRequest
    val verification = app.container.transferManager.verification
    val history = app.container.database.historyDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun saveProfile(name: String, avatar: Int) = app.container.profileStore.save(Profile(name.ifBlank { "Android Phone" }, avatar))
    suspend fun discover() = app.container.transferManager.startDiscovering()
    suspend fun stopDiscover() = app.container.transferManager.stopDiscovering()
    suspend fun receive(name: String) = app.container.transferManager.startReceiving(name)
    suspend fun stopReceive() = app.container.transferManager.stopReceiving()
    fun connect(d: DiscoveredDevice) { app.container.transferManager.connect(d, profile.value?.name ?: "Android Phone") }
    fun verify(accept: Boolean) = app.container.transferManager.confirmVerification(accept)
    fun prepareSend(file: LocalFile) { app.container.transferManager.prepareSend(profile.value?.name ?: "Android Phone", file) }
    fun acceptIncoming() = app.container.transferManager.acceptIncoming()
    fun rejectIncoming() = app.container.transferManager.rejectIncoming()
    fun cancel() = app.container.transferManager.cancelTransfer()
    suspend fun clearHistory() = app.container.database.historyDao().clear()
    suspend fun setTheme(value: String) = app.container.settingsStore.setTheme(value)
    fun purchase(activity: android.app.Activity) = app.container.billingManager.purchase(activity)
    fun restorePurchases() = app.container.billingManager.refreshPurchases()
}

class MainActivity : ComponentActivity() {
    private var permissionsRevision by mutableIntStateOf(0)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this)
        (application as App).container.billingManager.start()
        setContent {
            QuickShareApp(
                permissionsRequester = { requestNearbyPermissions() },
                permissionsRevision = permissionsRevision,
                permissionsGranted = { areNearbyPermissionsGranted() }
            )
        }
    }

    private fun areNearbyPermissionsGranted(): Boolean {
        val required = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= 32) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT in 29..31) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestNearbyPermissions() {
        val required = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= 32) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT in 29..31) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }.distinct().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (required.isNotEmpty()) permissions.launch(required.toTypedArray())
    }
}

@Composable
private fun QuickShareApp(
    permissionsRequester: () -> Unit,
    permissionsRevision: Int,
    permissionsGranted: () -> Boolean,
    vm: MainViewModel = viewModel()
) {
    val profile by vm.profile.collectAsState()
    val adFree by vm.adFree.collectAsState()
    val theme by vm.theme.collectAsState()
    val nav = rememberNavController()
    val systemDark = isSystemInDarkTheme()
    val darkMode = when (theme) { "dark" -> true; "light" -> false; else -> systemDark }

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF5B5FF5))) {
        NavHost(navController = nav, startDestination = Routes.Splash) {
            composable(Routes.Splash) { SplashScreen(nav, profile == null) }
            composable(Routes.Profile) { ProfileScreen(vm, nav, initial = profile, firstLaunch = profile == null) }
            composable(Routes.Home) { HomeScreen(vm, nav) }
            composable(Routes.Send) { SendSelectionScreen(nav, vm) }
            composable(Routes.Review) { ReviewScreen(nav, vm) }
            composable(Routes.Discover) { DiscoverScreen(nav, vm, permissionsRequester, permissionsRevision, permissionsGranted) }
            composable(Routes.Receive) { ReceiveScreen(nav, vm, permissionsRequester, permissionsRevision, permissionsGranted) }
            composable(Routes.Progress) { ProgressScreen(nav, vm) }
            composable(Routes.Complete) { CompleteScreen(nav) }
            composable(Routes.History) { HistoryScreen(nav, vm) }
            composable(Routes.Settings) { SettingsScreen(nav, vm, theme) }
            composable(Routes.Pro) { ProScreen(nav, vm) }
            composable(Routes.About) { AboutScreen(nav) }
        }
    }
}

@Composable
private fun Shell(title: String, nav: NavHostController, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(horizontal = 20.dp), content = content)
    }
}

@Composable
private fun SplashScreen(nav: NavHostController, needsProfile: Boolean) {
    LaunchedEffect(needsProfile) {
        kotlinx.coroutines.delay(650)
        nav.navigate(if (needsProfile) Routes.Profile else Routes.Home) { popUpTo(Routes.Splash) { inclusive = true } }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("QuickShare Lite", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel, nav: NavHostController) {
    var drawer by remember { mutableStateOf(false) }
    val profile by vm.profile.collectAsState()
    val adFree by vm.adFree.collectAsState()
    ModalNavigationDrawer(drawerState = rememberDrawerState(if (drawer) DrawerValue.Open else DrawerValue.Closed), drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(24.dp))
            ProfileBadge(profile)
            NavigationDrawerItem(label = { Text("History") }, selected = false, onClick = { drawer = false; nav.navigate(Routes.History) }, icon = { Icon(Icons.Rounded.History, null) })
            NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { drawer = false; nav.navigate(Routes.Settings) }, icon = { Icon(Icons.Rounded.Settings, null) })
            NavigationDrawerItem(label = { Text("Remove Ads") }, selected = false, onClick = { drawer = false; nav.navigate(Routes.Pro) }, icon = { Icon(Icons.Rounded.Star, null) })
            NavigationDrawerItem(label = { Text("About") }, selected = false, onClick = { drawer = false; nav.navigate(Routes.About) }, icon = { Icon(Icons.Rounded.Info, null) })
        }
    }) {
        Scaffold(bottomBar = { if (!adFree) AdBanner() }) { p ->
            Column(Modifier.fillMaxSize().padding(p).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ drawer = true }) { Icon(Icons.Rounded.Menu, "Menu") }
                    Text("QuickShare Lite", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    AvatarCircle(profile?.avatar ?: 0, 36.dp)
                }
                Spacer(Modifier.height(40.dp))
                Text("Ready to share", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                ActionCard("Send", "Choose files and send to a nearby phone", Icons.Rounded.Send) { nav.navigate(Routes.Send) }
                Spacer(Modifier.height(16.dp))
                ActionCard("Receive", "Wait for a nearby device to send", Icons.Rounded.CallReceived) { nav.navigate(Routes.Receive) }
                Spacer(Modifier.height(22.dp))
                OutlinedButton(onClick = { nav.navigate(Routes.History) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(10.dp)); Text("History") }
                Spacer(Modifier.weight(1f))
                Text("Nearby • Offline • Private", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}

@Composable
private fun ProfileBadge(profile: Profile?) {
    Column(Modifier.padding(20.dp)) { AvatarCircle(profile?.avatar ?: 0, 64.dp); Spacer(Modifier.height(10.dp)); Text(profile?.name ?: "Set up profile", fontWeight = FontWeight.SemiBold); Text("This phone", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun AvatarCircle(index: Int, size: androidx.compose.ui.unit.Dp) {
    val icons = listOf(Icons.Rounded.Person, Icons.Rounded.Face, Icons.Rounded.Bolt, Icons.Rounded.Pets, Icons.Rounded.Star, Icons.Rounded.Favorite, Icons.Rounded.Smartphone, Icons.Rounded.Cloud, Icons.Rounded.Public)
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(icons[index.coerceIn(0, icons.lastIndex)], null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * .48f)) }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun ProfileScreen(vm: MainViewModel, nav: NavHostController, initial: Profile?, firstLaunch: Boolean) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatar by remember { mutableIntStateOf(initial?.avatar ?: 0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Create your profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp)); Text("Choose a name other nearby phones will see.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp)); AvatarCircle(avatar, 112.dp)
        Spacer(Modifier.height(22.dp)); OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth(), supportingText = { Text("Stored only on this device") })
        Spacer(Modifier.height(18.dp)); Text("Avatar", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start)); Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0..4, 5..9).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { i ->
                        Box(Modifier.size(52.dp).clip(CircleShape).clickable { avatar = i }.semantics { contentDescription = "Avatar ${i + 1}" }.background(if (avatar == i) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { AvatarCircle(i, 44.dp) }
                    }
                }
            }
        }
        val scope = rememberCoroutineScope()
        Spacer(Modifier.weight(1f)); Button(onClick = { scope.launch { vm.saveProfile(name.ifBlank { "Android Phone" }, avatar); nav.navigate(Routes.Home) { popUpTo(Routes.Profile) { inclusive = true } } } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text(if (firstLaunch) "Continue" else "Save") }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SendSelectionScreen(nav: NavHostController, vm: MainViewModel) {
    val context = LocalContext.current
    val files = remember { mutableStateListOf<LocalFile>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        files.clear(); uris.forEach { uri -> context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) files += LocalFile(uri, c.getString(0) ?: "file", c.getLong(1).coerceAtLeast(0), context.contentResolver.getType(uri)) } }
    }
    Shell("Choose files", nav) {
        Text("Pick what you want to send", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("The Android picker controls what QuickShare Lite can access.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp)); Button({ picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(10.dp)); Text("Browse Files") }
        Spacer(Modifier.height(18.dp)); OutlinedButton({ picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Photos") }
        Spacer(Modifier.height(10.dp)); OutlinedButton({ picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Videos") }
        if (files.isNotEmpty()) { Spacer(Modifier.height(24.dp)); Text("Selected: ${files.size}", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); files.forEach { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.InsertDriveFile, null); Spacer(Modifier.width(12.dp)); Text(it.name, Modifier.weight(1f), maxLines = 1); Text(formatBytes(it.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        Spacer(Modifier.weight(1f)); Button(enabled = files.isNotEmpty(), onClick = { nav.currentBackStackEntry?.savedStateHandle?.set("files", files.toList()); nav.navigate(Routes.Review) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Review") }
    }
}

@Composable
private fun ReviewScreen(nav: NavHostController, vm: MainViewModel) {
    val state = nav.previousBackStackEntry?.savedStateHandle?.get<List<LocalFile>>("files") ?: emptyList()
    Shell("Review", nav) {
        Text("Ready to send", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("${state.size} file${if (state.size == 1) "" else "s"} • ${formatBytes(state.sumOf { it.size })}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp)); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(state) { f -> ListItem(headlineContent = { Text(f.name, maxLines = 1) }, supportingContent = { Text(formatBytes(f.size)) }, leadingContent = { Icon(Icons.Rounded.InsertDriveFile, null) }) } }
        Button(onClick = { if (state.size == 1) { vm.prepareSend(state.first()); nav.navigate(Routes.Discover) } }, enabled = state.size == 1, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Find nearby device") }
        if (state.size > 1) { Spacer(Modifier.height(10.dp)); Text("Milestone Zero validates one real file at a time. Multi-file queueing is a later phase.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun DiscoverScreen(nav: NavHostController, vm: MainViewModel, permissionsRequester: () -> Unit, permissionsRevision: Int = 0, permissionsGranted: () -> Boolean = { true }) {
    val devices by vm.devices.collectAsState(); val state by vm.transferState.collectAsState()
    LaunchedEffect(permissionsRevision) { if (permissionsGranted()) vm.discover() else permissionsRequester() }
    DisposableEffect(Unit) { onDispose { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { vm.stopDiscover() } } }
    Shell("Choose a device", nav) {
        Text(if (state == TransferState.AUTHENTICATING) "Confirm the connection on both phones." else "Looking for nearby devices…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp)); DiscoveryPulse()
        Spacer(Modifier.height(22.dp)); AnimatedVisibility(devices.isNotEmpty()) { LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(devices) { d -> ElevatedCard(onClick = { vm.connect(d); nav.navigate(Routes.Progress) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { ListItem(headlineContent = { Text(d.name, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("Nearby device") }, leadingContent = { AvatarCircle(0, 44.dp) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }) } } } }
        if (devices.isEmpty()) { Spacer(Modifier.weight(1f)); Text("Can’t find your device?", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = permissionsRequester, modifier = Modifier.fillMaxWidth()) { Text("Check permissions") }; Spacer(Modifier.height(20.dp)) } else Spacer(Modifier.weight(1f))
        Text("QR fallback is reserved for the next transport milestone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
        val verify by vm.verification.collectAsState()
        if (verify != null) VerificationDialog(verify!!.remoteName, verify!!.token, { vm.verify(true) }, { vm.verify(false) })
    }
}

@Composable
private fun VerificationDialog(name: String, token: String, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(onDismissRequest = onReject, title = { Text("Verify device") }, text = { Column { Text("Confirm the same code appears on the other phone:"); Spacer(Modifier.height(16.dp)); Text(token, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton(onAccept) { Text("Matches") } }, dismissButton = { TextButton(onReject) { Text("Reject") } })
}

@Composable
private fun DiscoveryPulse() {
    val transition = rememberInfiniteTransition(label = "discover"); val scale by transition.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale"); val alpha by transition.animateFloat(.35f, .08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(110.dp).scale(scale).alpha(alpha).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)); Icon(Icons.Rounded.WifiTethering, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun ReceiveScreen(nav: NavHostController, vm: MainViewModel, permissionsRequester: () -> Unit, permissionsRevision: Int = 0, permissionsGranted: () -> Boolean = { true }) {
    val profile by vm.profile.collectAsState(); val request by vm.incoming.collectAsState(); val state by vm.transferState.collectAsState()
    LaunchedEffect(profile?.name, permissionsRevision) { if (permissionsGranted()) vm.receive(profile?.name ?: "Android Phone") else permissionsRequester() }
    Shell("Receive", nav) {
        Spacer(Modifier.height(30.dp)); Icon(Icons.Rounded.CallReceived, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally)); Spacer(Modifier.height(18.dp)); Text("Ready to receive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(8.dp)); Text(profile?.name ?: "Android Phone", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(30.dp)); DiscoveryPulse(); Spacer(Modifier.weight(1f)); OutlinedButton({ kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { vm.stopReceive() }; nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Stop Receiving") }; Spacer(Modifier.height(16.dp)); Text("State: ${state.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(20.dp))
        if (request != null) IncomingDialog(request!!, vm)
    }
}

@Composable
private fun IncomingDialog(request: NearbyTransferManager.IncomingRequest, vm: MainViewModel) {
    AlertDialog(onDismissRequest = { vm.rejectIncoming() }, title = { Text("Incoming transfer") }, text = { Column { Text("From: ${request.senderName}"); Spacer(Modifier.height(8.dp)); Text("${request.fileName}"); Spacer(Modifier.height(8.dp)); Text(formatBytes(request.size), color = MaterialTheme.colorScheme.onSurfaceVariant) } }, confirmButton = { TextButton({ vm.acceptIncoming() }) { Text("Accept") } }, dismissButton = { TextButton({ vm.rejectIncoming() }) { Text("Decline") } })
}

@Composable
private fun ProgressScreen(nav: NavHostController, vm: MainViewModel) {
    val state by vm.transferState.collectAsState(); val name by vm.currentFile.collectAsState(); val bytes by vm.bytes.collectAsState(); val total by vm.total.collectAsState(); val speed by vm.speed.collectAsState(); val eta by vm.eta.collectAsState(); val verification by vm.verification.collectAsState()
    LaunchedEffect(state) { if (state == TransferState.COMPLETED) nav.navigate(Routes.Complete) }
    Shell("Transfer", nav) {
        Spacer(Modifier.height(20.dp)); Text(if (state == TransferState.WAITING_FOR_ACCEPT) "Waiting for receiver" else "Transferring", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(name.ifBlank { "Preparing…" }, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp)); val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f; LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant); Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${(progress * 100).toInt()}%"); Text("${formatBytes(bytes)} / ${formatBytes(total)}") }
        Spacer(Modifier.height(24.dp)); InfoRow("Speed", if (speed > 0) "${formatBytes(speed)}/s" else "—"); InfoRow("Remaining", eta?.let { formatEta(it) } ?: "—"); InfoRow("State", state.name.replace('_', ' '))
        Spacer(Modifier.weight(1f)); OutlinedButton({ vm.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }; Spacer(Modifier.height(20.dp)); if (verification != null) VerificationDialog(verification!!.remoteName, verification!!.token, { vm.verify(true) }, { vm.verify(false) })
    }
}

@Composable private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold) } }

@Composable private fun CompleteScreen(nav: NavHostController) { Shell("Complete", nav) { Spacer(Modifier.height(70.dp)); Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(88.dp).align(Alignment.CenterHorizontally)); Spacer(Modifier.height(22.dp)); Text("Transfer complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(8.dp)); Text("Your file transfer finished successfully.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.weight(1f)); Button({ nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Done") }; Spacer(Modifier.height(20.dp)) } }

@Composable
private fun AboutScreen(nav: NavHostController) {
    Shell("About", nav) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("QuickShare Lite", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(6.dp))
        Text("Simple nearby Android file sharing.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        InfoRow("Version", "0.1.0")
        InfoRow("Transport", "Google Nearby Connections")
        InfoRow("Storage", "Android app-private receiving folder")
        Spacer(Modifier.weight(1f))
        Text("Privacy policy and store metadata are release configuration, not hard-coded here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HistoryScreen(nav: NavHostController, vm: MainViewModel) { val items by vm.history.collectAsState(); Shell("History", nav) { if (items.isEmpty()) { Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.History, null, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(14.dp)); Text("No transfers yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally)); Text("Completed real transfers will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.weight(1f)) } else { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(items) { h -> ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { ListItem(headlineContent = { Text(if (h.direction == "SENT") "Sent to ${h.device}" else "Received from ${h.device}") }, supportingContent = { Text("${h.fileCount} file • ${formatBytes(h.totalBytes)} • ${h.status}") }, leadingContent = { Icon(if (h.direction == "SENT") Icons.Rounded.Send else Icons.Rounded.CallReceived, null) }) } }; item { TextButton({ kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { vm.clearHistory() } }) { Text("Clear history") } } } } } }

@Composable
private fun SettingsScreen(nav: NavHostController, vm: MainViewModel, theme: String) {
    val scope = rememberCoroutineScope()
    val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    Shell("Settings", nav) {
        Text("Profile", fontWeight = FontWeight.SemiBold)
        ListItem(headlineContent = { Text("${vm.profile.value?.name ?: "Android Phone"}") }, supportingContent = { Text("Tap to edit profile") }, leadingContent = { AvatarCircle(vm.profile.value?.avatar ?: 0, 44.dp) }, modifier = Modifier.clickable { nav.navigate(Routes.Profile) })
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Appearance", fontWeight = FontWeight.SemiBold)
        options.forEach { (value, label) ->
            ListItem(headlineContent = { Text(label) }, leadingContent = { RadioButton(selected = theme == value, onClick = { scope.launch { vm.setTheme(value) } }) }, modifier = Modifier.clickable { scope.launch { vm.setTheme(value) } })
        }
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Privacy", fontWeight = FontWeight.SemiBold)
        ListItem(headlineContent = { Text("Clear transfer history") }, modifier = Modifier.clickable { scope.launch { vm.clearHistory() } })
        Spacer(Modifier.weight(1f))
        Text("QuickShare Lite • 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp))
    }
}

@Composable
private fun ProScreen(nav: NavHostController, vm: MainViewModel) { val adFree by vm.adFree.collectAsState(); val activity = LocalContext.current as? android.app.Activity; Shell("Remove Ads", nav) { Spacer(Modifier.height(40.dp)); Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(70.dp).align(Alignment.CenterHorizontally)); Spacer(Modifier.height(18.dp)); Text("Go Ad-Free", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(8.dp)); Text("One-time purchase. Core transfer features remain unchanged.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.weight(1f)); if (adFree) { Text("Ads are already removed.", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally)) } else { Button(onClick = { activity?.let { vm.purchase(it) } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Purchase") }; Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { vm.restorePurchases() }, modifier = Modifier.fillMaxWidth()) { Text("Restore purchase") } }; Spacer(Modifier.height(20.dp)); Text("Create the one-time product `remove_ads` in Play Console before release.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun AdBanner() {
    val context = LocalContext.current
    AndroidView(factory = { AdView(context).apply { setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt())); adUnitId = context.getString(R.string.admob_test_banner_id); loadAd(AdRequest.Builder().build()) } }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp).padding(horizontal = 8.dp))
}

private fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; val kb = bytes / 1024.0; if (kb < 1024) return "%.1f KB".format(kb); val mb = kb / 1024.0; if (mb < 1024) return "%.1f MB".format(mb); val gb = mb / 1024.0; return "%.2f GB".format(gb) }
private fun formatEta(s: Long): String = if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
