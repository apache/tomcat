/* Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 *
 * @author Mladen Turk
 * @version $Revision$, $Date$
 */

#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0500
#endif
#include <winsock2.h>
#include <mswsock.h>
#include <ws2tcpip.h>
#include "apr.h"
#include "apr_pools.h"
#include "apr_poll.h"
#include "apr_network_io.h"
#include "apr_arch_misc.h" /* for apr_os_level */
#include "apr_arch_atime.h"  /* for FileTimeToAprTime */

#include "tcn.h"
#include "ssl_private.h"

#pragma warning(push)
#pragma warning(disable : 4201)
#if (_WIN32_WINNT < 0x0501)
#include <winternl.h>
#endif
#include <psapi.h>
#pragma warning(pop)


static CRITICAL_SECTION dll_critical_section;   /* dll's critical section */
static HINSTANCE        dll_instance = NULL;
static SYSTEM_INFO      dll_system_info;
static HANDLE           h_kernel = NULL;
static HANDLE           h_ntdll  = NULL;
static char             dll_file_name[MAX_PATH];

typedef BOOL (WINAPI *pfnGetSystemTimes)(LPFILETIME, LPFILETIME, LPFILETIME);
static pfnGetSystemTimes fnGetSystemTimes = NULL;
#if (_WIN32_WINNT < 0x0501)
typedef NTSTATUS (WINAPI *pfnNtQuerySystemInformation)(SYSTEM_INFORMATION_CLASS, PVOID, ULONG, PULONG);
static pfnNtQuerySystemInformation fnNtQuerySystemInformation = NULL;
#endif

BOOL
WINAPI
DllMain(
    HINSTANCE instance,
    DWORD reason,
    LPVOID reserved)
{

    switch (reason) {
        /** The DLL is loading due to process
         *  initialization or a call to LoadLibrary.
         */
        case DLL_PROCESS_ATTACH:
            InitializeCriticalSection(&dll_critical_section);
            dll_instance = instance;
            GetSystemInfo(&dll_system_info);
            if ((h_kernel = LoadLibrary("kernel32.dll")) != NULL)
                fnGetSystemTimes = (pfnGetSystemTimes)GetProcAddress(h_kernel,
                                                            "GetSystemTimes");
            if (fnGetSystemTimes == NULL) {
                FreeLibrary(h_kernel);
                h_kernel = NULL;
#if (_WIN32_WINNT < 0x0501)
                if ((h_ntdll = LoadLibrary("ntdll.dll")) != NULL)
                    fnNtQuerySystemInformation =
                        (pfnNtQuerySystemInformation)GetProcAddress(h_ntdll,
                                                "NtQuerySystemInformation");

                if (fnNtQuerySystemInformation == NULL) {
                    FreeLibrary(h_ntdll);
                    h_ntdll = NULL;
                }
#endif
            }
            GetModuleFileName(instance, dll_file_name, sizeof(dll_file_name));
            break;
        /** The attached process creates a new thread.
         */
        case DLL_THREAD_ATTACH:
            break;

        /** The thread of the attached process terminates.
         */
        case DLL_THREAD_DETACH:
            break;

        /** DLL unload due to process termination
         *  or FreeLibrary.
         */
        case DLL_PROCESS_DETACH:
            /* Make sure the library is always terminated */
            apr_terminate();
            if (h_kernel)
                FreeLibrary(h_kernel);
            if (h_ntdll)
                FreeLibrary(h_ntdll);
            DeleteCriticalSection(&dll_critical_section);
            break;

        default:
            break;
    }

    return TRUE;
    UNREFERENCED_PARAMETER(reserved);
}


