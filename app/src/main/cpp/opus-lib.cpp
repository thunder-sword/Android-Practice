#include <jni.h>
#include "include/opus.h"
#include <android/log.h>

#define LOG_TAG "NativeOpus"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局变量，用于保存 Opus 编码器和解码器的指针
static OpusEncoder* encoder = nullptr;
static OpusDecoder* decoder = nullptr;
static int channels = 1;

extern "C" JNIEXPORT jint JNICALL
Java_com_example_mypractice_AudioChatManager_initOpusEncoder(JNIEnv *env, jobject thiz, jint sampleRate, jint numChannels, jint application) {
    channels = numChannels;
    int error;
    encoder = opus_encoder_create(sampleRate, numChannels, application, &error);
    if (error != OPUS_OK) {
        LOGE("创建 Opus 编码器失败：%d", error);
        return error;
    }
    return OPUS_OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_mypractice_AudioChatManager_initOpusDecoder(JNIEnv *env, jobject thiz, jint sampleRate, jint numChannels) {
    channels = numChannels;
    int error;
    decoder = opus_decoder_create(sampleRate, numChannels, &error);
    if (error != OPUS_OK) {
        LOGE("创建 Opus 解码器失败：%d", error);
        return error;
    }
    return OPUS_OK;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_mypractice_AudioChatManager_encodeAudio(JNIEnv *env, jobject thiz, jshortArray pcmData) {
    // 获取 PCM 数据
    jsize len = env->GetArrayLength(pcmData);
    jshort *pcmBuffer = env->GetShortArrayElements(pcmData, nullptr);

    // 定义输出缓冲区（注意：实际项目中可根据需求动态分配）
    unsigned char outputBuffer[4000];

    // 计算帧数：每帧包含 samples = len / channels 个采样点
    int frameSize = len / channels;
    int encodedBytes = opus_encode(encoder, pcmBuffer, frameSize, outputBuffer, sizeof(outputBuffer));

    env->ReleaseShortArrayElements(pcmData, pcmBuffer, 0);

    if (encodedBytes < 0) {
        LOGE("Opus 编码失败：%d", encodedBytes);
        return nullptr;
    }

    // 将编码后的数据包装为 jbyteArray 返回
    jbyteArray encodedData = env->NewByteArray(encodedBytes);
    env->SetByteArrayRegion(encodedData, 0, encodedBytes, reinterpret_cast<jbyte*>(outputBuffer));
    return encodedData;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_example_mypractice_AudioChatManager_decodeAudio(JNIEnv *env, jobject thiz, jbyteArray opusData) {
    jsize len = env->GetArrayLength(opusData);
    jbyte *opusBuffer = env->GetByteArrayElements(opusData, nullptr);

    // 定义最大解码帧大小（例如：20ms 帧在 48000Hz 下大约 960 个采样点，可根据实际采样率调整）
    const int maxFrameSize = 960;
    short outputBuffer[maxFrameSize * channels];

    int frameSize = opus_decode(decoder, reinterpret_cast<unsigned char*>(opusBuffer), len, outputBuffer, maxFrameSize, 0);

    env->ReleaseByteArrayElements(opusData, opusBuffer, 0);

    if (frameSize < 0) {
        LOGE("Opus 解码失败：%d", frameSize);
        return nullptr;
    }

    jsize pcmLength = frameSize * channels;
    jshortArray pcmData = env->NewShortArray(pcmLength);
    env->SetShortArrayRegion(pcmData, 0, pcmLength, outputBuffer);
    return pcmData;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_mypractice_AudioChatManager_destroyOpus(JNIEnv *env, jobject thiz) {
    if (encoder) {
        opus_encoder_destroy(encoder);
        encoder = nullptr;
    }
    if (decoder) {
        opus_decoder_destroy(decoder);
        decoder = nullptr;
    }
}