/*
 * gw_paste98 - Java 5 / Windows 98 client for the Glitch Works pastes service
 * Copyright (C) 2026 Glitch Works, LLC
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file for details.
 *
 * gwtray.dll: a notification area ("system tray") icon for Java 5, which has
 * no tray API of its own (java.awt.SystemTray arrived in Java 6).
 *
 * Windows 98 constraints this code observes:
 *   - Only ANSI ("A") Win32 functions that Windows 98 exports.
 *   - No message-only window (HWND_MESSAGE is Windows 2000+); a hidden
 *     WS_POPUP window receives the tray messages instead.
 *   - The original 88-byte NOTIFYICONDATA layout, which is what the
 *     Windows 98 shell expects.
 *   - No C runtime at all: no CRT startup code and no msvcrt imports, so
 *     the only imports are KERNEL32, USER32 and SHELL32 functions.
 *
 * Threading: Java calls nativeRun() on a dedicated Java thread, which runs
 * the Win32 message loop until nativeQuit(). Callbacks into Java happen on
 * that same thread, so no JNI thread attaching is needed. The Java callback
 * must return quickly (it hands the event to Swing's event thread).
 */

#include <windows.h>
#include <shellapi.h>
#include <jni.h>

#define GWTRAY_VERSION 1

#define WM_GWTRAY       (WM_APP + 1)   /* tray icon mouse events */
#define WM_GWTIP        (WM_APP + 2)   /* tooltip changed */
#define MENU_ID_BASE    1000
#define MAX_ITEMS       16
#define MAX_LABEL       64
#define TIP_LEN         64

/* Event codes passed to NativeTray.onEvent(int); menu items are 0..n-1. */
#define EVENT_DEFAULT   (-1)   /* double-click on the icon */
#define EVENT_READY     (-2)   /* icon added; the tray is up */

/* Return codes of nativeRun(). */
#define RUN_OK            0
#define RUN_BAD_ARGS     (-1)
#define RUN_NO_CLASS     (-2)
#define RUN_NO_WINDOW    (-3)
#define RUN_NO_ICON      (-4)
#define RUN_BUSY         (-5)

/* NOTIFYICONDATA as the Windows 98 shell knows it (cbSize = 88). */
typedef struct {
    DWORD cbSize;
    HWND hWnd;
    UINT uID;
    UINT uFlags;
    UINT uCallbackMessage;
    HICON hIcon;
    CHAR szTip[TIP_LEN];
} GW_NOTIFYICONDATA;

static HINSTANCE g_module;
static CRITICAL_SECTION g_lock;
static HWND g_hwnd;
static HMENU g_menu;
static HICON g_icon;
static BOOL g_iconFromFile;
static UINT g_msgTaskbarCreated;
static volatile LONG g_running;
static volatile LONG g_quitRequested;
static char g_tip[TIP_LEN];

static JNIEnv *g_env;
static jclass g_class;
static jmethodID g_onEvent;

static const char CLASS_NAME[] = "GwPaste98Tray";

/* ---- no C runtime: the compiler may still emit calls to these ---------- */

void *memset(void *dst, int c, size_t n) {
    unsigned char *d = (unsigned char *) dst;
    while (n--) {
        *d++ = (unsigned char) c;
    }
    return dst;
}

void *memcpy(void *dst, const void *src, size_t n) {
    unsigned char *d = (unsigned char *) dst;
    const unsigned char *s = (const unsigned char *) src;
    while (n--) {
        *d++ = *s++;
    }
    return dst;
}

BOOL WINAPI DllMainCRTStartup(HINSTANCE module, DWORD reason, LPVOID reserved) {
    (void) reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        g_module = module;
        InitializeCriticalSection(&g_lock);
    } else if (reason == DLL_PROCESS_DETACH) {
        DeleteCriticalSection(&g_lock);
    }
    return TRUE;
}

/* ---- helpers ------------------------------------------------------------ */

/* Copies a Java string into buf as ASCII; anything else becomes '?'. */
static void copy_jstring(JNIEnv *env, jstring s, char *buf, int size) {
    int i = 0;
    if (s != NULL) {
        const jchar *chars = (*env)->GetStringChars(env, s, NULL);
        jsize len = (*env)->GetStringLength(env, s);
        if (chars != NULL) {
            for (; i < len && i < size - 1; i++) {
                jchar c = chars[i];
                buf[i] = (c >= 0x20 && c < 0x7f) ? (char) c : '?';
            }
            (*env)->ReleaseStringChars(env, s, chars);
        }
    }
    buf[i] = '\0';
}

static void fire(int code) {
    if (g_env == NULL || g_class == NULL || g_onEvent == NULL) {
        return;
    }
    (*g_env)->CallStaticVoidMethod(g_env, g_class, g_onEvent, (jint) code);
    if ((*g_env)->ExceptionCheck(g_env)) {
        /* Keep the message loop alive whatever the Java side does. */
        (*g_env)->ExceptionDescribe(g_env);
        (*g_env)->ExceptionClear(g_env);
    }
}

