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
    fun `la contrasena correcta se verifica y una incorrecta no`() {
        val guardado = hash("quizzly2026")

        assertTrue(HashUtils.verificar("quizzly2026", guardado))
        assertFalse(HashUtils.verificar("quizzly2025", guardado))
        assertFalse(HashUtils.verificar("", guardado))
    }

    @Test
    fun `la misma contrasena da hashes distintos gracias al salt`() {
        assertNotEquals(hash("quizzly2026"), hash("quizzly2026"))
    }

    @Test
    fun `se guarda el algoritmo, las iteraciones, el salt y el hash`() {
        val partes = hash("quizzly2026").split("$")

        assertEquals("pbkdf2", partes[0])
        assertEquals("1000", partes[1])
        assertEquals("16 bytes de salt en hexadecimal", 32, partes[2].length)
        assertEquals("256 bits de hash en hexadecimal", 64, partes[3].length)
    }

    @Test
    fun `las cuentas viejas con SHA-256 siguen entrando y se detectan para convertirlas`() {
        val viejo = HashUtils.sha256("secreto123")

        assertTrue(HashUtils.esFormatoViejo(viejo))
        assertTrue(HashUtils.verificar("secreto123", viejo))
        assertFalse(HashUtils.verificar("otra", viejo))
        assertFalse(HashUtils.esFormatoViejo(hash("secreto123")))
    }

    @Test
    fun `un valor guardado danado no coincide ni rompe la app`() {
        assertFalse(HashUtils.verificar("x", "pbkdf2\$abc\$zz\$yy"))
        assertFalse(HashUtils.verificar("x", "pbkdf2\$1000"))
    }
}
