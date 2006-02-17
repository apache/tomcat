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

#include "tcn.h"

static const char *tcn_errors[] = {
                            "Unknown user error",
    /* TCN_TIMEUP      */   "Operation timed out",
    /* TCN_EAGAIN      */   "There is no data ready",
    /* TCN_EINTR       */   "Interrupted system call",
    /* TCN_EINPROGRESS */   "Operation in prrogress",
    /* TCN_ETIMEDOUT   */   "Connection timed out",
    NULL,
};

/* Merge IS_ETIMEDOUT with APR_TIMEUP
 */
#define TCN_STATUS_IS_ETIMEDOUT(x) (APR_STATUS_IS_ETIMEDOUT((x)) || ((x) == APR_TIMEUP))
/*
 * Convenience function to help throw an java.lang.Exception.
 */
void tcn_ThrowException(JNIEnv *env, const char *msg)
{
    jclass javaExceptionClass;

    javaExceptionClass = (*env)->FindClass(env, "java/lang/Exception");
    if (javaExceptionClass == NULL) {
        fprintf(stderr, "Cannot find java/lang/Exception class\n");
        return;
    }
    (*env)->ThrowNew(env, javaExceptionClass, msg);
    (*env)->DeleteLocalRef(env, javaExceptionClass);

}

void tcn_Throw(JNIEnv *env, const char *fmt, ...)
{
    char msg[TCN_BUFFER_SZ] = {'\0'};
    va_list ap;

    va_start(ap, fmt);
    apr_vsnprintf(msg, TCN_BUFFER_SZ, fmt, ap);
    tcn_ThrowException(env, msg);
    va_end(ap);
}

/*
 * Convenience function to help throw an APR Exception
 * from native error code.
 */
void tcn_ThrowAPRException(JNIEnv *e, apr_status_t err)
{
    jclass aprErrorClass;
    jmethodID constructorID = 0;
    jobject throwObj;
    jstring jdescription;
    char serr[512] = {0};

    aprErrorClass = (*e)->FindClass(e, TCN_ERROR_CLASS);
    if (aprErrorClass == NULL) {
        fprintf(stderr, "Cannot find " TCN_ERROR_CLASS " class\n");
        return;
    }

    /* Find the constructor ID */
    constructorID = (*e)->GetMethodID(e, aprErrorClass,
                                      "<init>",
                                      "(ILjava/lang/String;)V");
    if (constructorID == NULL) {
        fprintf(stderr,
                "Cannot find constructor for " TCN_ERROR_CLASS " class\n");
        goto cleanup;
    }

    apr_strerror(err, serr, 512);
    /* Obtain the string objects */
    jdescription = AJP_TO_JSTRING(serr);
    if (jdescription == NULL) {
        fprintf(stderr,
                "Cannot allocate description for " TCN_ERROR_CLASS " class\n");
        goto cleanup;
    }
    /* Create the APR Error object */
    throwObj = (*e)->NewObject(e, aprErrorClass, constructorID,
                               (jint)err, jdescription);
    if (throwObj == NULL) {
        fprintf(stderr,
                "Cannot allocate new " TCN_ERROR_CLASS " object\n");
        goto cleanup;
    }

    (*e)->Throw(e, throwObj);
cleanup:
    (*e)->DeleteLocalRef(e, aprErrorClass);
}


TCN_IMPLEMENT_CALL(jint, Error, osError)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return (jint)apr_get_os_error();
}

TCN_IMPLEMENT_CALL(jint, Error, netosError)(TCN_STDARGS)
{
    UNREFERENCED_STDARGS;
    return (jint)apr_get_netos_error();
}

TCN_IMPLEMENT_CALL(jstring, Error, strerror)(TCN_STDARGS, jint err)
{
    char serr[512] = {0};
    jstring jerr;

    UNREFERENCED(o);
    if (err >= TCN_TIMEUP && err <= TCN_ETIMEDOUT) {
        err -= TCN_TIMEUP;
        jerr = AJP_TO_JSTRING(tcn_errors[err]);
    }
    else {
        apr_strerror(err, serr, 512);
        jerr = AJP_TO_JSTRING(serr);
    }
    return jerr;
}

