/* Copyright 2000-2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

#include "apr.h"
#include "apr_pools.h"
#include "apr_network_io.h"
#include "apr_poll.h"

#include "tcn.h"
#if defined(__linux__)
#include <sys/sysinfo.h>
#elif defined(sun)
#include <unistd.h>
#include <sys/swap.h>
#include <procfs.h>
#include <kstat.h>
#include <sys/sysinfo.h>
#endif

#if defined(DARWIN)
#include <mach/mach_init.h>
#include <mach/mach_host.h>
#include <mach/host_info.h>
#include <sys/sysctl.h>
#include <sys/stat.h>
#endif

#include <syslog.h>
#include <stdarg.h>

#ifndef LOG_WARN
#define LOG_WARN LOG_WARNING
#endif

#if defined(sun)
#define MAX_PROC_PATH_LEN 64
#define MAX_CPUS 512
#define PSINFO_T_SZ sizeof(psinfo_t)
#define PRUSAGE_T_SZ sizeof(prusage_t)

static int proc_open(const char *type)
{
    char proc_path[MAX_PROC_PATH_LEN+1];

    sprintf(proc_path, "/proc/self/%s", type);
    return open(proc_path, O_RDONLY);
}

static int proc_read(void *buf, const size_t size, int filedes)
{
    ssize_t bytes;

    if (filedes >= 0) {
        bytes = pread(filedes, buf, size, 0);
        if (bytes != size)
            return -1;
        else
            return 0;
    }
    else
        return -1;
}

#endif

TCN_IMPLEMENT_CALL(jboolean, OS, is)(TCN_STDARGS, jint type)
{
    UNREFERENCED_STDARGS;
    if (type == 1)
        return JNI_TRUE;
#if defined(__linux__)
    else if (type == 5)
        return JNI_TRUE;
#endif
#if defined(sun)
    else if (type == 6)
        return JNI_TRUE;
#endif
#if defined(__FreeBSD__) || defined(__NetBSD__)
    else if (type == 7)
        return JNI_TRUE;
#endif
    else
        return JNI_FALSE;
}

TCN_IMPLEMENT_CALL(jint, OS, info)(TCN_STDARGS,
                                   jlongArray inf)
{
    jint rv;
    int  i;
    jsize ilen = (*e)->GetArrayLength(e, inf);
    jlong *pvals = (*e)->GetLongArrayElements(e, inf, NULL);

    UNREFERENCED(o);
    if (ilen < 16) {
        return APR_EINVAL;
    }
    for (i = 0; i < 16; i++)
        pvals[i] = 0;
#if defined(__linux__)
    {
        struct sysinfo info;
        if (sysinfo(&info))
            rv = apr_get_os_error();
        else {
            pvals[0] = (jlong)(info.totalram  * info.mem_unit);
            pvals[1] = (jlong)(info.freeram   * info.mem_unit);
            pvals[2] = (jlong)(info.totalswap * info.mem_unit);
            pvals[3] = (jlong)(info.freeswap  * info.mem_unit);
            pvals[4] = (jlong)(info.sharedram * info.mem_unit);
            pvals[5] = (jlong)(info.bufferram * info.mem_unit);
            pvals[6] = (jlong)(100 - (info.freeram * 100 / info.totalram));
            rv = APR_SUCCESS;
        }
    }
#elif defined(sun)
    {
        /* static variables with basic procfs info */
        static long creation = 0;              /* unix timestamp of process creation */
        static int psinf_fd = 0;               /* file descriptor for the psinfo procfs file */
        static int prusg_fd = 0;               /* file descriptor for the usage procfs file */
        static size_t rss = 0;                 /* maximum of resident set size from previous calls */
        /* static variables with basic kstat info */
        static kstat_ctl_t *kstat_ctl = NULL;  /* kstat control object, only initialized once */
        static kstat_t *kstat_cpu[MAX_CPUS];   /* array of kstat objects for per cpu statistics */
        static int cpu_count = 0;              /* number of cpu structures found in kstat */
        static kid_t kid = 0;                  /* kstat ID, for which the kstat_ctl holds the correct chain */
        /* non-static variables - general use */
        int res = 0;                           /* general result state */
        /* non-static variables - sysinfo/swapctl use */
        long ret_sysconf;                      /* value returned from sysconf call */
        long tck_dividend;                     /* factor used by transforming tick numbers to milliseconds */
        long tck_divisor;                      /* divisor used by transforming tick numbers to milliseconds */
        long sys_pagesize = sysconf(_SC_PAGESIZE); /* size of a system memory page in bytes */
        long sys_clk_tck = sysconf(_SC_CLK_TCK); /* number of system ticks per second */
        struct anoninfo info;                  /* structure for information about sizes in anonymous memory system */
        /* non-static variables - procfs use */
        psinfo_t psinf;                        /* psinfo structure from procfs */
        prusage_t prusg;                       /* usage structure from procfs */
        size_t new_rss = 0;                    /* resident set size read from procfs */
        time_t now;                            /* time needed for calculating process creation time */
        /* non-static variables - kstat use */
        kstat_t *kstat = NULL;                 /* kstat working pointer */
        cpu_sysinfo_t cpu;                     /* cpu sysinfo working pointer */
        kid_t new_kid = 0;                     /* kstat ID returned from chain update */
        int new_kstat = 0;                     /* flag indicating, if kstat structure has changed since last call */

        rv = APR_SUCCESS;

        if (sys_pagesize <= 0) {
            rv = apr_get_os_error();
        }
        else {
            ret_sysconf = sysconf(_SC_PHYS_PAGES);
            if (ret_sysconf >= 0) {
                pvals[0] = (jlong)((jlong)sys_pagesize * ret_sysconf);
            }
            else {
                rv = apr_get_os_error();
            }
            ret_sysconf = sysconf(_SC_AVPHYS_PAGES);
            if (ret_sysconf >= 0) {
                pvals[1] = (jlong)((jlong)sys_pagesize * ret_sysconf);
            }
            else {
                rv = apr_get_os_error();
            }
            res=swapctl(SC_AINFO, &info);
            if (res >= 0) {
                pvals[2] = (jlong)((jlong)sys_pagesize * info.ani_max);
                pvals[3] = (jlong)((jlong)sys_pagesize * info.ani_free);
                pvals[6] = (jlong)(100 - (jlong)info.ani_free * 100 / info.ani_max);
            }
            else {
                rv = apr_get_os_error();
            }
        }

        if (psinf_fd == 0) {
            psinf_fd = proc_open("psinfo");
        }
        res = proc_read(&psinf, PSINFO_T_SZ, psinf_fd);
        if (res >= 0) {
            new_rss = psinf.pr_rssize*1024;
            pvals[13] = (jlong)(new_rss);
            if (new_rss > rss) {
                rss = new_rss;
            }
            pvals[14] = (jlong)(rss);
        }
        else {
            psinf_fd = 0;
            rv = apr_get_os_error();
        }
        if (prusg_fd == 0) {
            prusg_fd = proc_open("usage");
        }
        res = proc_read(&prusg, PRUSAGE_T_SZ, prusg_fd);
        if (res >= 0) {
            if (creation <= 0) {
                time(&now);
                creation = (long)(now - (prusg.pr_tstamp.tv_sec -
                                         prusg.pr_create.tv_sec));
            }
            pvals[10] = (jlong)(creation);
            pvals[11] = (jlong)((jlong)prusg.pr_stime.tv_sec * 1000 +
                                (prusg.pr_stime.tv_nsec / 1000000));
            pvals[12] = (jlong)((jlong)prusg.pr_utime.tv_sec * 1000 +
                                (prusg.pr_utime.tv_nsec / 1000000));
            pvals[15] = (jlong)(prusg.pr_majf);
        }
        else {
            prusg_fd = 0;
            rv = apr_get_os_error();
        }

        if (sys_clk_tck <= 0) {
            rv = apr_get_os_error();
        }
        else {
            tck_dividend = 1000;
            tck_divisor = sys_clk_tck;
            for (i = 0; i < 3; i++) {
                if (tck_divisor % 2 == 0) {
                    tck_divisor = tck_divisor / 2;
                    tck_dividend = tck_dividend / 2;
                }
                if (tck_divisor % 5 == 0) {
                    tck_divisor = tck_divisor / 5;
                    tck_dividend = tck_dividend / 5;
                }
            }
            if (kstat_ctl == NULL) {
                kstat_ctl = kstat_open();
                kid = kstat_ctl->kc_chain_id;
                new_kstat = 1;
            } else {
                new_kid = kstat_chain_update(kstat_ctl);
                if (new_kid < 0) {
                    res=kstat_close(kstat_ctl);
                    kstat_ctl = kstat_open();
                    kid = kstat_ctl->kc_chain_id;
                    new_kstat = 1;
                } else if (new_kid > 0 && kid != new_kid) {
                    kid = new_kid;
                    new_kstat = 1;
                }
            }
            if (new_kstat) {
                cpu_count = 0;
                for (kstat = kstat_ctl->kc_chain; kstat; kstat = kstat->ks_next) {
                    if (strncmp(kstat->ks_name, "cpu_stat", 8) == 0) {
                        kstat_cpu[cpu_count++]=kstat;
                    }
                }
            }
            for (i = 0; i < cpu_count; i++) {
                new_kid = kstat_read(kstat_ctl, kstat_cpu[i], NULL);
                if (new_kid >= 0) {
                    long tck_r = tck_dividend / tck_divisor;
                    cpu = ((cpu_stat_t *)kstat_cpu[i]->ks_data)->cpu_sysinfo;
                    if ( tck_divisor == 1 ) {
                        pvals[7] += (jlong)(((jlong)cpu.cpu[CPU_IDLE]) * tck_dividend);
                        pvals[7] += (jlong)(((jlong)cpu.cpu[CPU_WAIT]) * tck_dividend);
                        pvals[8] += (jlong)(((jlong)cpu.cpu[CPU_KERNEL]) * tck_dividend);
                        pvals[9] += (jlong)(((jlong)cpu.cpu[CPU_USER]) * tck_dividend);
                    } else {
                        pvals[7] += (jlong)(((jlong)cpu.cpu[CPU_IDLE]) * tck_dividend / tck_divisor);
                        pvals[7] += (jlong)(((jlong)cpu.cpu[CPU_WAIT]) * tck_dividend / tck_divisor);
                        pvals[8] += (jlong)(((jlong)cpu.cpu[CPU_KERNEL]) * tck_dividend / tck_divisor);
                        pvals[9] += (jlong)(((jlong)cpu.cpu[CPU_USER]) * tck_dividend / tck_divisor);
                    }
                }
            }
        }

        /*
         * The next two are not implemented yet for Solaris
         * inf[4]  - Amount of shared memory
         * inf[5]  - Memory used by buffers
         *
         */
    }