TCN_IMPLEMENT_CALL(jstring, OS, syserror)(TCN_STDARGS, jint err)
{
    jstring str;
    void *buf;

    UNREFERENCED(o);
    if (!FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER |
                       FORMAT_MESSAGE_FROM_SYSTEM |
                       FORMAT_MESSAGE_IGNORE_INSERTS,
                       NULL,
                       err,
                       MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                       (LPTSTR)&buf,
                       0,
                       NULL)) {
        str = AJP_TO_JSTRING("Unknown Error");
    }
    else {
        str = AJP_TO_JSTRING((const char *)buf);
        LocalFree(buf);
    }
    return str;
}

TCN_IMPLEMENT_CALL(jstring, OS, expand)(TCN_STDARGS, jstring val)
{
    jstring str;
    jchar buf[TCN_BUFFER_SZ] = L"";
    DWORD len;
    TCN_ALLOC_WSTRING(val);

    UNREFERENCED(o);
    TCN_INIT_WSTRING(val);

    len = ExpandEnvironmentStringsW(J2W(val), buf, TCN_BUFFER_SZ - 1);
    if (len > (TCN_BUFFER_SZ - 1)) {
        jchar *dbuf = malloc((len + 1) * 2);
        ExpandEnvironmentStringsW(J2W(val), dbuf, len);
        str = (*e)->NewString(e, dbuf, wcslen(dbuf));
        free(dbuf);
    }
    else
        str = (*e)->NewString(e, buf, wcslen(buf));

    TCN_FREE_WSTRING(val);
    return str;
}

#define LOG_MSG_EMERG                    0xC0000001L
#define LOG_MSG_ERROR                    0xC0000002L
#define LOG_MSG_NOTICE                   0x80000003L
#define LOG_MSG_WARN                     0x80000004L
#define LOG_MSG_INFO                     0x40000005L
#define LOG_MSG_DEBUG                    0x00000006L
#define LOG_MSG_DOMAIN                   "Native"

static char log_domain[MAX_PATH] = "Native";

static void init_log_source(const char *domain)
{
    HKEY  key;
    DWORD ts;
    char event_key[MAX_PATH];

    strcpy(event_key, "SYSTEM\\CurrentControlSet\\Services\\EventLog\\Application\\");
    strcat(event_key, domain);
    if (!RegCreateKey(HKEY_LOCAL_MACHINE, event_key, &key)) {
        RegSetValueEx(key, "EventMessageFile", 0, REG_SZ, (LPBYTE)&dll_file_name[0],
                      strlen(dll_file_name) + 1);
        ts = EVENTLOG_ERROR_TYPE | EVENTLOG_WARNING_TYPE | EVENTLOG_INFORMATION_TYPE;

        RegSetValueEx(key, "TypesSupported", 0, REG_DWORD, (LPBYTE) &ts, sizeof(DWORD));
        RegCloseKey(key);
    }
    strcpy(log_domain, domain);
}

TCN_IMPLEMENT_CALL(void, OS, sysloginit)(TCN_STDARGS, jstring domain)
{
    const char *d;
    TCN_ALLOC_CSTRING(domain);

    UNREFERENCED(o);

    if ((d = J2S(domain)) == NULL)
        d = LOG_MSG_DOMAIN;
    init_log_source(d);

    TCN_FREE_CSTRING(domain);
}

TCN_IMPLEMENT_CALL(void, OS, syslog)(TCN_STDARGS, jint level,
                                     jstring msg)
{
    TCN_ALLOC_CSTRING(msg);
    DWORD id = LOG_MSG_DEBUG;
    WORD  il = EVENTLOG_SUCCESS;
    HANDLE  source;
    const char *messages[1];
    UNREFERENCED(o);

    switch (level) {
        case TCN_LOG_EMERG:
            id = LOG_MSG_EMERG;
            il = EVENTLOG_ERROR_TYPE;
        break;
        case TCN_LOG_ERROR:
            id = LOG_MSG_ERROR;
            il = EVENTLOG_ERROR_TYPE;
        break;
        case TCN_LOG_NOTICE:
            id = LOG_MSG_NOTICE;
            il = EVENTLOG_WARNING_TYPE;
        break;
        case TCN_LOG_WARN:
            id = LOG_MSG_WARN;
            il = EVENTLOG_WARNING_TYPE;
        break;
        case TCN_LOG_INFO:
            id = LOG_MSG_INFO;
            il = EVENTLOG_INFORMATION_TYPE;
        break;
    }

    messages[0] = J2S(msg);
    source = RegisterEventSource(NULL, log_domain);

    if (source != NULL) {
        ReportEvent(source, il,
                    0,
                    id,
                    NULL,
                    1, 0,
                    messages, NULL);
        DeregisterEventSource(source);
    }

    TCN_FREE_CSTRING(msg);
}

