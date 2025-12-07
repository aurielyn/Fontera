package xyz.meowing.fontera

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.Minecraft

object Fontera : ClientModInitializer {
    private val startCallbacks = mutableListOf<() -> Unit>()
    private var _textRenderer: TextRenderer? = null

    @JvmStatic
    val client: Minecraft = Minecraft.getInstance()

    @JvmStatic
    val textRenderer: TextRenderer
        get() = _textRenderer ?: throw IllegalStateException("Text renderer not initialized.")

    @JvmStatic
    fun onClientStart(callback: () -> Unit) {
        startCallbacks.add(callback)
    }

    override fun onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register { _ ->
            startCallbacks.toList().forEach { it() }
            startCallbacks.clear()
            _textRenderer = TextRenderer(fontPath = "/assets/fontera/default.ttf")
        }
    }
}