package com.platform.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platform.android.BuildConfig
import com.platform.android.data.AppContainer
import com.platform.android.data.ChapterDto
import com.platform.android.data.CommentDto
import com.platform.android.data.CollectionDto
import com.platform.android.data.FollowDto
import com.platform.android.data.ReportDto
import com.platform.android.data.UserDto
import com.platform.android.data.WorkDto
import com.platform.android.ui.PlatformUiState
import com.platform.android.ui.PlatformViewModel
import com.platform.android.ui.PlatformViewModelFactory

private val AcPrimary = Color(0xFF1DA1F2)
private val AcBg = Color(0xFFF5F8FA)
private val AcText = Color(0xFF0F1419)
private val AcMuted = Color(0xFF536471)
private val AcBorder = Color(0xFFEFF3F4)
private val AcDanger = Color(0xFFE0245E)
private val StatusText = mapOf(
    "approved" to "已发布",
    "ai_approved" to "AI初审通过",
    "pending" to "待人工审核",
    "rejected" to "已驳回"
)

private fun generateCaptcha(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..4).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
}

private val AcColorScheme = lightColorScheme(
    primary = AcPrimary,
    onPrimary = Color.White,
    secondary = AcPrimary,
    background = AcBg,
    surface = Color.White,
    onSurface = AcText,
    outline = AcBorder,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PlatformApplication).container
        setContent {
            MaterialTheme(colorScheme = AcColorScheme) {
                PlatformApp(container)
            }
        }
    }
}

@Composable
private fun PlatformApp(container: AppContainer) {
    val vm: PlatformViewModel = viewModel(factory = PlatformViewModelFactory(container.api, container.sessionStore))
    val state by vm.state.collectAsState()
    val session by vm.session.collectAsState()
    var tab by remember { mutableStateOf("home") }
    var topKeyword by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            if (it.contains("作品已提交") || it.contains("AI初审") || it.contains("作品已更新")) {
                tab = "mine"
                vm.loadMine()
            }
            vm.clearMessage()
        }
    }

    LaunchedEffect(session.isAdmin, session.isLoggedIn) {
        if (session.isAdmin) {
            tab = "admin"
            vm.loadAdmin()
        } else if (!session.isLoggedIn && tab == "admin") {
            tab = "home"
        }
    }

    LaunchedEffect(session.isLoggedIn, session.isAdmin, session.userId) {
        if (session.isLoggedIn && !session.isAdmin) {
            vm.loadMine()
        }
    }

    BackHandler(enabled = !session.isAdmin && tab != "home") {
        tab = "home"
        vm.closeWorkDetail()
        vm.closePublicProfile()
        vm.refreshHome()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AcBg,
            topBar = {
                AppTopBar(
                    title = if (session.isAdmin) "后台管理系统" else session.nickname ?: "游客",
                    loading = state.loading,
                    isAdmin = session.isAdmin,
                    keyword = topKeyword,
                    onKeywordChange = { topKeyword = it },
                    onSearch = {
                        tab = "home"
                        vm.closeWorkDetail()
                        vm.refreshHome(topKeyword)
                    },
                )
            },
            snackbarHost = {},
            floatingActionButton = {
                if (!session.isAdmin && tab != "publish" && tab != "mine" && state.currentWork == null) {
                    FloatingActionButton(
                        onClick = {
                            tab = if (session.isLoggedIn) "publish" else "mine"
                        },
                        containerColor = AcPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                    if (session.isAdmin) {
                        NavItem("管理后台", "admin", "admin", "🛠") { tab = "admin"; vm.loadAdmin() }
                        NavItem("退出登录", "logout", tab, "↩") { vm.logout(); tab = "home" }
                    } else {
                        NavItem("首页", "home", tab, "🏠") { tab = "home"; vm.closeWorkDetail(); vm.closePublicProfile(); vm.refreshHome() }
                        NavItem("热门", "hot", tab, "🔥") { tab = "hot"; vm.closeWorkDetail(); vm.closePublicProfile(); vm.refreshHome(hot = true) }
                        NavItem("资讯", "news", tab, "📰") { tab = "news"; vm.closeWorkDetail(); vm.closePublicProfile(); vm.refreshHome(category = "资讯") }
                        NavItem("收藏", "favorites", tab, "⭐") {
                            tab = "favorites"
                            vm.closeWorkDetail()
                            if (session.isLoggedIn) vm.loadMine()
                        }
                        NavItem("我的", "mine", tab, "👤") { tab = "mine"; vm.closeWorkDetail(); vm.closePublicProfile(); if (session.isLoggedIn) vm.loadMine() }
                    }
                }
            },
        ) { padding ->
            Surface(Modifier.padding(padding).fillMaxSize(), color = AcBg) {
                when {
                    session.isAdmin -> AdminScreen(state, vm)
                    !session.isLoggedIn -> HomeScreen(state, vm, "全部")
                    tab == "publish" -> PublishScreen(vm)
                    tab == "mine" -> MineScreen(state, vm)
                    tab == "favorites" -> FavoritesScreen(state, vm)
                    tab == "hot" -> HomeScreen(state, vm, "热门")
                    tab == "news" -> HomeScreen(state, vm, "资讯")
                    else -> HomeScreen(state, vm, "全部")
                }
            }
        }
        if (!session.isLoggedIn) {
            LoginFloatingDialog(vm)
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val pullState = rememberPullRefreshState(refreshing, onRefresh)
    Box(Modifier.fillMaxSize().pullRefresh(pullState)) {
        content()
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = Color.White,
            contentColor = AcPrimary,
        )
    }
}

