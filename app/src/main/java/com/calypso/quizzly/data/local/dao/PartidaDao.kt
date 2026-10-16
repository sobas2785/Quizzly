package com.calypso.quizzly.data.local.dao

import androidx.room.*
import com.calypso.quizzly.data.local.entity.Partida

@Dao
interface PartidaDao {

    @Insert
    suspend fun insertar(partida: Partida): Long

    @Query("SELECT * FROM partidas WHERE usuarioId = :usuarioId ORDER BY fecha DESC LIMIT 10")
    suspend fun obtenerHistorialReciente(usuarioId: Long): List<Partida>

    @Query("SELECT COUNT(*) FROM partidas WHERE usuarioId = :usuarioId")
    suspend fun contarPartidasJugadas(usuarioId: Long): Int

    @Query("SELECT DISTINCT categoriaId FROM partidas WHERE usuarioId = :usuarioId")
    suspend fun obtenerCategoriasJugadas(usuarioId: Long): List<Int>

    /** Todas las partidas del dispositivo desde una fecha, para el ranking local por período. */
    @Query("SELECT * FROM partidas WHERE fecha >= :desde ORDER BY fecha")
    suspend fun obtenerDesde(desde: Long): List<Partida>
}
