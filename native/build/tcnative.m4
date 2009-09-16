#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

dnl
dnl TCN_FIND_APR: figure out where APR is located
dnl
AC_DEFUN(TCN_FIND_APR,[

  dnl use the find_apr.m4 script to locate APR. sets apr_found and apr_config
  APR_FIND_APR(,,,[1])
  if test "$apr_found" = "no"; then
    AC_MSG_ERROR(APR could not be located. Please use the --with-apr option.)
  fi

  sapr_pversion="`$apr_config --version`"
  if test -z "$sapr_pversion"; then
    AC_MSG_ERROR(APR config could not be located. Please use the --with-apr option.)
  fi
  sapr_version="`echo $sapr_pversion|sed -e 's/\([a-z]*\)$/.\1/'`"
  tc_save_IFS=$IFS; IFS=.; set $sapr_version; IFS=$tc_save_IFS
  if test "${1}" -lt "1"; then
    AC_MSG_ERROR(You need APR version 1.2.1 or newer installed.)
  else
    if test "${2}" -lt "2"; then
      AC_MSG_ERROR(You need APR version 1.2.1 or newer installed.)
    fi
  fi

  APR_BUILD_DIR="`$apr_config --installbuilddir`"

  dnl make APR_BUILD_DIR an absolute directory (we'll need it in the
  dnl sub-projects in some cases)
  APR_BUILD_DIR="`cd $APR_BUILD_DIR && pwd`"

  APR_INCLUDES="`$apr_config --includes`"
  APR_LIBS="`$apr_config --link-libtool --libs`"
  APR_SO_EXT="`$apr_config --apr-so-ext`"
  APR_LIB_TARGET="`$apr_config --apr-lib-target`"

  AC_SUBST(APR_INCLUDES)
  AC_SUBST(APR_LIBS)
  AC_SUBST(APR_BUILD_DIR)
])

dnl --------------------------------------------------------------------------
dnl TCN_JDK
dnl
dnl Detection of JDK location and Java Platform (1.2, 1.3, 1.4, 1.5, 1.6)
dnl result goes in JAVA_HOME / JAVA_PLATFORM (2 -> 1.2 and higher)
dnl 
dnl --------------------------------------------------------------------------
AC_DEFUN(
  [TCN_FIND_JDK],
  [
    tempval=""
    AC_MSG_CHECKING([for JDK location (please wait)])
    if test -n "${JAVA_HOME}" ; then
      JAVA_HOME_ENV="${JAVA_HOME}"
    else
      JAVA_HOME_ENV=""
    fi

    JAVA_HOME=""
    JAVA_PLATFORM=""

    AC_ARG_WITH(
      [java-home],
      [  --with-java-home=DIR     Location of JDK directory.],
      [

      # This stuff works if the command line parameter --with-java-home was
      # specified, so it takes priority rightfully.
  
      tempval=${withval}

      if test ! -d "${tempval}" ; then
          AC_MSG_ERROR(Not a directory: ${tempval})
      fi
  
      JAVA_HOME=${tempval}
      AC_MSG_RESULT(${JAVA_HOME})
    ],
    [
      # This works if the parameter was NOT specified, so it's a good time
      # to see what the enviroment says.
      # Since Sun uses JAVA_HOME a lot, we check it first and ignore the
      # JAVA_HOME, otherwise just use whatever JAVA_HOME was specified.

      if test -n "${JAVA_HOME_ENV}" ; then
        JAVA_HOME=${JAVA_HOME_ENV}
        AC_MSG_RESULT(${JAVA_HOME_ENV} from environment)
      fi
    ])

    if test -z "${JAVA_HOME}" ; then

      # Oh well, nobody set neither JAVA_HOME nor JAVA_HOME, have to guess
      # The following code is based on the code submitted by Henner Zeller
      # for ${srcdir}/src/scripts/package/rpm/ApacheJServ.spec
      # Two variables will be set as a result:
      #
      # JAVA_HOME
      # JAVA_PLATFORM
      AC_MSG_CHECKING([Try to guess JDK location])

      for JAVA_PREFIX in /usr/local /usr/local/lib /usr /usr/lib /opt /usr/java ; do

        for JAVA_PLATFORM in 6 5 4 3 2 ; do

          for subversion in .9 .8 .7 .6 .5 .4 .3 .2 .1 "" ; do

            for VARIANT in IBMJava2- java java- jdk jdk-; do
              GUESS="${JAVA_PREFIX}/${VARIANT}1.${JAVA_PLATFORM}${subversion}"
dnl           AC_MSG_CHECKING([${GUESS}])
              if test -d "${GUESS}/bin" & test -d "${GUESS}/include" ; then
                JAVA_HOME="${GUESS}"
                AC_MSG_RESULT([${GUESS}])
                break
              fi
            done

            if test -n "${JAVA_HOME}" ; then
              break;
            fi

          done

          if test -n "${JAVA_HOME}" ; then
            break;
          fi

        done

        if test -n "${JAVA_HOME}" ; then
          break;
        fi

      done

      if test ! -n "${JAVA_HOME}" ; then
        AC_MSG_ERROR(can't locate a valid JDK location)
      fi

    fi

    if test -n "${JAVA_PLATFORM}"; then
      AC_MSG_RESULT(Java Platform detected - 1.${JAVA_PLATFORM})
    else
      AC_MSG_CHECKING(Java platform)
    fi

    AC_ARG_WITH(java-platform,
     [  --with-java-platform[=2] Force the Java platform
                                 (value is 1 for 1.1.x or 2 for 1.2.x or greater)],
     [
        case "${withval}" in
          "1"|"2")
            JAVA_PLATFORM=${withval}
            ;;
          *)
            AC_MSG_ERROR(invalid java platform provided)
            ;;
        esac
     ],
     [
        if test -n "${JAVA_PLATFORM}"; then
          AC_MSG_RESULT(Java Platform detected - 1.${JAVA_PLATFORM})
        else
          AC_MSG_CHECKING(Java platform)
        fi
     ])

     AC_MSG_RESULT(${JAVA_PLATFORM})

    unset tempval
  ])


