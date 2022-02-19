package com.example.maskdetector


import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.maskdetector.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var objectDetector: ObjectDetector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)

        }, ContextCompat.getMainExecutor(this))
        val localModel = LocalModel.Builder()
            .setAssetFilePath("model.tflite")
            // or .setAbsoluteFilePath(absolute file path to model file)
            // or .setUri(URI to model file)
            .build()

// Live detection and tracking
        val customObjectDetectorOptions =
            CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .setClassificationConfidenceThreshold(0.9f)
                .setMaxPerObjectLabelCount(1)
                .build()

        objectDetector =
            ObjectDetection.getClient(customObjectDetectorOptions)
    }
    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider){
        val preview=Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(LENS_FACING_BACK).build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
        val imageAnalyser=ImageAnalysis
                                        .Builder()
                                        .setTargetResolution(Size(1280, 720))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
        imageAnalyser.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = imageProxy.image
            if (image != null) {
                val processImage = InputImage.fromMediaImage(image, rotation)
                objectDetector
                    .process(processImage)
                    .addOnFailureListener {
                        Log.v("MainActivity", "Error - ${it.message}")
                        imageProxy.close()
                    }
                    .addOnSuccessListener { results ->
                        for (i in results) {
                            if (binding.ParentView.childCount > 1) {
                                binding.ParentView.removeViewAt(1)
                            }
                            val element = Draw(
                                this,
                                i.boundingBox,
                                i.labels.firstOrNull()?.text ?: "Undefined"
                            )
                            binding.ParentView.addView(element)
                        }
                        imageProxy.close()
                    }
            }
        }
        cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalyser, preview)
    }
}