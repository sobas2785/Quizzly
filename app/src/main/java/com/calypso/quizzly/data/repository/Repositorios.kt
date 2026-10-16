package com.calypso.quizzly.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.calypso.quizzly.data.local.database.AppDatabase
import com.calypso.quizzly.data.local.entity.LogroUsuario
import com.calypso.quizzly.data.local.entity.Partida
import com.calypso.quizzly.data.local.entity.Pregunta
import com.calypso.quizzly.data.local.entity.Usuario
import com.calypso.quizzly.util.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Capa Repository: envuelve los DAO para que las Activities no toquen Room directamente.
 */
class UsuarioRepository(context: Context) {

    private val dao = AppDatabase.obtener(context).usuarioDao()

    sealed class ResultadoRegistro {
        data class Ok(val usuarioId: Long) : ResultadoRegistro()
        data class Error(val mensaje: String) : ResultadoRegistro()
    }

    suspend fun registrar(nombreUsuario: String, correo: String, contrasena: String): ResultadoRegistro {
        if (dao.obtenerPorCorreo(correo) != null) {
            return ResultadoRegistro.Error("Ya existe una cuenta con ese correo")
        }
        if (dao.obtenerPorNombreUsuario(nombreUsuario) != null) {
            return ResultadoRegistro.Error("Ese nombre de usuario ya está en uso")
        }
        val id = dao.insertar(
            Usuario(
                nombreUsuario = nombreUsuario,
                correo = correo,
                contrasenaHash = hashear(contrasena)
            )
        )
        return ResultadoRegistro.Ok(id)
    }

    /**
     * Devuelve el usuario si el correo existe y la contraseña coincide.
     * Las cuentas del formato anterior (SHA-256 sin salt) se pasan al formato nuevo al ingresar.
     */
    suspend fun autenticar(correo: String, contrasena: String): Usuario? {
        val usuario = dao.obtenerPorCorreo(correo) ?: return null
        val coincide = withContext(Dispatchers.Default) { HashUtils.verificar(contrasena, usuario.contrasenaHash) }
        if (!coincide) return null

        if (HashUtils.esFormatoViejo(usuario.contrasenaHash)) {
            dao.actualizar(usuario.copy(contrasenaHash = hashear(contrasena)))
        }
        return usuario
    }

    /** PBKDF2 es lento a propósito: se calcula fuera del hilo principal para no trabar la pantalla. */
    private suspend fun hashear(contrasena: String) = withContext(Dispatchers.Default) { HashUtils.hashear(contrasena) }

    suspend fun obtenerPorId(id: Long): Usuario? = dao.obtenerPorId(id)

    suspend fun actualizarProgreso(usuarioId: Long, nivel: Int, puntos: Int) =
        dao.actualizarProgreso(usuarioId, nivel, puntos)

    suspend fun actualizarRachaMaxima(usuarioId: Long, racha: Int) = dao.actualizarRachaMaxima(usuarioId, racha)

    suspend fun obtenerRankingGlobal(): List<Usuario> = dao.obtenerRankingGlobal()
}

class PartidaRepository(context: Context) {

    private val dao = AppDatabase.obtener(context).partidaDao()

    suspend fun guardar(partida: Partida): Long = dao.insertar(partida)

    suspend fun obtenerHistorialReciente(usuarioId: Long): List<Partida> =
        dao.obtenerHistorialReciente(usuarioId)

    suspend fun contarPartidasJugadas(usuarioId: Long): Int = dao.contarPartidasJugadas(usuarioId)

    suspend fun contarCategoriasJugadas(usuarioId: Long): Int =
        dao.obtenerCategoriasJugadas(usuarioId).size

    suspend fun obtenerDesde(desde: Long): List<Partida> = dao.obtenerDesde(desde)
}

class PreguntaRepository(context: Context) {

    private val db = AppDatabase.obtener(context)
    private val dao = db.preguntaDao()

    /**
     * Cambia el banco entero en una sola transacción: mientras tanto, quien lea la tabla
     * sigue viendo el banco anterior completo, nunca uno vacío o a medias.
     */
    suspend fun reemplazarTodas(preguntas: List<Pregunta>) = db.withTransaction {
        dao.borrarTodas()
        dao.insertarTodas(preguntas)
    }

    suspend fun contarPreguntas(): Int = dao.contarPreguntas()

    /**
     * Una pregunta al azar de la categoría, sin repetir las ya usadas en la partida.
     * Si la categoría se queda sin preguntas de esa dificultad, cae a cualquier otra dificultad.
     */
    suspend fun siguientePregunta(
        categoriaId: Int,
        dificultad: String,
        yaUsadas: Set<Long>
    ): Pregunta? {
        val deLaCategoria = dao.obtenerPorCategoria(categoriaId).filter { it.id !in yaUsadas }
        return deLaCategoria.filter { it.dificultad == dificultad }.randomOrNull()
            ?: deLaCategoria.randomOrNull()
    }
}

class LogroRepository(context: Context) {

    private val dao = AppDatabase.obtener(context).logroUsuarioDao()

    /** Inserta los logros que el usuario todavía no tenía y devuelve esos códigos nuevos. */
    suspend fun desbloquear(usuarioId: Long, codigos: List<String>): List<String> {
        val nuevos = codigos.filterNot { dao.yaTieneLogro(usuarioId, it) }
        nuevos.forEach { dao.insertar(LogroUsuario(usuarioId = usuarioId, logroId = it)) }
        return nuevos
    }

    suspend fun obtenerDeUsuario(usuarioId: Long): List<LogroUsuario> =
        dao.obtenerLogrosDeUsuario(usuarioId)
}
