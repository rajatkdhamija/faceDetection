package me.rajatdhamija.camerax.facedetectionexample

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.android.synthetic.main.activity_face_detection.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetectionActivity : AppCompatActivity() {

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var oldFacesValue = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_detection)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        btn_capture_image.setOnClickListener { takePhoto() }
    }

    //Initialize Camera to make it image capture ready
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()
            bindCapture(cameraProvider, preview)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }

    //Bind capturer to Lifecycle
    private fun bindCapture(cameraProvider: ProcessCameraProvider, preview: Preview) {
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val realTimeOpts = FaceDetectorOptions.Builder()
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        val detector = FaceDetection.getClient(realTimeOpts)

        val imageAnalyzer = ImageAnalysis.Builder()
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FaceDetectionAnalyser { inputImage, imageProxy ->
                    val result = detector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            Log.d("Faces", faces.size.toString())

                            tv_detected_faces.text =
                                "${getString(R.string.title_detected_faces)}${faces.size}"
                            if (faces.size != oldFacesValue) {
                                oldFacesValue = faces.size
                                if (faces.size > 0) {
                                    btn_capture_image.isEnabled = true
                                    ObjectAnimator.ofFloat(btn_capture_image, "alpha", 0.5f, 1f)
                                        .setDuration(100).start()
                                } else {
                                    btn_capture_image.isEnabled = false
                                    ObjectAnimator.ofFloat(btn_capture_image, "alpha", 1f, 0.5f)
                                        .setDuration(100).start()
                                }
                            }
                            imageProxy.close()
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                        }
                })
            }

        //Check if imageCapture is not null and Provide feedback if null
        try {
            imageCapture?.let {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } ?: kotlin.run {
                Toast.makeText(
                    this,
                    getString(R.string.error_image_capture),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    //Check for Camera permission
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    //get output directory path (Create if doesn't exists)
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_permisson_not_granted),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraX_FaceDetection"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

typealias AnalyzerListener = (inputImage: InputImage, imageProxy: ImageProxy) -> Unit