/* Copyright 2000-2004 The Apache Software Foundation
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

#include "apr.h"
#include "apr_pools.h"
#include "apr_file_io.h"

#include "tcn.h"

TCN_IMPLEMENT_CALL(jint, File, close)(TCN_STDARGS, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_close(f);
}

TCN_IMPLEMENT_CALL(jint, File, eof)(TCN_STDARGS, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_eof(f);
}

TCN_IMPLEMENT_CALL(jint, File, flush)(TCN_STDARGS, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_flush(f);
}

TCN_IMPLEMENT_CALL(jint, File, unlock)(TCN_STDARGS, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_unlock(f);
}

TCN_IMPLEMENT_CALL(jint, File, flagsGet)(TCN_STDARGS, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_flags_get(f);
}

TCN_IMPLEMENT_CALL(jint, File, lock)(TCN_STDARGS, jlong file, jint flags)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_lock(f, (int)flags);
}

TCN_IMPLEMENT_CALL(jint, File, trunc)(TCN_STDARGS, jlong file, jlong off)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_trunc(f, (apr_off_t)off);
}

TCN_IMPLEMENT_CALL(jlong, File, open)(TCN_STDARGS, jstring fname,
                                      jint flag, jint perm,
                                      jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_file_t *f = NULL;
    TCN_ALLOC_CSTRING(fname);

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_file_open(&f, J2S(fname), (apr_int32_t)flag,
                     (apr_fileperms_t)perm, p), f);

cleanup:
    TCN_FREE_CSTRING(fname);
    return P2J(f);
}

TCN_IMPLEMENT_CALL(jlong, File, mktemp)(TCN_STDARGS, jstring templ,
                                      jint flags,
                                      jint pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_file_t *f = NULL;
    char *ctempl = tcn_dup_string(e, templ);

    UNREFERENCED(o);
    if (!ctempl) {
       TCN_THROW_OS_ERROR(e);
       return 0;
    }
    TCN_THROW_IF_ERR(apr_file_mktemp(&f, ctempl,
                     (apr_int32_t)flags, p), f);

cleanup:
    free(ctempl);
    return P2J(f);
}

TCN_IMPLEMENT_CALL(jint, File, remove)(TCN_STDARGS, jstring path, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(path);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_file_remove(J2S(path), p);
    TCN_FREE_CSTRING(path);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, rename)(TCN_STDARGS, jstring from,
                                       jstring to, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(from);
    TCN_ALLOC_CSTRING(to);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_file_rename(J2S(from), J2S(to), p);
    TCN_FREE_CSTRING(from);
    TCN_FREE_CSTRING(to);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, copy)(TCN_STDARGS, jstring from,
                                     jstring to, jint perms, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(from);
    TCN_ALLOC_CSTRING(to);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_file_copy(J2S(from), J2S(to), (apr_fileperms_t)perms, p);
    TCN_FREE_CSTRING(from);
    TCN_FREE_CSTRING(to);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, append)(TCN_STDARGS, jstring from,
                                       jstring to, jint perms, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(from);
    TCN_ALLOC_CSTRING(to);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_file_append(J2S(from), J2S(to), (apr_fileperms_t)perms, p);
    TCN_FREE_CSTRING(from);
    TCN_FREE_CSTRING(to);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jstring, File, nameGet)(TCN_STDARGS, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    jstring name = NULL;
    const char *fname;

    UNREFERENCED(o);
    if (apr_file_name_get(&fname, f) == APR_SUCCESS)
        name = AJP_TO_JSTRING(fname);

    return name;
}

TCN_IMPLEMENT_CALL(jint, File, permsSet)(TCN_STDARGS, jstring file, jint perms)
{
    TCN_ALLOC_CSTRING(file);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_file_perms_set(J2S(file), (apr_fileperms_t)perms);
    TCN_FREE_CSTRING(file);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, attrsSet)(TCN_STDARGS, jstring file, jint attrs,
                                          jint mask, jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(file);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_file_attrs_set(J2S(file), (apr_fileattrs_t)attrs,
                            (apr_fileattrs_t)mask, p);
    TCN_FREE_CSTRING(file);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, mtimeSet)(TCN_STDARGS, jstring file, jlong mtime,
                                          jlong pool)
{
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    TCN_ALLOC_CSTRING(file);
    apr_status_t rv;

    UNREFERENCED(o);
    rv = apr_file_mtime_set(J2S(file), J2T(mtime), p);
    TCN_FREE_CSTRING(file);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jlong, File, seek)(TCN_STDARGS, jlong file,
                                      jint where, jlong offset)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_off_t pos = (apr_off_t)offset;
    apr_seek_where_t w;
    UNREFERENCED(o);
    switch (where) {
        case 1:
            w = APR_CUR;
            break;
        case 2:
            w = APR_END;
            break;
        default:
            w = APR_SET;
            break;
    }
    TCN_THROW_IF_ERR(apr_file_seek(f, w, &pos), pos);

cleanup:
    return (jlong)pos;
}

TCN_IMPLEMENT_CALL(jint, File, putc)(TCN_STDARGS, jbyte c, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_putc((char)c, f);
}

TCN_IMPLEMENT_CALL(jint, File, getc)(TCN_STDARGS, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    char ch;
    UNREFERENCED_STDARGS;
    TCN_THROW_IF_ERR(apr_file_getc(&ch, f), ch);

cleanup:
    return (jint)ch;
}

TCN_IMPLEMENT_CALL(jint, File, ungetc)(TCN_STDARGS, jbyte c, jlong file)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_ungetc((char)c, f);
}

TCN_IMPLEMENT_CALL(jint, File, puts)(TCN_STDARGS, jbyteArray str, jlong file)
{
    apr_status_t rv = APR_EINVAL;
    apr_file_t *f = J2P(file, apr_file_t *);
    jbyte *bytes = (*e)->GetPrimitiveArrayCritical(e, str, NULL);

    UNREFERENCED(o);
    if (bytes) {
        rv = apr_file_puts((const char *)bytes, f);
        (*e)->ReleasePrimitiveArrayCritical(e, str, bytes, JNI_ABORT);
    }
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, write)(TCN_STDARGS, jlong file,
                                      jbyteArray buf, jint towrite)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    jbyte *bytes = (*e)->GetPrimitiveArrayCritical(e, buf, NULL);

    UNREFERENCED(o);
    if (towrite > 0)
        nbytes = min(nbytes, (apr_size_t)towrite);
    TCN_THROW_IF_ERR(apr_file_write(f, bytes, &nbytes), nbytes);

cleanup:
    (*e)->ReleasePrimitiveArrayCritical(e, buf, bytes, JNI_ABORT);
    return (jint)nbytes;
}

TCN_IMPLEMENT_CALL(jint, File, writeFull)(TCN_STDARGS, jlong file,
                                          jbyteArray buf, jint towrite)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    apr_size_t written = 0;
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);

    UNREFERENCED(o);
    if (towrite > 0)
        nbytes = min(nbytes, (apr_size_t)towrite);
    TCN_THROW_IF_ERR(apr_file_write_full(f, bytes, nbytes, &written), nbytes);

cleanup:
    (*e)->ReleaseByteArrayElements(e, buf, bytes, JNI_ABORT);
    return (jint)written;
}

TCN_IMPLEMENT_CALL(jint, File, writev)(TCN_STDARGS, jlong file,
                                       jobjectArray bufs)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    jsize nvec = (*e)->GetArrayLength(e, bufs);
    jsize i;
    struct iovec vec[APR_MAX_IOVEC_SIZE];
    jobject ba[APR_MAX_IOVEC_SIZE];
    apr_size_t written = 0;

    UNREFERENCED(o);

    if (nvec >= APR_MAX_IOVEC_SIZE) {
        /* TODO: Throw something here */
        return 0;
    }
    for (i = 0; i < nvec; i++) {
        ba[i] = (*e)->GetObjectArrayElement(e, bufs, i);
        vec[i].iov_len  = (*e)->GetArrayLength(e, ba[i]);
        vec[i].iov_base = (*e)->GetByteArrayElements(e, ba[i], NULL);
    }

    TCN_THROW_IF_ERR(apr_file_writev(f, vec, nvec, &written), i);

