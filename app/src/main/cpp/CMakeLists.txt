cmake_minimum_required(VERSION 3.22.1)
project("kaitou")


add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libpaddle_light_api_shared.so
)

include_directories("${CMAKE_SOURCE_DIR}/include")
add_library(native-lib SHARED native-lib.cpp)      # 1 create the target
find_library(log-lib log)                          # 2 find system libs
target_link_libraries(native-lib ${log-lib} paddle_light_api_shared)

add_library(paddle_helper SHARED paddle-helper.cpp)
target_link_libraries(paddle_helper ${log-lib} paddle_light_api_shared)