AC_DEFUN(
  [TCN_FIND_JDK_OS],
  [
    tempval=""
    JAVA_OS=""
    AC_ARG_WITH(os-type,
      [  --with-os-type[=SUBDIR]  Location of JDK os-type subdirectory.],
      [
        tempval=${withval}

        if test ! -d "${JAVA_HOME}/${tempval}" ; then
          AC_MSG_ERROR(Not a directory: ${JAVA_HOME}/${tempval})
        fi

        JAVA_OS = ${tempval}
      ],
      [   
        AC_MSG_CHECKING(os_type directory)
        JAVA_OS=NONE
        if test -f ${JAVA_HOME}/${JAVA_INC}/jni_md.h; then
          JAVA_OS=""
        else
          for f in ${JAVA_HOME}/${JAVA_INC}/*/jni_md.h; do
            if test -f $f; then
              JAVA_OS=`dirname ${f}`
              JAVA_OS=`basename ${JAVA_OS}`
              echo " ${JAVA_OS}"
            fi
          done
          if test "${JAVA_OS}" = "NONE"; then
            AC_MSG_RESULT(Cannot find jni_md.h in ${JAVA_HOME}/${OS})
            AC_MSG_ERROR(You should retry --with-os-type=SUBDIR)
          fi
        fi
      ])
  ])

dnl check for sableVM
dnl (copied from daemon/src/native/unix/support/apjava.m4)
AC_DEFUN(
  [TCN_SABLEVM],
  [
  if test x"$JAVA_HOME" != x
  then
    AC_PATH_PROG(SABLEVM,sablevm,NONE,$JAVA_HOME/bin)
    if test "$SABLEVM" != "NONE"
    then
      AC_MSG_RESULT([Using sableVM: $SABLEVM])
      CFLAGS="$CFLAGS -DHAVE_SABLEVM"
      NEED_JNI_MD=no
    fi
  fi
  ])
dnl check for IBM J9VM
AC_DEFUN(
  [TCN_J9VM],
  [
  if test x"$JAVA_HOME" != x
  then
    J9VM=`$JAVA_HOME/bin/java -version 2>&1 | grep J9VM`
    if test x"$J9VM" != x
    then
      AC_MSG_RESULT([Using J9VM: $J9VM])
      NEED_JNI_MD=no
    fi
  fi
  ])

dnl TCN_HELP_STRING(LHS, RHS)
dnl Autoconf 2.50 can not handle substr correctly.  It does have 
dnl AC_HELP_STRING, so let's try to call it if we can.
dnl Note: this define must be on one line so that it can be properly returned
dnl as the help string.
AC_DEFUN(TCN_HELP_STRING,[ifelse(regexp(AC_ACVERSION, 2\.1), -1, AC_HELP_STRING($1,$2),[  ]$1 substr([                       ],len($1))$2)])dnl

dnl
dnl TCN_CHECK_SSL_TOOLKIT
dnl
dnl Configure for the detected openssl toolkit installation, giving
dnl preference to "--with-ssl=<path>" if it was specified.
dnl
AC_DEFUN(TCN_CHECK_SSL_TOOLKIT,[
OPENSSL_WARNING=
AC_MSG_CHECKING(for OpenSSL library)
AC_ARG_WITH(ssl,
[  --with-ssl[=PATH]   Build with OpenSSL [yes|no|path]],
    use_openssl="$withval", use_openssl="auto")

openssldirs="/usr /usr/local /usr/local/ssl /usr/pkg /usr/sfw"
if test "$use_openssl" = "auto"
then
    for d in $openssldirs
    do
        if test -f $d/include/openssl/opensslv.h
        then
            use_openssl=$d
            break
        fi
    done
fi
case "$use_openssl" in
    no)
        AC_MSG_RESULT(no)
        TCN_OPENSSL_INC=""
        USE_OPENSSL=""
        ;;
    auto)
        TCN_OPENSSL_INC=""
        USE_OPENSSL=""
        AC_MSG_RESULT(not found)
        ;;
    *)
        if test "$use_openssl" = "yes"
        then
            # User did not specify a path - guess it
            for d in $openssldirs
            do
                if test -f $d/include/openssl/opensslv.h
                then
                    use_openssl=$d
                    break
                fi
            done
            if test "$use_openssl" = "yes"
            then
                AC_MSG_RESULT(not found)
                AC_MSG_ERROR(
[OpenSSL was not found in any of $openssldirs; use --with-ssl=/path])
            fi
        fi
        USE_OPENSSL='-DOPENSSL'

        if test "$use_openssl" = "/usr"
        then
            TCN_OPENSSL_INC=""
            TCN_OPENSSL_LIBS="-lssl -lcrypto"
        else
            TCN_OPENSSL_INC="-I$use_openssl/include"
            case $host in
            *-solaris*)
                TCN_OPENSSL_LIBS="-L$use_openssl/lib -R$use_openssl/lib -lssl -lcrypto"
                ;;
            *-hp-hpux*)
                TCN_OPENSSL_LIBS="-L$use_openssl/lib -Wl,+b: -lssl -lcrypto"
                ;;
            *linux*)
                TCN_OPENSSL_LIBS="-L$use_openssl/lib -Wl,-rpath,$use_openssl/lib -lssl -lcrypto"
                ;;
            *)
                TCN_OPENSSL_LIBS="-L$use_openssl/lib -lssl -lcrypto"
                ;;
            esac
        fi
        AC_MSG_RESULT(using openssl from $use_openssl/lib and $use_openssl/include)

        saved_cflags="$CFLAGS"
        saved_libs="$LIBS"
        CFLAGS="$CFLAGS $TCN_OPENSSL_INC"
        LIBS="$LIBS $TCN_OPENSSL_LIBS"
         
AC_ARG_ENABLE(openssl-version-check,
[AC_HELP_STRING([--enable-openssl-version-check],
        [Check OpenSSL Version @<:@default=yes@:>@])])
case "$enable_openssl_version_check" in
yes|'')
        AC_MSG_CHECKING(OpenSSL library version)
        AC_TRY_RUN([
#include <stdio.h>
#include <openssl/opensslv.h>
int main() {
        if ((OPENSSL_VERSION_NUMBER >= 0x0090701fL &&
         OPENSSL_VERSION_NUMBER < 0x00908000L) ||
         OPENSSL_VERSION_NUMBER >= 0x0090801fL)
            return (0);
    printf("\n\nFound   OPENSSL_VERSION_NUMBER %#010x\n",
        OPENSSL_VERSION_NUMBER);
    printf("Require OPENSSL_VERSION_NUMBER 0x0090701f or greater (0.9.7a)\n"
           "Require OPENSSL_VERSION_NUMBER 0x0090801f or greater (0.9.8a)\n\n");
        return (1);
}
        ],
        [AC_MSG_RESULT(ok)],
        [AC_MSG_RESULT(not compatible)
            OPENSSL_WARNING=yes
        ],
        [AC_MSG_RESULT(assuming target platform has compatible version)])
;;
no)
    AC_MSG_RESULT(Skipped OpenSSL version check)
;;
esac

        AC_MSG_CHECKING(for OpenSSL DSA support)
        if test -f $use_openssl/include/openssl/dsa.h
        then
            AC_DEFINE(HAVE_OPENSSL_DSA)
            AC_MSG_RESULT(yes)
        else
            AC_MSG_RESULT(no)
        fi
        CFLAGS="$saved_cflags"
        LIBS="$saved_libs"
        ;;
esac
if test "x$USE_OPENSSL" != "x"
then
    APR_ADDTO(TCNATIVE_PRIV_INCLUDES, [$TCN_OPENSSL_INC])
    APR_ADDTO(TCNATIVE_LDFLAGS, [$TCN_OPENSSL_LIBS])
    APR_ADDTO(CFLAGS, [-DHAVE_OPENSSL])
fi
])
