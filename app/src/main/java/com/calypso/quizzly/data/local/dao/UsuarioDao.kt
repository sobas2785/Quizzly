package com.calypso.quizzly.data.local.dao
import androidx.room.*
import com.calypso.quizzly.data.local.entity.Usuario

@Dao
interface UsuarioDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(usuario: Usuario): Long

    @Update
    suspend fun actualizar(usuario: Usuario)

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun obtenerPorId(id: Long): Usuario?

    @Query("SELECT * FROM usuarios WHERE correo = :correo LIMIT 1")
    suspend fun obtenerPorCorreo(correo: String): Usuario?

    @Query("SELECT * FROM usuarios WHERE nombreUsuario = :nombreUsuario LIMIT 1")
    suspend fun obtenerPorNombreUsuario(nombreUsuario: String): Usuario?

    @Query("UPDATE usuarios SET nivel = :nivel, puntosTotales = :puntos WHERE id = :usuarioId")
    suspend fun actualizarProgreso(usuarioId: Long, nivel: Int, puntos: Int)

    /** Solo la cambia si la racha nueva supera a la guardada. */
    @Query("UPDATE usuarios SET rachaMaxima = :racha WHERE id = :usuarioId AND rachaMaxima < :racha")
    suspend fun actualizarRachaMaxima(usuarioId: Long, racha: Int)

    @Query("SELECT * FROM usuarios ORDER BY puntosTotales DESC")
    suspend fun obtenerRankingGlobal(): List<Usuario>
}