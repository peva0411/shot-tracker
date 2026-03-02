package com.shottracker.camera.detector

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "TFLiteInference"

/**
 * Minimal TensorFlow Lite wrapper for loading a model from assets and running inference.
 * Tries GPU delegate first for faster inference; falls back to CPU if unavailable.
 */
class TFLiteInferenceWrapper(
    context: Context,
    modelAssetPath: String,
    numThreads: Int = DEFAULT_NUM_THREADS,
    useGpu: Boolean = true
) : Closeable {

    private var gpuDelegate: GpuDelegate? = null

    val isUsingGpu: Boolean

    private val interpreter: Interpreter

    init {
        val modelBuffer = loadModelFile(context, modelAssetPath)
        var builtWithGpu = false
        interpreter = if (useGpu) {
            try {
                val delegate = GpuDelegate()
                val options = Interpreter.Options()
                    .addDelegate(delegate)
                    .setNumThreads(numThreads.coerceAtLeast(1))
                val interp = Interpreter(modelBuffer, options)
                gpuDelegate = delegate
                builtWithGpu = true
                Log.i(TAG, "GPU delegate enabled")
                interp
            } catch (e: Throwable) {
                Log.w(TAG, "GPU delegate unavailable, falling back to CPU: ${e.message}")
                Interpreter(modelBuffer, Interpreter.Options().setNumThreads(numThreads.coerceAtLeast(1)))
            }
        } else {
            Interpreter(modelBuffer, Interpreter.Options().setNumThreads(numThreads.coerceAtLeast(1)))
        }
        isUsingGpu = builtWithGpu
    }

    fun run(input: Any, output: Any) {
        interpreter.run(input, output)
    }

    fun runForMultipleInputsOutputs(inputs: Array<Any>, outputs: MutableMap<Int, Any>) {
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
    }

    fun getInputTensorShape(index: Int = 0): IntArray = interpreter.getInputTensor(index).shape()

    fun getOutputTensorShape(index: Int = 0): IntArray = interpreter.getOutputTensor(index).shape()

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }

    companion object {
        private const val DEFAULT_NUM_THREADS = 2

        private fun loadModelFile(context: Context, modelAssetPath: String): MappedByteBuffer {
            context.assets.openFd(modelAssetPath).use { assetFileDescriptor ->
                FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
                    return inputStream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        assetFileDescriptor.startOffset,
                        assetFileDescriptor.declaredLength
                    )
                }
            }
        }
    }
}
