package com.eagleslab.yingge.data.contract

import android.content.Context
import com.eagleslab.yingge.BuildConfig
import com.eagleslab.yingge.data.RepositoryException
import com.eagleslab.yingge.model.DealRequest
import com.eagleslab.yingge.model.SessionUser
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.UUID

internal interface MobileContractApi {
    @POST("api/v1/mobile/auth/registration-options")
    suspend fun registrationOptions(@Body body: RegistrationOptionsRequest): RegistrationOptionsDto

    @POST("api/v1/mobile/auth/register")
    suspend fun register(@Body body: MobileRegisterRequest): RegistrationResultDto

    @POST("api/v1/mobile/auth/login")
    suspend fun login(@Body body: MobileLoginRequest): MobileTokenResponse

    @POST("api/v1/mobile/auth/refresh")
    suspend fun refresh(@Body body: MobileRefreshRequest): MobileTokenResponse

    @POST("api/v1/mobile/auth/logout")
    suspend fun logout()

    @POST("api/v1/auth/password")
    suspend fun changePassword(@Body body: PasswordChangeRequest)

    @GET("api/v1/mobile/auth/devices")
    suspend fun devices(): List<MobileDeviceDto>

    @DELETE("api/v1/mobile/auth/devices/{deviceId}")
    suspend fun revokeDevice(@Path("deviceId") deviceId: String)

    @GET("api/v1/mobile/bootstrap")
    suspend fun bootstrap(): MobileBootstrapDto

    @POST("api/v1/mobile/devices/push-registration")
    suspend fun registerPush(@Body body: PushRegistrationRequest)

    @DELETE("api/v1/mobile/devices/push-registration")
    suspend fun unregisterPush()

    @PUT("api/v1/mobile/devices/notification-preferences")
    suspend fun notificationPreferences(@Body body: NotificationPreferencesDto): NotificationPreferencesDto

    @POST("api/v1/mobile/web-sessions")
    suspend fun createWebSession(@Body body: WebSessionTicketRequest): WebSessionTicketResponse

    @GET("api/v1/mobile/app-releases/current")
    suspend fun currentRelease(): Response<MobileAppReleaseDto>

    @Streaming
    @GET("api/v1/mobile/app-releases/{releaseId}/apk")
    suspend fun releaseApk(@Path("releaseId") releaseId: String): ResponseBody

    @GET("api/v1/customers/{customerId}")
    suspend fun customer(@Path("customerId") customerId: String): ContractCustomerDto

    @POST("api/v1/customers/{customerId}/deal-requests")
    suspend fun requestDeal(@Path("customerId") customerId: String, @Header("Idempotency-Key") idempotencyKey: String): DealRequestDto

}

data class TokenBundle(
    val accessToken: String,
    val accessExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
)

/**
 * Exact adapter for contracts/mobile-api.yaml. Employee APIs not present in that
 * document remain behind YinggeRepository and are not guessed here.
 */