static void fill_nid(GW_NOTIFYICONDATA *nid, UINT flags) {
    memset(nid, 0, sizeof(*nid));
    nid->cbSize = sizeof(*nid);
    nid->hWnd = g_hwnd;
    nid->uID = 1;
    nid->uFlags = flags;
    nid->uCallbackMessage = WM_GWTRAY;
    nid->hIcon = g_icon;
    EnterCriticalSection(&g_lock);
    lstrcpynA(nid->szTip, g_tip, TIP_LEN);
    LeaveCriticalSection(&g_lock);
}

static BOOL add_icon(void) {
    GW_NOTIFYICONDATA nid;
    fill_nid(&nid, NIF_ICON | NIF_MESSAGE | NIF_TIP);
    return Shell_NotifyIconA(NIM_ADD, (PNOTIFYICONDATAA) &nid);
}

static void update_tip(void) {
    GW_NOTIFYICONDATA nid;
    fill_nid(&nid, NIF_TIP);
    Shell_NotifyIconA(NIM_MODIFY, (PNOTIFYICONDATAA) &nid);
}

static void remove_icon(void) {
    GW_NOTIFYICONDATA nid;
    fill_nid(&nid, 0);
    Shell_NotifyIconA(NIM_DELETE, (PNOTIFYICONDATAA) &nid);
}

static void show_menu(HWND hwnd) {
    POINT p;
    UINT cmd;
    if (g_menu == NULL) {
        return;
    }
    GetCursorPos(&p);
    /* Without this the menu does not close when clicking elsewhere (KB135788). */
    SetForegroundWindow(hwnd);
    cmd = (UINT) TrackPopupMenu(g_menu, TPM_RETURNCMD | TPM_NONOTIFY | TPM_RIGHTBUTTON,
                                p.x, p.y, 0, hwnd, NULL);
    PostMessageA(hwnd, WM_NULL, 0, 0);
    if (cmd >= MENU_ID_BASE && cmd < MENU_ID_BASE + MAX_ITEMS) {
        fire((int) (cmd - MENU_ID_BASE));
    }
}

static LRESULT CALLBACK wnd_proc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    if (msg == WM_GWTRAY) {
        switch ((UINT) lp) {
        case WM_LBUTTONDBLCLK:
            fire(EVENT_DEFAULT);
            return 0;
        case WM_RBUTTONUP:
            show_menu(hwnd);
            return 0;
        default:
            return 0;
        }
    }
    if (g_msgTaskbarCreated != 0 && msg == g_msgTaskbarCreated) {
        add_icon(); /* Explorer restarted: put the icon back */
        return 0;
    }
    switch (msg) {
    case WM_GWTIP:
        update_tip();
        return 0;
    case WM_CLOSE:
        DestroyWindow(hwnd);
        return 0;
    case WM_DESTROY:
        remove_icon();
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProcA(hwnd, msg, wp, lp);
    }
}

/* ---- JNI ---------------------------------------------------------------- */

/*
 * Class:     com.glitchwrks.paste98.client.tray.NativeTray
 * Method:    nativeVersion()I
 */
JNIEXPORT jint JNICALL
Java_com_glitchwrks_paste98_client_tray_NativeTray_nativeVersion(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return GWTRAY_VERSION;
}

/*
 * Method: nativeRun(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;I)I
 *
 * Adds the icon and runs the message loop until nativeQuit(). Menu labels of
 * "-" are separators. Returns RUN_OK after a normal quit, or a negative code
 * if the tray could not be set up.
 */
