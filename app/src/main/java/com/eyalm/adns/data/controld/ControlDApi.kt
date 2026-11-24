package com.eyalm.adns.data.controld

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET

/**
 * Llamadas de solo lectura a la API de ControlD.
 * Respuesta general: {"body": {...}, "success": true}. Se lee a mano (JsonObject)
 * porque en los errores "body" llega como lista vacía y no como objeto.
 */
internal interface ControlDApi {
    @GET("users")
    suspend fun users(): Response<JsonObject>

    @GET("profiles")
    suspend fun profiles(): Response<JsonObject>

    @GET("devices")
    suspend fun devices(): Response<JsonObject>
}
