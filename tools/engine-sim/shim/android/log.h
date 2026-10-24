#pragma once
#include <stdio.h>
#include <stdarg.h>
#define ANDROID_LOG_INFO 4
extern int sim_verbose;
static inline int __android_log_print(int p, const char *tag, const char *fmt, ...) {
    if (!sim_verbose) return 0;
    va_list a; va_start(a, fmt); fprintf(stderr, "  [motor] "); vfprintf(stderr, fmt, a); fprintf(stderr, "\n"); va_end(a);
    return 0;
}