@Composable
private fun RowScope.NavItem(label: String, route: String, current: String, icon: String, onClick: () -> Unit) {
    val selected = current == route
    Column(
        modifier = Modifier
            .weight(1f)
            .height(62.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) AcPrimary else AcText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    loading: Boolean,
    isAdmin: Boolean,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    TopAppBar(
        title = {
            if (isAdmin) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("αcFun", color = AcPrimary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (loading) "加载中" else title,
                        style = MaterialTheme.typography.bodySmall,
                        color = AcMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("αcFun", color = AcPrimary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    BasicTextField(
                        value = keyword,
                        onValueChange = onKeywordChange,
                        singleLine = true,
                        textStyle = TextStyle(color = AcText, fontWeight = FontWeight.Normal, fontSize = 14.sp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(AcBorder)
                            .clickable(onClick = onSearch)
                            .padding(horizontal = 14.dp),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔍", fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.weight(1f)) {
                                    if (keyword.isBlank()) {
                                        Text(
                                            if (loading) "正在加载内容..." else "搜索感兴趣的内容...",
                                            color = AcMuted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                                TextButton(
                                    onClick = onSearch,
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("搜索", color = AcPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                    HeaderAvatar(title)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = AcText),
    )
}

@Composable
private fun LoginScreen(vm: PlatformViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("login_cache", android.content.Context.MODE_PRIVATE) }
    var entrance by remember { mutableStateOf("user") }
    var mode by remember { mutableStateOf("login_pwd") }
    var loginAccount by remember { mutableStateOf(prefs.getString("user_account", prefs.getString("account", "")) ?: "") }
    var adminAccount by remember { mutableStateOf(prefs.getString("admin_account", "") ?: "") }
    var otpAccount by remember { mutableStateOf("") }
    var registerAccount by remember { mutableStateOf("") }
    var resetAccount by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf(prefs.getString("user_password", prefs.getString("password", "")) ?: "") }
    var adminPassword by remember { mutableStateOf(prefs.getString("admin_password", "") ?: "") }
    var registerPassword by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf(generateCaptcha()) }
    var captchaInput by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var userRememberPassword by remember { mutableStateOf(prefs.getBoolean("user_remember", prefs.getBoolean("remember", false))) }
    var adminRememberPassword by remember { mutableStateOf(prefs.getBoolean("admin_remember", false)) }

    fun refreshCaptcha() {
        captcha = generateCaptcha()
        captchaInput = ""
    }

    fun captchaPassed(): Boolean {
        if (captchaInput.trim().uppercase() == captcha) return true
        android.widget.Toast.makeText(context, "图形验证码错误，请重试", android.widget.Toast.LENGTH_SHORT).show()
        refreshCaptcha()
        return false
    }
    fun cacheLogin(prefix: String, remember: Boolean, account: String, password: String) {
        prefs.edit().apply {
            putBoolean("${prefix}_remember", remember)
            if (remember) {
                putString("${prefix}_account", account)
                putString("${prefix}_password", password)
            } else {
                remove("${prefix}_account")
                remove("${prefix}_password")
            }
        }.apply()
    }
    
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            val title = when(mode) {
                "admin_pwd" -> "管理员登录"
                "register" -> "注册账号"
                "forgot" -> "重置密码"
                "login_otp" -> "验证码登录"
                else -> "密码登录"
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AcText)
            Text("与 Web 端数据同步共享", color = AcMuted)
        }
        item {
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SmallTab("普通用户", entrance == "user", Modifier.weight(1f)) {
                        entrance = "user"
                        mode = "login_pwd"
                    }
                    SmallTab("管理员", entrance == "admin", Modifier.weight(1f)) {
                        entrance = "admin"
                        mode = "admin_pwd"
                    }
                }
                
                if (mode == "admin_pwd") {
                    AppTextField(adminAccount, { adminAccount = it }, "手机号或邮箱")
                    AppTextField(adminPassword, { adminPassword = it }, "管理员密码", password = true)
                    CaptchaRow(captcha, captchaInput, { captchaInput = it }, ::refreshCaptcha)
                    RememberLoginRow(adminRememberPassword) { adminRememberPassword = it }
                    PrimaryButton("进入管理后台", Modifier.fillMaxWidth()) {
                        if (!captchaPassed()) return@PrimaryButton
                        cacheLogin("admin", adminRememberPassword, adminAccount, adminPassword)
                        vm.loginAdmin(adminAccount, adminPassword)
                    }
                } else if (mode == "login_pwd") {
                    AppTextField(loginAccount, { loginAccount = it }, "手机号或邮箱")
                    AppTextField(loginPassword, { loginPassword = it }, "密码", password = true)
                    CaptchaRow(captcha, captchaInput, { captchaInput = it }, ::refreshCaptcha)
                    RememberLoginRow(userRememberPassword) { userRememberPassword = it }
                    PrimaryButton("登录", Modifier.fillMaxWidth()) {
                        if (!captchaPassed()) return@PrimaryButton
                        cacheLogin("user", userRememberPassword, loginAccount, loginPassword)
                        vm.login(loginAccount, loginPassword)
                    }
                } else if (mode == "login_otp") {
                    AppTextField(otpAccount, { otpAccount = it }, "手机号或邮箱")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                        SecondaryButton("获取验证码", Modifier.width(120.dp)) { vm.sendCode(otpAccount, "login") }
                    }
                    PrimaryButton("登录", Modifier.fillMaxWidth()) { vm.loginWithCode(otpAccount, code) }
                } else if (mode == "register") {
                    AppTextField(registerAccount, { registerAccount = it }, "手机号或邮箱")
                    AppTextField(registerPassword, { registerPassword = it }, "设置密码", password = true)
                    AppTextField(nickname, { nickname = it }, "昵称")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                        SecondaryButton("获取验证码", Modifier.width(120.dp)) { vm.sendCode(registerAccount, "register") }
                    }
                    PrimaryButton("立即注册", Modifier.fillMaxWidth()) { vm.register(registerAccount, registerPassword, code, nickname) }
                } else if (mode == "forgot") {
                    AppTextField(resetAccount, { resetAccount = it }, "手机号或邮箱")
                    AppTextField(resetPassword, { resetPassword = it }, "新密码", password = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                        SecondaryButton("获取验证码", Modifier.width(120.dp)) { vm.sendCode(resetAccount, "reset") }
                    }
                    PrimaryButton("重置密码", Modifier.fillMaxWidth()) { vm.resetPassword(resetAccount, code, resetPassword) }
                }

                if (entrance == "user") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { mode = if (mode == "login_pwd") "login_otp" else "login_pwd" }) {
                            Text(if (mode == "login_pwd") "验证码登录" else "密码登录")
                        }
                        TextButton(onClick = { mode = if (mode == "register") "login_pwd" else "register" }) {
                            Text(if (mode == "register") "返回登录" else "注册账号")
                        }
                    }
                    if (mode == "login_pwd") {
                        TextButton(onClick = { mode = "forgot" }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("忘记密码？", color = AcMuted)
                        }
                    }
                } else {
                    Text("管理员入口仅用于内容审核、举报处理、用户管理和数据统计。", color = AcMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LoginFloatingDialog(vm: PlatformViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("login_cache", android.content.Context.MODE_PRIVATE) }
    var entrance by remember { mutableStateOf("user") }
    var mode by remember { mutableStateOf("pwd") }
    var loginAccount by remember { mutableStateOf(prefs.getString("user_account", prefs.getString("account", "")) ?: "") }
    var adminAccount by remember { mutableStateOf(prefs.getString("admin_account", "") ?: "") }
    var otpAccount by remember { mutableStateOf("") }
    var registerAccount by remember { mutableStateOf("") }
    var resetAccount by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf(prefs.getString("user_password", prefs.getString("password", "")) ?: "") }
    var adminPassword by remember { mutableStateOf(prefs.getString("admin_password", "") ?: "") }
    var registerPassword by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf(generateCaptcha()) }
    var captchaInput by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var userRememberPassword by remember { mutableStateOf(prefs.getBoolean("user_remember", prefs.getBoolean("remember", false))) }
    var adminRememberPassword by remember { mutableStateOf(prefs.getBoolean("admin_remember", false)) }

    fun refreshCaptcha() {
        captcha = generateCaptcha()
        captchaInput = ""
    }

    fun captchaPassed(): Boolean {
        if (captchaInput.trim().uppercase() == captcha) return true
        android.widget.Toast.makeText(context, "图形验证码错误，请重试", android.widget.Toast.LENGTH_SHORT).show()
        refreshCaptcha()
        return false
    }

    fun cacheLogin(prefix: String, remember: Boolean, account: String, password: String) {
        prefs.edit().apply {
            putBoolean("${prefix}_remember", remember)
            if (remember) {
                putString("${prefix}_account", account)
                putString("${prefix}_password", password)
            } else {
                remove("${prefix}_account")
                remove("${prefix}_password")
            }
        }.apply()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        when (mode) {
                            "register" -> "注册新账号"
                            "forgot" -> "重置密码"
                            else -> "欢迎来到 αcFun"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = AcText,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SmallTab("普通用户", entrance == "user", Modifier.weight(1f)) {
                            entrance = "user"
                            if (mode == "admin") mode = "pwd"
                        }
                        SmallTab("管理员入口", entrance == "admin", Modifier.weight(1f)) {
                            entrance = "admin"
                            mode = "admin"
                        }
                    }
                }
                if (mode != "register" && mode != "forgot" && entrance == "user") {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            LoginModeTab("密码登录", mode == "pwd", Modifier.weight(1f)) { mode = "pwd" }
                            LoginModeTab("验证码登录", mode == "otp", Modifier.weight(1f)) { mode = "otp" }
                        }
                    }
                }

                when (mode) {
                    "otp" -> {
                        item { AppTextField(otpAccount, { otpAccount = it }, "手机号/邮箱") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppTextField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                                SecondaryButton("获取", Modifier.width(82.dp)) { vm.sendCode(otpAccount, "login") }
                            }
                        }
                        item { PrimaryButton("登录", Modifier.fillMaxWidth()) { vm.loginWithCode(otpAccount, code) } }
                    }
                    "register" -> {
                        item { AppTextField(registerAccount, { registerAccount = it }, "手机号/邮箱") }
                        item { AppTextField(nickname, { nickname = it }, "昵称") }
                        item { AppTextField(code, { code = it }, "验证码") }
                        item { AppTextField(registerPassword, { registerPassword = it }, "密码", password = true) }
                        item { SecondaryButton("获取验证码", Modifier.fillMaxWidth()) { vm.sendCode(registerAccount, "register") } }
                        item { PrimaryButton("注册并登录", Modifier.fillMaxWidth()) { vm.register(registerAccount, registerPassword, code, nickname) } }
                    }
                    "forgot" -> {
                        item { AppTextField(resetAccount, { resetAccount = it }, "手机号/邮箱") }
                        item { AppTextField(code, { code = it }, "验证码") }
                        item { AppTextField(resetPassword, { resetPassword = it }, "新密码", password = true) }
                        item { SecondaryButton("获取验证码", Modifier.fillMaxWidth()) { vm.sendCode(resetAccount, "reset") } }
                        item { PrimaryButton("重置密码", Modifier.fillMaxWidth()) { vm.resetPassword(resetAccount, code, resetPassword) } }
                    }
                    "admin" -> {
                        item { AppTextField(adminAccount, { adminAccount = it }, "手机号/邮箱") }
                        item { AppTextField(adminPassword, { adminPassword = it }, "管理员密码", password = true) }
                        item { CaptchaRow(captcha, captchaInput, { captchaInput = it }, ::refreshCaptcha) }
                        item { RememberLoginRow(adminRememberPassword) { adminRememberPassword = it } }
                        item {
                            PrimaryButton("进入管理后台", Modifier.fillMaxWidth()) {
                                if (!captchaPassed()) return@PrimaryButton
                                cacheLogin("admin", adminRememberPassword, adminAccount, adminPassword)
                                vm.loginAdmin(adminAccount, adminPassword)
                            }
                        }
                    }
                    else -> {
                        item { AppTextField(loginAccount, { loginAccount = it }, "手机号/邮箱") }
                        item { AppTextField(loginPassword, { loginPassword = it }, "密码", password = true) }
                        item { CaptchaRow(captcha, captchaInput, { captchaInput = it }, ::refreshCaptcha) }
                        item { RememberLoginRow(userRememberPassword) { userRememberPassword = it } }
                        item {
                            PrimaryButton("登录", Modifier.fillMaxWidth()) {
                                if (!captchaPassed()) return@PrimaryButton
                                cacheLogin("user", userRememberPassword, loginAccount, loginPassword)
                                vm.login(loginAccount, loginPassword)
                            }
                        }
                    }
                }

                if (mode != "register") {
                    item { SecondaryButton("注册账号", Modifier.fillMaxWidth()) { entrance = "user"; mode = "register" } }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { entrance = "user"; mode = if (mode == "forgot") "pwd" else "forgot" }) {
                            Text(if (mode == "forgot") "返回登录" else "忘记密码？", color = AcPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptchaRow(
    captcha: String,
    value: String,
    onValueChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(108.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF3F6F8))
                .border(1.dp, AcBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                captcha,
                color = AcText,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontSize = 18.sp
            )
        }
        AppTextField(value, onValueChange, "图形验证码", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LoginModeTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = if (selected) AcPrimary else AcMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(56.dp)
                .height(2.dp)
                .background(if (selected) AcPrimary else Color.Transparent)
        )
    }
}

