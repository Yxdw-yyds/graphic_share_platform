package com.platform.android.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("/api/health")
    suspend fun health(): Map<String, String>

    @POST("/api/auth/login/password")
    suspend fun loginPassword(@Body body: LoginPasswordRequest): TokenDto

    @POST("/api/auth/login/code")
    suspend fun loginCode(@Body body: LoginCodeRequest): TokenDto

    @POST("/api/auth/send-code")
    suspend fun sendCode(@Body body: SendCodeRequest): Map<String, String>

    @POST("/api/auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenDto

    @POST("/api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Map<String, String>

    @GET("/api/auth/me")
    suspend fun me(): UserDto

    @PATCH("/api/users/me")
    suspend fun updateMe(@Body body: UserUpdateRequest): UserDto

    @DELETE("/api/users/me")
    suspend fun deleteMe(): Map<String, String>

    @GET("/api/works")
    suspend fun works(
        @Query("keyword") keyword: String? = null,
        @Query("category") category: String? = null,
        @Query("hot") hot: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): PageDto<WorkDto>

    @GET("/api/works/trending")
    suspend fun trending(): List<TrendingTopicDto>

    @GET("/api/works/{id}")
    suspend fun work(@Path("id") id: Int): WorkDto

    @GET("/api/works/{id}/chapters")
    suspend fun chapters(@Path("id") id: Int): List<ChapterDto>

    @GET("/api/works/{id}/comments")
    suspend fun comments(@Path("id") id: Int, @Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<CommentDto>

    @GET("/api/works/{id}/all-comments")
    suspend fun allComments(@Path("id") id: Int): List<CommentDto>

    @POST("/api/works/{id}/like")
    suspend fun like(@Path("id") id: Int): ToggleLikeResponse

    @POST("/api/works/{id}/favorite")
    suspend fun favorite(@Path("id") id: Int): ToggleFavoriteResponse

    @POST("/api/works/{id}/comments")
    suspend fun comment(@Path("id") id: Int, @Body body: CommentCreateRequest): CommentDto

    @POST("/api/reports")
    suspend fun report(@Body body: ReportCreateRequest): ReportDto

    @POST("/api/works")
    suspend fun createWork(@Body body: WorkCreateRequest): WorkDto

    @PATCH("/api/works/{id}")
    suspend fun updateWork(@Path("id") id: Int, @Body body: WorkUpdateRequest): WorkDto

    @DELETE("/api/works/{id}")
    suspend fun deleteWork(@Path("id") id: Int): Map<String, String>

    @POST("/api/works/{id}/chapters")
    suspend fun createChapter(@Path("id") id: Int, @Body body: ChapterCreateRequest): ChapterDto

    @PATCH("/api/works/chapters/{id}")
    suspend fun updateChapter(@Path("id") id: Int, @Body body: ChapterUpdateRequest): ChapterDto

    @DELETE("/api/works/chapters/{id}")
    suspend fun deleteChapter(@Path("id") id: Int): Map<String, String>

    @DELETE("/api/comments/{id}")
    suspend fun deleteComment(@Path("id") id: Int): Map<String, String>

    @Multipart
    @POST("/api/works/upload-image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): Map<String, String>

    @GET("/api/users/me/works")
    suspend fun myWorks(@Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<WorkDto>

    @GET("/api/users/me/comments")
    suspend fun myComments(@Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<CommentDto>

    @GET("/api/users/me/follows")
    suspend fun myFollows(@Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<FollowDto>

    @POST("/api/users/{id}/follow")
    suspend fun follow(@Path("id") id: Int): ToggleFollowResponse

    @GET("/api/favorites")
    suspend fun favorites(@Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<WorkDto>

    @GET("/api/admin/stats")
    suspend fun stats(): StatsDto

    @GET("/api/works/pending")
    suspend fun pendingWorks(@Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<WorkDto>

    @POST("/api/works/{id}/review")
    suspend fun review(@Path("id") id: Int, @Body body: ReviewRequest): WorkDto

    @GET("/api/reports")
    suspend fun reports(@Query("status") status: String? = null, @Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<ReportDto>

    @POST("/api/reports/{id}/handle")
    suspend fun handleReport(@Path("id") id: Int, @Query("approved") approved: Boolean): Map<String, String>

    @GET("/api/users")
    suspend fun users(@Query("keyword") keyword: String? = null, @Query("page") page: Int = 1, @Query("size") size: Int = 20): PageDto<UserDto>

    @PATCH("/api/users/{id}/status")
    suspend fun updateUserStatus(@Path("id") id: Int, @Query("status") status: String): Map<String, String>
}
