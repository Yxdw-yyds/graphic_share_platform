package com.platform.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.platform.android.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

private val Context.dataStore by preferencesDataStore("session")

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val roleKey = stringPreferencesKey("role")
    private val nicknameKey = stringPreferencesKey("nickname")

    val session: Flow<Session> = context.dataStore.data.map {
        Session(
            token = it[tokenKey],
            role = it[roleKey],
            nickname = it[nicknameKey],
        )
    }

    suspend fun save(token: TokenDto) {
        context.dataStore.edit {
            it[tokenKey] = token.accessToken
            it[roleKey] = token.user.role
            it[nicknameKey] = token.user.nickname
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

data class Session(val token: String? = null, val role: String? = null, val nickname: String? = null) {
    val isLoggedIn: Boolean get() = !token.isNullOrBlank()
    val isAdmin: Boolean get() = role == "admin"
}

class AppContainer(context: Context) {
    val sessionStore = SessionStore(context.applicationContext)

    @Volatile
    private var currentToken: String? = null

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            sessionStore.session.collect { currentToken = it.token }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = currentToken
        val authed = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        chain.proceed(authed)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
