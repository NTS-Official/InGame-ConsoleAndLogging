package com.nts.ingamecnl.client.grep

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object GrepNative {
    private const val RESOURCE = "assets/ingamecnl/native/windows-x86_64/ingamecnl_grep.dll"
    private const val ABI_VERSION = 1
    private const val TEXT_CAPACITY = 64 * 1024
    private const val FIELD_CAPACITY = 1024

    private val state: State? = load()

    val available: Boolean
        get() = state != null

    fun workspace(): Workspace? = state?.let { Workspace(it) }

    private fun load(): State? {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return null
        if (!System.getProperty("os.arch").equals("amd64", ignoreCase = true)) return null
        val stream = GrepNative::class.java.getResourceAsStream("/$RESOURCE") ?: return null
        return try {
            val directory = Files.createTempDirectory("ingamecnl-native")
            val file = directory.resolve("ingamecnl_grep.dll")
            stream.use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }
            val arena = Arena.ofShared()
            val linker = Linker.nativeLinker()
            val lookup = SymbolLookup.libraryLookup(file, arena)
            val versionHandle = linker.downcallHandle(
                lookup.find("igcnl_grep_abi_version").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT),
            )
            val version = versionHandle.invokeExact() as Int
            if (version != ABI_VERSION) {
                arena.close()
                return null
            }
            val scanHandle = linker.downcallHandle(
                lookup.find("igcnl_grep_scan_ascii_v1").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
            )
            State(arena, scanHandle)
        } catch (error: Throwable) {
            System.err.println("[ingamecnl] native grep unavailable: ${error.message ?: error.javaClass.name}")
            null
        }
    }

    data class State(val arena: Arena, val scanHandle: MethodHandle)

    class Workspace internal constructor(private val state: State) : AutoCloseable {
        private val arena = Arena.ofConfined()
        private val needle = arena.allocate(1024)
        private val text = arena.allocate(TEXT_CAPACITY.toLong())
        private val fields = arena.allocate((FIELD_CAPACITY * 12).toLong())
        private val output = arena.allocate(8)

        fun scan(needleBytes: ByteArray, textBytes: ByteArray, textLength: Int, fieldCount: Int): Long {
            require(needleBytes.size <= 1024) { "needle too long" }
            require(textLength <= TEXT_CAPACITY) { "text too long" }
            require(fieldCount in 0..FIELD_CAPACITY) { "field count out of range" }
            MemorySegment.copy(
                needleBytes,
                0,
                needle,
                ValueLayout.JAVA_BYTE,
                0,
                needleBytes.size,
            )
            MemorySegment.copy(
                textBytes,
                0,
                text,
                ValueLayout.JAVA_BYTE,
                0,
                textLength,
            )
            val result = state.scanHandle.invokeExact(
                needle,
                needleBytes.size,
                text,
                textLength,
                fields,
                fieldCount,
                output,
            ) as Int
            if (result != 0) throw IllegalStateException("native grep returned $result")
            return output.get(ValueLayout.JAVA_LONG, 0)
        }

        fun writeField(index: Int, offset: Int, length: Int, recordIndex: Int) {
            val base = index.toLong() * 12
            fields.set(ValueLayout.JAVA_INT, base, offset)
            fields.set(ValueLayout.JAVA_INT, base + 4, length)
            fields.set(ValueLayout.JAVA_INT, base + 8, recordIndex)
        }

        override fun close() {
            arena.close()
        }
    }

    class Batch internal constructor(
        private val workspace: Workspace,
        private val baseSlot: Int,
        private val needle: ByteArray,
    ) {
        private val text = ByteArray(TEXT_CAPACITY)
        private var textSize = 0
        private var fieldCount = 0
        private var pendingMask = 0L

        fun add(bytes: ByteArray, slot: Int): Boolean {
            if (slot !in baseSlot until baseSlot + 64) return false
            if (bytes.isEmpty()) return true
            if (bytes.size > TEXT_CAPACITY) return false
            if (fieldCount >= FIELD_CAPACITY || textSize + bytes.size > TEXT_CAPACITY) {
                flush()
            }
            if (fieldCount >= FIELD_CAPACITY || textSize + bytes.size > TEXT_CAPACITY) return false
            System.arraycopy(bytes, 0, text, textSize, bytes.size)
            workspace.writeField(fieldCount, textSize, bytes.size, slot - baseSlot)
            textSize += bytes.size
            fieldCount++
            return true
        }

        fun flush() {
            if (fieldCount == 0) return
            val mask = try {
                workspace.scan(needle, text, textSize, fieldCount)
            } catch (error: Throwable) {
                throw IllegalStateException("native grep failed", error)
            }
            pendingMask = pendingMask or mask
            textSize = 0
            fieldCount = 0
        }

        fun takeMask(): Long {
            flush()
            return pendingMask
        }
    }
}
