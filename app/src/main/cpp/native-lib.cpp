#include <jni.h>
#include <string>
#include <memory>
#include <android/bitmap.h>
#include <android/log.h>
#include <paddle_api.h>

#define LOG_TAG "OCRBridge - Native C++"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace paddle::lite_api;