#elif defined(DARWIN)

    uint64_t mem_total;
    size_t len = sizeof(mem_total);

    vm_statistics_data_t vm_info;
    mach_msg_type_number_t info_count = HOST_VM_INFO_COUNT;

    sysctlbyname("hw.memsize", &mem_total, &len, NULL, 0);
    pvals[0] = (jlong)mem_total;

    host_statistics(mach_host_self (), HOST_VM_INFO, (host_info_t)&vm_info, &info_count);
    pvals[1] = (jlong)(((double)vm_info.free_count)*vm_page_size);
    pvals[6] = (jlong)(100 - (pvals[1] * 100 / mem_total));
    rv = APR_SUCCESS;

/* DARWIN */
#else
    rv = APR_ENOTIMPL;
#endif
   (*e)->ReleaseLongArrayElements(e, inf, pvals, 0);
    return rv;
}

#define LOG_MSG_DOMAIN                   "Native"


TCN_IMPLEMENT_CALL(jstring, OS, expand)(TCN_STDARGS, jstring val)
{
    jstring str;
    TCN_ALLOC_CSTRING(val);

    UNREFERENCED(o);

    /* TODO: Make ${ENVAR} expansion */
    str = (*e)->NewStringUTF(e, J2S(val));

    TCN_FREE_CSTRING(val);
    return str;
}

TCN_IMPLEMENT_CALL(void, OS, sysloginit)(TCN_STDARGS, jstring domain)
{
    const char *d;
    TCN_ALLOC_CSTRING(domain);

    UNREFERENCED(o);
    if ((d = J2S(domain)) == NULL)
        d = LOG_MSG_DOMAIN;

    openlog(d, LOG_CONS | LOG_PID, LOG_LOCAL0);
    TCN_FREE_CSTRING(domain);
}

TCN_IMPLEMENT_CALL(void, OS, syslog)(TCN_STDARGS, jint level,
                                     jstring msg)
{
    TCN_ALLOC_CSTRING(msg);
    int id = LOG_DEBUG;
    UNREFERENCED(o);

    switch (level) {
        case TCN_LOG_EMERG:
            id = LOG_EMERG;
        break;
        case TCN_LOG_ERROR:
            id = LOG_ERR;
        break;
        case TCN_LOG_NOTICE:
            id = LOG_NOTICE;
        break;
        case TCN_LOG_WARN:
            id = LOG_WARN;
        break;
        case TCN_LOG_INFO:
            id = LOG_INFO;
        break;
    }
    syslog (id, "%s", J2S(msg));

    TCN_FREE_CSTRING(msg);
}