cleanup:
    for (i = 0; i < nvec; i++) {
        (*e)->ReleaseByteArrayElements(e, ba[i], vec[i].iov_base, JNI_ABORT);
    }
    return (jint)written;
}

TCN_IMPLEMENT_CALL(jint, File, writevFull)(TCN_STDARGS, jlong file,
                                           jobjectArray bufs)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    jsize nvec = (*e)->GetArrayLength(e, bufs);
    jsize i;
    struct iovec vec[APR_MAX_IOVEC_SIZE];
    jobject ba[APR_MAX_IOVEC_SIZE];
    apr_size_t written = 0;

    UNREFERENCED(o);

    if (nvec >= APR_MAX_IOVEC_SIZE) {
        /* TODO: Throw something here */
        return 0;
    }
    for (i = 0; i < nvec; i++) {
        ba[i] = (*e)->GetObjectArrayElement(e, bufs, i);
        vec[i].iov_len  = (*e)->GetArrayLength(e, ba[i]);
        vec[i].iov_base = (*e)->GetByteArrayElements(e, ba[i], NULL);
    }
#if (APR_VERSION_MAJOR >= 1) && (APR_VERSION_MINOR >= 1)
    TCN_THROW_IF_ERR(apr_file_writev_full(f, vec, nvec, &written), i);
