package com.calypso.quizzly.data.local.dao

import androidx.room.*
import com.calypso.quizzly.data.local.entity.LogroUsuario

@Dao
interface LogroUsuarioDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(logro: LogroUsuario)

    @Query("SELECT * FROM logros_usuario WHERE usuarioId = :usuarioId")
    suspend fun obtenerLogrosDeUsuario(usuarioId: Long): List<LogroUsuario>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM logros_usuario 
            WHERE usuarioId = :usuarioId AND logroId = :logroId
        )
    """)
    suspend fun yaTieneLogro(usuarioId: Long, logroId: String): Boolean
}