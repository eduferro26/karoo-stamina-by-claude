package com.example.stamina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaminaModelTest {

    private val params = StaminaParams(cp = 250.0, wPrime = 15_000.0, weightKg = 70.0)

    @Test fun `a 400 W se agota W' en unos 100 s`() {
        val m = StaminaModel(params)
        repeat(100) { m.step(400.0) }
        assertEquals(0.0, m.snapshot().wBalKj, 0.05)
    }

    @Test fun `por debajo de CP es sostenible y recupera`() {
        val m = StaminaModel(params)
        repeat(60) { m.step(400.0) }
        val vacio = m.snapshot().actualPct
        repeat(600) { m.step(100.0) }
        val s = m.step(100.0)
        assertTrue(s.actualPct > vacio)
        assertNull(s.timeToEmptySec)
    }

    @Test fun `el potencial baja con el trabajo acumulado`() {
        val m = StaminaModel(params)
        repeat(5 * 3600) { m.step(200.0) }   // ~3600 kJ, ~51 kJ/kg
        val s = m.snapshot()
        assertTrue(s.potentialPct < 95.0)
        assertTrue(s.actualPct <= s.potentialPct + 1e-6)
    }

    @Test fun `tiempo hasta agotamiento coincide con W'bal sobre exceso`() {
        val m = StaminaModel(params)
        val s = m.step(350.0)                 // exceso 100 W, ~14,9 kJ restantes
        assertEquals(149.0, s.timeToEmptySec!!, 2.0)
    }

    @Test fun `durabilityFactor no penaliza bajo el umbral y respeta el tope`() {
        val p = StaminaParams(weightKg = 70.0, freeKjPerKg = 20.0, lossPctPerKjKg = 0.4, maxLossPct = 35.0)
        assertEquals(1.0, p.durabilityFactor(1400.0), 1e-9)      // 20 kJ/kg: justo en el umbral
        assertEquals(0.92, p.durabilityFactor(2800.0), 1e-9)      // 40 kJ/kg: 20 por encima × 0,4 % = 8 %
        assertEquals(0.65, p.durabilityFactor(1_000_000.0), 1e-9) // tope del 35 %
    }
}