#else
    TCN_THROW_IF_ERR(apr_file_writev(f, vec, nvec, &written), i);
#endif

cleanup:
    for (i = 0; i < nvec; i++) {
        (*e)->ReleaseByteArrayElements(e, ba[i], vec[i].iov_base,
                                       JNI_ABORT);
    }
    return (jint)written;
}

TCN_IMPLEMENT_CALL(jint, File, read)(TCN_STDARGS, jlong file,
                                     jbyteArray buf, jint toread)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);

    UNREFERENCED(o);
    if (toread > 0)
        nbytes = min(nbytes, (apr_size_t)toread);

    TCN_THROW_IF_ERR(apr_file_read(f, bytes, &nbytes), nbytes);

cleanup:
    (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                   nbytes ? 0 : JNI_ABORT);
    return (jint)nbytes;
}

TCN_IMPLEMENT_CALL(jint, File, readFull)(TCN_STDARGS, jlong file,
                                         jbyteArray buf, jint toread)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_size_t nbytes = (*e)->GetArrayLength(e, buf);
    apr_size_t nread;
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);

    UNREFERENCED(o);
    if (toread > 0)
        nbytes = min(nbytes, (apr_size_t)toread);
    TCN_THROW_IF_ERR(apr_file_read_full(f, bytes, nbytes, &nread), nread);

cleanup:
    (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                   nread ? 0 : JNI_ABORT);
    return (jint)nread;
}

TCN_IMPLEMENT_CALL(jint, File, gets)(TCN_STDARGS, jbyteArray buf, jlong file)
{
    apr_status_t rv;
    apr_file_t *f = J2P(file, apr_file_t *);
    jsize nbytes = (*e)->GetArrayLength(e, buf);
    jbyte *bytes = (*e)->GetByteArrayElements(e, buf, NULL);

    UNREFERENCED(o);
    rv = apr_file_gets(bytes, nbytes, f);

    (*e)->ReleaseByteArrayElements(e, buf, bytes,
                                   rv == APR_SUCCESS ? 0 : JNI_ABORT);
    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, pipeCreate)(TCN_STDARGS, jlongArray io, jlong pool)
{
    apr_status_t rv;
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    jsize npipes = (*e)->GetArrayLength(e, io);
    jlong *pipes = (*e)->GetLongArrayElements(e, io, NULL);
    apr_file_t *in;
    apr_file_t *out;

    UNREFERENCED(o);
    if (npipes < 2) {
        (*e)->ReleaseLongArrayElements(e, io, pipes, JNI_ABORT);
        return APR_EINVAL;
    }

    rv = apr_file_pipe_create(&in, &out, p);
    if (rv == APR_SUCCESS) {
        pipes[0] = P2J(in);
        pipes[1] = P2J(out);
        (*e)->ReleaseLongArrayElements(e, io, pipes, 0);
    }
    else
        (*e)->ReleaseLongArrayElements(e, io, pipes, JNI_ABORT);

    return (jint)rv;
}

TCN_IMPLEMENT_CALL(jint, File, pipeTimeoutSet)(TCN_STDARGS, jlong pipe,
                                               jlong timeout)
{
    apr_file_t *f = J2P(pipe, apr_file_t *);
    UNREFERENCED_STDARGS;
    return (jint)apr_file_pipe_timeout_set(f, J2T(timeout));
}

TCN_IMPLEMENT_CALL(jlong, File, pipeTimeoutGet)(TCN_STDARGS, jlong pipe)
{
    apr_file_t *f = J2P(pipe, apr_file_t *);
    apr_interval_time_t timeout;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_file_pipe_timeout_get(f, &timeout), timeout);

cleanup:
    return (jlong)timeout;
}

TCN_IMPLEMENT_CALL(jlong, File, dup)(TCN_STDARGS, jlong newf, jlong file,
                                     jlong pool)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_file_t *d = J2P(newf, apr_file_t *);;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_file_dup(&d, f, p), d);

cleanup:
    return P2J(d);
}

TCN_IMPLEMENT_CALL(jlong, File, dup2)(TCN_STDARGS, jlong newf, jlong file,
                                      jlong pool)
{
    apr_file_t *f = J2P(file, apr_file_t *);
    apr_pool_t *p = J2P(pool, apr_pool_t *);
    apr_file_t *d = J2P(newf, apr_file_t *);;

    UNREFERENCED(o);
    TCN_THROW_IF_ERR(apr_file_dup(&d, f, p), d);

cleanup:
    return P2J(d);
}
