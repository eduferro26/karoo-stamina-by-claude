package com.example.stamina

import android.content.SharedPreferences
import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Vive en el servicio de la extensión (no en la vista), así el estado sobrevive
 * a los cambios de pantalla. Las vistas y los data types solo observan [state].
 */
class StaminaEngine(
    private val karoo: KarooSystemService,
    private val settings: StaminaSettings,
    private val scope: CoroutineScope,
) {
    private val model = StaminaModel(settings.load())
    private val _state = MutableStateFlow(model.snapshot())
    val state: StateFlow<StaminaSnapshot> = _state

    @Volatile private var lastPower: Double = 0.0
    @Volatile private var lastPowerAtMs: Long = 0L
    @Volatile private var recording = false
    @Volatile private var profileWeightKg: Double? = null
    private var wasIdle = true

    private val consumerIds = mutableListOf<String>()
    private var tickJob: Job? = null

    // SharedPreferences guarda los listeners con referencia débil: hay que conservar este.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadParams()
    }

    private fun reloadParams() {
        model.params = settings.load(profileWeightKg)
        _state.value = model.snapshot()
    }

    fun start() {
        settings.register(prefsListener)

        consumerIds += karoo.addConsumer { ride: RideState ->
            when (ride) {
                is RideState.Recording -> {
                    if (wasIdle) {                       // actividad nueva: estado limpio
                        reloadParams()
                        model.reset()
                        _state.value = model.snapshot()
                    }
                    wasIdle = false
                    recording = true
                }
                is RideState.Paused -> recording = false // en pausa no se recupera W′
                is RideState.Idle -> { recording = false; wasIdle = true }
            }
        }

        consumerIds += karoo.addConsumer { profile: UserProfile ->
            // Peso del perfil del Karoo (kg). Solo se usa si no hay peso propio en los ajustes.
            profileWeightKg = profile.weight.toDouble()
            reloadParams()
        }

        consumerIds += karoo.addConsumer(OnStreamState.StartStreaming(DataType.Type.POWER)) { event: OnStreamState ->
            val s = event.state
            if (s is StreamState.Streaming) {
                lastPower = s.dataPoint.singleValue ?: 0.0
                lastPowerAtMs = SystemClock.elapsedRealtime()
            }
        }

        tickJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                val dt = ((now - last) / 1000.0).coerceIn(0.5, 3.0)
                last = now
                val fresh = now - lastPowerAtMs < 3000   // sin dato reciente: no se actualiza
                if (recording && fresh) _state.value = model.step(lastPower, dt)
            }
        }
    }

    fun stop() {
        settings.unregister(prefsListener)
        consumerIds.forEach { karoo.removeConsumer(it) }
        consumerIds.clear()
        tickJob?.cancel()
    }
}
