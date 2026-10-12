package com.calypso.quizzly.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hasheo de contraseñas (RNF-03: nunca se guardan en texto plano).
 *
 * Se usa PBKDF2 con un salt al azar por cuenta: el salt hace que dos contraseñas iguales den hashes
 * distintos, y las miles de iteraciones hacen muy lento probar contraseñas por fuerza bruta.
 * Se guarda como "pbkdf2$iteraciones$salt$hash", con el salt y el hash en hexadecimal.
 *
 * Las cuentas creadas antes de este cambio tienen un SHA-256 sin salt: [verificar] las sigue
 * aceptando y [esFormatoViejo] avisa que hay que pasarlas al formato nuevo.
 */
object HashUtils {

    private const val PREFIJO = "pbkdf2"
    private const val ITERACIONES = 120_000
    private const val BITS_DEL_HASH = 256
    private const val BYTES_DEL_SALT = 16

    fun hashear(contrasena: String, iteraciones: Int = ITERACIONES): String {
        val salt = ByteArray(BYTES_DEL_SALT).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(contrasena, salt, iteraciones)
        return listOf(PREFIJO, iteraciones.toString(), salt.aHex(), hash.aHex()).joinToString("$")
    }

    /** Si la contraseña coincide con lo guardado, en el formato nuevo o en el viejo. */
    fun verificar(contrasena: String, guardado: String): Boolean = runCatching {
        if (esFormatoViejo(guardado)) {
            return@runCatching iguales(sha256(contrasena).toByteArray(), guardado.toByteArray())
        }
        val partes = guardado.split("$")
        require(partes.size == 4)
        iguales(pbkdf2(contrasena, partes[2].deHex(), partes[1].toInt()), partes[3].deHex())
    }.getOrDefault(false) // Un valor guardado dañado no rompe la app: simplemente no coincide.

    fun esFormatoViejo(guardado: String): Boolean = !guardado.startsWith("$PREFIJO$")

    /** El formato anterior, sin salt. Solo se usa para verificar las cuentas viejas. */
    fun sha256(texto: String): String =
        MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8)).aHex()

    private fun pbkdf2(contrasena: String, salt: ByteArray, iteraciones: Int): ByteArray {
        val spec = PBEKeySpec(contrasena.toCharArray(), salt, iteraciones, BITS_DEL_HASH)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Compara siempre todos los bytes: así no se puede adivinar el hash midiendo cuánto tarda en fallar. */
    private fun iguales(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)

    private fun ByteArray.aHex() = joinToString("") { "%02x".format(it) }

    private fun String.deHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
