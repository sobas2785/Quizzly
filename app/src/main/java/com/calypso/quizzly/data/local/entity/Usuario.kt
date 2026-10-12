package com.calypso.quizzly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nombreUsuario: String,
    val correo: String,
    val contrasenaHash: String,

    val nivel: Int = 1,
    val puntosTotales: Int = 0,
    val rachaMaxima: Int = 0,

    val fechaRegistro: Long = System.currentTimeMillis()
)