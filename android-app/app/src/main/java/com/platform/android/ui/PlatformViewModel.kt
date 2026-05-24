package com.platform.android.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platform.android.data.ApiService
import com.platform.android.data.ChapterCreateRequest
import com.platform.android.data.ChapterDto
import com.platform.android.data.ChapterUpdateRequest
import com.platform.android.data.CollectionCreateRequest
import com.platform.android.data.CollectionDto
import com.platform.android.data.CollectionItemCreateRequest
import com.platform.android.data.CollectionItemUpdateRequest
import com.platform.android.data.CommentCreateRequest
import com.platform.android.data.CommentDto
import com.platform.android.data.FollowDto
import com.platform.android.data.LoginCodeRequest
import com.platform.android.data.LoginPasswordRequest
import com.platform.android.data.RegisterRequest
import com.platform.android.data.ReportCreateRequest
import com.platform.android.data.ReportDto
import com.platform.android.data.ReportTargetDetailDto
import com.platform.android.data.ResetPasswordRequest
import com.platform.android.data.ReviewRequest
import com.platform.android.data.SendCodeRequest
import com.platform.android.data.Session
import com.platform.android.data.SessionStore
import com.platform.android.data.StatsDto
import com.platform.android.data.TrendingTopicDto
import com.platform.android.data.UserDto
import com.platform.android.data.UserUpdateRequest
import com.platform.android.data.WorkCreateRequest
import com.platform.android.data.WorkDto
import com.platform.android.data.WorkUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

data class PlatformUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val works: List<WorkDto> = emptyList(),
    val trendingTopics: List<TrendingTopicDto> = emptyList(),
    val currentWork: WorkDto? = null,
    val currentWorkCollections: List<CollectionDto> = emptyList(),
    val publicProfile: UserDto? = null,
    val publicWorks: List<WorkDto> = emptyList(),
    val publicCollections: List<CollectionDto> = emptyList(),
    val publicCollection: CollectionDto? = null,
    val profile: UserDto? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val comments: List<CommentDto> = emptyList(),
    val myWorks: List<WorkDto> = emptyList(),
    val myComments: List<CommentDto> = emptyList(),
    val collections: List<CollectionDto> = emptyList(),
    val follows: List<FollowDto> = emptyList(),
    val followedAuthorIds: Set<Int> = emptySet(),
    val favorites: List<WorkDto> = emptyList(),
    val stats: StatsDto? = null,
    val pendingWorks: List<WorkDto> = emptyList(),
    val reports: List<ReportDto> = emptyList(),
    val reportTargetDetails: Map<String, ReportTargetDetailDto> = emptyMap(),
    val users: List<UserDto> = emptyList(),
)

