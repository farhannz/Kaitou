#include <jni.h>
#include <string>
#include <memory>
#include <android/bitmap.h>
#include <android/log.h>
#include <paddle_api.h>
//#include <mecab.h>

#define LOG_TAG "OCRBridge - Native C++"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace paddle::lite_api;

std::shared_ptr<PaddlePredictor> predictor;
//std::shared_ptr<MeCab::Tagger> tagger;

extern "C"
JNIEXPORT void JNICALL
Java_com_farhannz_kaitou_bridges_OCRBridge_initPaddle(JNIEnv* env) {
    LOGI("Initializing Paddle Lite");
    // const char* modelDir = env->GetStringUTFChars(modelDir_, nullptr);

    // MobileConfig config;
    // config.set_model_from_file(std::string(modelDir) + "/model.nb");
    // predictor = CreatePaddlePredictor<MobileConfig>(config);

    // env->ReleaseStringUTFChars(modelDir_, modelDir);
}


//extern "C"
//JNIEXPORT void JNICALL
//Java_com_farhannz_kaitou_bridges_OCRBridge_initMecab(JNIEnv* env, jobject /*this*/, jstring dictPath) {
//    auto path = env->GetStringUTFChars(dictPath,nullptr);
//    std::string mecabDictPath = "-d ";
//    mecabDictPath += std::string(path);
//    LOGI("Initializing MeCab (neologd) with dict path : %s ", path);
//    tagger = std::shared_ptr<MeCab::Tagger>(MeCab::createTagger(const_cast<char*>(mecabDictPath.c_str())), MeCab::deleteTagger);
//    // const char* modelDir = env->GetStringUTFChars(modelDir_, nullptr);
//
//    // MobileConfig config;
//    // config.set_model_from_file(std::string(modelDir) + "/model.nb");
//    // predictor = CreatePaddlePredictor<MobileConfig>(config);
//
//     env->ReleaseStringUTFChars(dictPath, path);
//}




extern "C"
JNIEXPORT jstring JNICALL
Java_com_farhannz_kaitou_bridges_OCRBridge_runOCR(JNIEnv *env) {
    std::string result = R"([
        {"word":"こんにちは","x1":100,"y1":100,"x2":300,"y2":150},
        {"word":"日本語","x1":120,"y1":200,"x2":280,"y2":240}
    ])";

//    auto tokenized = tagger->parse("日本語がわかりませんだから勉強した");
//    LOGI("Tokenizer\n %s \n", tokenized);
    return env->NewStringUTF(result.c_str());
}