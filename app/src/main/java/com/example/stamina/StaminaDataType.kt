package com.example.stamina

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

class StaminaDataType(
    extension: String,
    private val engine: StaminaEngine,
) : DataTypeImpl(extension, TYPE_ID) {

    /** Valor numérico (stamina actual, %) por si otro campo o la vista estándar lo consumen. */
    override fun startStream(emitter: Emitter<StreamState>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            engine.state.collect { s ->
                emitter.onNext(
                    StreamState.Streaming(
                        DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to s.actualPct))
                    )
                )
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    /** Vista gráfica del boceto: número + barra de dos capas + pie. */
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        // Celda baja = versión compacta, sin pie. Umbral a ajustar en el dispositivo.
        val compact = config.viewSize.second < COMPACT_HEIGHT_PX

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            engine.state.collect { s -> emitter.updateView(render(context, s, compact)) }
        }
        emitter.setCancellable { scope.cancel() }
    }

    private fun render(context: Context, s: StaminaSnapshot, compact: Boolean): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.stamina_field)
        val actual = s.actualPct.toInt().coerceIn(0, 100)
        val potential = s.potentialPct.toInt().coerceIn(0, 100)

        rv.setTextViewText(R.id.tvValue, actual.toString())
        rv.setTextViewText(R.id.tvPotential, context.getString(R.string.potential_fmt, potential))

        rv.setProgressBar(R.id.pbPotential, 100, potential, false)
        listOf(R.id.pbGreen, R.id.pbAmber, R.id.pbRed).forEach {
            rv.setViewVisibility(it, View.INVISIBLE)
        }
        val active = when {
            actual > GREEN_ABOVE -> R.id.pbGreen
            actual >= AMBER_ABOVE -> R.id.pbAmber
            else -> R.id.pbRed
        }
        rv.setViewVisibility(active, View.VISIBLE)
        rv.setProgressBar(active, 100, actual, false)

        rv.setViewVisibility(R.id.footer, if (compact) View.GONE else View.VISIBLE)
        rv.setTextViewText(
            R.id.tvFooterLeft,
            String.format(Locale.getDefault(), "W′ %.1f / %.0f kJ", s.wBalKj, s.wPrimeEffKj)
        )
        rv.setTextViewText(R.id.tvFooterRight, timeToEmptyText(context, s.timeToEmptySec))
        return rv
    }

    private fun timeToEmptyText(context: Context, sec: Double?): String = when {
        sec == null -> context.getString(R.string.sustainable)
        sec < 60 -> context.getString(R.string.tte_seconds_fmt, sec.toInt())
        sec > 99 * 60 -> context.getString(R.string.tte_over_fmt)
        else -> context.getString(R.string.tte_minutes_fmt, (sec / 60).toInt())
    }

    companion object {
        const val TYPE_ID = "stamina"
        private const val GREEN_ABOVE = 50
        private const val AMBER_ABOVE = 25
        private const val COMPACT_HEIGHT_PX = 220
    }
}