@Composable
private fun RememberLoginRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text("保存用户名和密码", color = AcMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeScreen(state: PlatformUiState, vm: PlatformViewModel, initialCategory: String) {
    val session by vm.session.collectAsState()
    var keyword by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(initialCategory) }
    var detailId by remember { mutableStateOf<Int?>(null) }
    var reportTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(initialCategory) {
        selectedCat = initialCategory
        vm.refreshHome(
            keyword = keyword,
            category = if (initialCategory == "全部" || initialCategory == "热门") null else initialCategory,
            hot = initialCategory == "热门"
        )
        vm.loadTrending()
    }

    BackHandler(enabled = reportTarget != null) { reportTarget = null }
    BackHandler(enabled = state.publicProfile != null) { vm.closePublicProfile() }
    BackHandler(enabled = detailId != null) {
        detailId = null
        vm.closeWorkDetail()
    }
    reportTarget?.let { (targetType, targetId) ->
        ReportDialog(
            title = "举报图文内容",
            onSubmit = { reason, description ->
                vm.report(targetType, targetId, reason, description)
                reportTarget = null
            },
            onCancel = { reportTarget = null }
        )
    }

    RefreshBox(
        refreshing = state.loading,
        onRefresh = {
            vm.refreshHome(keyword, if (selectedCat == "全部" || selectedCat == "热门") null else selectedCat, hot = selectedCat == "热门")
            vm.loadTrending()
        }
    ) {
    if (state.publicProfile != null) {
        PublicProfileScreen(state, vm) { work ->
            detailId = work.id
            vm.openWork(work.id)
        }
    } else if (detailId != null) {
        LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                WorkDetail(state, vm, detailId!!) {
                    detailId = null
                    vm.closeWorkDetail()
                }
            }
        }
    } else {
    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (initialCategory == "热门" && state.trendingTopics.isNotEmpty()) {
            item {
                Text("正在流行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.trendingTopics) { topic ->
                        AppCard(Modifier.width(160.dp).clickable { 
                            selectedCat = topic.category
                            vm.refreshHome(category = topic.category) 
                        }) {
                            Text("#${topic.topic}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${topic.category} · ${topic.postCount}贴", style = MaterialTheme.typography.bodySmall, color = AcMuted)
                        }
                    }
                }
            }
        }

        items(state.works) { work ->
            WorkCard(work, onOpen = {
                detailId = work.id
                vm.openWork(work.id)
            }, onAuthorOpen = {
                vm.openPublicProfile(work.authorId)
            }, onComment = {
                detailId = work.id
                vm.openWork(work.id)
            }, onReport = {
                reportTarget = "work" to work.id
            }, vm = vm, currentUserId = session.userId ?: state.profile?.id, followedAuthorIds = state.followedAuthorIds)
        }
    }
    }
    }
}

