cmake_minimum_required(VERSION 3.18.1)
project("Kaitou")
set (CMAKE_CXX_STANDARD 17)

add_library(paddle_light SHARED IMPORTED)
set_target_properties(paddle_light PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libpaddle_light_api_shared.so
)


# add_library(mecab SHARED IMPORTED)
# set_target_properties(mecab PROPERTIES
#     IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libmecab.so
# )

include_directories(${CMAKE_SOURCE_DIR}/include)

add_library(native-lib SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(native-lib ${log-lib} ${paddle_light})
