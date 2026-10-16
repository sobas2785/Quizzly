package com.calypso.quizzly

import com.calypso.quizzly.game.Desenlace
import com.calypso.quizzly.game.DueloOnline
import com.calypso.quizzly.game.EstadoDuelo
import com.calypso.quizzly.game.ReglasDueloOnline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Reglas del duelo online: turnos, desenlace, códigos de invitación y orden de la lista. */
class DueloOnlineTest {

    private val seba = "uidA_1"
    private val magali = "uidB_1"

    private fun duelo(
        estado: EstadoDuelo = EstadoDuelo.EN_CURSO,
        turno: Int = 1,
        puntos1: Int = 0,
        puntos2: Int = 0,
        conRival: Boolean = true,
        codigo: String = "ABC234",
        actualizado: Long = 0L
    ) = DueloOnline(
        codigo = codigo, estado = estado, turno = turno, turnosJugados = 0,
        jugadorId1 = seba, nombre1 = "seba", puntos1 = puntos1, correctas1 = 0,
        jugadorId2 = magali.takeIf { conRival }, nombre2 = "magali".takeIf { conRival }, puntos2 = puntos2, correctas2 = 0,
        ultimaCategoriaId = 0, actualizado = actualizado
    )

    @Test
    fun numeroYRival() {
        val d = duelo()

        assertEquals(1, d.numeroDe(seba))
        assertEquals(2, d.numeroDe(magali))
        assertNull("Un tercero no participa", d.numeroDe("otro_1"))
        assertEquals("magali", d.rivalDe(seba))
        assertEquals("seba", d.rivalDe(magali))
    }

    @Test
    fun turnoDelJugador() {
        assertTrue(duelo(turno = 1).esTurnoDe(seba))
        assertFalse(duelo(turno = 1).esTurnoDe(magali))
        assertTrue(duelo(turno = 2).esTurnoDe(magali))

        assertFalse("Sin rival todavía no se juega", duelo(EstadoDuelo.ESPERANDO, conRival = false).esTurnoDe(seba))
        assertFalse(duelo(EstadoDuelo.TERMINADO).esTurnoDe(seba))
    }

    @Test
    fun desenlacePorJugador() {
        val terminado = duelo(EstadoDuelo.TERMINADO, puntos1 = 45, puntos2 = 50)

        assertEquals(Desenlace.PERDIDA, terminado.desenlacePara(seba))
        assertEquals(Desenlace.GANADA, terminado.desenlacePara(magali))
        assertEquals(Desenlace.EMPATE, duelo(EstadoDuelo.TERMINADO, puntos1 = 30, puntos2 = 30).desenlacePara(seba))
        assertNull(duelo(puntos1 = 45, puntos2 = 50).desenlacePara(seba))
    }

    @Test
    fun cierraEnElSextoTurno() {
        assertEquals(EstadoDuelo.EN_CURSO, ReglasDueloOnline.estadoTrasElTurno(turnosJugadosAntes = 0))
        assertEquals(EstadoDuelo.EN_CURSO, ReglasDueloOnline.estadoTrasElTurno(turnosJugadosAntes = 4))
        assertEquals(EstadoDuelo.TERMINADO, ReglasDueloOnline.estadoTrasElTurno(turnosJugadosAntes = 5))
    }

    @Test
    fun codigoDeSeisCaracteres() {
        repeat(200) { semilla ->
            val codigo = ReglasDueloOnline.nuevoCodigo(Random(semilla))
            assertEquals(6, codigo.length)
            assertTrue(codigo, codigo.none { it in "01OIL" })
            assertEquals("Un código nuevo ya está normalizado", codigo, ReglasDueloOnline.normalizarCodigo(codigo))
        }
    }

    @Test
    fun codigoNormalizado() {
        assertEquals("ABC234", ReglasDueloOnline.normalizarCodigo(" abc-234 "))
        assertEquals("ABC234", ReglasDueloOnline.normalizarCodigo("ABC 234"))

        assertNull("Muy corto", ReglasDueloOnline.normalizarCodigo("ABC2"))
        assertNull("Muy largo", ReglasDueloOnline.normalizarCodigo("ABC2345"))
        assertNull("Tiene una O, que no se usa", ReglasDueloOnline.normalizarCodigo("ABCO34"))
    }

    @Test
    fun ordenDeLaLista() {
        val terminado = duelo(EstadoDuelo.TERMINADO, codigo = "TERM22", actualizado = 900)
        val esperaAlRival = duelo(turno = 2, codigo = "RVAL22", actualizado = 500)
        val meTocaViejo = duelo(turno = 1, codigo = "VJEJ22", actualizado = 100)
        val meTocaNuevo = duelo(turno = 1, codigo = "NVEV22", actualizado = 300)

        val orden = ReglasDueloOnline.ordenar(listOf(terminado, esperaAlRival, meTocaViejo, meTocaNuevo), seba)

        assertEquals(listOf("NVEV22", "VJEJ22", "RVAL22", "TERM22"), orden.map { it.codigo })
    }
}
