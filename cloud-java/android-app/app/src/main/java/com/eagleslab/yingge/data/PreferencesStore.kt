package com.eagleslab.yingge.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eagleslab.yingge.model.CustomerDraft
import com.eagleslab.yingge.model.DailySummaryDraft
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

private val Context.yinggeDataStore by preferencesDataStore("yingge_mobile")

enum class ThemePreference { SYSTEM, LIGHT, DARK }

class PreferencesStore(private val context: Context) {
    private val gson = Gson()
    private val secure = AndroidSecureStore(context, "yingge_mobile_secure_state", "yingge_mobile_secure_state_v1")
    private val dailyDraftState = MutableStateFlow(readSecure(DAILY_DRAFT, DailySummaryDraft::class.java) ?: DailySummaryDraft())
    private val customerDraftState = MutableStateFlow(readSecure(CUSTOMER_DRAFT, CustomerDraft::class.java) ?: CustomerDraft())
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val theme = stringPreferencesKey("theme")
        val dailyDeep = stringPreferencesKey("daily_deep")
        val dailyIssues = stringPreferencesKey("daily_issues")
        val dailyPlan = stringPreferencesKey("daily_plan")
        val customerName = stringPreferencesKey("customer_name")
        val customerNickname = stringPreferencesKey("customer_nickname")
        val customerSchool = stringPreferencesKey("customer_school")
        val customerMajor = stringPreferencesKey("customer_major")
        val customerGrade = stringPreferencesKey("customer_grade")
        val customerPhone = stringPreferencesKey("customer_phone")
        val customerWechat = stringPreferencesKey("customer_wechat")
        val customerQq = stringPreferencesKey("customer_qq")
        val customerSource = stringPreferencesKey("customer_source")
        val customerDemand = stringPreferencesKey("customer_demand")
    }

    val serverUrl: Flow<String> = context.yinggeDataStore.data.map { it[Keys.serverUrl] ?: DEFAULT_SERVER_ORIGIN }
    val theme: Flow<ThemePreference> = context.yinggeDataStore.data.map { preferences ->
        runCatching { ThemePreference.valueOf(preferences[Keys.theme] ?: "SYSTEM") }.getOrDefault(ThemePreference.SYSTEM)
    }
    val dailyDraft: Flow<DailySummaryDraft> = dailyDraftState
    val customerDraft: Flow<CustomerDraft> = customerDraftState

    init {
        // Remove the legacy plaintext draft keys on upgrade. Draft content is only
        // retained inside the Keystore-encrypted envelope above.
        CoroutineScope(Dispatchers.IO).launch {
            context.yinggeDataStore.edit { preferences -> LEGACY_DRAFT_KEYS.forEach(preferences::remove) }
        }
    }

    suspend fun setServerUrl(value: String) = edit(Keys.serverUrl, value.trim().trimEnd('/'))
    suspend fun setTheme(value: ThemePreference) = edit(Keys.theme, value.name)
    suspend fun currentServerUrl(): String = serverUrl.first()
    suspend fun saveDailyDraft(value: DailySummaryDraft) { secure.put(DAILY_DRAFT, gson.toJson(value)); dailyDraftState.value = value }
    suspend fun clearDailyDraft() { secure.remove(DAILY_DRAFT); dailyDraftState.value = DailySummaryDraft() }
    suspend fun saveCustomerDraft(value: CustomerDraft) { secure.put(CUSTOMER_DRAFT, gson.toJson(value)); customerDraftState.value = value }
    suspend fun clearCustomerDraft() { secure.remove(CUSTOMER_DRAFT); customerDraftState.value = CustomerDraft() }
    suspend fun clearSensitiveState() {
        secure.clear()
        dailyDraftState.value = DailySummaryDraft()
        customerDraftState.value = CustomerDraft()
    }

    fun getOrCreateDealKey(origin: String, userId: String, customerId: String): String {
        val key = "deal:$origin:$userId:$customerId"
        return secure.get(key) ?: UUID.randomUUID().toString().also { secure.put(key, it) }
    }

    fun clearDealKey(origin: String, userId: String, customerId: String) = secure.remove("deal:$origin:$userId:$customerId")
    fun savePushRegistration(registrationId: String, vendor: String?) = secure.put(PUSH_REGISTRATION, gson.toJson(StoredPushRegistration(registrationId, vendor)))
    fun pushRegistration(): StoredPushRegistration? = readSecure(PUSH_REGISTRATION, StoredPushRegistration::class.java)

    private suspend fun edit(key: Preferences.Key<String>, value: String) {
        context.yinggeDataStore.edit { it[key] = value }
    }

    private fun <T> readSecure(key: String, type: Class<T>): T? = secure.get(key)?.let { runCatching { gson.fromJson(it, type) }.getOrNull() }

    companion object {
        const val DEFAULT_SERVER_ORIGIN = "http://192.0.2.10"
        private const val DAILY_DRAFT = "daily-draft"
        private const val CUSTOMER_DRAFT = "customer-draft"
        private const val PUSH_REGISTRATION = "push-registration"
        private val LEGACY_DRAFT_KEYS = listOf(
            Keys.dailyDeep, Keys.dailyIssues, Keys.dailyPlan, Keys.customerName, Keys.customerNickname,
            Keys.customerSchool, Keys.customerMajor, Keys.customerGrade, Keys.customerPhone, Keys.customerWechat,
            Keys.customerQq, Keys.customerSource, Keys.customerDemand,
        )
    }
}

data class StoredPushRegistration(val registrationId: String, val vendor: String? = null)