TCN_IMPLEMENT_CALL(jboolean, OS, is)(TCN_STDARGS, jint type)
{
    UNREFERENCED_STDARGS;
#ifdef _WIN64
    if (type == 4)
        return JNI_TRUE;
    else
#endif
    if (type == 3)
        return JNI_TRUE;
    else
        return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jint, OS, info)(TCN_STDARGS,
                                   jlongArray inf)
{
    MEMORYSTATUSEX ms;
    ULONGLONG st[4];
    FILETIME ft[4];
    PROCESS_MEMORY_COUNTERS pmc;
    jint rv;
    int i;
    jsize ilen = (*e)->GetArrayLength(e, inf);
    jlong *pvals = (*e)->GetLongArrayElements(e, inf, NULL);

    if (ilen < 16) {
        return APR_EINVAL;
    }
    for (i = 0; i < 16; i++)
        pvals[i] = 0;

    ms.dwLength = sizeof(MEMORYSTATUSEX);

    UNREFERENCED(o);
    if (GlobalMemoryStatusEx(&ms)) {
        pvals[0] = (jlong)ms.ullTotalPhys;
        pvals[1] = (jlong)ms.ullAvailPhys;
        pvals[2] = (jlong)ms.ullTotalPageFile;
        pvals[3] = (jlong)ms.ullAvailPageFile;
        /* Slots 4 and 5 are for shared memory */
        pvals[6] = (jlong)ms.dwMemoryLoad;
    }
    else
        goto cleanup;

    memset(st, 0, sizeof(st));

    if (fnGetSystemTimes) {
        if ((*fnGetSystemTimes)(&ft[0], &ft[1], &ft[2])) {
            st[0] = (((ULONGLONG)ft[0].dwHighDateTime << 32) | ft[0].dwLowDateTime) / 10;
            st[1] = (((ULONGLONG)ft[1].dwHighDateTime << 32) | ft[1].dwLowDateTime) / 10;
            st[2] = (((ULONGLONG)ft[2].dwHighDateTime << 32) | ft[2].dwLowDateTime) / 10;
        }
        else
            goto cleanup;
    }
#if (_WIN32_WINNT < 0x0501)
    else if (fnNtQuerySystemInformation) {
        BYTE buf[2048]; /* This should ne enough for 32 processors */
        NTSTATUS rs = (*fnNtQuerySystemInformation)(SystemProcessorPerformanceInformation,
                                           (LPVOID)buf, 2048, NULL);
        if (rs == 0) {
            PSYSTEM_PROCESSOR_PERFORMANCE_INFORMATION pspi = (PSYSTEM_PROCESSOR_PERFORMANCE_INFORMATION)&buf[0];
            DWORD i;
            /* Calculate all processors */
            for (i = 0; i < dll_system_info.dwNumberOfProcessors; i++) {
                st[0] += pspi[i].IdleTime.QuadPart / 10;
                st[1] += pspi[i].KernelTime.QuadPart / 10;
                st[2] += pspi[i].UserTime.QuadPart / 10;
            }
        }
        else
            goto cleanup;
    }
#endif
    pvals[7] = st[0];
    pvals[8] = st[1];
    pvals[9] = st[2];

    memset(st, 0, sizeof(st));
    if (GetProcessTimes(GetCurrentProcess(), &ft[0], &ft[1], &ft[2], &ft[3])) {
        FileTimeToAprTime((apr_time_t *)&st[0], &ft[0]);
        st[1] = (((ULONGLONG)ft[2].dwHighDateTime << 32) | ft[2].dwLowDateTime) / 10;
        st[2] = (((ULONGLONG)ft[3].dwHighDateTime << 32) | ft[3].dwLowDateTime) / 10;
    }
    pvals[10] = st[0];
    pvals[11] = st[1];
    pvals[12] = st[2];

    if (GetProcessMemoryInfo(GetCurrentProcess(), &pmc, sizeof(pmc))) {
        pvals[13] = pmc.WorkingSetSize;
        pvals[14] = pmc.PeakWorkingSetSize;
        pvals[15] = pmc.PageFaultCount;
    }

    (*e)->ReleaseLongArrayElements(e, inf, pvals, 0);
    return APR_SUCCESS;
cleanup:
    rv = apr_get_os_error();
    (*e)->ReleaseLongArrayElements(e, inf, pvals, 0);
    return rv;
}