@Composable
private fun FavoritesScreen(state: PlatformUiState, vm: PlatformViewModel) {
    var detailId by remember { mutableStateOf<Int?>(null) }
    var reportTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(Unit) { vm.loadMine() }
    BackHandler(enabled = reportTarget != null) { reportTarget = null }
    BackHandler(enabled = detailId != null) {
        detailId = null
        vm.closeWorkDetail()
    }
    reportTarget?.let { (targetType, targetId) ->
        ReportDialog(
            title = "举报图文内容",
            onSubmit = { reason, description ->
                vm.report(targetType, targetId, reason, description)
                reportTarget = null
            },
            onCancel = { reportTarget = null }
        )
    }
    RefreshBox(refreshing = state.loading, onRefresh = { vm.loadMine() }) {
        if (detailId != null) {
            LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    WorkDetail(state, vm, detailId!!) {
                        detailId = null
                        vm.closeWorkDetail()
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SectionTitle("我的收藏") }
                if (state.favorites.isEmpty()) {
                    item { EmptyAdminCard("暂无收藏") }
                }
                items(state.favorites) { work ->
                    WorkCard(
                        work = work,
                        onOpen = {
                            detailId = work.id
                            vm.openWork(work.id)
                        },
                        onAuthorOpen = { vm.openPublicProfile(work.authorId) },
                        onComment = {
                            detailId = work.id
                            vm.openWork(work.id)
                        },
                        onReport = { reportTarget = "work" to work.id },
                        vm = vm,
                        currentUserId = state.profile?.id,
                        followedAuthorIds = state.followedAuthorIds
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicProfileScreen(state: PlatformUiState, vm: PlatformViewModel, onOpenWork: (WorkDto) -> Unit) {
    val profile = state.publicProfile ?: return
    val selected = state.publicCollection
    val visibleWorks = selected?.items?.mapNotNull { it.work } ?: state.publicWorks
    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(profile.nickname)
                        Column {
                            Text(profile.nickname, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text("ID: U${profile.id} · ${profile.creatorLevel}", color = AcMuted, style = MaterialTheme.typography.bodySmall)
                            Text(profile.bio ?: "这个用户还没有填写简介", color = AcMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = { vm.closePublicProfile() }) { Text("返回") }
                }
            }
        }
        if (state.publicCollections.isNotEmpty()) {
            item {
                AppCard {
                    Text("合集和系列", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            CompactPill("全部动态", selected = selected == null) {
                                vm.openPublicProfile(profile.id)
                            }
                        }
                        items(state.publicCollections) { collection ->
                            CompactPill("${collection.title} (${collection.items.size})", selected = selected?.id == collection.id) {
                                vm.openPublicCollection(collection.id)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle(selected?.title ?: "公开动态") }
        if (visibleWorks.isEmpty()) {
            item { EmptyAdminCard("暂无公开内容") }
        }
        items(visibleWorks) { work ->
            WorkCard(work, onOpen = { onOpenWork(work) }, vm = vm, currentUserId = state.profile?.id, followedAuthorIds = state.followedAuthorIds)
        }
    }
}

@Composable
private fun WorkDetail(state: PlatformUiState, vm: PlatformViewModel, id: Int, showComments: Boolean = true, onClose: () -> Unit) {
    val work = state.currentWork ?: return
    var comment by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val showSummary = !work.summary.isNullOrBlank() && work.summary.trim() != work.title.trim()
    BackHandler(enabled = reportTarget != null) { reportTarget = null }
    reportTarget?.let { (targetType, targetId) ->
        ReportDialog(
            title = if (targetType == "work") "举报图文内容" else "举报评论",
            onSubmit = { reason, description ->
                vm.report(targetType, targetId, reason, description)
                reportTarget = null
            },
            onCancel = { reportTarget = null }
        )
    }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable { vm.openPublicProfile(work.authorId) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Avatar(work.author?.nickname ?: "用户")
                Column {
                    Text(work.author?.nickname ?: "用户", fontWeight = FontWeight.Bold, color = AcPrimary)
                    Text(work.createdAt.replace("T", " "), color = AcMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            IconButton(onClick = onClose) { Text("✕", fontWeight = FontWeight.Bold) }
        }
        Text(work.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("#${work.category} · ${StatusText[work.status] ?: work.status}", color = AcMuted)
        if (state.currentWorkCollections.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("所属合集", color = AcMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                items(state.currentWorkCollections) { collection ->
                    CompactPill(collection.title, selected = false) {
                        vm.openPublicCollection(collection.id)
                    }
                }
            }
        }
        if (showSummary) Text(work.summary ?: "", color = AcText)
        WorkImage(work.coverImage)
        
        HorizontalDivider(color = AcBorder)
        PostActionRow(
            commentCount = work.commentCount,
            likeCount = work.likeCount,
            favoriteCount = work.favoriteCount,
            onComment = { },
            onLike = { vm.like(id) },
            onFavorite = { vm.favorite(id) },
            onReport = { reportTarget = "work" to id },
        )

        if (showComments) {
            HorizontalDivider(color = AcBorder)
            Text("评论 (${state.comments.size})", fontWeight = FontWeight.Bold)
            AppTextField(comment, { comment = it }, "发表你的看法...")
            PrimaryButton("发送评论", Modifier.fillMaxWidth()) {
                if (comment.isNotBlank()) {
                    vm.comment(id, comment)
                    comment = ""
                }
            }

            state.comments.forEach { c ->
                CommentItem(c, vm, id) { targetType, targetId ->
                    reportTarget = targetType to targetId
                }
            }
        }
    }
}

@Composable
private fun CommentItem(c: CommentDto, vm: PlatformViewModel, workId: Int, onReport: (String, Int) -> Unit) {
    var replying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                "${c.user?.nickname ?: "用户"}  ",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = AcText,
            )
            Text(
                c.content,
                color = AcText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                "回复",
                color = AcPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { replying = !replying }
            )
            Text(
                "举报",
                color = AcDanger,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onReport("comment", c.id) }
            )
        }
        
        if (c.replies.isNotEmpty()) {
            Row(Modifier.padding(start = 26.dp, top = 8.dp)) {
                Box(
                    Modifier
                        .width(2.dp)
                        .heightIn(min = (34 * c.replies.size).dp)
                        .background(AcBorder)
                )
                Column(
                    Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    c.replies.forEach { r ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                "${r.user?.nickname ?: "用户"}: ",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = AcText,
                            )
                            Text(
                                r.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = AcText,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "举报",
                                style = MaterialTheme.typography.labelSmall,
                                color = AcDanger,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable { onReport("comment", r.id) }
                            )
                        }
                    }
                }
            }
        }
        
        if (replying) {
            Row(Modifier.padding(start = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppTextField(replyText, { replyText = it }, "回复 @${c.user?.nickname}", modifier = Modifier.weight(1f))
                TextButton(onClick = { 
                    if (replyText.isNotBlank()) {
                        vm.comment(workId, replyText, c.id)
                        replyText = ""
                        replying = false
                    }
                }) { Text("发送") }
            }
        }
        HorizontalDivider(color = AcBorder, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ReportPanel(title: String, onSubmit: (String, String?) -> Unit, onCancel: () -> Unit) {
    var reason by remember { mutableStateOf("垃圾广告") }
    var description by remember { mutableStateOf("") }
    val reasons = listOf("垃圾广告", "违法违规", "涉黄涉暴", "人身攻击", "其他")
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AcBg)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reasons) { item ->
                    SmallTab(item, reason == item) { reason = item }
                }
            }
            AppTextField(description, { description = it }, "详细描述（选填）", minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("提交举报", Modifier.weight(1f)) { onSubmit(reason, description.ifBlank { null }) }
                SecondaryButton("取消", Modifier.weight(1f), onCancel)
            }
        }
    }
}

@Composable
private fun ReportDialog(title: String, onSubmit: (String, String?) -> Unit, onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onCancel() }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {}
            ) {
                ReportPanel(title, onSubmit, onCancel)
            }
        }
    }
}

@Composable
private fun PublishScreen(vm: PlatformViewModel) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("#生活分享") }
    var content by remember { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        imageUris = (imageUris + uris).distinct().take(4)
    }
    
    val categories = listOf("#生活分享", "#摄影打卡", "#前端开发", "#运动健康", "#AI人工智能")

    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("发布新内容", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("分享你的快乐生活", color = AcMuted)
        }
        item {
            AppCard {
                Text("选择话题", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        SmallTab(cat, category == cat) { category = cat }
                    }
                }
                AppTextField(content, { content = it }, "分享你的想法...", minLines = 6)
                SelectedImageGrid(
                    imageUris = imageUris,
                    onRemove = { uri -> imageUris = imageUris.filterNot { it == uri } }
                )
                SecondaryButton(
                    if (imageUris.isEmpty()) "添加图片（最多4张）" else "继续添加（${imageUris.size}/4）",
                    Modifier.fillMaxWidth()
                ) {
                    if (imageUris.size < 4) imagePicker.launch("image/*")
                }
                
                Spacer(Modifier.height(8.dp))
                PrimaryButton("立即发布", Modifier.fillMaxWidth()) {
                    vm.publishWithImages(context, imageUris, null, content, category, content)
                    content = ""
                    imageUris = emptyList()
                }
            }
        }
    }
}

