#include <jni.h>
#include <string>
#include <memory>
#include <android/bitmap.h>
#include <android/log.h>
#include <paddle_api.h>

#define LOG_TAG "PaddleHelper - Native C++"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace paddle::lite_api;

extern "C"
JNIEXPORT jlong JNICALL
Java_com_farhannz_kaitou_paddle_PaddleHelper_getTensorBufferAddress(
        JNIEnv *env, jclass clazz, jlong tensorAddr, jint length) {
    auto *tensor = reinterpret_cast<Tensor *>(tensorAddr);
    tensor->Resize({length});
    float* buffer = tensor->mutable_data<float>(TargetType::kHost);
    return reinterpret_cast<jlong>(buffer);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_farhannz_kaitou_paddle_PaddleHelper_copyBufferToAddress(
        JNIEnv *env, jclass clazz, jobject jbuffer, jlong nativePtr, jint length) {
    float *dst = reinterpret_cast<float *>(nativePtr);
    float *src = reinterpret_cast<float *>(env->GetDirectBufferAddress(jbuffer));
    memcpy(dst, src, length * sizeof(float));
}