static DWORD WINAPI password_thread(void *data)
{
    tcn_pass_cb_t *cb = (tcn_pass_cb_t *)data;
    MSG     msg;
    HWINSTA hwss;
    HWINSTA hwsu;
    HDESK   hwds;
    HDESK   hwdu;
    HWND    hwnd;

    /* Ensure connection to service window station and desktop, and
     * save their handles.
     */
    GetDesktopWindow();
    hwss = GetProcessWindowStation();
    hwds = GetThreadDesktop(GetCurrentThreadId());

    /* Impersonate the client and connect to the User's
     * window station and desktop.
     */
    hwsu = OpenWindowStation("WinSta0", FALSE, MAXIMUM_ALLOWED);
    if (hwsu == NULL) {
        ExitThread(1);
        return 1;
    }
    SetProcessWindowStation(hwsu);
    hwdu = OpenDesktop("Default", 0, FALSE, MAXIMUM_ALLOWED);
    if (hwdu == NULL) {
        SetProcessWindowStation(hwss);
        CloseWindowStation(hwsu);
        ExitThread(1);
        return 1;
    }
    SetThreadDesktop(hwdu);

    hwnd = CreateDialog(dll_instance, MAKEINTRESOURCE(1001), NULL, NULL);
    if (hwnd != NULL)
        ShowWindow(hwnd, SW_SHOW);
    else  {
        ExitThread(1);
        return 1;
    }
    while (1) {
        if (PeekMessage(&msg, hwnd, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_KEYUP) {
                int nVirtKey = (int)msg.wParam;
                if (nVirtKey == VK_ESCAPE) {
                    DestroyWindow(hwnd);
                    break;
                }
                else if (nVirtKey == VK_RETURN) {
                    HWND he = GetDlgItem(hwnd, 1002);
                    if (he) {
                        int n = GetWindowText(he, cb->password, SSL_MAX_PASSWORD_LEN - 1);
                        cb->password[n] = '\0';
                    }
                    DestroyWindow(hwnd);
                    break;
                }
            }
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
        else
            Sleep(100);
    }
    /* Restore window station and desktop.
     */
    SetThreadDesktop(hwds);
    SetProcessWindowStation(hwss);
    CloseDesktop(hwdu);
    CloseWindowStation(hwsu);

    ExitThread(0);
    return 0;
}

int WIN32_SSL_password_prompt(tcn_pass_cb_t *data)
{
    DWORD id;
    HANDLE thread;
    /* TODO: See how to display this from service mode */
    thread = CreateThread(NULL, 0,
                password_thread, data,
                0, &id);
    WaitForSingleObject(thread, INFINITE);
    CloseHandle(thread);
    return (int)strlen(data->password);
}


