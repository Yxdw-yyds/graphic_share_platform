package com.platform.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.platform.android.data.AppContainer
import com.platform.android.data.ChapterDto
import com.platform.android.data.CommentDto
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
private val StatusText = mapOf("approved" to "已发布", "pending" to "待审核", "rejected" to "已驳回")

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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = AcBg,
        topBar = { AppTopBar(session.nickname ?: "αcFun", state.loading) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                NavItem("首页", "home", tab) { tab = "home"; vm.refreshHome() }
                NavItem("发布", "publish", tab) { tab = "publish" }
                NavItem("我的", "mine", tab) { tab = "mine"; if (session.isLoggedIn) vm.loadMine() }
                if (session.isAdmin) NavItem("管理", "admin", tab) { tab = "admin"; vm.loadAdmin() }
            }
        },
    ) { padding ->
        Surface(Modifier.padding(padding).fillMaxSize(), color = AcBg) {
            when {
                !session.isLoggedIn && tab != "home" -> LoginScreen(vm)
                tab == "publish" -> PublishScreen(vm)
                tab == "mine" -> MineScreen(state, vm)
                tab == "admin" && session.isAdmin -> AdminScreen(state, vm)
                else -> HomeScreen(state, vm)
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, route: String, current: String, onClick: () -> Unit) {
    val selected = current == route
    Column(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .width(24.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) AcPrimary else Color.Transparent)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) AcText else AcMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, loading: Boolean) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("αcFun", color = AcPrimary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (loading) "加载中" else title,
                    style = MaterialTheme.typography.bodySmall,
                    color = AcMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = AcText),
    )
}

@Composable
private fun LoginScreen(vm: PlatformViewModel) {
    var mode by remember { mutableStateOf("login_pwd") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            val title = when(mode) {
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
                AppTextField(account, { account = it }, "手机号或邮箱")
                
                if (mode == "login_pwd") {
                    AppTextField(password, { password = it }, "密码", password = true)
                    PrimaryButton("登录", Modifier.fillMaxWidth()) { vm.login(account, password) }
                } else if (mode == "login_otp") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                        SecondaryButton("获取验证码", Modifier.width(120.dp)) { vm.sendCode(account, "login") }
                    }
                    PrimaryButton("登录", Modifier.fillMaxWidth()) { vm.loginWithCode(account, code) }
                } else if (mode == "register") {
                    AppTextField(password, { password = it }, "设置密码", password = true)
                    AppTextField(nickname, { nickname = it }, "昵称")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                        SecondaryButton("获取验证码", Modifier.width(120.dp)) { vm.sendCode(account, "register") }
                    }
                    PrimaryButton("立即注册", Modifier.fillMaxWidth()) { vm.register(account, password, code, nickname) }
                } else if (mode == "forgot") {
                    AppTextField(password, { password = it }, "新密码", password = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(code, { code = it }, "验证码", modifier = Modifier.weight(1f))
                        SecondaryButton("获取验证码", Modifier.width(120.dp)) { vm.sendCode(account, "reset") }
                    }
                    PrimaryButton("重置密码", Modifier.fillMaxWidth()) { vm.resetPassword(account, code, password) }
                }

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
            }
        }
    }
}

@Composable
private fun HomeScreen(state: PlatformUiState, vm: PlatformViewModel) {
    var keyword by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("全部") }
    var detailId by remember { mutableStateOf<Int?>(null) }
    
    val categories = listOf("全部", "热门", "资讯", "推荐")

    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = "搜索感兴趣的内容...",
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { vm.refreshHome(keyword, if(selectedCat=="全部") null else selectedCat) },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AcPrimary)
                    ) {
                        Text("搜索")
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    SmallTab(cat, selectedCat == cat) {
                        selectedCat = cat
                        vm.refreshHome(keyword, if(cat=="全部") null else if(cat=="热门") null else cat, hot = cat == "热门")
                    }
                }
            }
        }
        
        if (state.trendingTopics.isNotEmpty()) {
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

        detailId?.let { item { WorkDetail(state, vm, it) { detailId = null } } }
        
        items(state.works) { work ->
            WorkCard(work, onOpen = {
                detailId = work.id
                vm.openWork(work.id)
            }, vm = vm)
        }
    }
}

@Composable
private fun WorkDetail(state: PlatformUiState, vm: PlatformViewModel, id: Int, onClose: () -> Unit) {
    val work = state.currentWork ?: return
    var comment by remember { mutableStateOf("") }
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(work.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Text("✕", fontWeight = FontWeight.Bold) }
        }
        Text("#${work.category} · ${work.author?.nickname ?: "用户"}", color = AcMuted)
        WorkImage(work.coverImage)
        Text(work.summary ?: "", color = AcText)
        
        if (state.chapters.isNotEmpty()) {
            Text("章节内容", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            state.chapters.forEach { chapter ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AcBg)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(chapter.title, fontWeight = FontWeight.Bold)
                        Text(chapter.content, color = AcText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("👍 ${work.likeCount}", Modifier.weight(1f)) { vm.like(id) }
            SecondaryButton("⭐ ${work.favoriteCount}", Modifier.weight(1f)) { vm.favorite(id) }
        }
        
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
            CommentItem(c, vm, id)
        }
    }
}