@Composable
private fun MineScreen(state: PlatformUiState, vm: PlatformViewModel) {
    var section by remember { mutableStateOf("menu") }
    val menuItems = listOf(
        "info" to "个人信息",
        "works" to "个人动态",
        "chapters" to "合集和系列",
        "follows" to "我的关注",
        "comments" to "评论管理",
        "logout" to "退出登录",
        "delete" to "账户注销",
    )
    BackHandler(enabled = section != "menu") {
        section = "menu"
    }
    BackHandler(enabled = state.publicProfile != null) {
        vm.closePublicProfile()
    }
    if (state.publicProfile != null) {
        PublicProfileScreen(state, vm) { work ->
            vm.openWork(work.id)
        }
        return
    }
    RefreshBox(refreshing = state.loading, onRefresh = { vm.loadMine() }) {
    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (section != "menu") {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { section = "menu" }) { Text("返回") }
                }
            }
        }
        when (section) {
            "menu" -> item {
                ProfileHeader(state.profile)
                Spacer(Modifier.height(10.dp))
                AppCard {
                    menuItems.forEach { (key, label) ->
                        MineMenuItem(label, selected = false, danger = key == "logout" || key == "delete") {
                            if (key == "logout") {
                                vm.logout()
                            } else {
                                section = key
                            }
                        }
                    }
                }
            }
            "info" -> item { ProfileEditor(state.profile, vm) }
            "works" -> {
                item { SectionTitle("个人动态") }
                if (state.myWorks.isEmpty()) item { EmptyAdminCard("暂无发布内容") }
                items(state.myWorks) { MyWorkCard(it, vm) }
            }
            "chapters" -> item { ChapterManager(state, vm) }
            "comments" -> item { CommentManager(state, vm) }
            "follows" -> {
                item { SectionTitle("我的关注") }
                if (state.follows.isEmpty()) item { EmptyAdminCard("暂无关注用户") }
                items(state.follows) { FollowCard(it, vm) }
            }
            "delete" -> item { DeleteAccountPanel(vm) }
        }
    }
    }
}

@Composable
private fun ProfileHeader(user: UserDto?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF8A3D)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (user?.nickname ?: "user").take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Text(user?.nickname ?: "user", color = AcText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text("ID: U${user?.id ?: 0}", color = AcMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MineMenuItem(label: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFE8F5FD) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = when {
                danger -> AcDanger
                selected -> AcPrimary
                else -> AcText
            },
            fontWeight = FontWeight.Bold
        )
        if (!danger) {
            Text(">", color = Color(0xFFB8C4CC), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileEditor(user: UserDto?, vm: PlatformViewModel) {
    var nickname by remember(user?.id) { mutableStateOf(user?.nickname ?: "") }
    var contact by remember(user?.id) { mutableStateOf(user?.contact ?: user?.account ?: "") }
    var bio by remember(user?.id) { mutableStateOf(user?.bio ?: "") }
    AppCard {
        SectionTitle("个人信息")
        HorizontalDivider(color = AcBorder)
        ReadOnlyInfoBox("用户ID", user?.id?.toString() ?: "")
        AppTextField(contact, { contact = it }, "联系方式")
        AppTextField(nickname, { nickname = it }, "昵称")
        AppTextField(bio, { bio = it }, "个人简介", minLines = 3)
        ReadOnlyInfoBox("创作等级", "${user?.creatorLevel ?: "Lv1"} / 热度 ${user?.heatScore ?: 0.0}")
        ReadOnlyInfoBox("注册时间", user?.createdAt?.replace("T", " ") ?: "")
        PrimaryButton("保存修改", Modifier.width(120.dp)) { vm.saveProfile(nickname, contact, bio) }
    }
}

@Composable
private fun ReadOnlyInfoBox(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = AcMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFCFD9DE), RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(value.ifBlank { "未填写" }, color = AcMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeleteAccountPanel(vm: PlatformViewModel) {
    var confirm by remember { mutableStateOf(false) }
    AppCard {
        SectionTitle("账户注销")
        Text("注销后当前账号资料将被删除，无法继续使用该账号登录。", color = AcMuted)
        if (confirm) {
            Text("请再次确认是否注销账号。", color = AcDanger, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DangerButton("确认注销", Modifier.weight(1f)) { vm.deleteAccount() }
                SecondaryButton("取消", Modifier.weight(1f)) { confirm = false }
            }
        } else {
            DangerButton("注销账号", Modifier.fillMaxWidth()) { confirm = true }
        }
    }
}

@Composable
private fun MyWorkCard(work: WorkDto, vm: PlatformViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var title by remember(work.id, editing) { mutableStateOf(work.title) }
    var summary by remember(work.id, editing) { mutableStateOf(work.summary ?: "") }
    var category by remember(work.id, editing) { mutableStateOf(work.category) }
    var imageUri by remember(work.id, editing) { mutableStateOf<Uri?>(null) }
    var removeImage by remember(work.id, editing) { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri
            removeImage = false
        }
    }
    AppCard {
        Text("#${work.category} · ${StatusText[work.status] ?: work.status}", color = AcMuted)
        if (work.status == "ai_approved") {
            Text("已临时发布，等待管理员终审", color = AcMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (work.status == "rejected" && !work.reviewNote.isNullOrBlank()) {
            Text("驳回原因：${work.reviewNote}", color = AcDanger, style = MaterialTheme.typography.bodySmall)
        }
        if (editing) {
            AppTextField(title, { title = it }, "标题")
            AppTextField(summary, { summary = it }, "正文/摘要", minLines = 4)
            AppTextField(category, { category = it }, "分类")
            WorkImage(
                when {
                    imageUri != null -> imageUri.toString()
                    removeImage -> null
                    else -> work.coverImage
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(if (imageUri == null && work.coverImage.isNullOrBlank()) "添加图片" else "更换图片", Modifier.weight(1f)) {
                    imagePicker.launch("image/*")
                }
                if (imageUri != null || !work.coverImage.isNullOrBlank()) {
                    SecondaryButton("移除图片", Modifier.weight(1f)) {
                        imageUri = null
                        removeImage = true
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("保存", Modifier.weight(1f)) {
                    vm.updateWorkWithImage(context, work.id, title, summary, category, imageUri, removeImage)
                    editing = false
                }
                SecondaryButton("取消", Modifier.weight(1f)) { editing = false }
            }
        } else {
            Text(work.title, fontWeight = FontWeight.Bold)
            Text(work.summary ?: "", color = AcText)
            WorkImage(work.coverImage)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("编辑", Modifier.weight(1f)) { editing = true }
                DangerButton("删除", Modifier.weight(1f)) { vm.deleteWork(work.id) }
            }
        }
    }
}

@Composable
private fun ChapterManager(state: PlatformUiState, vm: PlatformViewModel) {
    var selectedCollectionId by remember { mutableStateOf<Int?>(null) }
    var creating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    LaunchedEffect(state.collections) {
        if (selectedCollectionId == null && state.collections.isNotEmpty()) {
            selectedCollectionId = state.collections.first().id
        } else if (selectedCollectionId != null && state.collections.none { it.id == selectedCollectionId }) {
            selectedCollectionId = state.collections.firstOrNull()?.id
        }
    }
    val selected = state.collections.firstOrNull { it.id == selectedCollectionId }
    val selectedWorkIds = selected?.items?.map { it.workId }?.toSet().orEmpty()
    state.currentWork?.let { work ->
        Dialog(
            onDismissRequest = { vm.closeWorkDetail() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { vm.closeWorkDetail() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .clickable {},
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { WorkDetail(state, vm, work.id, showComments = false) { vm.closeWorkDetail() } }
                }
            }
        }
    }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("合集和系列", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("自建主题，把已有动态按顺序放进合集。", color = AcMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { creating = !creating }) { Text(if (creating) "收起" else "新建") }
        }
        if (creating) {
            AppTextField(title, { title = it }, "合集标题")
            AppTextField(description, { description = it }, "合集简介", minLines = 2)
            PrimaryButton("创建合集", Modifier.fillMaxWidth()) {
                if (title.isNotBlank()) {
                    vm.createCollection(title, description)
                    title = ""
                    description = ""
                    creating = false
                }
            }
        }

        if (state.collections.isEmpty()) {
            Text("暂无合集，点击右上角新建一个主题。", color = AcMuted)
            return@AppCard
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.collections) { collection ->
                Button(
                    onClick = { selectedCollectionId = collection.id },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (collection.id == selectedCollectionId) AcPrimary else Color(0xFFE8F5FD),
                        contentColor = if (collection.id == selectedCollectionId) Color.White else AcPrimary
                    )
                ) {
                    Text("${collection.title} (${collection.items.size})", maxLines = 1)
                }
            }
        }

        selected?.let { collection ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(collection.title, fontWeight = FontWeight.Bold)
                    Text(collection.description ?: "暂无简介", color = AcMuted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.deleteCollection(collection.id) }) { Text("删除合集", color = AcDanger) }
            }
            HorizontalDivider(color = AcBorder)
            Text("合集内容", fontWeight = FontWeight.Bold)
            if (collection.items.isEmpty()) {
                Text("还没有动态，下面选择已有动态加入。", color = AcMuted)
            }
            collection.items.sortedBy { it.sortOrder }.forEachIndexed { index, item ->
                val work = item.work
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AcBg)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}", color = AcPrimary, fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(work?.title ?: "动态 ${item.workId}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(work?.category ?: "", color = AcMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.openWork(item.workId) }) { Text("查看") }
                        TextButton(onClick = { vm.moveCollectionItem(item.id, (item.sortOrder - 1).coerceAtLeast(1)) }) { Text("上移") }
                        TextButton(onClick = { vm.moveCollectionItem(item.id, item.sortOrder + 1) }) { Text("下移") }
                        TextButton(onClick = { vm.removeCollectionItem(item.id) }) { Text("移除", color = AcDanger) }
                    }
                }
            }
            Text("选择已有动态加入", fontWeight = FontWeight.Bold)
            if (state.myWorks.isEmpty()) {
                Text("暂无动态，先发布内容后再加入合集。", color = AcMuted)
            }
            state.myWorks.forEach { work ->
                val added = selectedWorkIds.contains(work.id)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(work.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("#${work.category} · ${StatusText[work.status] ?: work.status}", color = AcMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.openWork(work.id) }) { Text("查看") }
                    SecondaryButton(if (added) "已加入" else "加入", Modifier.width(86.dp)) {
                        if (!added) vm.addWorkToCollection(collection.id, work.id, collection.items.size + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChapterForm(workId: Int, nextSort: Int, vm: PlatformViewModel) {
    var show by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    if (!show) {
        SecondaryButton("新增合集章节", Modifier.fillMaxWidth()) { show = true }
        return
    }
    AppTextField(title, { title = it }, "章节标题")
    AppTextField(content, { content = it }, "章节正文", minLines = 4)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("保存章节", Modifier.weight(1f)) {
            if (title.isNotBlank() && content.isNotBlank()) {
                vm.createChapter(workId, title, content, nextSort)
                title = ""
                content = ""
                show = false
            }
        }
        SecondaryButton("取消", Modifier.weight(1f)) { show = false }
    }
}

@Composable
private fun ChapterEditor(chapter: ChapterDto, workId: Int, vm: PlatformViewModel) {
    var editing by remember { mutableStateOf(false) }
    var title by remember(chapter.id, editing) { mutableStateOf(chapter.title) }
    var content by remember(chapter.id, editing) { mutableStateOf(chapter.content) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = AcBg)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("P${chapter.sortOrder} · ${StatusText[chapter.status] ?: chapter.status}", style = MaterialTheme.typography.labelSmall, color = AcMuted)
            if (editing) {
                AppTextField(title, { title = it }, "章节标题")
                AppTextField(content, { content = it }, "章节正文", minLines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("保存", Modifier.weight(1f)) {
                        vm.updateChapter(chapter.id, workId, title, content, chapter.sortOrder)
                        editing = false
                    }
                    SecondaryButton("取消", Modifier.weight(1f)) { editing = false }
                }
            } else {
                Text(chapter.title, fontWeight = FontWeight.Bold)
                Text(chapter.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { editing = true }) { Text("编辑") }
                    TextButton(onClick = { vm.deleteChapter(chapter.id, workId) }) { Text("删除", color = AcDanger) }
                }
            }
        }
    }
}

