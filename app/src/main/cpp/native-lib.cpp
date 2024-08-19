#include <jni.h>
#include <string>
#include <python3.10/Python.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapp_MainActivity_runPythonScript(JNIEnv* env, jobject /* this */) {
    Py_Initialize();
    PyObject *pName = PyUnicode_DecodeFSDefault("face_recognition");
    PyObject *pModule = PyImport_Import(pName);
    Py_XDECREF(pName);

    if (pModule != nullptr) {
        PyObject *pFunc = PyObject_GetAttrString(pModule, "main");
        if (pFunc && PyCallable_Check(pFunc)) {
            PyObject *pValue = PyObject_CallObject(pFunc, nullptr);
            if (pValue != nullptr) {
                std::string result = PyUnicode_AsUTF8(pValue);
                Py_XDECREF(pValue);
                Py_XDECREF(pFunc);
                Py_XDECREF(pModule);
                Py_Finalize();
                return env->NewStringUTF(result.c_str());
            }
        }
    }
    PyErr_Print();
    Py_Finalize();
    return env->NewStringUTF("Error");
}