@Composable
private fun CommentItem(c: CommentDto, vm: PlatformViewModel, workId: Int) {
    var replying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(c.user?.nickname ?: "用户", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { replying = !replying }) { Text("回复", style = MaterialTheme.typography.bodySmall) }
        }
        Text(c.content, color = AcText)
        
        c.replies.forEach { r ->
            Row(Modifier.padding(start = 24.dp, top = 4.dp)) {
                Text("${r.user?.nickname}: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(r.content, style = MaterialTheme.typography.bodySmall)
            }
        }
        
        if (replying) {
            Row(Modifier.padding(start = 24.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AppTextField(replyText, { replyText = it }, "回复 @${c.user?.nickname}", modifier = Modifier.weight(1f))
                TextButton(onClick = { 
                    vm.comment(workId, replyText, c.id)
                    replyText = ""
                    replying = false
                }) { Text("发送") }
            }
        }
    }
}

@Composable
private fun PublishScreen(vm: PlatformViewModel) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("#生活分享") }
    var content by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> imageUri = uri }
    
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
                WorkImage(imageUri?.toString())
                SecondaryButton(if(imageUri==null) "添加图片" else "更换图片", Modifier.fillMaxWidth()) { imagePicker.launch("image/*") }
                
                Spacer(Modifier.height(8.dp))
                PrimaryButton("立即发布", Modifier.fillMaxWidth()) {
                    vm.publishWithImage(context, imageUri, null, content, category, content)
                }
            }
        }
    }
}

@Composable
private fun MineScreen(state: PlatformUiState, vm: PlatformViewModel) {
    var section by remember { mutableStateOf("info") }
    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("个人中心", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.logout() }) { Text("退出", color = AcDanger) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("info" to "资料", "works" to "动态", "chapters" to "章节").forEach { (key, label) ->
                    SmallTab(label, section == key, Modifier.weight(1f)) { section = key }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("comments" to "评论", "follows" to "关注", "favorites" to "收藏").forEach { (key, label) ->
                    SmallTab(label, section == key, Modifier.weight(1f)) { section = key }
                }
            }
        }
        when (section) {
            "info" -> item { ProfileEditor(state.profile, vm) }
            "works" -> {
                item { SectionTitle("我的作品") }
                items(state.myWorks) { MyWorkCard(it, vm) }
            }
            "chapters" -> item { ChapterManager(state, vm) }
            "comments" -> {
                item { SectionTitle("收到的评论") }
                items(state.myComments) { MyCommentCard(it, vm) }
            }
            "follows" -> {
                item { SectionTitle("我的关注") }
                items(state.follows) { FollowCard(it, vm) }
            }
            "favorites" -> {
                item { SectionTitle("收藏夹") }
                items(state.favorites) { WorkCard(it, onOpen = {}) }
            }
        }
    }
}

@Composable
private fun ProfileEditor(user: UserDto?, vm: PlatformViewModel) {
    var nickname by remember(user?.id) { mutableStateOf(user?.nickname ?: "") }
    var contact by remember(user?.id) { mutableStateOf(user?.contact ?: user?.account ?: "") }
    var bio by remember(user?.id) { mutableStateOf(user?.bio ?: "") }
    AppCard {
        Text("创作等级：${user?.creatorLevel ?: "Lv1"} / 热度 ${user?.heatScore ?: 0.0}", color = AcPrimary, fontWeight = FontWeight.Bold)
        AppTextField(nickname, { nickname = it }, "昵称")
        AppTextField(contact, { contact = it }, "联系方式")
        AppTextField(bio, { bio = it }, "个人简介", minLines = 3)
        PrimaryButton("保存修改", Modifier.fillMaxWidth()) { vm.saveProfile(nickname, contact, bio) }
    }
}