@Composable
private fun MyCommentCard(comment: CommentDto, vm: PlatformViewModel) {
    AppCard {
        Text("在作品 #${comment.workId} 下:", style = MaterialTheme.typography.labelSmall, color = AcMuted)
        Text(comment.content)
        TextButton(onClick = { vm.deleteComment(comment.id, null) }, modifier = Modifier.align(Alignment.End)) {
            Text("删除", color = AcDanger)
        }
    }
}

@Composable
private fun CommentManager(state: PlatformUiState, vm: PlatformViewModel) {
    var selectedWork by remember { mutableStateOf<WorkDto?>(null) }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("评论管理")
            selectedWork?.let {
                TextButton(onClick = { selectedWork = null }) { Text("返回列表") }
            }
        }
        HorizontalDivider(color = AcBorder)
        if (selectedWork == null) {
            Text("请选择一个帖子查看其下方评论", color = AcMuted)
            if (state.myWorks.isEmpty()) {
                Text("暂无帖子", color = AcMuted)
                return@AppCard
            }
            state.myWorks.forEach { work ->
                CommentWorkCard(work) {
                    selectedWork = work
                    vm.openWorkCommentsForManage(work.id)
                }
            }
        } else {
            val work = selectedWork!!
            Text(work.title, fontWeight = FontWeight.Bold)
            Text("#${work.category} · ${work.commentCount} 条评论", color = AcMuted, style = MaterialTheme.typography.bodySmall)
            if (state.currentWork?.id != work.id) {
                Text("评论加载中...", color = AcMuted)
            } else if (state.comments.isEmpty()) {
                Text("该帖子暂无评论", color = AcMuted)
            } else {
                state.comments.filter { it.parentId == null }.forEach { comment ->
                    CommentManageItem(comment, vm)
                }
            }
        }
    }
}

