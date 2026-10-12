package com.calypso.quizzly.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.calypso.quizzly.data.repository.UsuarioRepository
import com.calypso.quizzly.data.repository.UsuarioRepository.ResultadoRegistro
import com.calypso.quizzly.util.Sesion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Lo que el login y el registro le piden a la pantalla, una sola vez. */
sealed interface EfectoAcceso {
    data class Aviso(val mensaje: String) : EfectoAcceso

    /** La sesión ya quedó iniciada: hay que pasar al menú. */
    data object Entrar : EfectoAcceso
}

/** Base común del login y el registro: el botón deshabilitado mientras se consulta la base, y los efectos. */
abstract class AccesoViewModel(app: Application) : AndroidViewModel(app) {

    protected val usuarios = UsuarioRepository(app)

    protected val _ocupado = MutableLiveData(false)
    val ocupado: LiveData<Boolean> = _ocupado

    // Con búfer: si el resultado llega con la pantalla tapada, se entrega cuando vuelve.
    private val _efectos = Channel<EfectoAcceso>(Channel.BUFFERED)
    val efectos = _efectos.receiveAsFlow()

    protected fun avisar(mensaje: String) {
        _efectos.trySend(EfectoAcceso.Aviso(mensaje))
    }

    protected fun entrar(usuarioId: Long) {
        Sesion.iniciar(getApplication(), usuarioId)
        _efectos.trySend(EfectoAcceso.Entrar)
    }
}

/** Inicio de sesión (RF-02). */
class LoginViewModel(app: Application) : AccesoViewModel(app) {

    fun ingresar(correo: String, contrasena: String) {
        if (correo.isEmpty() || contrasena.isEmpty()) {
            avisar("Completá correo y contraseña")
            return
        }

        _ocupado.value = true
        viewModelScope.launch {
            val usuario = usuarios.autenticar(correo, contrasena)
            _ocupado.value = false

            if (usuario == null) avisar("Correo o contraseña incorrectos") else entrar(usuario.id)
        }
    }
}

/** Registro de usuario (RF-01). */
class RegistroViewModel(app: Application) : AccesoViewModel(app) {

    fun registrar(nombre: String, correo: String, contrasena: String) {
        validar(nombre, correo, contrasena)?.let {
            avisar(it)
            return
        }

        _ocupado.value = true
        viewModelScope.launch {
            when (val resultado = usuarios.registrar(nombre, correo, contrasena)) {
                is ResultadoRegistro.Ok -> entrar(resultado.usuarioId)
                is ResultadoRegistro.Error -> {
                    _ocupado.value = false
                    avisar(resultado.mensaje)
                }
            }
        }
    }

    /** Validaciones de formulario antes de escribir en la base. Devuelve el error, o null si está todo bien. */
    private fun validar(nombre: String, correo: String, contrasena: String): String? = when {
        nombre.isEmpty() || correo.isEmpty() || contrasena.isEmpty() ->
            "Completá todos los campos"

        nombre.length < 3 -> "El nombre de usuario necesita al menos 3 caracteres"

        !Patterns.EMAIL_ADDRESS.matcher(correo).matches() -> "El correo no es válido"

        contrasena.length < 6 -> "La contraseña necesita al menos 6 caracteres"

        else -> null
    }
}
