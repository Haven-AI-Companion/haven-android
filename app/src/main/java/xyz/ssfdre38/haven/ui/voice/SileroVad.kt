package xyz.ssfdre38.haven.ui.voice

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

class SileroVad(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // Hidden and cell states for LSTM: shape [2, 1, 64]
    private var hState: Array<Array<FloatArray>> = Array(2) { Array(1) { FloatArray(64) } }
    private var cState: Array<Array<FloatArray>> = Array(2) { Array(1) { FloatArray(64) } }

    init {
        val modelBytes = context.assets.open("silero_vad.ort").readBytes()
        session = env.createSession(modelBytes)
    }

    /**
     * Resets the LSTM state to clean memory between speech periods.
     */
    fun reset() {
        hState = Array(2) { Array(1) { FloatArray(64) } }
        cState = Array(2) { Array(1) { FloatArray(64) } }
    }

    /**
     * Process a chunk of raw 16-bit PCM audio samples.
     * @param pcmData PCM samples (chunk size should be 512, 1024, or 1536 at 16000Hz).
     * @return Voice probability score between 0.0 and 1.0.
     */
    fun process(pcmData: ShortArray): Float {
        if (pcmData.isEmpty()) return 0.0f

        // Convert PCM ShortArray to FloatArray normalized to [-1.0, 1.0]
        val floatAudio = FloatArray(pcmData.size)
        for (i in pcmData.indices) {
            floatAudio[i] = pcmData[i].toFloat() / 32768.0f
        }

        // Prepare input tensors:
        // 1. input: float32 [1, chunk_size]
        val inputShape = longArrayOf(1, pcmData.size.toLong())
        val inputBuffer = FloatBuffer.wrap(floatAudio)
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)

        // 2. sr: int64 scalar (16000)
        val srTensor = OnnxTensor.createTensor(env, 16000L)

        // 3. h: float32 [2, 1, 64]
        val hTensor = OnnxTensor.createTensor(env, hState)

        // 4. c: float32 [2, 1, 64]
        val cTensor = OnnxTensor.createTensor(env, cState)

        val inputs = mapOf(
            "input" to inputTensor,
            "sr" to srTensor,
            "h" to hTensor,
            "c" to cTensor
        )

        try {
            session.run(inputs).use { results ->
                val outputOpt = results.get("output")
                val hnOpt = results.get("hn")
                val cnOpt = results.get("cn")

                if (!outputOpt.isPresent || !hnOpt.isPresent || !cnOpt.isPresent) {
                    return 0.0f
                }

                val outputTensor = outputOpt.get() as OnnxTensor
                val outputArray = outputTensor.value as Array<FloatArray>
                val probability = outputArray[0][0]

                val hnTensor = hnOpt.get() as OnnxTensor
                @Suppress("UNCHECKED_CAST")
                hState = hnTensor.value as Array<Array<FloatArray>>

                val cnTensor = cnOpt.get() as OnnxTensor
                @Suppress("UNCHECKED_CAST")
                cState = cnTensor.value as Array<Array<FloatArray>>

                return probability
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return 0.0f
        } finally {
            // Close session-run specific input tensors to prevent native memory leaks
            inputTensor.close()
            srTensor.close()
            hTensor.close()
            cTensor.close()
        }
    }

    override fun close() {
        session.close()
        env.close()
    }
}
