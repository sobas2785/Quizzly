package com.calypso.quizzly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "logros_usuario",
    foreignKeys = [
        ForeignKey(
            entity = Usuario::class,
            parentColumns = ["id"],
            childColumns = ["usuarioId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LogroUsuario(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val usuarioId: Long,
    val logroId: String,        // ej: "primera_racha", "en_llamas"
    val fechaObtenido: Long = System.currentTimeMillis()
)