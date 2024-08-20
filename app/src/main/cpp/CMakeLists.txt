cmake_minimum_required(VERSION 3.4.1)

# Define the project name and the languages used
project(SecondDetectionPython LANGUAGES CXX)

# Specify the library to be built
add_library(native-lib SHARED native-lib.cpp)

# Locate Python library
find_library(python-lib python3.12 PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/arm64-v8a/ NO_DEFAULT_PATH)

# Locate OpenCV library
find_library(opencv-lib opencv PATHS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/arm64-v8a/ NO_DEFAULT_PATH)

# Include directories if necessary
include_directories(${CMAKE_SOURCE_DIR}/src/main/include)

# Link native libraries
target_link_libraries(
        native-lib
        ${python-lib}
        ${opencv-lib}
        log)  # Android log library
