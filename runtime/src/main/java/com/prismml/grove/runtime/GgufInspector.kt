package com.prismml.grove.runtime

import java.io.IOException
import java.io.InputStream

internal class GgufInspector {
    private enum class ValueType(val code: Int) {
        UINT8(0),
        INT8(1),
        UINT16(2),
        INT16(3),
        UINT32(4),
        INT32(5),
        FLOAT32(6),
        BOOL(7),
        STRING(8),
        ARRAY(9),
        UINT64(10),
        INT64(11),
        FLOAT64(12),
        ;

        companion object {
            fun from(code: Int): ValueType =
                entries.firstOrNull { it.code == code } ?: throw IOException("Unknown GGUF value type $code")
        }
    }

    fun readMetadata(input: InputStream): ModelMetadata {
        requireMagic(input)
        readUInt32(input)
        readLong(input)
        val kvCount = readLong(input)
        var architecture: String? = null
        var contextLength: Int? = null
        var chatTemplate: String? = null
        var name: String? = null
        var architecturePrefix: String? = null

        repeat(kvCount.toInt()) {
            val key = readString(input)
            val type = ValueType.from(readInt(input))
            val value = readValue(input, type)
            when (key) {
                "general.architecture" -> {
                    architecture = value as? String
                    architecturePrefix = architecture
                }
                "general.name", "general.basename" -> if (name == null) name = value as? String
                "tokenizer.chat_template" -> chatTemplate = value as? String
                "${architecturePrefix ?: "llama"}.context_length" -> contextLength = (value as? Number)?.toInt()
            }
        }
        return ModelMetadata(
            architecture = architecture,
            contextLength = contextLength,
            chatTemplate = chatTemplate,
            name = name,
        )
    }

    private fun requireMagic(input: InputStream) {
        val magic = input.readNBytes(4)
        if (!magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
            throw IOException("Not a GGUF file")
        }
    }

    private fun readValue(input: InputStream, type: ValueType): Any? = when (type) {
        ValueType.STRING -> readString(input)
        ValueType.BOOL -> input.read() != 0
        ValueType.INT8 -> input.read().toByte()
        ValueType.UINT8 -> input.read()
        ValueType.INT16 -> readShort(input)
        ValueType.UINT16 -> readShort(input).toInt() and 0xFFFF
        ValueType.INT32 -> readInt(input)
        ValueType.UINT32 -> readUInt32(input)
        ValueType.INT64 -> readLong(input)
        ValueType.UINT64 -> readLong(input)
        ValueType.FLOAT32 -> Float.fromBits(readInt(input))
        ValueType.FLOAT64 -> Double.fromBits(readLong(input))
        ValueType.ARRAY -> {
            val elementType = ValueType.from(readInt(input))
            val count = readLong(input).toInt()
            repeat(count) { readValue(input, elementType) }
            null
        }
    }

    private fun readString(input: InputStream): String {
        val length = readLong(input).toInt()
        return input.readNBytes(length).toString(Charsets.UTF_8)
    }

    private fun readInt(input: InputStream): Int {
        val bytes = input.readNBytes(4)
        if (bytes.size != 4) throw IOException("Unexpected EOF")
        return ((bytes[3].toInt() and 0xFF) shl 24) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            (bytes[0].toInt() and 0xFF)
    }

    private fun readUInt32(input: InputStream): Long = readInt(input).toLong() and 0xFFFF_FFFFL

    private fun readShort(input: InputStream): Short {
        val bytes = input.readNBytes(2)
        if (bytes.size != 2) throw IOException("Unexpected EOF")
        return (((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)).toShort()
    }

    private fun readLong(input: InputStream): Long {
        val bytes = input.readNBytes(8)
        if (bytes.size != 8) throw IOException("Unexpected EOF")
        return ((bytes[7].toLong() and 0xFFL) shl 56) or
            ((bytes[6].toLong() and 0xFFL) shl 48) or
            ((bytes[5].toLong() and 0xFFL) shl 40) or
            ((bytes[4].toLong() and 0xFFL) shl 32) or
            ((bytes[3].toLong() and 0xFFL) shl 24) or
            ((bytes[2].toLong() and 0xFFL) shl 16) or
            ((bytes[1].toLong() and 0xFFL) shl 8) or
            (bytes[0].toLong() and 0xFFL)
    }
}
