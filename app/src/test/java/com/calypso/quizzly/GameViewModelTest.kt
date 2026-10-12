package com.calypso.quizzly

import androidx.lifecycle.SavedStateHandle
import com.calypso.quizzly.data.repository.PartidaRepository
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.data.repository.UsuarioRepository.ResultadoRegistro
import com.calypso.quizzly.game.Categorias
import com.calypso.quizzly.game.Desenlace
import com.calypso.quizzly.util.Sesion
import com.calypso.quizzly.viewmodel.CierreDePartida
import com.calypso.quizzly.viewmodel.EstadoJuego
import com.calypso.quizzly.viewmodel.FaseDeJuego
import com.calypso.quizzly.viewmodel.GameViewModel
import com.calypso.quizzly.viewmodel.ResultadoRonda
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La partida: ruleta, turnos y marcador del duelo, y el guardado al terminar (RF-04, RF-11). */
class GameViewModelTest : PruebaDeViewModel() {

    private val turno = ResultadoRonda(puntos = 20, correctas = 1, total = 1, rachaMaxima = 1)

    private fun memoriaDeDuelo() = SavedStateHandle(mapOf(GameActivity.EXTRA_MODO to GameActivity.MODO_DUELO))

    private val GameViewModel.ahora: EstadoJuego get() = estado.value!!

    /** Registra un usuario y lo deja logueado, como hace la pantalla de registro. */
    private fun iniciarSesion(): Long {
        val resultado = runBlocking { UsuarioRepository(app).registrar("seba", "seba@quizzly.test", "secreto1") }
        val usuarioId = (resultado as ResultadoRegistro.Ok).usuarioId
        Sesion.iniciar(app, usuarioId)
        return usuarioId
    }

    @Test
    fun `la ruleta sortea una categoria y esa es la que se juega`() {
        val vm = GameViewModel(app, memoriaDeDuelo())
        assertEquals(FaseDeJuego.LISTA_PARA_GIRAR, vm.ahora.fase)

        val indice = vm.girar()!!
        assertEquals(FaseDeJuego.GIRANDO, vm.ahora.fase)
        assertNull("No se puede girar mientras gira", vm.girar())

        vm.ruletaDetenida()
        assertEquals(FaseDeJuego.CATEGORIA_ELEGIDA, vm.ahora.fase)

        val ronda = vm.rondaPorJugar()
        assertEquals(Categorias.porIndice(indice).id, ronda.categoriaId)
        assertEquals("En el duelo se responde de a una pregunta", 1, ronda.cantidad)
    }

    @Test
    fun `desde una card del menu se juega esa categoria con cinco preguntas`() {
        val vm = GameViewModel(app, SavedStateHandle(mapOf(GameActivity.EXTRA_CATEGORIA_FIJA to 4)))

        assertEquals(4, vm.categoriaFija)
        assertFalse(vm.esDuelo)
        assertEquals(4, vm.rondaPorJugar().categoriaId)
        assertEquals(5, vm.rondaPorJugar().cantidad)
    }

    @Test
    fun `en el duelo los turnos se alternan y cada jugador suma lo suyo`() {
        val vm = GameViewModel(app, memoriaDeDuelo())

        assertTrue("Después de Jugador 1 le toca a Jugador 2", vm.rondaTerminada(turno))
        assertEquals(1, vm.ahora.jugadorDeTurno)
        assertEquals(listOf(20, 0), vm.ahora.puntos)

        assertTrue(vm.rondaTerminada(turno.copy(puntos = 35)))
        assertEquals(0, vm.ahora.jugadorDeTurno)
        assertEquals(listOf(20, 35), vm.ahora.puntos)
        assertEquals(FaseDeJuego.LISTA_PARA_GIRAR, vm.ahora.fase)
    }

    @Test
    fun `salir de la ronda con atras no cambia el turno ni el marcador`() {
        val vm = GameViewModel(app, memoriaDeDuelo())
        vm.rondaTerminada(turno)
        vm.girar()
        vm.ruletaDetenida()

        vm.rondaCancelada()

        assertEquals(1, vm.ahora.jugadorDeTurno)
        assertEquals(listOf(20, 0), vm.ahora.puntos)
        assertEquals(FaseDeJuego.LISTA_PARA_GIRAR, vm.ahora.fase)
    }

    @Test
    fun `despues de tres turnos por jugador el duelo se guarda y avisa el desenlace`() {
        val usuarioId = iniciarSesion()
        val vm = GameViewModel(app, memoriaDeDuelo())

        repeat(5) { assertTrue(vm.rondaTerminada(turno)) }
        assertFalse("El sexto turno cierra el duelo", vm.rondaTerminada(turno))
        assertEquals(FaseDeJuego.GUARDANDO, vm.ahora.fase)

        esperarHasta { vm.cierre.value != null }
        val cierre = vm.cierre.value as CierreDePartida.Guardada

        assertEquals(listOf(60, 60), cierre.puntos)
        assertEquals(Desenlace.EMPATE, cierre.desenlace)
        // Se guarda la partida del usuario logueado, que es Jugador 1.
        assertEquals(60, cierre.resumen.puntosTotales)
        assertEquals(1, runBlocking { PartidaRepository(app).contarPartidasJugadas(usuarioId) })
    }

    @Test
    fun `la partida individual se cierra despues de una ronda y desbloquea logros`() {
        iniciarSesion()
        val vm = GameViewModel(app, SavedStateHandle())

        val sigue = vm.rondaTerminada(ResultadoRonda(puntos = 70, correctas = 3, total = 5, rachaMaxima = 3))
        assertFalse(sigue)

        esperarHasta { vm.cierre.value != null }
        val cierre = vm.cierre.value as CierreDePartida.Guardada

        assertEquals(Desenlace.GANADA, cierre.desenlace)
        assertEquals(70, cierre.resumen.puntos)
        assertEquals(listOf("primera_partida", "primera_racha"), cierre.resumen.logrosNuevos)
    }

    @Test
    fun `sin sesion iniciada no se guarda nada`() {
        val vm = GameViewModel(app, SavedStateHandle())
        vm.rondaTerminada(turno)

        assertEquals(CierreDePartida.SinSesion, vm.cierre.value)
    }

    @Test
    fun `el marcador y el turno se recuperan si Android cierra la app`() {
        val memoria = memoriaDeDuelo()
        val vm = GameViewModel(app, memoria)
        vm.rondaTerminada(turno)
        vm.girar()
        vm.ruletaDetenida()
        val indice = vm.ahora.indiceSorteado

        // Un ViewModel nuevo con la misma memoria guardada: lo que pasa al volver a una app cerrada.
        val recuperado = GameViewModel(app, memoria)

        assertTrue(recuperado.esDuelo)
        assertEquals(listOf(20, 0), recuperado.ahora.puntos)
        assertEquals(1, recuperado.ahora.jugadorDeTurno)
        assertEquals(FaseDeJuego.CATEGORIA_ELEGIDA, recuperado.ahora.fase)
        assertEquals(indice, recuperado.ahora.indiceSorteado)
    }
}
