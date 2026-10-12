package com.calypso.quizzly.data.local.dao

import androidx.room.*
import com.calypso.quizzly.data.local.entity.Pregunta

@Dao
interface PreguntaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(preguntas: List<Pregunta>)

    @Query("""
        SELECT * FROM preguntas 
        WHERE categoriaId = :categoriaId AND dificultad = :dificultad 
        ORDER BY RANDOM() LIMIT 1
    """)
    suspend fun obtenerPreguntaAleatoria(categoriaId: Int, dificultad: String): Pregunta?

    @Query("SELECT COUNT(*) FROM preguntas")
    suspend fun contarPreguntas(): Int

    @Query("DELETE FROM preguntas")
    suspend fun borrarTodas()

    @Query("SELECT * FROM preguntas WHERE categoriaId = :categoriaId")
    suspend fun obtenerPorCategoria(categoriaId: Int): List<Pregunta>
}