@Composable
private fun CommentWorkCard(work: WorkDto, onOpen: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, AcBorder, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(work.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "动态ID ${work.id} · #${work.category} · ${work.commentCount} 条评论",
                    color = AcMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onOpen,
                modifier = Modifier.width(78.dp).height(34.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AcPrimary, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("查看", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CommentManageItem(comment: CommentDto, vm: PlatformViewModel, parentName: String? = null) {
    val author = userDisplayName(comment.user, comment.userId)
    val replyTarget = parentName ?: comment.parentUser?.let { userDisplayName(it, comment.parentId ?: 0) }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = if (replyTarget == null) 8.dp else 4.dp, start = if (replyTarget == null) 0.dp else 18.dp)
            .border(1.dp, AcBorder, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = if (replyTarget == null) Color.White else AcBg)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(author, fontWeight = FontWeight.Bold)
                    replyTarget?.let {
                        Text("回复 @$it", color = AcMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = { vm.deleteManagedComment(comment.id, comment.workId) }) {
                    Text("删除", color = AcDanger)
                }
            }
            Text(comment.content, color = AcText)
            Text(comment.createdAt.replace("T", " "), color = AcMuted, style = MaterialTheme.typography.bodySmall)
            comment.replies.forEach { reply ->
                CommentManageItem(reply, vm, parentName = author)
            }
        }
    }
}

@Composable
private fun FollowCard(follow: FollowDto, vm: PlatformViewModel) {
    val user = follow.followed
    val targetId = user?.id ?: follow.followedId
    AppCard(Modifier.clickable { vm.openPublicProfile(targetId) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clickable { vm.openPublicProfile(targetId) }) {
                Avatar(user?.nickname ?: "用")
            }
            Column(Modifier.weight(1f)) {
                Text(user?.nickname ?: "用户${follow.followedId}", fontWeight = FontWeight.Bold, color = AcPrimary)
                Text(user?.bio ?: "暂无简介", style = MaterialTheme.typography.bodySmall, color = AcMuted)
            }
            Button(
                onClick = { vm.followAuthor(targetId) },
                modifier = Modifier.width(112.dp).height(40.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5FD), contentColor = AcPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text("取消关注", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AdminScreen(state: PlatformUiState, vm: PlatformViewModel) {
    var section by remember { mutableStateOf("review") }
    var reportSection by remember { mutableStateOf("work") }
    val reviewCount = state.stats?.pendingWorks ?: state.pendingWorks.size
    val reportCount = state.stats?.pendingReports ?: state.reports.size
    val workReports = state.reports.filter { it.targetType == "work" }
    val commentReports = state.reports.filter { it.targetType == "comment" }
    BackHandler(enabled = section != "review") {
        section = "review"
    }
    RefreshBox(refreshing = state.loading, onRefresh = { vm.loadAdmin() }) {
    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("管理后台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.loadAdmin() }) { Text("刷新") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallTab("内容审核", section == "review", Modifier.weight(1f), badgeCount = reviewCount) { section = "review" }
                SmallTab("举报管理", section == "reports", Modifier.weight(1f), badgeCount = reportCount) { section = "reports" }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallTab("用户管理", section == "users", Modifier.weight(1f)) { section = "users" }
                SmallTab("数据统计", section == "stats", Modifier.weight(1f)) { section = "stats" }
            }
        }
        when (section) {
            "review" -> {
                item { SectionTitle("待审核图文 ($reviewCount)") }
                if (state.pendingWorks.isEmpty()) item { EmptyAdminCard("暂无待审核内容") }
                items(state.pendingWorks) { AdminWorkCard(it, state, vm) }
            }
            "reports" -> {
                item { SectionTitle("举报处理列表 ($reportCount)") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SmallTab("帖子举报", reportSection == "work", Modifier.weight(1f), badgeCount = workReports.size) { reportSection = "work" }
                        SmallTab("评论举报", reportSection == "comment", Modifier.weight(1f), badgeCount = commentReports.size) { reportSection = "comment" }
                    }
                }
                val visibleReports = if (reportSection == "work") workReports else commentReports
                if (visibleReports.isEmpty()) item { EmptyAdminCard(if (reportSection == "work") "暂无帖子举报" else "暂无评论举报") }
                items(visibleReports) { ReportCard(it, state, vm) }
            }
            "users" -> {
                item { SectionTitle("用户列表 (${state.users.size})") }
                if (state.users.isEmpty()) item { EmptyAdminCard("暂无用户数据") }
                items(state.users) { UserCard(it, vm) }
            }
            "stats" -> {
                item { SectionTitle("数据统计面板") }
                item { AdminStatsCard(state.stats) }
            }
        }
    }
    }
}

@Composable
private fun EmptyAdminCard(text: String) {
    AppCard {
        Text(text, color = AcMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun AdminStatsCard(stats: com.platform.android.data.StatsDto?) {
    AppCard {
        if (stats == null) {
            Text("统计数据加载中", color = AcMuted)
            return@AppCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill("今日新增用户", stats.newUsersToday.toString(), Modifier.weight(1f))
            StatPill("今日发布内容", stats.worksToday.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill("待审核内容", stats.pendingWorks.toString(), Modifier.weight(1f))
            StatPill("待处理举报", stats.pendingReports.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill("用户总数", stats.totalUsers.toString(), Modifier.weight(1f))
            StatPill("作品总数", stats.totalWorks.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun WorkCard(
    work: WorkDto,
    onOpen: () -> Unit,
    onAuthorOpen: (() -> Unit)? = null,
    onComment: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    vm: PlatformViewModel? = null,
    currentUserId: Int? = null,
    followedAuthorIds: Set<Int> = emptySet(),
) {
    val summary = work.summary?.trim().orEmpty()
    val showTitle = work.title.trim().isNotBlank() && work.title.trim() != summary
    val isOwnWork = currentUserId != null && work.authorId == currentUserId
    val isFollowed = followedAuthorIds.contains(work.authorId)
    AppCard(Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.clickable(enabled = onAuthorOpen != null) { onAuthorOpen?.invoke() }) {
                Avatar(work.author?.nickname ?: "用户")
            }
            Column(Modifier.weight(1f).clickable(enabled = onAuthorOpen != null) { onAuthorOpen?.invoke() }) {
                Text(work.author?.nickname ?: "用户", fontWeight = FontWeight.Bold, color = if (onAuthorOpen != null) AcPrimary else AcText)
                Text(work.createdAt.replace("T", " "), color = AcMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (vm != null && work.author != null && !isOwnWork) {
                CompactPill(if (isFollowed) "已关注" else "关注", selected = isFollowed) {
                    vm.followAuthor(work.author.id)
                }
            }
        }
        Text("#${work.category} · ${StatusText[work.status] ?: work.status}", color = AcMuted, style = MaterialTheme.typography.bodySmall)
        if (showTitle) Text(work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(summary.ifBlank { work.title.ifBlank { "暂无摘要" } }, color = AcText, maxLines = 3, overflow = TextOverflow.Ellipsis)
        WorkImage(work.coverImage)
        HorizontalDivider(color = AcBorder)
        PostActionRow(
            commentCount = work.commentCount,
            likeCount = work.likeCount,
            favoriteCount = work.favoriteCount,
            onComment = { (onComment ?: onOpen).invoke() },
            onLike = { vm?.like(work.id) },
            onFavorite = { vm?.favorite(work.id) },
            onReport = { onReport?.invoke() },
        )
    }
}

@Composable
private fun AdminWorkCard(work: WorkDto, state: PlatformUiState, vm: PlatformViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val summary = work.summary?.trim().orEmpty()
    val detailWork = state.currentWork?.takeIf { it.id == work.id }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(work.title, fontWeight = FontWeight.Bold)
                Text("${work.author?.nickname ?: "用户"} · #${work.category} · ${StatusText[work.status] ?: work.status}", color = AcMuted, style = MaterialTheme.typography.bodySmall)
                Text("提交审核 ${work.updatedAt.ifBlank { work.createdAt }.replace("T", " ")}", color = AcMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = {
                expanded = !expanded
                if (expanded) vm.openWork(work.id)
            }) { Text(if (expanded) "收起" else "查看") }
        }
        if (expanded) {
            val display = detailWork ?: work
            val displaySummary = display.summary?.trim().orEmpty()
            if (displaySummary.isNotBlank() && displaySummary != display.title.trim()) Text(displaySummary, color = AcText)
            WorkImage(display.coverImage)
            Text("浏览 ${display.viewCount} · 点赞 ${display.likeCount} · 收藏 ${display.favoriteCount} · 评论 ${display.commentCount}", color = AcMuted, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("通过", Modifier.weight(1f)) { vm.reviewWork(work.id, true) }
            DangerButton("拒绝", Modifier.weight(1f)) { vm.reviewWork(work.id, false) }
        }
    }
}

@Composable
private fun ReportCard(report: ReportDto, state: PlatformUiState, vm: PlatformViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val detail = state.reportTargetDetails["${report.targetType}:${report.targetId}"]
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${if (report.targetType == "work") "帖子" else "评论"} #${report.targetId} · ${report.reason}", fontWeight = FontWeight.Bold)
                Text(report.description ?: "无补充说明", color = AcMuted)
            }
            TextButton(onClick = {
                expanded = !expanded
                if (!expanded) return@TextButton
                vm.openReportTarget(report.targetType, report.targetId)
            }) { Text(if (expanded) "收起" else "查看") }
        }
        if (expanded) {
            if (detail != null) {
                Text(detail.work.title, fontWeight = FontWeight.Bold)
                Text(detail.work.summary ?: "", color = AcText, maxLines = 4, overflow = TextOverflow.Ellipsis)
                WorkImage(detail.work.coverImage)
                if (report.targetType == "comment") {
                    Text("全部评论 (${detail.comments.size})", fontWeight = FontWeight.Bold)
                    detail.comments.forEach { comment ->
                        AdminReportCommentItem(comment, report.targetId)
                    }
                }
            } else {
                Text("正在加载举报目标详情...", color = AcMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("采纳", Modifier.weight(1f)) { vm.handleReport(report.id, true) }
            SecondaryButton("驳回", Modifier.weight(1f)) { vm.handleReport(report.id, false) }
        }
    }
}

@Composable
private fun AdminReportCommentItem(comment: CommentDto, reportedCommentId: Int) {
    val isTarget = comment.id == reportedCommentId
    val parentName = comment.parentUser?.let { userDisplayName(it, comment.parentId ?: 0) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = if (comment.parentId == null) 0.dp else 18.dp)
            .background(if (isTarget) Color(0xFFFFF5F8) else AcBg, RoundedCornerShape(8.dp))
            .border(if (isTarget) 2.dp else 1.dp, if (isTarget) Color(0xFFE0245E) else AcBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(userDisplayName(comment.user, comment.userId), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            parentName?.let {
                Text("回复 @$it", color = AcMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (isTarget) Text("被举报", color = AcDanger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
        Text("${comment.createdAt.replace("T", " ")} · ${comment.status}", color = AcMuted, style = MaterialTheme.typography.labelSmall)
        Text(comment.content, color = AcText)
    }
}

private fun userDisplayName(user: UserDto?, fallbackId: Int): String {
    return user?.nickname?.takeIf { it.isNotBlank() }
        ?: user?.account?.takeIf { it.isNotBlank() }
        ?: "用户 $fallbackId"
}

@Composable
private fun UserCard(user: UserDto, vm: PlatformViewModel) {
    var expanded by remember { mutableStateOf(false) }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.nickname, fontWeight = FontWeight.Bold)
                Text("${user.account} · ${user.role} · ${user.status}", color = AcMuted)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "详情")
            }
        }
        if (expanded) {
            InfoRow("用户ID", user.id.toString())
            InfoRow("账号", user.account)
            InfoRow("昵称", user.nickname)
            InfoRow("角色", if (user.role == "admin") "管理员" else "普通用户")
            InfoRow("状态", if (user.status == "active") "正常" else "已禁用")
            InfoRow("联系方式", user.contact ?: "未填写")
            InfoRow("个人简介", user.bio ?: "未填写")
            InfoRow("创作等级", "${user.creatorLevel} / 热度 ${user.heatScore}")
            InfoRow("注册时间", user.createdAt)
            SecondaryButton(if (user.status == "active") "禁用用户" else "恢复用户", Modifier.fillMaxWidth()) {
                vm.setUserStatus(user.id, if (user.status == "active") "disabled" else "active")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AcMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = AcText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFE8F5FD))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = AcPrimary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(label, color = AcMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    minLines: Int = 1,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    EyeLineIcon(hidden = !passwordVisible)
                }
            }
        } else null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AcPrimary,
            unfocusedBorderColor = Color(0xFFCFD9DE),
            focusedLabelColor = AcPrimary,
        )
    )
}

@Composable
private fun EyeLineIcon(hidden: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val eye = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            cubicTo(size.width * 0.28f, size.height * 0.18f, size.width * 0.72f, size.height * 0.18f, size.width * 0.92f, size.height * 0.5f)
            cubicTo(size.width * 0.72f, size.height * 0.82f, size.width * 0.28f, size.height * 0.82f, size.width * 0.08f, size.height * 0.5f)
        }
        drawPath(eye, color = Color.Black, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        drawCircle(
            color = Color.Black,
            radius = size.minDimension * 0.12f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f)
        )
        if (hidden) {
            drawLine(
                color = Color.Black,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.18f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.82f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = AcText, fontWeight = FontWeight.Normal),
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .border(1.dp, AcPrimary, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AcPrimary, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5FD), contentColor = AcPrimary)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5FD), contentColor = AcPrimary),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PostActionRow(
    commentCount: Int,
    likeCount: Int,
    favoriteCount: Int,
    onComment: () -> Unit,
    onLike: () -> Unit,
    onFavorite: () -> Unit,
    onReport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PostActionText("💬 $commentCount", Modifier.weight(1f), onComment)
        PostActionText("👍 $likeCount", Modifier.weight(1f), onLike)
        PostActionText("⭐ ${if (favoriteCount > 0) favoriteCount else "收藏"}", Modifier.weight(1f), onFavorite)
        PostActionText("🚩 举报", Modifier.weight(1f), onReport)
    }
}

@Composable
private fun PostActionText(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        Text(text, color = AcText, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CompactPill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(72.dp).height(40.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AcPrimary else Color(0xFFE8F5FD),
            contentColor = if (selected) Color.White else AcPrimary
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SmallTab(text: String, selected: Boolean, modifier: Modifier = Modifier, badgeCount: Int = 0, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AcPrimary else Color(0xFFE8F5FD),
            contentColor = if (selected) Color.White else AcPrimary
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 42.dp, y = 0.dp)
                        .height(18.dp)
                        .widthIn(min = 18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AcDanger, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AcText, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Avatar(seed: String) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE8F5FD)),
        contentAlignment = Alignment.Center
    ) {
        Text(seed.take(1).ifBlank { "用" }.uppercase(), color = AcPrimary, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun HeaderAvatar(seed: String) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF7A32)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            seed.take(1).ifBlank { "用" }.uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun SelectedImageGrid(imageUris: List<Uri>, onRemove: (Uri) -> Unit) {
    if (imageUris.isEmpty()) return
    ImageGrid(
        urls = imageUris.map { it.toString() },
        action = { index ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000))
                    .clickable { onRemove(imageUris[index]) },
                contentAlignment = Alignment.Center
            ) {
                Text("x", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun WorkImage(url: String?) {
    if (url.isNullOrBlank()) return
    val urls = remember(url) { parseImageUrls(url) }
    if (urls.size > 1) {
        ImageGrid(urls)
        return
    }
    val imageUrl = urls.firstOrNull() ?: return
    val context = LocalContext.current
    val fullUrl = remember(imageUrl) { resolveImageUrl(imageUrl) }
    var previewing by remember { mutableStateOf(false) }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(fullUrl)
            .addHeader("User-Agent", "Mozilla/5.0")
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 230.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(AcBorder)
            .clickable { previewing = true },
        contentScale = ContentScale.Crop,
    )
    if (previewing) {
        FullImageDialog(fullUrl) { previewing = false }
    }
}

@Composable
private fun ImageGrid(urls: List<String>, action: @Composable BoxScope.(Int) -> Unit = {}) {
    val context = LocalContext.current
    var previewUrl by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        urls.take(4).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { rawUrl ->
                    val fullUrl = remember(rawUrl) { resolveImageUrl(rawUrl) }
                    Box(Modifier.weight(1f)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fullUrl)
                                .addHeader("User-Agent", "Mozilla/5.0")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AcBorder)
                                .clickable { previewUrl = fullUrl },
                            contentScale = ContentScale.Crop,
                        )
                        action(urls.indexOf(rawUrl))
                    }
                }
                if (row.size == 1 && urls.size > 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    previewUrl?.let { FullImageDialog(it) { previewUrl = null } }
}

private fun parseImageUrls(value: String): List<String> {
    val cleaned = value.trim()
    if (!cleaned.startsWith("[")) return listOf(cleaned).filter { it.isNotBlank() }
    return runCatching {
        Json.parseToJsonElement(cleaned).jsonArray.mapNotNull { it.jsonPrimitive.content.trim().takeIf(String::isNotBlank) }
    }.getOrElse { listOf(cleaned).filter { it.isNotBlank() } }
}

private fun resolveImageUrl(url: String): String {
    val cleaned = url.trim()
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    return when {
        cleaned.startsWith("http://127.0.0.1") || cleaned.startsWith("http://localhost") -> {
            cleaned.replace(Regex("^http://(127\\.0\\.0\\.1|localhost)(:\\d+)?"), base)
        }
        cleaned.contains("images.unsplash.com") -> {
            val separator = if (cleaned.contains("?")) "&" else "?"
            if (cleaned.contains("fm=")) cleaned else "$cleaned${separator}fm=jpg"
        }
        cleaned.startsWith("http://") || cleaned.startsWith("https://") || cleaned.startsWith("content://") -> cleaned
        cleaned.startsWith("/") -> base + cleaned
        else -> "$base/$cleaned"
    }
}

@Composable
private fun FullImageDialog(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onClose() }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("✕", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}
