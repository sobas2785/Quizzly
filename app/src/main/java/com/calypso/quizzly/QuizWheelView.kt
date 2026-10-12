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

class QuizWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Los gajos salen de Categorias, la misma lista que usa el resto del juego y la base.
    val categories = Categorias.todas.map { it.nombre }

    private val personajes = Categorias.todas.map { ContextCompat.getDrawable(context, it.ilustracion) }

    private val colors = Categorias.todas.map { it.color }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }

    // Disco blanco detrás de cada personaje, para que no se pierda sobre el color del gajo.
    private val discoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(
            18f,
            0f,
            5f,
            Color.argb(80, 0, 0, 0)
        )
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6D28D9")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var rotationAngle = 0f
        set(value) {
            field = value % 360f
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        val padding = size * 0.035f
        val radius = (size / 2f) - padding

        val wheelRect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        val sweepAngle = 360f / categories.size

        canvas.save()
        canvas.rotate(rotationAngle, centerX, centerY)

        for (i in categories.indices) {

            val startAngle = -90f + i * sweepAngle

            segmentPaint.color = colors[i]
            segmentPaint.style = Paint.Style.FILL

            canvas.drawArc(
                wheelRect,
                startAngle,
                sweepAngle,
                true,
                segmentPaint
            )

            canvas.drawArc(
                wheelRect,
                startAngle,
                sweepAngle,
                true,
                separatorPaint
            )

            val middleAngle =
                Math.toRadians((startAngle + sweepAngle / 2f).toDouble())

            val textRadius = radius * 0.62f

            val x =
                centerX + cos(middleAngle).toFloat() * textRadius

            val y =
                centerY + sin(middleAngle).toFloat() * textRadius

            val centroPersonaje = y - size * 0.02f
            val radioDisco = size * 0.068f

            canvas.drawCircle(x, centroPersonaje, radioDisco, discoPaint)

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

            textPaint.textSize = size * 0.035f

            val categoryText =
                if (categories[i] == "Entretenimiento") {
                    "Entreten."
                } else {
                    categories[i]
                }

            canvas.drawText(
                categoryText,
                x,
                y + size * 0.088f,
                textPaint
            )
        }

        canvas.restore()

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            outerBorderPaint
        )

        val centerRadius = radius * 0.19f

        canvas.drawCircle(
            centerX,
            centerY,
            centerRadius,
            centerPaint
        )

        centerTextPaint.textSize = size * 0.11f

        val centerOffset =
            (centerTextPaint.descent() + centerTextPaint.ascent()) / 2

        canvas.drawText(
            "Q",
            centerX,
            centerY - centerOffset,
            centerTextPaint
        )
    }

    fun getRotationForCategory(index: Int): Float {

        val sweep = 360f / categories.size

        val categoryCenter =
            index * sweep + sweep / 2f

        return -categoryCenter
    }
}