package com.example.stamina

import kotlin.math.max
import kotlin.math.min

/**
 * Parámetros del modelo. Todos deben ser ajustables por el usuario.
 */
data class StaminaParams(
    val cp: Double = 250.0,              // Potencia crítica (W). Punto de partida: tu FTP.
    val wPrime: Double = 15_000.0,       // W′ (J). Típico 15-20 kJ.
    val weightKg: Double = 70.0,         // Del perfil del Karoo.
    val freeKjPerKg: Double = 20.0,      // Trabajo "gratis" antes de empezar a perder durabilidad.
    val lossPctPerKjKg: Double = 0.4,    // % de pérdida por cada kJ/kg por encima del umbral.
    val maxLossPct: Double = 35.0,       // Tope de pérdida para que el modelo no se desboque.
)

/**
 * Factor de durabilidad (1.0 = sin pérdida) tras acumular [workKj] kilojulios.
 * Se expone aparte para que la pantalla de ajustes pueda previsualizar el efecto.
 */
fun StaminaParams.durabilityFactor(workKj: Double): Double {
    val kjPerKg = workKj / weightKg
    val lossPct = min(maxLossPct, max(0.0, kjPerKg - freeKjPerKg) * lossPctPerKjKg)
    return 1.0 - lossPct / 100.0
}

/** Estado calculado, listo para pintar o publicar. */
data class StaminaSnapshot(
    val actualPct: Double,               // 0..100, relativo al W′ original (stamina "actual")
    val potentialPct: Double,            // 0..100, techo por durabilidad (stamina "potencial")
    val wBalKj: Double,
    val wPrimeEffKj: Double,
    val cpEff: Double,
    val timeToEmptySec: Double?,         // null = ritmo sostenible (P <= CP efectiva)
    val workKj: Double,
)

/**
 * Modelo puro (sin dependencias de Android) para poder testearlo en la JVM.
 *
 * 1) W′bal: forma diferencial de Skiba (2015).
 *      P > CP : dW′bal/dt = -(P - CP)
 *      P < CP : dW′bal/dt = (W′ - W′bal) * (CP - P) / W′
 * 2) Durabilidad: la CP y el W′ efectivos se escalan por un factor f (1 → 1-maxLoss)
 *    que baja con el trabajo acumulado (kJ/kg) por encima de un umbral.
 *    ES UNA APROXIMACIÓN PROPIA, no un modelo validado: calíbrala con tus FIT.
 */
class StaminaModel(params: StaminaParams = StaminaParams()) {

    @get:Synchronized
    @set:Synchronized
    var params: StaminaParams = params
        set(value) {
            field = value
            wBal = min(wBal, value.wPrime)
        }

    private var wBal: Double = params.wPrime
    private var workJ: Double = 0.0

    @Synchronized
    fun reset() {
        wBal = params.wPrime
        workJ = 0.0
    }

    @Synchronized
    fun step(powerW: Double, dtSec: Double = 1.0): StaminaSnapshot {
        val p = max(0.0, powerW)
        workJ += p * dtSec

        val f = durabilityFactor()
        val cpEff = params.cp * f
        val wEff = params.wPrime * f

        if (p > cpEff) {
            wBal -= (p - cpEff) * dtSec
        } else {
            wBal += (wEff - wBal) * (cpEff - p) / wEff * dtSec
        }
        wBal = wBal.coerceIn(0.0, wEff)

        return snapshot(p, f, cpEff, wEff)
    }

    @Synchronized
    fun snapshot(powerW: Double = 0.0): StaminaSnapshot {
        val f = durabilityFactor()
        return snapshot(powerW, f, params.cp * f, params.wPrime * f)
    }

    private fun durabilityFactor(): Double = params.durabilityFactor(workJ / 1000.0)

    private fun snapshot(p: Double, f: Double, cpEff: Double, wEff: Double) = StaminaSnapshot(
        actualPct = wBal / params.wPrime * 100.0,
        potentialPct = f * 100.0,
        wBalKj = wBal / 1000.0,
        wPrimeEffKj = wEff / 1000.0,
        cpEff = cpEff,
        timeToEmptySec = if (p > cpEff) wBal / (p - cpEff) else null,
        workKj = workJ / 1000.0,
    )
}