TCN_IMPLEMENT_CALL(jboolean, Status, is)(TCN_STDARGS, jint err, jint idx)
{
#define APR_IS(I, E) case I: if (E(err)) return JNI_TRUE; break
#define APR_ISX(I, E, T) case I: if (E(err) || (err == T)) return JNI_TRUE; break

    UNREFERENCED_STDARGS;
    switch (idx) {
        APR_IS(1,  APR_STATUS_IS_ENOSTAT);
        APR_IS(2,  APR_STATUS_IS_ENOPOOL);
        /* empty slot: +3 */
        APR_IS(4,  APR_STATUS_IS_EBADDATE);
        APR_IS(5,  APR_STATUS_IS_EINVALSOCK);
        APR_IS(6,  APR_STATUS_IS_ENOPROC);
        APR_IS(7,  APR_STATUS_IS_ENOTIME);
        APR_IS(8,  APR_STATUS_IS_ENODIR);
        APR_IS(9,  APR_STATUS_IS_ENOLOCK);
        APR_IS(10, APR_STATUS_IS_ENOPOLL);
        APR_IS(11, APR_STATUS_IS_ENOSOCKET);
        APR_IS(12, APR_STATUS_IS_ENOTHREAD);
        APR_IS(13, APR_STATUS_IS_ENOTHDKEY);
        APR_IS(14, APR_STATUS_IS_EGENERAL);
        APR_IS(15, APR_STATUS_IS_ENOSHMAVAIL);
        APR_IS(16, APR_STATUS_IS_EBADIP);
        APR_IS(17, APR_STATUS_IS_EBADMASK);
        /* empty slot: +18 */
        APR_IS(19, APR_STATUS_IS_EDSOOPEN);
        APR_IS(20, APR_STATUS_IS_EABSOLUTE);
        APR_IS(21, APR_STATUS_IS_ERELATIVE);
        APR_IS(22, APR_STATUS_IS_EINCOMPLETE);
        APR_IS(23, APR_STATUS_IS_EABOVEROOT);
        APR_IS(24, APR_STATUS_IS_EBADPATH);
        APR_IS(25, APR_STATUS_IS_EPATHWILD);
        APR_IS(26, APR_STATUS_IS_ESYMNOTFOUND);
        APR_IS(27, APR_STATUS_IS_EPROC_UNKNOWN);
        APR_IS(28, APR_STATUS_IS_ENOTENOUGHENTROPY);


        /* APR_Error */
        APR_IS(51, APR_STATUS_IS_INCHILD);
        APR_IS(52, APR_STATUS_IS_INPARENT);
        APR_IS(53, APR_STATUS_IS_DETACH);
        APR_IS(54, APR_STATUS_IS_NOTDETACH);
        APR_IS(55, APR_STATUS_IS_CHILD_DONE);
        APR_IS(56, APR_STATUS_IS_CHILD_NOTDONE);
        APR_ISX(57, APR_STATUS_IS_TIMEUP, TCN_TIMEUP);
        APR_IS(58, APR_STATUS_IS_INCOMPLETE);
        /* empty slot: +9 */
        /* empty slot: +10 */
        /* empty slot: +11 */
        APR_IS(62, APR_STATUS_IS_BADCH);
        APR_IS(63, APR_STATUS_IS_BADARG);
        APR_IS(64, APR_STATUS_IS_EOF);
        APR_IS(65, APR_STATUS_IS_NOTFOUND);
        /* empty slot: +16 */
        /* empty slot: +17 */
        /* empty slot: +18 */
        APR_IS(69, APR_STATUS_IS_ANONYMOUS);
        APR_IS(70, APR_STATUS_IS_FILEBASED);
        APR_IS(71, APR_STATUS_IS_KEYBASED);
        APR_IS(72, APR_STATUS_IS_EINIT);
        APR_IS(73, APR_STATUS_IS_ENOTIMPL);
        APR_IS(74, APR_STATUS_IS_EMISMATCH);
        APR_IS(75, APR_STATUS_IS_EBUSY);
        /* Socket errors */
        APR_ISX(90, APR_STATUS_IS_EAGAIN, TCN_EAGAIN);
        APR_ISX(91, TCN_STATUS_IS_ETIMEDOUT, TCN_ETIMEDOUT);
        APR_IS(92, APR_STATUS_IS_ECONNABORTED);
        APR_IS(93, APR_STATUS_IS_ECONNRESET);
        APR_ISX(94, APR_STATUS_IS_EINPROGRESS, TCN_EINPROGRESS);
        APR_ISX(95, APR_STATUS_IS_EINTR, TCN_EINTR);
        APR_IS(96, APR_STATUS_IS_ENOTSOCK);
        APR_IS(97, APR_STATUS_IS_EINVAL);
    }
    return JNI_FALSE;
}
