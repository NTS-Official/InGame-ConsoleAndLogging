package com.nts.ingamecnl.client.command

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

    private var requestedAtNanos = 0L

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
        CommandNetwork.sendCapabilityRequest(registryAccess(minecraft))
    }

    fun onResponse(enabled: Boolean) {
        access = if (enabled) RemoteCommandAccess.ENABLED else RemoteCommandAccess.DISABLED
    }

    fun tick(nowNanos: Long) {
        if (access == RemoteCommandAccess.PENDING && nowNanos - requestedAtNanos > REQUEST_TIMEOUT_NANOS) {
            access = RemoteCommandAccess.DISABLED
        }
    }

    fun reset() {
        access = RemoteCommandAccess.UNKNOWN
        requestedAtNanos = 0L
    }

    fun registryAccess(minecraft: Minecraft): RegistryAccess =
        minecraft.level?.registryAccess() ?: RegistryAccess.EMPTY
}