JNIEXPORT jint JNICALL
Java_com_glitchwrks_paste98_client_tray_NativeTray_nativeRun(JNIEnv *env, jclass cls, jstring iconPath,
                                                            jstring tooltip, jobjectArray items,
                                                            jint defaultItem) {
    WNDCLASSA wc;
    MSG msg;
    char path[MAX_PATH];
    char label[MAX_LABEL];
    jsize count;
    jsize i;

    if (InterlockedExchange((LONG *) &g_running, 1) != 0) {
        return RUN_BUSY;
    }
    g_quitRequested = 0;

    g_env = env;
    g_class = (jclass) (*env)->NewGlobalRef(env, cls);
    g_onEvent = (*env)->GetStaticMethodID(env, cls, "onEvent", "(I)V");
    if (g_class == NULL || g_onEvent == NULL || items == NULL) {
        (*env)->ExceptionClear(env);
        if (g_class != NULL) {
            (*env)->DeleteGlobalRef(env, g_class);
        }
        g_class = NULL;
        g_env = NULL;
        g_running = 0;
        return RUN_BAD_ARGS;
    }

    EnterCriticalSection(&g_lock);
    copy_jstring(env, tooltip, g_tip, TIP_LEN);
    LeaveCriticalSection(&g_lock);

    /* Icon: from the .ico file if possible, else the stock application icon. */
    copy_jstring(env, iconPath, path, MAX_PATH);
    g_icon = (HICON) LoadImageA(NULL, path, IMAGE_ICON, 16, 16, LR_LOADFROMFILE);
    g_iconFromFile = (g_icon != NULL);
    if (g_icon == NULL) {
        g_icon = LoadIconA(NULL, (LPCSTR) IDI_APPLICATION);
    }

    /* Menu */
    g_menu = CreatePopupMenu();
    count = (*env)->GetArrayLength(env, items);
    for (i = 0; g_menu != NULL && i < count && i < MAX_ITEMS; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, items, i);
        copy_jstring(env, s, label, MAX_LABEL);
        if (s != NULL) {
            (*env)->DeleteLocalRef(env, s);
        }
        if (label[0] == '-' && label[1] == '\0') {
            AppendMenuA(g_menu, MF_SEPARATOR, 0, NULL);
        } else {
            AppendMenuA(g_menu, MF_STRING, (UINT_PTR) (MENU_ID_BASE + i), label);
        }
    }
    if (g_menu != NULL && defaultItem >= 0 && defaultItem < count) {
        SetMenuDefaultItem(g_menu, (UINT) (MENU_ID_BASE + defaultItem), FALSE);
    }

    /* Hidden window to receive tray messages */
    memset(&wc, 0, sizeof(wc));
    wc.lpfnWndProc = wnd_proc;
    wc.hInstance = g_module;
    wc.lpszClassName = CLASS_NAME;
    RegisterClassA(&wc); /* may already exist from an earlier run; CreateWindow decides */

    g_hwnd = CreateWindowExA(0, CLASS_NAME, "gw_paste98", WS_POPUP, 0, 0, 0, 0, NULL, NULL, g_module, NULL);
    if (g_hwnd == NULL) {
        jint rc = RUN_NO_WINDOW;
        if (g_menu != NULL) {
            DestroyMenu(g_menu);
            g_menu = NULL;
        }
        if (g_iconFromFile) {
            DestroyIcon(g_icon);
        }
        g_icon = NULL;
        (*env)->DeleteGlobalRef(env, g_class);
        g_class = NULL;
        g_env = NULL;
        g_running = 0;
        return rc;
    }

    g_msgTaskbarCreated = RegisterWindowMessageA("TaskbarCreated");

    if (!add_icon()) {
        DestroyWindow(g_hwnd); /* WM_DESTROY posts WM_QUIT; drained below */
        while (GetMessageA(&msg, NULL, 0, 0) > 0) {
            DispatchMessageA(&msg);
        }
        g_hwnd = NULL;
        if (g_menu != NULL) {
            DestroyMenu(g_menu);
            g_menu = NULL;
        }
        if (g_iconFromFile) {
            DestroyIcon(g_icon);
        }
        g_icon = NULL;
        (*env)->DeleteGlobalRef(env, g_class);
        g_class = NULL;
        g_env = NULL;
        g_running = 0;
        return RUN_NO_ICON;
    }

    fire(EVENT_READY);
    if (g_quitRequested) {
        PostMessageA(g_hwnd, WM_CLOSE, 0, 0);
    }

    while (GetMessageA(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageA(&msg);
    }

    g_hwnd = NULL;
    if (g_menu != NULL) {
        DestroyMenu(g_menu);
        g_menu = NULL;
    }
    if (g_iconFromFile) {
        DestroyIcon(g_icon);
    }
    g_icon = NULL;
    (*env)->DeleteGlobalRef(env, g_class);
    g_class = NULL;
    g_onEvent = NULL;
    g_env = NULL;
    g_running = 0;
    return RUN_OK;
}

/* Method: nativeSetTooltip(Ljava/lang/String;)V -- callable from any thread. */
JNIEXPORT void JNICALL
Java_com_glitchwrks_paste98_client_tray_NativeTray_nativeSetTooltip(JNIEnv *env, jclass cls, jstring tooltip) {
    HWND hwnd;
    (void) cls;
    EnterCriticalSection(&g_lock);
    copy_jstring(env, tooltip, g_tip, TIP_LEN);
    LeaveCriticalSection(&g_lock);
    hwnd = g_hwnd;
    if (hwnd != NULL) {
        PostMessageA(hwnd, WM_GWTIP, 0, 0);
    }
}

/* Method: nativeQuit()V -- callable from any thread; removes the icon and ends nativeRun(). */
JNIEXPORT void JNICALL
Java_com_glitchwrks_paste98_client_tray_NativeTray_nativeQuit(JNIEnv *env, jclass cls) {
    HWND hwnd;
    (void) env;
    (void) cls;
    g_quitRequested = 1;
    hwnd = g_hwnd;
    if (hwnd != NULL) {
        PostMessageA(hwnd, WM_CLOSE, 0, 0);
    }
}
