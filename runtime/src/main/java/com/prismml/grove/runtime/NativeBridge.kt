package com.prismml.grove.runtime

internal class NativeBridge {
    external fun init(nativeLibDir: String)
    external fun load(modelPath: String): Int
    external fun prepare(
        contextLength: Int,
        threadCount: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): Int

    external fun systemInfo(): String
    external fun reset(systemPrompt: String): Int
    external fun append(role: String, content: String): Int
    external fun beginCompletion(userPrompt: String, predictLength: Int): Int
    external fun generateNextToken(): String?
    external fun unload()
    external fun shutdown()
}
