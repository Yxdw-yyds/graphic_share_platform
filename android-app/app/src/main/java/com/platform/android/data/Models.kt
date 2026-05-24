package com.platform.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 20,
    val pages: Int = 1,
)

@Serializable
data class UserDto(
    val id: Int,
    val account: String,
    val nickname: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    val contact: String? = null,
    val role: String = "user",
    val status: String = "active",
    @SerialName("creator_level") val creatorLevel: String = "Lv1",
    @SerialName("heat_score") val heatScore: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: UserDto,
)

@Serializable
data class WorkDto(
    val id: Int,
    @SerialName("author_id") val authorId: Int,
    val title: String,
    val summary: String? = null,
    val category: String,
    @SerialName("cover_image") val coverImage: String? = null,
    val images: List<String> = emptyList(),
    val status: String,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    val heat: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val author: UserDto? = null,
)

@Serializable
data class ChapterDto(
    val id: Int,
    @SerialName("work_id") val workId: Int,
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 1,
    val status: String = "approved",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class CollectionItemDto(
    val id: Int,
    @SerialName("collection_id") val collectionId: Int,
    @SerialName("work_id") val workId: Int,
    @SerialName("sort_order") val sortOrder: Int = 1,
    @SerialName("created_at") val createdAt: String = "",
    val work: WorkDto? = null,
)

@Serializable
data class CollectionDto(
    val id: Int,
    @SerialName("user_id") val userId: Int,
    val title: String,
    val description: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val items: List<CollectionItemDto> = emptyList(),
)

@Serializable
data class CommentDto(
    val id: Int,
    @SerialName("work_id") val workId: Int,
    @SerialName("chapter_id") val chapterId: Int? = null,
    @SerialName("user_id") val userId: Int,
    @SerialName("parent_id") val parentId: Int? = null,
    val content: String,
    val status: String,
    @SerialName("created_at") val createdAt: String = "",
    val user: UserDto? = null,
    @SerialName("parent_user") val parentUser: UserDto? = null,
    val replies: List<CommentDto> = emptyList()
)

@Serializable
data class ReportDto(
    val id: Int,
    @SerialName("reporter_id") val reporterId: Int,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: Int,
    val reason: String,
    val description: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String = "",
    val reporter: UserDto? = null,
)

@Serializable
data class ReportTargetDetailDto(
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: Int,
    val work: WorkDto,
    val comments: List<CommentDto> = emptyList(),
)

@Serializable
data class StatsDto(
    @SerialName("new_users_today") val newUsersToday: Int = 0,
    @SerialName("works_today") val worksToday: Int = 0,
    @SerialName("pending_works") val pendingWorks: Int = 0,
    @SerialName("pending_reports") val pendingReports: Int = 0,
    @SerialName("total_users") val totalUsers: Int = 0,
    @SerialName("total_works") val totalWorks: Int = 0,
)

@Serializable
data class FollowDto(
    val id: Int,
    @SerialName("follower_id") val followerId: Int,
    @SerialName("followed_id") val followedId: Int,
    @SerialName("created_at") val createdAt: String = "",
    val followed: UserDto? = null,
)

@Serializable
data class TrendingTopicDto(
    val topic: String,
    val category: String,
    @SerialName("heat_score") val heatScore: Double,
    @SerialName("post_count") val postCount: Int
)

@Serializable data class LoginPasswordRequest(val account: String, val password: String)
@Serializable data class LoginCodeRequest(val account: String, val code: String)
@Serializable data class SendCodeRequest(val account: String, val purpose: String)
@Serializable data class SendCodeResponse(val message: String, val code: String? = null, val sent: Boolean = false)
@Serializable data class RegisterRequest(val account: String, val password: String, val code: String, val nickname: String? = null)
@Serializable data class ResetPasswordRequest(val account: String, val code: String, @SerialName("new_password") val newPassword: String)

@Serializable data class WorkCreateRequest(
    val title: String,
    val summary: String? = null,
    val category: String = "生活分享",
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("first_chapter_title") val firstChapterTitle: String = "正文",
    @SerialName("first_chapter_content") val firstChapterContent: String,
)

@Serializable data class UploadImagesResponse(
    val urls: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

@Serializable data class WorkUpdateRequest(
    val title: String? = null,
    val summary: String? = null,
    val category: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
)

@Serializable data class ChapterCreateRequest(
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 1,
)

@Serializable data class ChapterUpdateRequest(
    val title: String? = null,
    val content: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

@Serializable data class CollectionCreateRequest(
    val title: String,
    val description: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
)
@Serializable data class CollectionUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
)
@Serializable data class CollectionItemCreateRequest(
    @SerialName("work_id") val workId: Int,
    @SerialName("sort_order") val sortOrder: Int? = null,
)
@Serializable data class CollectionItemUpdateRequest(@SerialName("sort_order") val sortOrder: Int)

@Serializable data class UserUpdateRequest(
    val nickname: String? = null,
    val bio: String? = null,
    val contact: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable data class CommentCreateRequest(val content: String, @SerialName("parent_id") val parentId: Int? = null, @SerialName("chapter_id") val chapterId: Int? = null)
@Serializable data class ReportCreateRequest(@SerialName("target_type") val targetType: String, @SerialName("target_id") val targetId: Int, val reason: String, val description: String? = null)
@Serializable data class ReviewRequest(val approved: Boolean, val note: String? = null)
@Serializable data class ToggleLikeResponse(val liked: Boolean, @SerialName("like_count") val likeCount: Int)
@Serializable data class ToggleFavoriteResponse(val favorited: Boolean, @SerialName("favorite_count") val favoriteCount: Int)
@Serializable data class ToggleFollowResponse(val followed: Boolean)
