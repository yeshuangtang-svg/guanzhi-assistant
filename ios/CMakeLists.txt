cmake_minimum_required(VERSION 3.28)
project(QwenVision LANGUAGES C CXX OBJCXX Swift)
if(NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
    message(FATAL_ERROR "Configure on macOS with -DCMAKE_SYSTEM_NAME=iOS and Xcode")
endif()
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_Swift_LANGUAGE_VERSION 5)
set(LLAMA_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/vendor/llama.cpp" CACHE PATH "Pinned llama.cpp source")
set(MODEL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/Models" CACHE PATH "Verified lightweight weights")
set(QWEN_TEAM "" CACHE STRING "Apple signing team (optional for unsigned compile)")
set(QWEN_BUNDLE_ID "cn.shiguang.qwenvision.ios" CACHE STRING "Unique Apple bundle identifier")
foreach(option BUILD_SHARED_LIBS LLAMA_BUILD_APP LLAMA_BUILD_TESTS LLAMA_BUILD_TOOLS LLAMA_BUILD_EXAMPLES LLAMA_BUILD_SERVER LLAMA_BUILD_COMMON LLAMA_CURL LLAMA_OPENSSL LLAMA_SUBPROCESS MTMD_VIDEO GGML_NATIVE GGML_OPENMP GGML_BACKEND_DL GGML_VULKAN)
    set(${option} OFF CACHE BOOL "" FORCE)
endforeach()
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(GGML_METAL ON CACHE BOOL "" FORCE)
set(GGML_METAL_EMBED_LIBRARY ON CACHE BOOL "" FORCE)
set(GGML_METAL_TARGET_OS ios CACHE STRING "" FORCE)
set(GGML_BLAS ON CACHE BOOL "" FORCE)
set(GGML_BLAS_VENDOR Apple CACHE STRING "" FORCE)
add_subdirectory("${LLAMA_SOURCE}" llama-build EXCLUDE_FROM_ALL)
set(weights "${MODEL_DIR}/model-allint-w4w8.gguf" "${MODEL_DIR}/vision-allint-q8.gguf" "${MODEL_DIR}/manifest.json" "${MODEL_DIR}/allint-mobile-info.json")
foreach(path IN LISTS weights)
    if(NOT EXISTS "${path}")
        message(FATAL_ERROR "Missing bundled model asset: ${path}")
    endif()
endforeach()
set_source_files_properties(${weights} PROPERTIES MACOSX_PACKAGE_LOCATION Resources/Models)
file(GLOB swift_sources CONFIGURE_DEPENDS Sources/*.swift)
file(GLOB license_files Licenses/*)
set_source_files_properties(${license_files} PROPERTIES MACOSX_PACKAGE_LOCATION Resources/Licenses)
set_source_files_properties(Resources/Assets.xcassets PROPERTIES MACOSX_PACKAGE_LOCATION Resources)
add_executable(QwenVision MACOSX_BUNDLE ${swift_sources} Sources/EngineCore.cpp Sources/QVEngine.mm Sources/QVEngine.h ${weights} ${license_files} Resources/Assets.xcassets)
target_link_libraries(QwenVision PRIVATE llama mtmd "-framework SwiftUI" "-framework UIKit" "-framework PhotosUI" "-framework Security" "-framework Foundation")
target_compile_options(QwenVision PRIVATE "$<$<COMPILE_LANGUAGE:OBJCXX>:-fobjc-arc>")
set_target_properties(QwenVision PROPERTIES
    MACOSX_BUNDLE_INFO_PLIST "${CMAKE_CURRENT_SOURCE_DIR}/Resources/Info.plist"
    XCODE_GENERATE_SCHEME YES
    XCODE_ATTRIBUTE_PRODUCT_BUNDLE_IDENTIFIER "${QWEN_BUNDLE_ID}"
    XCODE_ATTRIBUTE_PRODUCT_NAME QwenVision
    XCODE_ATTRIBUTE_SWIFT_OBJC_BRIDGING_HEADER "${CMAKE_CURRENT_SOURCE_DIR}/Sources/QVEngine.h"
    XCODE_ATTRIBUTE_CLANG_ENABLE_MODULES YES
    XCODE_ATTRIBUTE_TARGETED_DEVICE_FAMILY 1
    XCODE_ATTRIBUTE_IPHONEOS_DEPLOYMENT_TARGET 18.0
    XCODE_ATTRIBUTE_ASSETCATALOG_COMPILER_APPICON_NAME AppIcon
    XCODE_ATTRIBUTE_CODE_SIGN_STYLE Automatic
    XCODE_ATTRIBUTE_DEVELOPMENT_TEAM "${QWEN_TEAM}"
    XCODE_ATTRIBUTE_ENABLE_BITCODE NO
    XCODE_ATTRIBUTE_SWIFT_STRICT_CONCURRENCY minimal
)
