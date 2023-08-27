package ru.inncreator.sintez

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import ru.inncreator.sintez.utility.Utility
import java.util.concurrent.CopyOnWriteArrayList

object MlModel {

    private lateinit var converter: PyObject
    private lateinit var py: Python

    private var isInit = false


    fun initModel(context: Context) {
        if (!isInit) {
            py = Python.getInstance()
            val file = Utility.getFileFromAssets(context, "random_forest28.pkl")
            converter = py.getModule("ConvertString").callAttr("ConvertString", file.absolutePath)
//            val pandasTmp = py.getModule("pandas")
//            pandas = pandasTmp.callAttr("pandas")
//            val joblib = py.getModule("joblib")
//            val file = Utility.getFileFromAssets(context, "random_forest28.pkl")
//            loadedModel = joblib.callAttr("load", file.absolutePath)
//            isInit = true
        }
    }

    fun tryModel(list: PoseLandmarkerResult) {
        if (list.landmarks().isNotEmpty()) {
            val input = Utility.getAngel(list)
            val pyStr = PyObject.fromJava(input)
            py.builtins.callAttr("dict",pyStr).toString()
            val b = converter.callAttr("setString", pyStr)
            converter.callAttr("updateData")
            converter.callAttr("getData")
            b.toString()
//            val inb = py.getModule("ConvertString").callAttr("ConvertString", input).callAttr("getResult")
//            val dataFrame = pandas.callAttr("DataFrame", inb)
//            val newPredictions = loadedModel.callAttr("predict", dataFrame)
//            println(newPredictions)
        }
    }

    private fun createDir(input: List<Float>) {
        val mutableList = mutableListOf<PyObject>()
        for (i in input) {
            mutableList.add(py.builtins.callAttr("float", i))
        }
        PyObject.fromJava(mutableList)
    }
}