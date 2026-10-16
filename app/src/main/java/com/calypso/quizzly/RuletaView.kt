package com.calypso.quizzly

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.calypso.quizzly.game.Categorias
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** La ruleta de categorías: un gajo por categoría, con su color, su personaje y su nombre. */
class RuletaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Los gajos salen de Categorias, la misma lista que usa el resto del juego y la base.
    private val nombres = Categorias.todas.map { it.nombre }

    private val personajes = Categorias.todas.map { ContextCompat.getDrawable(context, it.ilustracion) }

    private val colores = Categorias.todas.map { it.color }

    private val pinturaGajo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val pinturaSeparador = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val pinturaBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }

    // Disco blanco detrás de cada personaje, para que no se pierda sobre el color del gajo.
    private val pinturaDisco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val pinturaTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val pinturaCentro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(18f, 0f, 5f, Color.argb(80, 0, 0, 0))
    }

    private val pinturaLetraCentro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6D28D9")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** Cuánto está girada la ruleta, en grados. */
    var angulo = 0f
        set(value) {
            field = value % 360f
            invalidate()
        }

    init {
        // La sombra del círculo central necesita dibujarse por software.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val tamano = min(width, height).toFloat()
        val centroX = width / 2f
        val centroY = height / 2f
        val radio = tamano / 2f - tamano * 0.035f
        val area = RectF(centroX - radio, centroY - radio, centroX + radio, centroY + radio)
        val anchoGajo = 360f / nombres.size

        canvas.save()
        canvas.rotate(angulo, centroX, centroY)

        for (i in nombres.indices) {
            val inicio = -90f + i * anchoGajo

            pinturaGajo.color = colores[i]
            canvas.drawArc(area, inicio, anchoGajo, true, pinturaGajo)
            canvas.drawArc(area, inicio, anchoGajo, true, pinturaSeparador)

            // Personaje y nombre van sobre la línea media del gajo, a un 62 % del radio.
            val medio = Math.toRadians((inicio + anchoGajo / 2f).toDouble())
            val x = centroX + cos(medio).toFloat() * radio * 0.62f
            val y = centroY + sin(medio).toFloat() * radio * 0.62f

            val centroPersonaje = y - tamano * 0.02f
            val radioDisco = tamano * 0.068f
            canvas.drawCircle(x, centroPersonaje, radioDisco, pinturaDisco)

            val mitad = (radioDisco * 0.86f).toInt()
            personajes[i]?.apply {
                setBounds(
                    (x - mitad).toInt(),
                    (centroPersonaje - mitad).toInt(),
                    (x + mitad).toInt(),
                    (centroPersonaje + mitad).toInt()
                )
                draw(canvas)
            }

            pinturaTexto.textSize = tamano * 0.035f
            val nombre = if (nombres[i] == "Entretenimiento") "Entreten." else nombres[i]
            canvas.drawText(nombre, x, y + tamano * 0.088f, pinturaTexto)
        }

        canvas.restore()

        canvas.drawCircle(centroX, centroY, radio, pinturaBorde)
        canvas.drawCircle(centroX, centroY, radio * 0.19f, pinturaCentro)

        // La "Q" del centro, centrada también en vertical.
        pinturaLetraCentro.textSize = tamano * 0.11f
        val ajusteVertical = (pinturaLetraCentro.descent() + pinturaLetraCentro.ascent()) / 2
        canvas.drawText("Q", centroX, centroY - ajusteVertical, pinturaLetraCentro)
    }

    /** El ángulo en el que hay que dejar la ruleta para que el puntero quede en el medio de esa categoría. */
    fun anguloDeCategoria(indice: Int): Float {
        val anchoGajo = 360f / nombres.size
        return -(indice * anchoGajo + anchoGajo / 2f)
    }
}