class MobileContractGateway private constructor(
    private val api: MobileContractApi,
    private val tokens: MobileTokenVault,
    val origin: String,
) {
    private val refreshGate = SingleFlightRefreshGate()
    suspend fun registrationOptions(code: String): RegistrationOptionsDto = call {
        api.registrationOptions(RegistrationOptionsRequest(code))
    }

    /** Registration never invents an authenticated session: mobileLogin is mandatory after 201. */
    suspend fun registerAndLogin(username: String, realName: String, password: String, registrationCode: String, teamId: String): SessionUser = call {
        api.register(MobileRegisterRequest(username, realName, password, registrationCode, teamId))
        val response = api.login(MobileLoginRequest(username, password, "Android 客户端"))
        tokens.save(origin, response.toBundle())
        response.actor.toModel()
    }

    suspend fun login(username: String, password: String): SessionUser = call {
        val response = api.login(MobileLoginRequest(username, password, "Android 客户端"))
        tokens.save(origin, response.toBundle())
        response.actor.toModel()
    }

    suspend fun refresh(): SessionUser {
        var actor: SessionUser? = null
        refreshGate.force { actor = refreshInternal() }
        return requireNotNull(actor)
    }

    /** Shared 401 recovery for the employee API client. Concurrent failures rotate once. */
    suspend fun refreshAfterUnauthorized(usedAccessToken: String?) {
        refreshGate.refreshIfUnchanged(usedAccessToken, { tokens.accessToken(origin) }) {
            runCatching { refreshInternal() }.getOrElse { tokens.clear(origin); throw it }
        }
    }

    private suspend fun refreshInternal(): SessionUser = call {
        val current = tokens.refreshToken(origin) ?: throw RepositoryException("MOBILE_SESSION_MISSING", "请重新登录")
        val response = api.refresh(MobileRefreshRequest(current.refreshToken))
        tokens.save(origin, response.toBundle())
        response.actor.toModel()
    }

    suspend fun logout() {
        try { authorizedCall { api.logout() } } finally { tokens.clear(origin) }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        try { authorizedCall { api.changePassword(PasswordChangeRequest(currentPassword, newPassword)) } }
        finally { tokens.clear(origin) }
    }
    suspend fun devices(): List<MobileDeviceDto> = authorizedCall { api.devices() }
    suspend fun revokeDevice(deviceId: String) = authorizedCall { api.revokeDevice(deviceId) }

    suspend fun bootstrap(): MobileBootstrapDto = authorizedCall { api.bootstrap() }
    suspend fun registerPush(registrationId: String, vendor: String?) = authorizedCall { api.registerPush(PushRegistrationRequest(registrationId = registrationId, vendor = vendor)) }
    suspend fun unregisterPush() = authorizedCall { api.unregisterPush() }
    suspend fun updateNotificationPreferences(value: NotificationPreferencesDto): NotificationPreferencesDto = authorizedCall { api.notificationPreferences(value) }
    suspend fun createQqTicket(): WebSessionTicketResponse = authorizedCall { api.createWebSession(WebSessionTicketRequest("QQ_WORKBENCH")) }
    suspend fun currentRelease(): MobileAppReleaseDto? = authorizedCall {
        val response = api.currentRelease()
        if (!response.isSuccessful) throw HttpException(response)
        response.body()
    }
    suspend fun releaseApk(releaseId: String): ResponseBody = authorizedCall { api.releaseApk(releaseId) }
    suspend fun customer(customerId: String): ContractCustomerDto = authorizedCall { api.customer(customerId) }
    suspend fun requestDeal(customerId: String, idempotencyKey: String): DealRequest =
        authorizedCall { api.requestDeal(customerId, idempotencyKey).toModel() }

    private suspend fun <T> call(block: suspend () -> T): T = MobileNetworkCall.execute(block)

    private suspend fun <T> authorizedCall(block: suspend () -> T): T {
        if (tokens.accessToken(origin) == null) refreshGate.refreshIfUnchanged(null, { tokens.accessToken(origin) }) {
            runCatching { refreshInternal() }.getOrElse { tokens.clear(origin); throw it }
        }
        val usedAccessToken = tokens.accessToken(origin)
        return try { call(block) } catch (error: RepositoryException) {
            if (error.httpStatus != 401) throw error
            refreshGate.refreshIfUnchanged(usedAccessToken, { tokens.accessToken(origin) }) {
                runCatching { refreshInternal() }.getOrElse { tokens.clear(origin); throw it }
            }
            try { call(block) } catch (retryError: RepositoryException) {
                if (retryError.httpStatus == 401) tokens.clear(origin)
                throw retryError
            }
        }
    }

    companion object {
        fun create(context: Context, serverUrl: String, allowInsecureHttp: Boolean = false, tokens: MobileTokenVault = MobileTokenVault.get(context)): MobileContractGateway {
            val origin = ServerOriginValidator.requireValid(serverUrl, allowInsecureHttp)
            val deviceId = DeviceIdentity(context).id
            val client = MobileHttpClientFactory.create(
                origin = origin,
                deviceId = deviceId,
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                accessToken = { tokens.accessToken(origin.canonical) },
            )
            val api = Retrofit.Builder()
                .baseUrl(origin.canonical + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MobileContractApi::class.java)
            return MobileContractGateway(api, tokens, origin.canonical)
        }
    }
}

private fun MobileTokenResponse.toBundle() = TokenBundle(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt)

internal class DeviceIdentity(context: Context) {
    private val preferences = context.getSharedPreferences("yingge_device", Context.MODE_PRIVATE)
    val id: String = preferences.getString("instance_id", null) ?: UUID.randomUUID().toString()
        .also { preferences.edit().putString("instance_id", it).apply() }
}
