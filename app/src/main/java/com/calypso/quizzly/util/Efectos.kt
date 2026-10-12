package com.calypso.quizzly.util

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Escucha los efectos de una sola vez de un ViewModel (un sonido, un aviso, pasar de pantalla)
 * mientras la pantalla está visible. A diferencia del estado, un efecto no se repite al girar el celular.
 */
fun <T> AppCompatActivity.alRecibir(efectos: Flow<T>, accion: (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            efectos.collect { accion(it) }
        }
    }
}
