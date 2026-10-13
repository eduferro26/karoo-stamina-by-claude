package com.example.stamina

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class StaminaExtension : KarooExtension("stamina", "0.1") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var karoo: KarooSystemService
    private lateinit var engine: StaminaEngine

    override val types: List<DataTypeImpl> by lazy {
        listOf(StaminaDataType(extension, engine))
    }

    override fun onCreate() {
        super.onCreate()
        karoo = KarooSystemService(applicationContext)
        engine = StaminaEngine(karoo, StaminaSettings(applicationContext), scope)
        karoo.connect { connected -> if (connected) engine.start() }
    }

    override fun onDestroy() {
        engine.stop()
        karoo.disconnect()
        scope.cancel()
        super.onDestroy()
    }
}
