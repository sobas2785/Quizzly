package com.calypso.quizzly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.launch

/**
 * Sala del duelo local (P7). El Jugador 1 se completa con el usuario logueado, que es a quien
 * se le guarda la partida al terminar.
 */
class SalaDueloViewModel(app: Application) : AndroidViewModel(app) {

    private val usuarios = UsuarioRepository(app)

    private val _nombreDelUsuario = MutableLiveData<String>()
    val nombreDelUsuario: LiveData<String> = _nombreDelUsuario

    init {
        viewModelScope.launch {
            usuarios.obtenerPorId(Sesion.usuarioId(app))?.let { _nombreDelUsuario.value = it.nombreUsuario }
        }
    }
}
