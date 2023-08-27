package ru.inncreator.sintez.ui.gallery

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.tasks.vision.core.RunningMode
import ru.inncreator.sintez.R
import ru.inncreator.sintez.databinding.FragmentGalleryBinding
import ru.inncreator.sintez.detector.PoseLandmarkerHelper
import ru.inncreator.sintez.detector.PoseRepository
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class GalleryFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {


    enum class MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: PoseRepository by activityViewModels()

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService
    private lateinit var seekBarExecutor: ScheduledExecutorService
    private lateinit var currentResultBundle: PoseLandmarkerHelper.ResultBundle


    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Handle the returned Uri
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runDetectionOnImage(mediaUri)
                    MediaType.VIDEO -> runPlayVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        updateDisplayView(mediaType)
                        Toast.makeText(
                            requireContext(),
                            "Unsupported data type.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)

        return fragmentGalleryBinding.root
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentGalleryBinding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }

        seekBarExecutor = Executors.newSingleThreadScheduledExecutor()

        seekBarExecutor.scheduleAtFixedRate(
            {
                fragmentGalleryBinding.seekbar.post {
                    fragmentGalleryBinding.seekbar.setProgress(
                        fragmentGalleryBinding.videoView.currentPosition,
                        true
                    )
                }

            }, 0, 50L, TimeUnit.MILLISECONDS
        )

        fragmentGalleryBinding.seekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar) {
                fragmentGalleryBinding.videoView.seekTo(p0.progress)
            }

        })

        fragmentGalleryBinding.pause.setOnClickListener {
            if (fragmentGalleryBinding.videoView.isPlaying) {
                fragmentGalleryBinding.videoView.pause()
                fragmentGalleryBinding.pause.setImageDrawable(requireContext().getDrawable(R.drawable.ic_play))
            } else {
                fragmentGalleryBinding.videoView.seekTo(fragmentGalleryBinding.seekbar.progress)
                fragmentGalleryBinding.videoView.start()
                fragmentGalleryBinding.pause.setImageDrawable(requireContext().getDrawable(R.drawable.ic_pause))

            }

        }

    }

    override fun onPause() {
        fragmentGalleryBinding.overlay.clear()
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        super.onPause()
    }

    private fun runDetectionOnImage(uri: Uri) {
        setUiEnabled(false)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        updateDisplayView(MediaType.IMAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                requireActivity().contentResolver,
                uri
            )
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(
                requireActivity().contentResolver,
                uri
            )
        }
            .copy(Bitmap.Config.ARGB_8888, true)
            ?.let { bitmap ->
                fragmentGalleryBinding.imageResult.setImageBitmap(bitmap)

                // Run pose landmarker on the input image
                backgroundExecutor.execute {

                    poseLandmarkerHelper =
                        PoseLandmarkerHelper(
                            context = requireContext(),
                            runningMode = RunningMode.IMAGE,
                            minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                            minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                            minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                            currentDelegate = viewModel.currentDelegate
                        )

                    poseLandmarkerHelper.detectImage(bitmap)?.let { result ->
                        activity?.runOnUiThread {
                            fragmentGalleryBinding.overlay.setResults(
                                result.results[0],
                                bitmap.height,
                                bitmap.width,
                                RunningMode.IMAGE
                            )

                            setUiEnabled(true)
                        }
                    } ?: run { Timber.e("Error running pose landmarker.") }

                    poseLandmarkerHelper.clearPoseLandmarker()
                }
            }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun runPlayVideo(uri: Uri) {
        updateDisplayView(MediaType.VIDEO)

        fragmentGalleryBinding.videoView.setVideoURI(uri)
        fragmentGalleryBinding.videoView.setOnPreparedListener { mp ->
            fragmentGalleryBinding.seekbar.max = mp.duration
            fragmentGalleryBinding.pause.setImageDrawable(requireContext().getDrawable(R.drawable.ic_pause))
            mp.isLooping = true
            mp.start()
        }

        fragmentGalleryBinding.videoView.requestFocus()
        runDetectionOnVideo(uri)


    }

    private fun runDetectionOnVideo(uri: Uri) {
        setUiEnabled(false)
        fragmentGalleryBinding.progress.visibility = View.VISIBLE
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()

        backgroundExecutor.execute {

            poseLandmarkerHelper =
                PoseLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.VIDEO,
                    minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                    minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                    minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                    currentDelegate = viewModel.currentDelegate
                )
            val start = SystemClock.uptimeMillis()
            poseLandmarkerHelper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    activity?.runOnUiThread { runShowBones() }
                    currentResultBundle = resultBundle
                    val end = SystemClock.uptimeMillis()
                    Timber.i("Time End-Start ${(end - start) / 1000F}")
                }
                ?: run { Timber.e("Error running pose landmarker.") }

            poseLandmarkerHelper.clearPoseLandmarker()
        }


    }

    private fun runShowBones() {
        setUiEnabled(true)
        fragmentGalleryBinding.progress.visibility = View.GONE

        backgroundExecutor.scheduleAtFixedRate(
            {
                activity?.runOnUiThread {

                    val timeVideo = fragmentGalleryBinding.videoView.currentPosition
                    val resultIndex =
                        timeVideo.div(VIDEO_INTERVAL_MS).toInt()
                    fragmentGalleryBinding.overlay.setResults(
                        currentResultBundle.results[resultIndex],
                        fragmentGalleryBinding.videoView.height,
                        fragmentGalleryBinding.videoView.width,
                        RunningMode.VIDEO
                    )

                }
            },
            0,
            VIDEO_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )

    }

    private fun updateDisplayView(mediaType: MediaType) {
        fragmentGalleryBinding.imageResult.visibility =
            if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
        fragmentGalleryBinding.videoView.visibility =
            if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility =
            if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    // Check the type of media that user selected.
    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (mimeType.startsWith("image")) return MediaType.IMAGE
            if (mimeType.startsWith("video")) return MediaType.VIDEO
        }

        return MediaType.UNKNOWN
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
    }

    private fun classifyingError() {
        activity?.runOnUiThread {
            fragmentGalleryBinding.progress.visibility = View.GONE
            setUiEnabled(true)
            updateDisplayView(MediaType.UNKNOWN)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Timber.e("Error: $error")
        classifyingError()
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // no-op
    }

    companion object {
        // Value used to get frames at specific intervals for inference (e.g. every 300ms)
        private const val VIDEO_INTERVAL_MS = 100L
    }

}