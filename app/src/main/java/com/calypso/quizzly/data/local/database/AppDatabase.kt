package com.calypso.quizzly.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.calypso.quizzly.data.local.dao.LogroUsuarioDao
import com.calypso.quizzly.data.local.dao.PartidaDao
import com.calypso.quizzly.data.local.dao.PreguntaDao
import com.calypso.quizzly.data.local.dao.UsuarioDao
import com.calypso.quizzly.data.local.entity.LogroUsuario
import com.calypso.quizzly.data.local.entity.Partida
import com.calypso.quizzly.data.local.entity.Pregunta
import com.calypso.quizzly.data.local.entity.Usuario

@Database(
    entities = [Usuario::class, Partida::class, Pregunta::class, LogroUsuario::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun partidaDao(): PartidaDao
    abstract fun preguntaDao(): PreguntaDao
    abstract fun logroUsuarioDao(): LogroUsuarioDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun obtener(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quizzly.db"
                )
                    // Durante el desarrollo preferimos recrear la base antes que escribir migraciones.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instancia = it }
            }
    }
}
