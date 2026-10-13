package com.example.stamina

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Pantalla de ajustes sencilla, construida por código (sin Compose ni AndroidX).
 * Los cambios se aplican al motor en caliente a través de SharedPreferences.
 */
class SettingsActivity : Activity() {

    private class Spec(
        val key: String,
        val labelRes: Int,
        val helpRes: Int,
        val min: Double,
        val max: Double,
        val optional: Boolean = false,
    )

    private val specs = listOf(
        Spec("cp", R.string.label_cp, R.string.help_cp, 50.0, 600.0),
        Spec("wPrimeKj", R.string.label_wprime, R.string.help_wprime, 5.0, 40.0),
        Spec("weight", R.string.label_weight, R.string.help_weight, 30.0, 200.0, optional = true),
        Spec("free", R.string.label_free, R.string.help_free, 0.0, 100.0),
        Spec("loss", R.string.label_loss, R.string.help_loss, 0.0, 2.0),
        Spec("max", R.string.label_max, R.string.help_max, 0.0, 60.0),
    )

    private val fields = mutableMapOf<String, EditText>()
    private lateinit var preview: TextView
    private lateinit var settings: StaminaSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = StaminaSettings(this)

        // Diseñado para el Karoo 3: 480x724 px útiles (480x800 menos las barras del sistema).
        // Todo en dp/sp para no depender de la densidad; los botones quedan fijos abajo y solo
        // se desplaza el contenido.
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        content.addView(TextView(this).apply {
            setText(R.string.settings_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, 0, dp(4))
        })

        specs.forEach { addField(content, it) }

        content.addView(TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(14), 0, dp(2))
            id = android.R.id.title
        })
        preview = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) }
        content.addView(preview)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        buttons.addView(
            Button(this).apply { setText(R.string.btn_defaults); setOnClickListener { restoreDefaults() } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        )
        buttons.addView(
            Button(this).apply { setText(R.string.btn_save); setOnClickListener { save() } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        )

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(content, MATCH_PARENT, WRAP_CONTENT) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(buttons, MATCH_PARENT, WRAP_CONTENT)
        setContentView(root)

        populate(settings.load(), settings.weightOverrideKg)
    }

    private fun addField(root: LinearLayout, spec: Spec) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        row.addView(TextView(this).apply {
            setText(spec.labelRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine()
            gravity = Gravity.END
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = refreshPreview()
            })
        }
        fields[spec.key] = edit
        row.addView(edit, LinearLayout.LayoutParams(dp(96), WRAP_CONTENT))
        root.addView(row, MATCH_PARENT, WRAP_CONTENT)

        root.addView(TextView(this).apply {
            setText(spec.helpRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            alpha = 0.7f
        })
    }

    private fun populate(p: StaminaParams, weightOverride: Double?) {
        fields.getValue("cp").setText(fmt(p.cp))
        fields.getValue("wPrimeKj").setText(fmt(p.wPrime / 1000.0))
        fields.getValue("weight").setText(weightOverride?.let(::fmt) ?: "")
        fields.getValue("free").setText(fmt(p.freeKjPerKg))
        fields.getValue("loss").setText(fmt(p.lossPctPerKjKg))
        fields.getValue("max").setText(fmt(p.maxLossPct))
        refreshPreview()
    }

    private fun save() {
        var ok = true
        fun num(key: String): Double {
            val spec = specs.first { it.key == key }
            val v = parse(spec)
            if (v == null) ok = false
            return v ?: 0.0
        }

        val weightBlank = fields.getValue("weight").text.isBlank()
        val weight = if (weightBlank) null else num("weight")
        val cp = num("cp")
        val wPrimeKj = num("wPrimeKj")
        val free = num("free")
        val loss = num("loss")
        val max = num("max")
        if (!ok) return

        val params = StaminaParams(
            cp = cp,
            wPrime = wPrimeKj * 1000.0,
            weightKg = weight ?: StaminaParams().weightKg,   // el motor aplica el del perfil si no hay propio
            freeKjPerKg = free,
            lossPctPerKjKg = loss,
            maxLossPct = max,
        )
        settings.save(params, weight)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun restoreDefaults() {
        settings.resetToDefaults()
        populate(settings.load(), null)
    }

    /** Devuelve el valor validado o null (y marca el error en el campo). */
    private fun parse(spec: Spec): Double? {
        val edit = fields.getValue(spec.key)
        val v = edit.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (v == null || v < spec.min || v > spec.max) {
            edit.error = getString(R.string.invalid_range, fmt(spec.min), fmt(spec.max))
            return null
        }
        return v
    }

    private fun lenient(key: String): Double? =
        fields.getValue(key).text.toString().trim().replace(',', '.').toDoubleOrNull()

    /** Muestra qué haría el modelo de durabilidad con los valores escritos. */
    private fun refreshPreview() {
        if (!::preview.isInitialized) return
        val cp = lenient("cp"); val wp = lenient("wPrimeKj")
        val free = lenient("free"); val loss = lenient("loss"); val max = lenient("max")
        val weight = lenient("weight") ?: StaminaParams().weightKg
        val title = findViewById<TextView>(android.R.id.title)
        if (cp == null || wp == null || free == null || loss == null || max == null || weight <= 0) {
            title?.text = ""; preview.text = ""; return
        }
        val p = StaminaParams(cp, wp * 1000.0, weight, free, loss, max)
        title?.text = getString(R.string.preview_title, weight.toInt())
        preview.text = listOf(2000, 4000, 6000).joinToString("\n") { kj ->
            val f = p.durabilityFactor(kj.toDouble())
            getString(R.string.preview_line, kj, (f * 100).toInt(), (cp * f).toInt())
        }
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
