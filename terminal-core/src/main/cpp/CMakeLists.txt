cmake_minimum_required(VERSION 3.22.1)

project(nbg_terminal_pty)

add_library(nbgpty SHARED pty.c)

find_library(log-lib log)

target_link_libraries(nbgpty ${log-lib})
