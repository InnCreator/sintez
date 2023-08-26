//package ru.inncreator.sintez.ui.camera
//
//import android.annotation.SuppressLint
//import android.content.ContentValues
//import android.content.Context
//import android.graphics.ImageFormat
//import android.hardware.camera2.CameraCharacteristics
//import android.hardware.camera2.CameraManager
//import android.hardware.display.DisplayManager
//import android.os.Bundle
//import android.provider.MediaStore
//import android.util.Size
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.appcompat.content.res.AppCompatResources
//import androidx.camera.camera2.Camera2Config
//import androidx.camera.core.*
//import androidx.camera.core.impl.CameraConfig
//import androidx.camera.core.impl.ImageFormatConstants
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.video.*
//import androidx.camera.video.VideoCapture
//import androidx.camera.video.impl.VideoCaptureConfig
//import androidx.core.content.ContextCompat
//import androidx.core.util.Consumer
//import androidx.fragment.app.Fragment
//import androidx.lifecycle.lifecycleScope
//import androidx.navigation.fragment.findNavController
//import kotlinx.coroutines.flow.*
//import kotlinx.coroutines.launch
//import ru.inncreator.sintez.R
//import ru.inncreator.sintez.databinding.CameraUiContainerBinding
//import ru.inncreator.sintez.databinding.FragmentCameraBinding
//import ru.inncreator.sintez.utility.getNameString
//import timber.log.Timber
//import java.text.SimpleDateFormat
//import java.util.*
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import java.util.concurrent.TimeUnit
//
//
//class CameraFragment3 : Fragment() {
//
//    private var fragmentCameraBinding: FragmentCameraBinding? = null
//    private var cameraUiContainerBinding: CameraUiContainerBinding? = null
//
//    private var displayId: Int = -1
//    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
//    private var preview: Preview? = null
//    private var videoCapture: VideoCapture<Recorder>? = null
//    private var imageAnalyzer: ImageAnalysis? = null
//    private var camera: Camera? = null
//    private var cameraProvider: ProcessCameraProvider? = null
//
//    private var cameraFlash = false
//
//    private lateinit var cameraExecutor: ExecutorService
//
//    private lateinit var recordingState: VideoRecordEvent
//    private var currentRecording: Recording? = null
//
//    private val captureLiveStatus
//        get() = MutableStateFlow("")
//
//
//    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
//
//    enum class UiState {
//        IDLE,       // Not recording, all UI controls are active.
//        RECORDING,  // Camera is recording, only display Pause/Resume & Stop button.
//        FINALIZED,  // Recording just completes, disable all RECORDING UI controls.
//    }
//
//
//    private val displayManager by lazy {
//        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
//    }
//
//
//    private val displayListener = object : DisplayManager.DisplayListener {
//        override fun onDisplayAdded(displayId: Int) = Unit
//        override fun onDisplayRemoved(displayId: Int) = Unit
//
//        @SuppressLint("RestrictedApi")
//        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
//            if (displayId == this@CameraFragment3.displayId) {
//                videoCapture?.targetRotation = view.display.rotation
//                imageAnalyzer?.targetRotation = view.display.rotation
//            }
//        } ?: Unit
//    }
//
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
//        return fragmentCameraBinding?.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        cameraExecutor = Executors.newSingleThreadExecutor()
//
//        displayManager.registerDisplayListener(displayListener, null)
//
//        fragmentCameraBinding?.viewFinder?.post {
//
//            displayId = fragmentCameraBinding?.viewFinder?.display?.displayId!!
//
//            updateCameraUi()
//
//            lifecycleScope.launch {
//                setUpCamera()
//            }
//        }
//
//    }
//
//    override fun onResume() {
//        super.onResume()
//        if (!PermissionsFragment.hasPermissions(requireContext())) {
//            findNavController().navigate(R.id.action_cameraFragment_to_permissionsFragment)
//        }
//    }
//
//
//    private fun updateCameraUi() {
//        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
//            LayoutInflater.from(requireContext()),
//            fragmentCameraBinding?.root,
//            true
//        )
//
//        cameraUiContainerBinding?.cameraFlashButton?.setOnClickListener {
//            if (camera?.cameraInfo?.hasFlashUnit() == true) {
//                cameraFlash = !cameraFlash
//                val ic = if (cameraFlash) R.drawable.ic_flash_on else R.drawable.ic_flash_off
//                cameraUiContainerBinding?.cameraFlashButton?.setImageDrawable(
//                    AppCompatResources.getDrawable(
//                        requireContext(),
//                        ic
//                    )
//                )
//                camera?.cameraControl?.enableTorch(cameraFlash)
//            }
//        }
//
//        cameraUiContainerBinding?.cameraCloseButton?.setOnClickListener {
//            requireActivity().finish()
//        }
//
//        fragmentCameraBinding?.overlay?.clear()
//
//        cameraUiContainerBinding?.cameraCaptureButton?.setOnClickListener {
//            captureClick()
//        }
//
//        lifecycleScope.launch {
//            captureLiveStatus.collect {
//                cameraUiContainerBinding?.captureStatus.apply {
//                    this?.post { text = it }
//                }
//            }
//        }
//    }
//
//    private fun enableUI(enable: Boolean) {
//        arrayOf(
//            cameraUiContainerBinding?.cameraCaptureButton,
//            cameraUiContainerBinding?.cameraFlashButton,
//            cameraUiContainerBinding?.cameraCloseButton
//        ).forEach {
//            it?.isEnabled = enable
//        }
//    }
//
//    private fun captureClick() {
//
//        if (!this::recordingState.isInitialized ||
//            recordingState is VideoRecordEvent.Finalize
//        ) {
//            enableUI(false)  // Our eventListener will turn on the Recording UI.
//            startRecording()
//        } else {
//            when (recordingState) {
//                is VideoRecordEvent.Start -> {
//                    currentRecording?.pause()
////                    captureViewBinding.stopButton.visibility = View.VISIBLE
//                }
//                else -> throw IllegalStateException("recordingState in unknown state")
//            }
//        }
////        isEnabled = false
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun startRecording() {
//        // create MediaStoreOutputOptions for our recorder: resulting our recording!
//        val name = "CameraX-recording-" +
//                SimpleDateFormat(FILENAME, Locale.US)
//                    .format(System.currentTimeMillis()) + ".mp4"
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Video.Media.DISPLAY_NAME, name)
//        }
//        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
//            requireActivity().contentResolver,
//            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
//        )
//            .setContentValues(contentValues)
//            .build()
//
//        // configure Recorder and Start recording to the mediaStoreOutput.
//        currentRecording = videoCapture?.output
//            ?.prepareRecording(requireActivity(), mediaStoreOutput)
//            ?.start(mainThreadExecutor, captureListener)
//
//        Timber.i("Recording started")
//    }
//
//    private val captureListener = Consumer<VideoRecordEvent> { event ->
//        // cache the recording state
//        if (event !is VideoRecordEvent.Status)
//            recordingState = event
//
//        updateUI(event)
//
//        if (event is VideoRecordEvent.Finalize) {
//            Timber.d("End")
//            // display the captured video
////            lifecycleScope.launch {
////                navController.navigate(
////                    CaptureFragmentDirections.actionCaptureToVideoViewer(
////                        event.outputResults.outputUri
////                    )
////                )
////            }
//        }
//    }
//
//    private fun updateUI(event: VideoRecordEvent) {
//        val state = if (event is VideoRecordEvent.Status) recordingState.getNameString()
//        else event.getNameString()
//        when (event) {
//            is VideoRecordEvent.Status -> {
//                // placeholder: we update the UI with new status after this when() block,
//                // nothing needs to do here.
//            }
//            is VideoRecordEvent.Start -> {
//                showUI(UiState.RECORDING, event.getNameString())
//            }
//            is VideoRecordEvent.Finalize -> {
//                showUI(UiState.FINALIZED, event.getNameString())
//            }
//            is VideoRecordEvent.Pause,
//            is VideoRecordEvent.Resume -> {
//            }
//        }
//
//        val stats = event.recordingStats
//        val size = stats.numBytesRecorded / 1000
//        val time = TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
//        var text = "${state}: recorded ${size}KB, in ${time}second"
//        if (event is VideoRecordEvent.Finalize)
//            text = "${text}\nFile saved to: ${event.outputResults.outputUri}"
//
//        captureLiveStatus.tryEmit(text)
//        Timber.i("recording event: $text")
//    }
//
//    private fun showUI(state: UiState, status: String = "idle") {
//        cameraUiContainerBinding?.let {
//            when (state) {
//                UiState.IDLE -> {
//                    it.cameraCaptureButton.setImageResource(R.drawable.ic_shutter_red)
////                    it.cameraButton.visibility= View.VISIBLE
//                }
//                UiState.RECORDING -> {
////                    it.cameraButton.visibility = View.INVISIBLE
//                    it.cameraCaptureButton.setImageResource(R.drawable.ic_shutter)
//                    it.cameraCaptureButton.isEnabled = true
//                }
//                UiState.FINALIZED -> {
//                    it.cameraCaptureButton.setImageResource(R.drawable.ic_shutter_red)
//                }
//                else -> {
//                    val errorMsg = "Error: showUI($state) is not supported"
//                    Timber.e(errorMsg)
//                    return
//                }
//            }
//            it.captureStatus.text = status
//        }
//    }
//
//    private fun setUpCamera() {
//        cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get()
//        bindCameraUseCases()
//    }
//
//    @SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
//    private fun bindCameraUseCases() {
//        val rotation = fragmentCameraBinding?.viewFinder?.display?.rotation!!
//
//        val cameraProvider = cameraProvider
//            ?: throw IllegalStateException("Camera initialization failed.")
//
//        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
//
//        val cameraManager =
//            requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
//
//        val characteristics: CameraCharacteristics =
//            cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[0])
//        val configs = characteristics.get(
//            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
//        )
//        val lvl = characteristics.get(
//            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
//        )
//        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
//
//
//        val b = configs?.getOutputSizes(ImageFormat.YUV_420_888)
//
//        preview = Preview.Builder()
//            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//            .setTargetRotation(rotation)
//            .setMaxResolution(Size(320, 240))
//            .build()
//
////        val recorder = Recorder.Builder()
////            .setQualitySelector(QualitySelector.from(Quality.SD))
////            .build()
//
//        val preferredQuality = Quality.LOWEST
//        val recorder = Recorder.Builder()
//            .setQualitySelector(
//                QualitySelector.from(
//                    preferredQuality,
//                    FallbackStrategy.higherQualityOrLowerThan(preferredQuality)
//                )
//            )
//            .build()
//
//        val bc = VideoCaptureConfig.BU
//
//
//        videoCapture = VideoCapture.withOutput(recorder)
//
//        imageAnalyzer = ImageAnalysis.Builder()
//            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//            .setTargetRotation(rotation)
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//            .setMaxResolution(Size(320, 240))
//            .build()
//
//
//        cameraProvider.unbindAll()
//
//        if (camera != null) {
//            removeCameraStateObservers(camera!!.cameraInfo)
//        }
//
//        try {
//
//            val useCaseGroup = UseCaseGroup.Builder()
//                .addUseCase(preview!!)
//                .addUseCase(videoCapture!!)
//                .addUseCase(imageAnalyzer!!)
//                .build()
//            camera?.extendedConfig
//            camera?.cameraInternals
//            camera = cameraProvider.bindToLifecycle(
//                this, cameraSelector, useCaseGroup
//            )
//            val a = camera?.extendedConfig
//            preview?.setSurfaceProvider(fragmentCameraBinding?.viewFinder?.surfaceProvider)
//            Timber.i(
//                "My Resolution:\n" +
//                        "Preview: ${preview?.resolutionInfo}\n" +
//                        "videoCapture: ${videoCapture?.resolutionInfo}\n" +
//                        "imageAnalyzer: ${imageAnalyzer?.resolutionInfo}"
//            )
//            observeCameraState(camera?.cameraInfo!!)
//        } catch (exc: Exception) {
//            Timber.e("Use case binding failed $exc")
//
//        }
//    }
//
//    private fun removeCameraStateObservers(cameraInfo: CameraInfo) {
//        cameraInfo.cameraState.removeObservers(viewLifecycleOwner)
//    }
//
//    private fun observeCameraState(cameraInfo: CameraInfo) {
//        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
//            run {
//                when (cameraState.type) {
//                    CameraState.Type.PENDING_OPEN,
//                    CameraState.Type.OPENING,
//                    CameraState.Type.OPEN,
//                    CameraState.Type.CLOSING,
//                    CameraState.Type.CLOSED -> {
//                        Timber.d("CameraState : ${cameraState.type}")
//                    }
//                }
//            }
//            var errorCode = ""
//
//            cameraState.error?.let { error ->
//                when (error.code) {
//                    CameraState.ERROR_STREAM_CONFIG -> {
//                        errorCode = "Stream config error"
//                    }
//                    CameraState.ERROR_CAMERA_IN_USE -> {
//                        errorCode = "Camera in use"
//                    }
//                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
//                        errorCode = "Max cameras in use"
//                    }
//                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
//                        errorCode = "Other recoverable error"
//                    }
//                    CameraState.ERROR_CAMERA_DISABLED -> {
//                        errorCode = "Camera disabled"
//                    }
//                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
//                        errorCode = "Fatal error"
//                    }
//                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
//                        errorCode = "Do not disturb mode enabled"
//                    }
//                }
//                Timber.e(
//                    "Camera open error",
//                    mapOf(Pair("Error code", errorCode))
//                )
//            }
//        }
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        cameraUiContainerBinding = null
//        fragmentCameraBinding = null
//    }
//
//    companion object {
//        fun newInstance() = CameraFragment3()
//
//        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
//        private const val PHOTO_TYPE = "image/jpeg"
//        private const val RATIO_4_3_VALUE = 4.0 / 3.0
//        private const val RATIO_16_9_VALUE = 16.0 / 9.0
//    }
//
//}