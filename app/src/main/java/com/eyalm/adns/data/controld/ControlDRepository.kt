package com.eyalm.adns.data.controld

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response as HttpResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Acceso a la API de ControlD con un token de API (Bearer).
 * El token solo se envía al host de [baseUrl].
 */
class ControlDRepository(
    private val tokenProvider: () -> String?,
    baseUrl: String = DEFAULT_BASE_URL,
) {
    private val api: ControlDApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(buildClient(baseUrl.toHttpUrl().host))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ControlDApi::class.java)

    private fun buildClient(allowedHost: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain -> addToken(chain, allowedHost) })
            .build()

    private fun addToken(chain: Interceptor.Chain, allowedHost: String): HttpResponse {
        val request = chain.request()
        val builder = request.newBuilder().removeHeader("Authorization")
        if (request.url.host == allowedHost) {
            tokenProvider()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
        }
        return chain.proceed(builder.build())
    }

    /** Sirve para comprobar que el token funciona. */
    suspend fun getAccount(): ControlDResult<ControlDAccount> =
        call(api::users) { body -> ControlDAccount(email = body.str("email")) }

    suspend fun getProfiles(): ControlDResult<List<ControlDProfile>> =
        call(api::profiles) { body ->
            body.objects("profiles").mapNotNull { item ->
                val id = item.str("PK") ?: return@mapNotNull null
                ControlDProfile(id = id, name = item.str("name") ?: id)
            }
        }

    suspend fun getEndpoints(): ControlDResult<List<ControlDEndpoint>> =
        call(api::devices) { body -> body.objects("devices").mapNotNull(::parseEndpoint) }

    private fun parseEndpoint(item: JsonObject): ControlDEndpoint? {
        val id = item.str("PK") ?: item.str("device_id") ?: return null
        val profile = item.obj("profile")
        val resolvers = item.obj("resolvers")
        val uid = resolvers?.str("uid")
        val dot = resolvers?.str("dot")
            ?: uid?.let { "$it.dns.controld.com" }
        return ControlDEndpoint(
            id = id,
            name = item.str("name") ?: id,
            profileId = profile?.str("PK"),
            profileName = profile?.str("name"),
            dotHost = dot,
            dohUrl = resolvers?.str("doh"),
            ipv4 = resolvers?.strings("v4").orEmpty(),
            ipv6 = resolvers?.strings("v6").orEmpty(),
        )
    }

    private suspend fun <T> call(
        request: suspend () -> Response<JsonObject>,
        map: (JsonObject) -> T,
    ): ControlDResult<T> = try {
        val response = request()
        if (response.isSuccessful) {
            val root = response.body()
            val body = root?.obj("body")
            if (root == null || body == null) {
                ControlDResult.Failure(ControlDFailureKind.BadResponse, httpStatus = response.code())
            } else {
                ControlDResult.Success(map(body))
            }
        } else {
            failureFrom(response)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        ControlDResult.Failure(ControlDFailureKind.Network, error.message)
    } catch (error: Exception) {
        ControlDResult.Failure(ControlDFailureKind.BadResponse, error.message)
    }

    private fun failureFrom(response: Response<JsonObject>): ControlDResult.Failure {
        val status = response.code()
        val message = try {
            val text = response.errorBody()?.string().orEmpty()
            JsonParser.parseString(text).obj()?.obj("error")?.str("message")
        } catch (ignored: Exception) {
            null
        }
        val kind = when (status) {
            401 -> ControlDFailureKind.InvalidToken
            403 -> ControlDFailureKind.Forbidden
            else -> ControlDFailureKind.Server
        }
        return ControlDResult.Failure(kind, message, status)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.controld.com/"
    }
}

private fun JsonElement?.obj(): JsonObject? =
    if (this != null && isJsonObject) asJsonObject else null

private fun JsonObject.obj(key: String): JsonObject? = get(key).obj()

private fun JsonObject.str(key: String): String? {
    val element = get(key) ?: return null
    return if (element.isJsonPrimitive) element.asString.takeIf { it.isNotBlank() } else null
}

private fun JsonObject.objects(key: String): List<JsonObject> {
    val element = get(key)
    if (element == null || !element.isJsonArray) return emptyList()
    return element.asJsonArray.mapNotNull { it.obj() }
}

private fun JsonObject.strings(key: String): List<String> {
    val element = get(key)
    if (element == null || !element.isJsonArray) return emptyList()
    return element.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
}
