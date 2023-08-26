package ru.inncreator.sintez.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.launch
import ru.inncreator.sintez.detector.PoseLandmarkerHelper
import ru.inncreator.sintez.R
import ru.inncreator.sintez.databinding.CameraUiContainerBinding
import ru.inncreator.sintez.databinding.FragmentCameraBinding
import ru.inncreator.sintez.detector.PoseRepository
import timber.log.Timber
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    private var fragmentCameraBinding: FragmentCameraBinding? = null
    private var cameraUiContainerBinding: CameraUiContainerBinding? = null

    private var displayId: Int = -1

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null


    private var cameraFlash = false

    private lateinit var cameraExecutor: ExecutorService
    private val viewModel: PoseRepository by activityViewModels()

    private lateinit var backgroundExecutor: ExecutorService

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }


    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        @SuppressLint("RestrictedApi")
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        displayManager.registerDisplayListener(displayListener, null)

        fragmentCameraBinding?.viewFinder?.post {

            displayId = fragmentCameraBinding?.viewFinder?.display?.displayId!!

            updateCameraUi()

            lifecycleScope.launch {
                setUpCamera()
            }
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_cameraFragment_to_permissionsFragment)
        }

        backgroundExecutor.execute {
            if (this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }


    override fun onPause() {
        super.onPause()
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    private fun updateCameraUi() {
        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
            LayoutInflater.from(requireContext()),
            fragmentCameraBinding?.root,
            true
        )

        cameraUiContainerBinding?.cameraFlashButton?.setOnClickListener {
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                cameraFlash = !cameraFlash
                val ic = if (cameraFlash) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                cameraUiContainerBinding?.cameraFlashButton?.setImageDrawable(
                    AppCompatResources.getDrawable(
                        requireContext(),
                        ic
                    )
                )
                camera?.cameraControl?.enableTorch(cameraFlash)
            }
        }

        cameraUiContainerBinding?.cameraCloseButton?.setOnClickListener {
            requireActivity().finish()
        }

        backgroundExecutor.execute {
            poseLandmarkerHelper.clearPoseLandmarker()
            poseLandmarkerHelper.setupPoseLandmarker()
        }
        fragmentCameraBinding?.overlay?.clear()
    }

    private fun setUpCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get()
        bindCameraUseCases()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val rotation = fragmentCameraBinding?.viewFinder?.display?.rotation!!

        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    detectPose(image)
                }
            }


        cameraProvider.unbindAll()

        if (camera != null) {
            removeCameraStateObservers(camera!!.cameraInfo)
        }

        try {
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            preview?.setSurfaceProvider(fragmentCameraBinding?.viewFinder?.surfaceProvider)
            observeCameraState(camera?.cameraInfo!!)
        } catch (exc: Exception) {
            Timber.e("Use case binding failed", exc)
        }
    }

    private fun removeCameraStateObservers(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.removeObservers(viewLifecycleOwner)
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN,
                    CameraState.Type.OPENING,
                    CameraState.Type.OPEN,
                    CameraState.Type.CLOSING,
                    CameraState.Type.CLOSED -> {
                    }
                }
            }
            var errorCode = ""

            cameraState.error?.let { error ->
                when (error.code) {
                    CameraState.ERROR_STREAM_CONFIG -> {
                        errorCode = "Stream config error"
                    }
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        errorCode = "Camera in use"
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        errorCode = "Max cameras in use"
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        errorCode = "Other recoverable error"
                    }
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        errorCode = "Camera disabled"
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        errorCode = "Fatal error"
                    }
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        errorCode = "Do not disturb mode enabled"
                    }
                }
                Timber.e(
                    "Camera open error",
                    mapOf(Pair("Error code", errorCode))
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
        cameraUiContainerBinding = null
        fragmentCameraBinding = null
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }


    override fun onError(error: String, errorCode: Int) {
        Timber.e("Error: $errorCode $error")
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        if (fragmentCameraBinding != null) {
            fragmentCameraBinding?.overlay?.setResults(
                resultBundle.results.first(),
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM
            )
            fragmentCameraBinding?.overlay?.invalidate()
        }
    }

}