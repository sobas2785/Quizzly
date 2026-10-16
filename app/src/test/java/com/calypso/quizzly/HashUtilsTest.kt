package com.calypso.quizzly

import com.calypso.quizzly.util.HashUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hasheo de contraseñas con PBKDF2 y salt (RNF-03), y compatibilidad con las cuentas viejas. */
class HashUtilsTest {

    // Pocas iteraciones para que los tests sean rápidos: el algoritmo es el mismo que usa la app.
    private fun hash(contrasena: String) = HashUtils.hashear(contrasena, iteraciones = 1_000)

    @Test
    fun verificaContrasena() {
        val guardado = hash("quizzly2026")

        assertTrue(HashUtils.verificar("quizzly2026", guardado))
        assertFalse(HashUtils.verificar("quizzly2025", guardado))
        assertFalse(HashUtils.verificar("", guardado))
    }

    @Test
    fun saltDistinto() {
        assertNotEquals(hash("quizzly2026"), hash("quizzly2026"))
    }

    @Test
    fun formatoGuardado() {
        val partes = hash("quizzly2026").split("$")

        assertEquals("pbkdf2", partes[0])
        assertEquals("1000", partes[1])
        assertEquals("16 bytes de salt en hexadecimal", 32, partes[2].length)
        assertEquals("256 bits de hash en hexadecimal", 64, partes[3].length)
    }

    @Test
    fun cuentaViejaSigueEntrando() {
        val viejo = HashUtils.sha256("secreto123")

        assertTrue(HashUtils.esFormatoViejo(viejo))
        assertTrue(HashUtils.verificar("secreto123", viejo))
        assertFalse(HashUtils.verificar("otra", viejo))
        assertFalse(HashUtils.esFormatoViejo(hash("secreto123")))
    }

    @Test
    fun valorDanado() {
        assertFalse(HashUtils.verificar("x", "pbkdf2\$abc\$zz\$yy"))
        assertFalse(HashUtils.verificar("x", "pbkdf2\$1000"))
    }
}
