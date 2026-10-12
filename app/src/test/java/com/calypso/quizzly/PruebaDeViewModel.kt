package com.calypso.quizzly

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.calypso.quizzly.data.local.database.AppDatabase
import com.google.firebase.FirebaseApp
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Base de los tests de ViewModels. Corren en la PC con Robolectric, que simula Android:
 * la base Room es real y el reloj del hilo principal se adelanta a mano.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class PruebaDeViewModel {

    protected val app: Application get() = ApplicationProvider.getApplicationContext()

    /** Los tests no tienen que publicar nada en el ranking real: se desconecta Firebase antes de cada uno. */
    @Before
    fun desconectarFirebase() {
        FirebaseApp.getApps(app).forEach { it.delete() }
    }

    /** Cada test arranca con una base vacía: se cierra y se olvida la instancia compartida. */
    @After
    fun cerrarBase() {
        AppDatabase.obtener(app).close()
        AppDatabase::class.java.getDeclaredField("instancia").apply {
            isAccessible = true
            set(null, null)
        }
    }

    /** Adelanta el reloj del hilo principal: así corren los delay() del temporizador sin esperar de verdad. */
    protected fun avanzar(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    /** Las consultas de Room corren en otro hilo y vuelven al principal: se espera hasta que se cumpla la condición. */
    protected fun esperarHasta(condicion: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (!condicion()) {
            check(System.currentTimeMillis() < limite) { "La condición no se cumplió a tiempo" }
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
