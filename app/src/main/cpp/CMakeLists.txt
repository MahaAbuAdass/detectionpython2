cmake_minimum_required(VERSION 3.4.1)

# Define your native library
add_library(native-lib SHARED native-lib.cpp)

# Include Python libraries
find_library(python-lib python3.12 PATHS src/main/jniLibs/arm64-v8a/)

# Include OpenCV and other library paths if needed
find_library(opencv-lib opencv PATHS src/main/jniLibs/arm64-v8a/)

# Link libraries
target_link_libraries(
        native-lib
        ${python-lib}
        ${opencv-lib}
        log)  # Android log library
