package com.eyalm.adns.data.controld

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Llamadas a la API de ControlD (lectura y activar/desactivar filtros).
 * Respuesta general: {"body": {...}, "success": true}. Se lee a mano (JsonObject)
 * porque en los errores "body" llega como lista vacía y no como objeto.
 */
internal interface ControlDApi {
    @GET("users")
    suspend fun users(): Response<JsonObject>

    @GET("devices")
    suspend fun devices(): Response<JsonObject>

    @GET("profiles/{id}/filters")
    suspend fun profileFilters(@Path("id") id: String): Response<JsonObject>

    @FormUrlEncoded
    @PUT("profiles/{id}/filters/filter/{filter}")
    suspend fun setFilter(
        @Path("id") id: String,
        @Path("filter") filter: String,
        @Field("status") status: Int,
    ): Response<JsonObject>

    @GET("profiles/{id}/filters/external")
    suspend fun profileExternalFilters(@Path("id") id: String): Response<JsonObject>
}
