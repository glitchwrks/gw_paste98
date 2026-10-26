/*
 * gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 *
 * Machine-dependent JNI types for 32-bit Windows, as the Windows JDKs define
 * them. Needed because we cross-compile against a Linux JDK's jni.h, whose
 * own jni_md.h describes Linux instead.
 */
#ifndef GW_PASTE98_JNI_MD_H
#define GW_PASTE98_JNI_MD_H

/*
 * Empty on purpose: build.sh lists the exports for the linker with their
 * undecorated names. __declspec(dllexport) would add a second, decorated
 * export directive (Java_..._nativeRun@24) that the JVM does not look up.
 */
#define JNIEXPORT
#define JNIIMPORT __declspec(dllimport)
#define JNICALL   __stdcall

typedef long jint;
typedef long long jlong;
typedef signed char jbyte;

#endif