class PlatformViewModel(
    private val api: ApiService,
    private val sessionStore: SessionStore,
) : ViewModel() {
    val session: StateFlow<Session> = sessionStore.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Session())
    private val _state = MutableStateFlow(PlatformUiState())
    val state: StateFlow<PlatformUiState> = _state

    init {
        refreshHome()
        loadTrending()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun closeWorkDetail() = _state.update { it.copy(currentWork = null, currentWorkCollections = emptyList(), chapters = emptyList(), comments = emptyList()) }

    fun closePublicProfile() = _state.update { it.copy(publicProfile = null, publicWorks = emptyList(), publicCollections = emptyList(), publicCollection = null) }

    fun login(account: String, password: String) = loginWithRole(account, password, expectedAdmin = false)

    fun loginAdmin(account: String, password: String) = loginWithRole(account, password, expectedAdmin = true)

    private fun loginWithRole(account: String, password: String, expectedAdmin: Boolean) = run("登录成功") {
        val token = api.loginPassword(LoginPasswordRequest(account, password))
        if (expectedAdmin && token.user.role != "admin") {
            throw IllegalStateException("请使用管理员账号登录")
        }
        if (!expectedAdmin && token.user.role == "admin") {
            throw IllegalStateException("管理员请从管理员入口登录")
        }
        sessionStore.save(token)
        if (token.user.role == "admin") loadAdminInternal() else refreshHomeInternal()
    }

    fun loginWithCode(account: String, code: String) = run("登录成功") {
        val token = api.loginCode(LoginCodeRequest(account, code))
        sessionStore.save(token)
        refreshHomeInternal()
    }

    fun register(account: String, password: String, code: String, nickname: String?) = run("注册成功") {
        val token = api.register(RegisterRequest(account, password, code, nickname))
        sessionStore.save(token)
        refreshHomeInternal()
    }

    fun resetPassword(account: String, code: String, newPw: String) = run("密码已重置") {
        api.resetPassword(ResetPasswordRequest(account, code, newPw))
    }

    fun sendCode(account: String, purpose: String = "register") = run(null) {
        validateAccount(account)
        val res = api.sendCode(SendCodeRequest(account.trim(), purpose))
        _state.update { it.copy(message = res.message) }
    }

    fun logout() = viewModelScope.launch {
        sessionStore.clear()
        _state.update { PlatformUiState(message = "已退出登录") }
        refreshHome()
    }

    fun deleteAccount() = run("账号已注销") {
        api.deleteMe()
        sessionStore.clear()
        _state.update { PlatformUiState() }
        refreshHome()
    }

    fun refreshHome(keyword: String? = null, category: String? = null, hot: Boolean = false) = run(null) {
        refreshHomeInternal(keyword, category, hot)
    }

    private suspend fun refreshHomeInternal(keyword: String? = null, category: String? = null, hot: Boolean = false) {
        val page = api.works(keyword = keyword?.ifBlank { null }, category = category?.ifBlank { null }, hot = hot)
        _state.update { it.copy(works = page.items) }
    }

    fun loadTrending() = run(null) {
        val topics = api.trending()
        _state.update { it.copy(trendingTopics = topics) }
    }

    fun openWork(id: Int) = run(null) {
        openWorkInternal(id)
    }

    fun openPublicProfile(userId: Int) = run(null) {
        val profile = api.user(userId)
        val works = api.userWorks(userId).items
        val collections = api.userCollections(userId)
        _state.update {
            it.copy(
                publicProfile = profile,
                publicWorks = works,
                publicCollections = collections,
                publicCollection = null,
                currentWork = null,
                currentWorkCollections = emptyList(),
                comments = emptyList(),
                chapters = emptyList(),
            )
        }
    }

    fun openPublicCollection(collectionId: Int) = run(null) {
        val current = state.value
        val collection = current.publicCollections.firstOrNull { it.id == collectionId }
            ?: current.currentWorkCollections.firstOrNull { it.id == collectionId }
        if (collection != null) {
            val ownerId = collection.userId
            val profile = current.publicProfile?.takeIf { it.id == ownerId } ?: api.user(ownerId)
            val collections = if (current.publicCollections.any { it.userId == ownerId }) current.publicCollections else api.userCollections(ownerId)
            _state.update {
                it.copy(
                    publicProfile = profile,
                    publicWorks = collections.flatMap { c -> c.items.mapNotNull { item -> item.work } },
                    publicCollections = collections,
                    publicCollection = collections.firstOrNull { c -> c.id == collectionId } ?: collection,
                    currentWork = null,
                    currentWorkCollections = emptyList(),
                    comments = emptyList(),
                    chapters = emptyList(),
                )
            }
        }
    }

    fun openWorkCommentsForManage(id: Int) = run(null) {
        openWorkCommentsForManageInternal(id)
    }

    fun like(id: Int) = run(null) {
        val res = api.like(id)
        _state.update { it.copy(message = if (res.liked) "已点赞" else "已取消点赞") }
        openWorkInternal(id)
    }

    fun favorite(id: Int) = run(null) {
        val res = api.favorite(id)
        _state.update { it.copy(message = if (res.favorited) "已收藏" else "已取消收藏") }
        openWorkInternal(id)
    }

    fun followAuthor(id: Int) = run(null) {
        val res = api.follow(id)
        _state.update {
            val nextFollowed = if (res.followed) {
                it.followedAuthorIds + id
            } else {
                it.followedAuthorIds - id
            }
            it.copy(
                followedAuthorIds = nextFollowed,
                follows = if (res.followed) it.follows else it.follows.filterNot { follow -> follow.followedId == id },
            )
        }
    }

    fun comment(workId: Int, content: String, parentId: Int? = null) = run("评论已发布") {
        val text = content.trim()
        if (text.isBlank()) return@run
        api.comment(workId, CommentCreateRequest(text, parentId))
        openWorkInternal(workId)
    }

    fun deleteComment(id: Int, workId: Int?) = run("评论已删除") {
        api.deleteComment(id)
        if (workId != null) openWorkInternal(workId)
        else loadMineInternal()
    }

    fun deleteManagedComment(id: Int, workId: Int) = run("评论已删除") {
        api.deleteComment(id)
        openWorkCommentsForManageInternal(workId)
        loadMineInternal()
    }

    fun report(targetType: String, targetId: Int, reason: String, description: String?) = run("举报已提交") {
        api.report(ReportCreateRequest(targetType, targetId, reason, description?.ifBlank { null }))
    }

    private suspend fun openWorkInternal(id: Int) {
        val work = api.work(id)
        val chapters = api.chapters(id)
        val comments = api.comments(id, size = 100).items
        val collections = api.workCollections(id)
        _state.update {
            it.copy(
                currentWork = work,
                currentWorkCollections = collections,
                chapters = chapters,
                comments = comments,
                works = it.works.map { item -> if (item.id == work.id) work else item },
                myWorks = it.myWorks.map { item -> if (item.id == work.id) work else item },
                favorites = it.favorites.map { item -> if (item.id == work.id) work else item },
            )
        }
    }

    private suspend fun openWorkCommentsForManageInternal(id: Int) {
        val work = api.work(id)
        val comments = api.allComments(id)
        _state.update {
            it.copy(
                currentWork = work,
                comments = comments,
                myWorks = it.myWorks.map { item -> if (item.id == work.id) work else item },
            )
        }
    }

    fun publish(title: String, summary: String, category: String, content: String) = run("作品已提交，AI初审通过后会先临时发布") {
        api.createWork(
            WorkCreateRequest(
                title = title,
                summary = summary.ifBlank { null },
                category = category.ifBlank { "生活分享" },
                firstChapterContent = content,
            )
        )
        refreshHomeInternal()
    }

    fun publishWithImage(context: Context, imageUri: Uri?, title: String?, summary: String, category: String, content: String) =
        run("作品已提交，AI初审通过后会先临时发布") {
            val cover = imageUri?.let { uploadImage(context, it) }
            val finalTitle = if (title.isNullOrBlank()) {
                category.removePrefix("#").ifBlank { "图文动态" }
            } else title
            api.createWork(
                WorkCreateRequest(
                    title = finalTitle,
                    summary = summary.ifBlank { null },
                    category = category.ifBlank { "生活分享" },
                    coverImage = cover,
                    firstChapterContent = content,
                )
            )
            refreshHomeInternal()
        }

    fun publishWithImages(context: Context, imageUris: List<Uri>, title: String?, summary: String, category: String, content: String) =
        run("作品已提交，AI初审通过后会先临时发布") {
            val urls = uploadImages(context, imageUris.take(4))
            val cover = urls.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) }
            val finalTitle = if (title.isNullOrBlank()) {
                category.removePrefix("#").ifBlank { "图文动态" }
            } else title
            api.createWork(
                WorkCreateRequest(
                    title = finalTitle,
                    summary = summary.ifBlank { null },
                    category = category.ifBlank { "生活分享" },
                    coverImage = cover,
                    firstChapterContent = content,
                )
            )
            refreshHomeInternal()
        }

    fun updateWork(id: Int, title: String, summary: String, category: String) = run("作品已更新，AI初审通过后会重新临时发布") {
        api.updateWork(id, WorkUpdateRequest(title = title, summary = summary, category = category))
        loadMineInternal()
        refreshHomeInternal()
    }

    fun updateWorkWithImage(
        context: Context,
        id: Int,
        title: String,
        summary: String,
        category: String,
        imageUri: Uri?,
        removeImage: Boolean,
    ) = run("作品已更新，AI初审通过后会重新临时发布") {
        val cover = when {
            imageUri != null -> uploadImage(context, imageUri)
            removeImage -> ""
            else -> null
        }
        api.updateWork(
            id,
            WorkUpdateRequest(
                title = title,
                summary = summary,
                category = category,
                coverImage = cover,
            )
        )
        loadMineInternal()
        refreshHomeInternal()
    }

    fun deleteWork(id: Int) = run("作品已删除") {
        api.deleteWork(id)
        loadMineInternal()
        refreshHomeInternal()
    }

    fun saveProfile(nickname: String, contact: String, bio: String) = run("资料已保存") {
        val user = api.updateMe(UserUpdateRequest(nickname = nickname.ifBlank { null }, contact = contact.ifBlank { null }, bio = bio.ifBlank { null }))
        _state.update { it.copy(profile = user) }
        loadMineInternal()
    }

    fun loadWorkChapters(workId: Int) = run(null) {
        val chapters = api.chapters(workId)
        _state.update { it.copy(chapters = chapters) }
    }

    fun createChapter(workId: Int, title: String, content: String, sort: Int) = run("章节已新增") {
        api.createChapter(workId, ChapterCreateRequest(title = title, content = content, sortOrder = sort))
        loadWorkChapters(workId)
        loadMineInternal()
    }

    fun updateChapter(id: Int, workId: Int, title: String, content: String, sort: Int) = run("章节已更新") {
        api.updateChapter(id, ChapterUpdateRequest(title = title, content = content, sortOrder = sort))
        loadWorkChapters(workId)
        loadMineInternal()
    }

    fun deleteChapter(id: Int, workId: Int) = run("章节已删除") {
        api.deleteChapter(id)
        loadWorkChapters(workId)
        loadMineInternal()
    }

    fun createCollection(title: String, description: String) = run("合集已创建") {
        api.createCollection(CollectionCreateRequest(title.trim(), description.ifBlank { null }))
        loadMineInternal()
    }

    fun deleteCollection(id: Int) = run("合集已删除") {
        api.deleteCollection(id)
        loadMineInternal()
    }

    fun addWorkToCollection(collectionId: Int, workId: Int, nextSort: Int) = run("已加入合集") {
        api.addCollectionItem(collectionId, CollectionItemCreateRequest(workId, nextSort))
        loadMineInternal()
    }

    fun removeCollectionItem(itemId: Int) = run("已移出合集") {
        api.deleteCollectionItem(itemId)
        loadMineInternal()
    }

    fun moveCollectionItem(itemId: Int, sortOrder: Int) = run("排序已更新") {
        api.updateCollectionItem(itemId, CollectionItemUpdateRequest(sortOrder))
        loadMineInternal()
    }

    private suspend fun uploadImage(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val body = bytes.toRequestBody("image/*".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "cover.jpg", body)
        return api.uploadImage(part)["url"]
    }

    private suspend fun uploadImages(context: Context, uris: List<Uri>): List<String> {
        if (uris.isEmpty()) return emptyList()
        val parts = uris.mapIndexedNotNull { index, uri ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapIndexedNotNull null
            val body = bytes.toRequestBody("image/*".toMediaType())
            MultipartBody.Part.createFormData("files", "image_${index + 1}.jpg", body)
        }
        if (parts.isEmpty()) return emptyList()
        val response = api.uploadImages(parts)
        return response.urls.ifEmpty { response.images }
    }

    fun loadMine() = run(null) {
        loadMineInternal()
    }

    private suspend fun loadMineInternal() {
        val profile = api.me()
        val myWorks = api.myWorks().items
        val myComments = api.myComments().items
        val collections = api.myCollections()
        val follows = api.myFollows().items
        val favorites = api.favorites().items
        _state.update {
            it.copy(
                profile = profile,
                myWorks = myWorks,
                myComments = myComments,
                collections = collections,
                follows = follows,
                followedAuthorIds = follows.map { follow -> follow.followedId }.toSet(),
                favorites = favorites
            )
        }
    }

    fun loadAdmin() = run(null) {
        val stats = api.stats()
        val pending = api.pendingWorks().items
        val reports = api.reports(status = "pending").items
        val users = api.users().items
        _state.update { it.copy(stats = stats, pendingWorks = pending, reports = reports, users = users) }
    }

    fun reviewWork(id: Int, approved: Boolean) = run("审核已处理") {
        api.review(id, ReviewRequest(approved))
        loadAdminInternal()
    }

    fun handleReport(id: Int, approved: Boolean) = run("举报已处理") {
        api.handleReport(id, approved)
        loadAdminInternal()
    }

    fun openReportTarget(targetType: String, targetId: Int) = run(null) {
        val detail = api.reportTarget(targetType, targetId)
        _state.update {
            it.copy(reportTargetDetails = it.reportTargetDetails + ("$targetType:$targetId" to detail))
        }
    }

    fun setUserStatus(id: Int, status: String) = run("用户状态已更新") {
        api.updateUserStatus(id, status)
        loadAdminInternal()
    }

    private suspend fun loadAdminInternal() {
        val stats = api.stats()
        val pending = api.pendingWorks().items
        val reports = api.reports(status = "pending").items
        val users = api.users().items
        _state.update { it.copy(stats = stats, pendingWorks = pending, reports = reports, users = users) }
    }

    private fun run(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try {
                block()
                if (successMessage != null) _state.update { it.copy(message = successMessage) }
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 401) {
                    sessionStore.clear()
                    _state.update {
                        PlatformUiState(
                            works = it.works,
                            trendingTopics = it.trendingTopics,
                            message = "登录已过期，请重新登录"
                        )
                    }
                } else {
                    _state.update { it.copy(message = readableError(e)) }
                }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun validateAccount(account: String) {
        val trimmed = account.trim()
        val accountPattern = Regex("^(?:1\\d{10}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("请先输入手机号或邮箱")
        }
        if (!accountPattern.matches(trimmed)) {
            throw IllegalArgumentException("请输入正确的手机号或邮箱")
        }
    }

    private fun readableError(e: Exception): String {
        if (e is HttpException) {
            if (e.code() == 401) return "登录已过期，请重新登录"
            val detail = e.response()?.errorBody()?.string()
                ?.substringAfter("\"detail\":\"", "")
                ?.substringBefore("\"")
                ?.takeIf { it.isNotBlank() }
            if (!detail.isNullOrBlank()) return detail
            if (e.code() == 422) return "请输入正确的手机号或邮箱"
        }
        return e.message ?: "请求失败"
    }
}
