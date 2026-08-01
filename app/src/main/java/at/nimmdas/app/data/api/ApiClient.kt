package at.nimmdas.app.data.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.nimmdas.app.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nimmdas_prefs")

class ApiClient(private val context: Context) {

    companion object {
        val TOKEN_KEY = stringPreferencesKey("jwt_token")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USER_AVATAR_KEY = stringPreferencesKey("user_avatar")
        val USER_LOCALE_KEY = stringPreferencesKey("user_locale")
    }

    private val authInterceptor = Interceptor { chain ->
        val (token, locale) = runBlocking {
            val t = context.dataStore.data.map { it[TOKEN_KEY] }.first()
            val l = context.dataStore.data.map { it[USER_LOCALE_KEY] }.first() ?: "de"
            Pair(t, l)
        }
        val builder = chain.request().newBuilder()
        builder.addHeader("Accept-Language", locale)
        if (token != null) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(builder.build())
    }

    /**
     * Turns an expired/invalid JWT into a real logout. Only fires for requests that
     * actually carried a token, so a failed login (401 without Authorization header)
     * is left alone.
     */
    private val sessionInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 401 && chain.request().header("Authorization") != null) {
            runBlocking { context.dataStore.edit { it.clear() } }
            SessionEvents.notifyExpired()
        }
        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(sessionInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL + "/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: NimmdasApi = retrofit.create(NimmdasApi::class.java)

    // ── Token Management ─────────────────────────

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.map { it[TOKEN_KEY] }.first()
    }

    suspend fun saveUserInfo(id: String, name: String, email: String, avatar: String?) {
        context.dataStore.edit {
            it[USER_ID_KEY] = id
            it[USER_NAME_KEY] = name
            it[USER_EMAIL_KEY] = email
            if (avatar != null) it[USER_AVATAR_KEY] = avatar
        }
    }

    suspend fun getUserName(): String? {
        return context.dataStore.data.map { it[USER_NAME_KEY] }.first()
    }

    suspend fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    suspend fun getUserId(): String? {
        return context.dataStore.data.map { it[USER_ID_KEY] }.first()
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
    }

    fun getBearerToken(): String? {
        return runBlocking {
            getToken()?.let { "Bearer $it" }
        }
    }

    suspend fun saveLocale(locale: String) {
        context.dataStore.edit { it[USER_LOCALE_KEY] = locale }
    }

    suspend fun getLocale(): String {
        return context.dataStore.data.map { it[USER_LOCALE_KEY] }.first() ?: "de"
    }
}
