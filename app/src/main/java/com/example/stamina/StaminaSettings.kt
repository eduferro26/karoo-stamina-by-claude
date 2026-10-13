package com.example.stamina

import android.content.Context
import android.content.SharedPreferences

/**
 * Ajustes persistentes, compartidos entre la pantalla de ajustes y el motor.
 * El peso es opcional: si no hay valor propio se usa el del perfil del Karoo.
 */
class StaminaSettings(context: Context) {
    private val prefs = context.getSharedPreferences("stamina", Context.MODE_PRIVATE)

    val weightOverrideKg: Double?
        get() = if (prefs.contains(KEY_WEIGHT)) prefs.getFloat(KEY_WEIGHT, 0f).toDouble() else null

    fun load(profileWeightKg: Double? = null): StaminaParams {
        val d = StaminaParams()
        return StaminaParams(
            cp = prefs.getFloat("cp", d.cp.toFloat()).toDouble(),
            wPrime = prefs.getFloat("wPrime", d.wPrime.toFloat()).toDouble(),
            weightKg = weightOverrideKg ?: profileWeightKg ?: d.weightKg,
            freeKjPerKg = prefs.getFloat("freeKjPerKg", d.freeKjPerKg.toFloat()).toDouble(),
            lossPctPerKjKg = prefs.getFloat("lossPctPerKjKg", d.lossPctPerKjKg.toFloat()).toDouble(),
            maxLossPct = prefs.getFloat("maxLossPct", d.maxLossPct.toFloat()).toDouble(),
        )
    }

    /** [weightOverrideKg] = null borra el peso propio y vuelve a usar el del perfil. */
    fun save(p: StaminaParams, weightOverrideKg: Double?) {
        prefs.edit().apply {
            putFloat("cp", p.cp.toFloat())
            putFloat("wPrime", p.wPrime.toFloat())
            putFloat("freeKjPerKg", p.freeKjPerKg.toFloat())
            putFloat("lossPctPerKjKg", p.lossPctPerKjKg.toFloat())
            putFloat("maxLossPct", p.maxLossPct.toFloat())
            if (weightOverrideKg != null) putFloat(KEY_WEIGHT, weightOverrideKg.toFloat()) else remove(KEY_WEIGHT)
        }.apply()
    }

    fun resetToDefaults() = prefs.edit().clear().apply()

    fun register(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregister(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    private companion object { const val KEY_WEIGHT = "weightKg" }
}
