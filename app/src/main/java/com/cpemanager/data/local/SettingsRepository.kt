package com.cpemanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 扩展属性，单例 DataStore
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "cpe_settings")

const val DEFAULT_REFRESH_INTERVAL_SEC = 3
val REFRESH_INTERVAL_OPTIONS_SEC = listOf(1, 2, 5, 10)

/**
 * 设置键定义
 */
private object Keys {
    val BASE_URL = stringPreferencesKey("base_url")
    val USERNAME = stringPreferencesKey("username")
    val PASSWORD = stringPreferencesKey("password")
    val REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
    val AUTO_LOGIN = booleanPreferencesKey("auto_login")
    val AUTO_RELOGIN = booleanPreferencesKey("auto_relogin")
    val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
    val REFRESH_INTERVAL = intPreferencesKey("refresh_interval")
    val SPEED_TEST_URL = stringPreferencesKey("speed_test_url")
    val LATENCY_TEST_URL = stringPreferencesKey("latency_test_url")
}

/**
 * 持久化的设置数据
 */
data class SettingsData(
    val baseUrl: String = "http://10.0.0.1",
    val username: String = "admin",
    val password: String = "",
    val rememberPassword: Boolean = false,
    val autoLogin: Boolean = false,
    val autoRelogin: Boolean = true,
    val autoRefresh: Boolean = true,
    val refreshIntervalSec: Int = DEFAULT_REFRESH_INTERVAL_SEC,
    val speedTestUrl: String = "https://speed.cloudflare.com/__down?bytes=10485760",
    val latencyTestUrl: String = "https://www.gstatic.com/generate_204"
)

/**
 * 设置仓库 — 读写 DataStore
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** 读取设置流 */
    val settings: Flow<SettingsData> = dataStore.data.map { prefs ->
        SettingsData(
            baseUrl = prefs[Keys.BASE_URL] ?: "http://10.0.0.1",
            username = prefs[Keys.USERNAME] ?: "admin",
            password = prefs[Keys.PASSWORD] ?: "",
            rememberPassword = prefs[Keys.REMEMBER_PASSWORD] ?: false,
            autoLogin = prefs[Keys.AUTO_LOGIN] ?: false,
            autoRelogin = prefs[Keys.AUTO_RELOGIN] ?: true,
            autoRefresh = prefs[Keys.AUTO_REFRESH] ?: true,
            refreshIntervalSec = prefs[Keys.REFRESH_INTERVAL] ?: DEFAULT_REFRESH_INTERVAL_SEC,
            speedTestUrl = prefs[Keys.SPEED_TEST_URL] ?: "https://speed.cloudflare.com/__down?bytes=10485760",
            latencyTestUrl = prefs[Keys.LATENCY_TEST_URL] ?: "https://www.gstatic.com/generate_204"
        )
    }

    suspend fun saveBaseUrl(url: String) {
        dataStore.edit { it[Keys.BASE_URL] = url }
    }

    suspend fun saveCredentials(username: String, password: String) {
        dataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.PASSWORD] = password
        }
    }

    suspend fun saveRememberPassword(value: Boolean) {
        dataStore.edit { it[Keys.REMEMBER_PASSWORD] = value }
    }

    suspend fun saveAutoLogin(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_LOGIN] = value }
    }

    suspend fun saveAutoRelogin(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_RELOGIN] = value }
    }

    suspend fun saveAutoRefresh(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_REFRESH] = value }
    }

    suspend fun saveRefreshInterval(seconds: Int) {
        dataStore.edit { it[Keys.REFRESH_INTERVAL] = seconds }
    }

    suspend fun saveTestUrls(speedUrl: String, latencyUrl: String) {
        dataStore.edit {
            it[Keys.SPEED_TEST_URL] = speedUrl
            it[Keys.LATENCY_TEST_URL] = latencyUrl
        }
    }
}
