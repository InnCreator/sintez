package ru.inncreator.sintez.utility

import android.content.Context
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.lang.Math.acos
import java.lang.Math.sqrt

class Utility {


    companion object {

        private fun calculateAngle(vector1: List<Float>, vector2: List<Float>): Double {
            val dotProduct = vector1.zip(vector2).sumOf { (it.first * it.second).toDouble() }
            val length1 = sqrt(vector1.sumOf { (it * it).toDouble() })
            val length2 = sqrt(vector2.sumOf { (it * it).toDouble() })
            val angleRadians = acos(dotProduct / (length1 * length2))
            val angleDegrees = Math.toDegrees(angleRadians)
            return angleDegrees
        }

        fun getAngel(landmarker: PoseLandmarkerResult): String {
            val landmarks: List<NormalizedLandmark> = landmarker.landmarks().first()
            val listPoints = mutableListOf<List<Float>>()
            for (landmark in landmarks) {
                listPoints.add(listOf(landmark.x(), landmark.y(), landmark.z()))
            }


            val angle_x = calculateAngle(listPoints[0], listPoints[1])
            val angle_x_2 = calculateAngle(listPoints[4], listPoints[5])
            val angle_x_3 = calculateAngle(listPoints[6], listPoints[7])
            val angle_x_4 = calculateAngle(listPoints[8], listPoints[9])
            val angle_x_5 = calculateAngle(listPoints[10], listPoints[11])
            val angle_x_6 = calculateAngle(listPoints[11], listPoints[12])
            val angle_12_26 = calculateAngle(listPoints[12], listPoints[26])
            val angle_11_25 = calculateAngle(listPoints[11], listPoints[25])
            val angle_20_24 = calculateAngle(listPoints[20], listPoints[24])
            val angle_x12_x23 = calculateAngle(listPoints[12], listPoints[23])
            val angle_x11_x24 = calculateAngle(listPoints[11], listPoints[24])
            val angle_x26_x25 = calculateAngle(listPoints[26], listPoints[25])
            val angle_x28_x27 = calculateAngle(listPoints[28], listPoints[27])
//            return "{'angle_x' :  $angle_x , 'angle_x_2' : $angle_x_2, 'angle_x_3' : $angle_x_3, " +
//                    "    'angle_x_4' : $angle_x_4, 'angle_x_5': $angle_x_5, 'angle_x_6': $angle_x_6,'angle_12_26': $angle_12_26, 'angle_11_25':$angle_11_25," +
//                    "    'angle_20_24' :$angle_20_24, 'angle_x12_x23' : $angle_x12_x23,'angle_x11_x24':$angle_x11_x24, 'angle_x26_x25':$angle_x26_x25," +
//                    "    'angle_x28_x27' : $angle_x28_x27}"
           return mapOf(
                Pair("angle_x",angle_x.toFloat()),
                Pair("angle_x_2",angle_x.toFloat()),
                Pair("angle_x_3",angle_x.toFloat()),
                Pair("angle_x_4",angle_x.toFloat()),
                Pair("angle_x_5",angle_x.toFloat()),
                Pair("angle_x_6",angle_x.toFloat()),
                Pair("angle_12_26",angle_x.toFloat()),
                Pair("angle_11_25",angle_x.toFloat()),
                Pair("angle_20_24",angle_x.toFloat()),
                Pair("angle_x12_x23",angle_x.toFloat()),
                Pair("angle_x11_x24",angle_x.toFloat()),
                Pair("angle_x26_x25",angle_x.toFloat()),
                Pair("angle_x28_x27",angle_x.toFloat())
            ).toString()
//            return listOf(
//                angle_x.toFloat(),
//                angle_x_2.toFloat(),
//                angle_x_3.toFloat(),
//                angle_x_4.toFloat(),
//                angle_x_5.toFloat(),
//                angle_x_6.toFloat(),
//                angle_12_26.toFloat(),
//                angle_11_25.toFloat(),
//                angle_20_24.toFloat(),
//                angle_x12_x23.toFloat(),
//                angle_x11_x24.toFloat(),
//                angle_x26_x25.toFloat(),
//                angle_x28_x27.toFloat()
//            )
        }

        @Throws(IOException::class)
        fun getFileFromAssets(context: Context, fileName: String): File =
            File(context.cacheDir, fileName)
                .also {
                    if (!it.exists()) {
                        it.outputStream().use { cache ->
                            context.assets.open(fileName).use { inputStream ->
                                inputStream.copyTo(cache)
                            }
                        }
                    }
                }
    }
}