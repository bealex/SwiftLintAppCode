cmake_minimum_required(VERSION 3.4)
project(SwiftLintTests)

add_custom_target(SwiftLintTests
        COMMAND /Applications/Xcode.13.2.1.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
        SOURCES SwiftLintTests/*.swift)
