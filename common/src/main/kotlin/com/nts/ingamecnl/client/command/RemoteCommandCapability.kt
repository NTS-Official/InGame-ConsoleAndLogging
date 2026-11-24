package com.nts.ingamecnl.client.command

import com.nts.ingamecnl.command.CommandCapability
import com.nts.ingamecnl.command.CommandNetwork
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess

internal enum class RemoteCommandAccess {
    UNKNOWN,
    PENDING,
    ENABLED,
    DISABLED,
}

internal object RemoteCommandCapability {
    private const val REQUEST_TIMEOUT_NANOS = 5_000_000_000L

    var access = RemoteCommandAccess.UNKNOWN
        private set

    var token: String? = null
        private set
    var logStreaming = false
        private set
    var reason = ""
        private set
    var mixedMode = false
        private set

    var onChanged: () -> Unit = {}

    private var requestedAtNanos = 0L
    private var expiresAtNanos = 0L

    val enabled: Boolean
        get() = access == RemoteCommandAccess.ENABLED

    fun request(minecraft: Minecraft) {
        if (access != RemoteCommandAccess.UNKNOWN) return
        if (minecraft.singleplayerServer != null || minecraft.level == null) return
        if (!CommandNetwork.canSendCapabilityRequest()) {
            access = RemoteCommandAccess.DISABLED
            return
        }
        access = RemoteCommandAccess.PENDING
        requestedAtNanos = System.nanoTime()
        if (CommandNetwork.canUseV2()) {
            CommandNetwork.sendCapabilityRequestV2(registryAccess(minecraft))
        } else {
            CommandNetwork.sendCapabilityRequest(registryAccess(minecraft))
        }
    }

    fun onResponse(enabled: Boolean) {
        access = if (enabled) RemoteCommandAccess.ENABLED else RemoteCommandAccess.DISABLED
        token = null
        logStreaming = false
        mixedMode = false
        reason = ""
        expiresAtNanos = 0L
        onChanged()
    }

    fun onResponse(capability: CommandCapability) {
        access = if (capability.enabled) RemoteCommandAccess.ENABLED else RemoteCommandAccess.DISABLED
        token = capability.token
        logStreaming = capability.logStreaming
        mixedMode = capability.mixed
        reason = capability.reason
        expiresAtNanos = if (capability.enabled && capability.token != null) {
            System.nanoTime() + capability.ttlSeconds.coerceAtLeast(1) * 1_000_000_000L
        } else {
            0L
        }
        onChanged()
    }

    fun refreshIfExpired(minecraft: Minecraft) {
        if (access != RemoteCommandAccess.ENABLED || token == null) return
        if (System.nanoTime() < expiresAtNanos) return
        access = RemoteCommandAccess.UNKNOWN
        token = null
        mixedMode = false
        logStreaming = false
        expiresAtNanos = 0L
        request(minecraft)
    }

    fun refresh(minecraft: Minecraft) {
        if (minecraft.singleplayerServer != null || minecraft.level == null) return
        access = RemoteCommandAccess.UNKNOWN
        token = null
        mixedMode = false
        logStreaming = false
        expiresAtNanos = 0L
        request(minecraft)
    }

    fun tick(nowNanos: Long) {
        if (access == RemoteCommandAccess.PENDING && nowNanos - requestedAtNanos > REQUEST_TIMEOUT_NANOS) {
            access = RemoteCommandAccess.DISABLED
            onChanged()
        }
    }

    fun reset() {
        access = RemoteCommandAccess.UNKNOWN
        token = null
        logStreaming = false
        mixedMode = false
        reason = ""
        requestedAtNanos = 0L
        onChanged()
    }

    fun registryAccess(minecraft: Minecraft): RegistryAccess =
        minecraft.level?.registryAccess() ?: RegistryAccess.EMPTY
}
