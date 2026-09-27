cmake_minimum_required(VERSION 3.22.1)
project("vido_pro_native")

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# FFmpeg libraries path (if linked against ffmpeg-kit or standalone shared libraries)
set(FFMPEG_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
include_directories(${CMAKE_SOURCE_DIR}/include)

find_library(avformat-lib avformat PATHS ${FFMPEG_DIR})
find_library(avcodec-lib avcodec PATHS ${FFMPEG_DIR})
find_library(avutil-lib avutil PATHS ${FFMPEG_DIR})
find_library(swscale-lib swscale PATHS ${FFMPEG_DIR})

target_link_libraries(native-lib
    ${avformat-lib}
    ${avcodec-lib}
    ${avutil-lib}
    ${swscale-lib}
    ${jnigraphics-lib}
    ${log-lib}
)
