package com.shottracker.camera.detector

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Minimal TensorFlow Lite wrapper for loading a model from assets and running inference.
 */
class TFLiteInferenceWrapper(
    context: Context,
    modelAssetPath: String,
    numThreads: Int = DEFAULT_NUM_THREADS
) : Closeable {

    private val interpreter: Interpreter = Interpreter(
        loadModelFile(context, modelAssetPath),
        Interpreter.Options().setNumThreads(numThreads.coerceAtLeast(1))
    )

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
