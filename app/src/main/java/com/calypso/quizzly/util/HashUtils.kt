package com.calypso.quizzly.util

import java.security.MessageDigest

/** Hasheo de contraseñas con SHA-256 (RNF03: nunca se guardan en texto plano). */
object HashUtils {

    fun sha256(texto: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(texto.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
