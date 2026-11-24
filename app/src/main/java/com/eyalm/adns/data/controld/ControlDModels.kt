package com.eyalm.adns.data.controld

/** Cuenta de ControlD (solo lo que usamos). */
data class ControlDAccount(
    val email: String?,
)

/** Perfil de ControlD: conjunto de reglas y filtros. */
data class ControlDProfile(
    val id: String,
    val name: String,
)

/**
 * Endpoint (dispositivo) de ControlD. Es el que tiene la dirección DNS propia
 * y el que aplica un perfil. Para usarlo como DNS privado de Android se usa [dotHost].
 */
data class ControlDEndpoint(
    val id: String,
    val name: String,
    val profileId: String?,
    val profileName: String?,
    val dotHost: String?,
    val dohUrl: String?,
    val ipv4: List<String>,
    val ipv6: List<String>,
)

enum class ControlDFailureKind {
    /** El token no es válido o fue borrado (HTTP 401). */
    InvalidToken,

    /** El token no tiene permiso o la IP no está permitida (HTTP 403). */
    Forbidden,

    /** Sin conexión o la red falló. */
    Network,

    /** ControlD devolvió un error. */
    Server,

    /** La respuesta no tiene el formato esperado. */
    BadResponse,
}

sealed interface ControlDResult<out T> {
    data class Success<T>(val value: T) : ControlDResult<T>

    data class Failure(
        val kind: ControlDFailureKind,
        val message: String? = null,
        val httpStatus: Int? = null,
    ) : ControlDResult<Nothing>
}
