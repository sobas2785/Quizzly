package com.calypso.quizzly

import com.calypso.quizzly.util.Sesion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Vencimiento de la sesión (RNF-03). */
class SesionTest {

    private val dia = TimeUnit.DAYS.toMillis(1)
    private val inicio = 1_760_000_000_000L

    @Test
    fun `la sesion dura 30 dias y despues vence`() {
        assertFalse(Sesion.vencio(inicio, inicio + 29 * dia))
        assertFalse(Sesion.vencio(inicio, inicio + 30 * dia))
        assertTrue(Sesion.vencio(inicio, inicio + 30 * dia + 1))
    }
}
