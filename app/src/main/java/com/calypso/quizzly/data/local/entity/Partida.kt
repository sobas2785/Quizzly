package com.calypso.quizzly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "partidas",
    foreignKeys = [
        ForeignKey(
            entity = Usuario::class,
            parentColumns = ["id"],
            childColumns = ["usuarioId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Partida(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val usuarioId: Long,
    val modo: String,           // "individual" o "duelo_local"
    val categoriaId: Int,       // categoría jugada (ver Categorias.kt)
    val puntos: Int,
    val correctas: Int,
    val totalPreguntas: Int,
    val rachaMaxima: Int,
    val nivelAlcanzado: Int,
    val fecha: Long = System.currentTimeMillis()
)
