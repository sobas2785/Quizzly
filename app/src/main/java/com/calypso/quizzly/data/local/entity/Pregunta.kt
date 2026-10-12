package com.calypso.quizzly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preguntas")
data class Pregunta(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val categoriaId: Int,      // ver game/Categorias.kt (1=Geografía ... 6=Música)
    val texto: String,
    val opcionA: String,
    val opcionB: String,
    val opcionC: String,
    val opcionD: String,
    val respuesta: String,     // "A", "B", "C" o "D"
    val dificultad: String     // "Facil", "Medio", "Dificil"
)