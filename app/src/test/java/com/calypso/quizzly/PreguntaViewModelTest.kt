package com.calypso.quizzly

import com.calypso.quizzly.data.local.entity.Pregunta
import com.calypso.quizzly.data.repository.PreguntaRepository
import com.calypso.quizzly.viewmodel.EstadoPregunta
import com.calypso.quizzly.viewmodel.PreguntaViewModel
import com.calypso.quizzly.viewmodel.ResultadoRonda
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La ronda de preguntas: puntaje, racha, temporizador y resultado (RF-05, RF-06, RF-14). */
class PreguntaViewModelTest : PruebaDeViewModel() {

    // En todas las preguntas de prueba la respuesta correcta es la B.
    private fun pregunta(n: Int) = Pregunta(
        categoriaId = 1, texto = "¿Pregunta $n?",
        opcionA = "a", opcionB = "b", opcionC = "c", opcionD = "d",
        respuesta = "B", dificultad = "Facil"
    )

    /** Un ViewModel con la ronda ya arrancada y la primera pregunta en pantalla. */
    private fun rondaDe(cantidad: Int): PreguntaViewModel {
        runBlocking { PreguntaRepository(app).reemplazarTodas((1..5).map(::pregunta)) }
        return PreguntaViewModel(app).also { vm ->
            vm.iniciar(categoriaId = 1, cantidad = cantidad)
            esperarHasta { vm.estado.value?.pregunta != null }
        }
    }

    private val PreguntaViewModel.ahora: EstadoPregunta get() = estado.value!!

    private fun PreguntaViewModel.pasarALaSiguiente(numero: Int) {
        avanzar(900)
        esperarHasta { ahora.numero == numero && !ahora.respondida }
    }

    @Test
    fun `la ronda arranca en la pregunta 1 con el tiempo completo`() {
        val vm = rondaDe(2)

        assertEquals(1, vm.ahora.numero)
        assertEquals(2, vm.ahora.cantidad)
        assertEquals(15, vm.ahora.segundosRestantes)
        assertFalse(vm.ahora.respondida)
    }

    @Test
    fun `acertar al toque suma base mas tiempo y arranca la racha`() {
        val vm = rondaDe(2)
        vm.responder("B")

        assertTrue(vm.ahora.respondida)
        assertEquals("B", vm.ahora.letraElegida)
        assertEquals(1, vm.ahora.racha)
        assertEquals(25, vm.ahora.puntos) // 10 base + 15 segundos
    }

    @Test
    fun `el temporizador descuenta un segundo por vez y da menos puntos`() {
        val vm = rondaDe(2)
        avanzar(4_000)
        assertEquals(11, vm.ahora.segundosRestantes)

        vm.responder("B")
        assertEquals(21, vm.ahora.puntos) // 10 base + 11 segundos
    }

    @Test
    fun `errar corta la racha y no suma`() {
        val vm = rondaDe(3)
        vm.responder("B")
        vm.pasarALaSiguiente(2)
        vm.responder("A")

        assertEquals(0, vm.ahora.racha)
        assertEquals(25, vm.ahora.puntos)
    }

    @Test
    fun `la segunda respuesta seguida suma el bonus de racha`() {
        val vm = rondaDe(3)
        vm.responder("B")
        vm.pasarALaSiguiente(2)
        vm.responder("B")

        assertEquals(2, vm.ahora.racha)
        assertEquals(25 + 30, vm.ahora.puntos) // la segunda: 10 + 15 + 5 de racha
    }

    @Test
    fun `tocar dos veces no suma dos veces`() {
        val vm = rondaDe(2)
        vm.responder("B")
        vm.responder("B")

        assertEquals(25, vm.ahora.puntos)
    }

    @Test
    fun `si se acaba el tiempo cuenta como incorrecta`() {
        val vm = rondaDe(2)
        vm.responder("B")
        vm.pasarALaSiguiente(2)

        avanzar(15_000)

        assertTrue(vm.ahora.tiempoAgotado)
        assertEquals(0, vm.ahora.segundosRestantes)
        assertEquals(0, vm.ahora.racha)
        assertEquals(25, vm.ahora.puntos)
    }

    @Test
    fun `al terminar la ultima pregunta devuelve el resultado de la ronda`() {
        val vm = rondaDe(2)
        vm.responder("B")
        vm.pasarALaSiguiente(2)
        vm.responder("C")
        assertNull(vm.ahora.resultado)

        avanzar(900)
        esperarHasta { vm.ahora.resultado != null }

        assertEquals(ResultadoRonda(puntos = 25, correctas = 1, total = 2, rachaMaxima = 1), vm.ahora.resultado)
    }

    @Test
    fun `volver a iniciar no reinicia la ronda, como cuando se gira el celular`() {
        val vm = rondaDe(2)
        vm.responder("B")

        vm.iniciar(categoriaId = 1, cantidad = 2)

        assertEquals(1, vm.ahora.numero)
        assertEquals(25, vm.ahora.puntos)
    }

    @Test
    fun `si la categoria se queda sin preguntas la ronda se cierra con lo jugado`() {
        runBlocking { PreguntaRepository(app).reemplazarTodas(listOf(pregunta(1))) }
        val vm = PreguntaViewModel(app)
        vm.iniciar(categoriaId = 1, cantidad = 5)
        esperarHasta { vm.ahora.pregunta != null }

        vm.responder("B")
        avanzar(900)
        esperarHasta { vm.ahora.resultado != null }

        assertEquals(ResultadoRonda(puntos = 25, correctas = 1, total = 1, rachaMaxima = 1), vm.ahora.resultado)
    }
}
