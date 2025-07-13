cmake_minimum_required(VERSION 3.18.1)
project("Kaitou")
set (CMAKE_CXX_STANDARD 17)


# Declare the prebuilt Paddle Lite shared library
# add_library(paddle_light_api_shared SHARED IMPORTED)
# set_target_properties(paddle_light_api_shared PROPERTIES
#     IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaddle_light_api_shared.so
# )


# add_library(mecab SHARED IMPORTED)
# set_target_properties(mecab PROPERTIES
#     IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libmecab.so
# )

include_directories(${CMAKE_SOURCE_DIR}/include)

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(native-lib ${log-lib})