@Composable
private fun MyWorkCard(work: WorkDto, vm: PlatformViewModel) {
    var editing by remember { mutableStateOf(false) }
    var title by remember(work.id, editing) { mutableStateOf(work.title) }
    var summary by remember(work.id, editing) { mutableStateOf(work.summary ?: "") }
    var category by remember(work.id, editing) { mutableStateOf(work.category) }
    AppCard {
        Text("#${work.category} · ${StatusText[work.status] ?: work.status}", color = AcMuted)
        if (editing) {
            AppTextField(title, { title = it }, "标题")
            AppTextField(summary, { summary = it }, "正文/摘要", minLines = 4)
            AppTextField(category, { category = it }, "分类")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("保存", Modifier.weight(1f)) {
                    vm.updateWork(work.id, title, summary, category)
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
    var selectedWorkId by remember { mutableStateOf(state.myWorks.firstOrNull()?.id) }
    AppCard {
        if (state.myWorks.isEmpty()) {
            Text("暂无稿件，发布内容后可管理章节", color = AcMuted)
            return@AppCard
        }
        Text("选择稿件", fontWeight = FontWeight.Bold)
        state.myWorks.forEach { work ->
            Row(Modifier.fillMaxWidth().clickable {
                selectedWorkId = work.id
                vm.loadWorkChapters(work.id)
            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (work.id == selectedWorkId) "●" else "○", color = AcPrimary, modifier = Modifier.width(24.dp))
                Text(work.title, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
        
        selectedWorkId?.let { workId ->
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = AcBorder)
            Spacer(Modifier.height(10.dp))
            NewChapterForm(workId, state.chapters.size + 1, vm)
            state.chapters.forEach { chapter -> ChapterEditor(chapter, workId, vm) }
        }
    }
}

@Composable
private fun NewChapterForm(workId: Int, nextSort: Int, vm: PlatformViewModel) {
    var show by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    if (!show) {
        SecondaryButton("新增章节", Modifier.fillMaxWidth()) { show = true }
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
private fun FollowCard(follow: FollowDto, vm: PlatformViewModel) {
    val user = follow.followed
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(user?.nickname ?: "用")
            Column(Modifier.weight(1f)) {
                Text(user?.nickname ?: "用户${follow.followedId}", fontWeight = FontWeight.Bold)
                Text(user?.bio ?: "暂无简介", style = MaterialTheme.typography.bodySmall, color = AcMuted)
            }
            SecondaryButton("取消关注", Modifier.width(100.dp)) { vm.followAuthor(user?.id ?: follow.followedId) }
        }
    }
}

@Composable
private fun AdminScreen(state: PlatformUiState, vm: PlatformViewModel) {
    LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("管理后台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            state.stats?.let { Text("用户 ${it.totalUsers} · 作品 ${it.totalWorks} · 待审 ${it.pendingWorks} · 举报 ${it.pendingReports}", color = AcMuted) }
        }
        item { SectionTitle("待审核作品") }
        items(state.pendingWorks) { AdminWorkCard(it, vm) }
        item { SectionTitle("待处理举报") }
        items(state.reports) { ReportCard(it, vm) }
        item { SectionTitle("用户管理") }
        items(state.users) { UserCard(it, vm) }
    }
}

@Composable
private fun WorkCard(work: WorkDto, onOpen: () -> Unit, vm: PlatformViewModel? = null) {
    AppCard(Modifier.clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(work.author?.nickname ?: "用户")
            Column(Modifier.weight(1f)) {
                Text(work.author?.nickname ?: "用户", fontWeight = FontWeight.Bold)
            Text("#${work.category} · ${StatusText[work.status] ?: work.status}", color = AcMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (vm != null && work.author != null) {
                CompactPill("关注") { vm.followAuthor(work.author.id) }
            }
        }
        Text(work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(work.summary ?: "暂无摘要", color = AcText, maxLines = 3, overflow = TextOverflow.Ellipsis)
        WorkImage(work.coverImage)
        Text("👍 ${work.likeCount}  ⭐ ${work.favoriteCount}  💬 ${work.commentCount}  👁 ${work.viewCount}", color = AcMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AdminWorkCard(work: WorkDto, vm: PlatformViewModel) {
    AppCard {
        Text(work.title, fontWeight = FontWeight.Bold)
        Text(work.summary ?: "", color = AcText)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("通过", Modifier.weight(1f)) { vm.reviewWork(work.id, true) }
            DangerButton("拒绝", Modifier.weight(1f)) { vm.reviewWork(work.id, false) }
        }
    }
}

@Composable
private fun ReportCard(report: ReportDto, vm: PlatformViewModel) {
    AppCard {
        Text("${report.targetType} #${report.targetId} · ${report.reason}", fontWeight = FontWeight.Bold)
        Text(report.description ?: "无补充说明", color = AcMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("采纳", Modifier.weight(1f)) { vm.handleReport(report.id, true) }
            SecondaryButton("驳回", Modifier.weight(1f)) { vm.handleReport(report.id, false) }
        }
    }
}

@Composable
private fun UserCard(user: UserDto, vm: PlatformViewModel) {
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(user.nickname, fontWeight = FontWeight.Bold)
                Text("${user.account} · ${user.role} · ${user.status}", color = AcMuted)
            }
            SecondaryButton(if (user.status == "active") "禁用" else "启用") {
                vm.setUserStatus(user.id, if (user.status == "active") "disabled" else "active")
            }
        }
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
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
private fun CompactPill(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(72.dp).height(40.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5FD), contentColor = AcPrimary),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SmallTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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
private fun WorkImage(url: String?) {
    if (url.isNullOrBlank()) return
    val fullUrl = if (url.startsWith("/uploads/")) {
        "http://10.0.2.2:8000$url"
    } else url
    AsyncImage(
        model = fullUrl,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 230.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(AcBorder),
        contentScale = ContentScale.Crop,
    )
}
