#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <android/bitmap.h>

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
}

#define TAG "NativeThumbnailExtractor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

static jobject createBitmap(JNIEnv *env, int width, int height) {
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethod = env->GetStaticMethodID(
        bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
    );
    return env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, width, height, config);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_vikash_vidopro_editor_media_NativeThumbnailExtractor_extractFramesNative(
    JNIEnv *env,
    jobject /* this */,
    jstring videoPathStr,
    jlongArray timestampsMsArray,
    jint targetWidth,
    jint targetHeight
) {
    if (videoPathStr == nullptr || timestampsMsArray == nullptr) {
        return nullptr;
    }

    const char *videoPath = env->GetStringUTFChars(videoPathStr, nullptr);
    jsize numTimestamps = env->GetArrayLength(timestampsMsArray);
    jlong *timestamps = env->GetLongArrayElements(timestampsMsArray, nullptr);

    AVFormatContext *formatCtx = nullptr;
    if (avformat_open_input(&formatCtx, videoPath, nullptr, nullptr) != 0) {
        LOGE("Failed to open video file: %s", videoPath);
        env->ReleaseStringUTFChars(videoPathStr, videoPath);
        env->ReleaseLongArrayElements(timestampsMsArray, timestamps, JNI_ABORT);
        return nullptr;
    }

    if (avformat_find_stream_info(formatCtx, nullptr) < 0) {
        LOGE("Failed to retrieve stream info");
        avformat_close_input(&formatCtx);
        env->ReleaseStringUTFChars(videoPathStr, videoPath);
        env->ReleaseLongArrayElements(timestampsMsArray, timestamps, JNI_ABORT);
        return nullptr;
    }

    int videoStreamIdx = -1;
    for (unsigned int i = 0; i < formatCtx->nb_streams; i++) {
        if (formatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoStreamIdx = static_cast<int>(i);
            break;
        }
    }

    if (videoStreamIdx == -1) {
        LOGE("No video stream found");
        avformat_close_input(&formatCtx);
        env->ReleaseStringUTFChars(videoPathStr, videoPath);
        env->ReleaseLongArrayElements(timestampsMsArray, timestamps, JNI_ABORT);
        return nullptr;
    }

    AVCodecParameters *codecPar = formatCtx->streams[videoStreamIdx]->codecpar;
    const AVCodec *codec = avcodec_find_decoder(codecPar->codec_id);
    if (!codec) {
        LOGE("Codec not found");
        avformat_close_input(&formatCtx);
        env->ReleaseStringUTFChars(videoPathStr, videoPath);
        env->ReleaseLongArrayElements(timestampsMsArray, timestamps, JNI_ABORT);
        return nullptr;
    }

    AVCodecContext *codecCtx = avcodec_alloc_context3(codec);
    if (avcodec_parameters_to_context(codecCtx, codecPar) < 0 || avcodec_open2(codecCtx, codec, nullptr) < 0) {
        LOGE("Could not open video codec");
        avcodec_free_context(&codecCtx);
        avformat_close_input(&formatCtx);
        env->ReleaseStringUTFChars(videoPathStr, videoPath);
        env->ReleaseLongArrayElements(timestampsMsArray, timestamps, JNI_ABORT);
        return nullptr;
    }

    AVRational timeBase = formatCtx->streams[videoStreamIdx]->time_base;

    AVFrame *frame = av_frame_alloc();
    AVFrame *rgbaFrame = av_frame_alloc();
    int numBytes = av_image_get_buffer_size(AV_PIX_FMT_RGBA, targetWidth, targetHeight, 1);
    auto *rgbaBuffer = static_cast<uint8_t *>(av_malloc(numBytes * sizeof(uint8_t)));
    av_image_fill_arrays(rgbaFrame->data, rgbaFrame->linesize, rgbaBuffer, AV_PIX_FMT_RGBA, targetWidth, targetHeight, 1);

    struct SwsContext *swsCtx = sws_getContext(
        codecCtx->width, codecCtx->height, codecCtx->pix_fmt,
        targetWidth, targetHeight, AV_PIX_FMT_RGBA,
        SWS_FAST_BILINEAR, nullptr, nullptr, nullptr
    );

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jobjectArray bitmapArray = env->NewObjectArray(numTimestamps, bitmapClass, nullptr);
    AVPacket *packet = av_packet_alloc();

    for (int t = 0; t < numTimestamps; ++t) {
        int64_t targetPts = static_cast<int64_t>(timestamps[t] * (timeBase.den / (1000.0 * timeBase.num)));

        // Accurate Keyframe seek
        av_seek_frame(formatCtx, videoStreamIdx, targetPts, AVSEEK_FLAG_BACKWARD);
        avcodec_flush_buffers(codecCtx);

        bool frameFound = false;
        while (av_read_frame(formatCtx, packet) >= 0) {
            if (packet->stream_index == videoStreamIdx) {
                if (avcodec_send_packet(codecCtx, packet) == 0) {
                    while (avcodec_receive_frame(codecCtx, frame) == 0) {
                        if (frame->pts >= targetPts || frameFound) {
                            sws_scale(swsCtx, static_cast<const uint8_t *const *>(frame->data),
                                      frame->linesize, 0, codecCtx->height,
                                      rgbaFrame->data, rgbaFrame->linesize);

                            jobject bmp = createBitmap(env, targetWidth, targetHeight);
                            void *bitmapPixels = nullptr;
                            if (AndroidBitmap_lockPixels(env, bmp, &bitmapPixels) == 0) {
                                memcpy(bitmapPixels, rgbaFrame->data[0], targetWidth * targetHeight * 4);
                                AndroidBitmap_unlockPixels(env, bmp);
                            }
                            env->SetObjectArrayElement(bitmapArray, t, bmp);
                            env->DeleteLocalRef(bmp);

                            frameFound = true;
                            break;
                        }
                    }
                }
            }
            av_packet_unref(packet);
            if (frameFound) break;
        }
    }

    // Cleanup resources
    av_packet_free(&packet);
    av_free(rgbaBuffer);
    av_frame_free(&rgbaFrame);
    av_frame_free(&frame);
    if (swsCtx) sws_freeContext(swsCtx);
    avcodec_free_context(&codecCtx);
    avformat_close_input(&formatCtx);
    env->ReleaseStringUTFChars(videoPathStr, videoPath);
    env->ReleaseLongArrayElements(timestampsMsArray, timestamps, JNI_ABORT);

    return bitmapArray;
}
