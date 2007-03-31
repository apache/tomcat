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
  IFS=.; set $sapr_version; IFS=' '
  if test "$1" -lt "1"; then
    AC_MSG_ERROR(You need APR version 1.2.1 or newer installed.)
  else
    if test "$2" -lt "2"; then
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
     [  --with-java-platform[=2] Force the Java platorm
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
  dnl initialise the variables we use
  tcn_ssltk_base=""
  tcn_ssltk_inc=""
  tcn_ssltk_lib=""
  tcn_ssltk_type=""
  AC_ARG_WITH(ssl, TCN_HELP_STRING(--with-ssl=DIR,OpenSSL SSL/TLS toolkit), [
    dnl If --with-ssl specifies a directory, we use that directory or fail
    if test "x$withval" != "xyes" -a "x$withval" != "x"; then
      dnl This ensures $withval is actually a directory and that it is absolute
      tcn_ssltk_base="`cd $withval ; pwd`"
    fi
  ])
  if test "x$tcn_ssltk_base" = "x"; then
    AC_MSG_RESULT(none)
  else
    AC_MSG_RESULT($tcn_ssltk_base)
  fi

  dnl Run header and version checks
  saved_CPPFLAGS=$CPPFLAGS
  if test "x$tcn_ssltk_base" != "x"; then
    tcn_ssltk_inc="-I$tcn_ssltk_base/include"
    CPPFLAGS="$CPPFLAGS $tcn_ssltk_inc"
  fi

  if test "x$tcn_ssltk_type" = "x"; then
    AC_MSG_CHECKING(for OpenSSL version)
    dnl First check for manditory headers
    AC_CHECK_HEADERS([openssl/opensslv.h], [tcn_ssltk_type="openssl"], [])
    if test "$tcn_ssltk_type" = "openssl"; then
      dnl so it's OpenSSL - test for a good version
      AC_TRY_COMPILE([#include <openssl/opensslv.h>],[
#if !defined(OPENSSL_VERSION_NUMBER)
  #error "Missing openssl version"
#endif
#if  (OPENSSL_VERSION_NUMBER < 0x0090701f)
  #error "Unsuported openssl version " OPENSSL_VERSION_TEXT
#endif],
      [AC_MSG_RESULT(OK)],
      [dnl Unsuported OpenSSL version
         AC_MSG_ERROR([Unsupported OpenSSL version. Use 0.9.7a or higher version])
      ])
      dnl Look for additional, possibly missing headers
      AC_CHECK_HEADERS(openssl/engine.h)
      if test -n "$PKGCONFIG"; then
        $PKGCONFIG openssl
        if test $? -eq 0; then
          tcn_ssltk_inc="$tcn_ssltk_inc `$PKGCONFIG --cflags-only-I openssl`"
          CPPFLAGS="$CPPFLAGS $tcn_ssltk_inc"
        fi
      fi
    else
      AC_MSG_RESULT([no OpenSSL headers found])
    fi
  fi
  if test "$tcn_ssltk_type" != "openssl"; then
    AC_MSG_ERROR([... No OpenSSL headers found])
  fi
  dnl restore
  CPPFLAGS=$saved_CPPFLAGS
  if test "x$tcn_ssltk_type" = "x"; then
    AC_MSG_ERROR([...No recognized SSL/TLS toolkit detected])
  fi

  dnl Run library and function checks
  saved_LDFLAGS=$LDFLAGS
  saved_LIBS=$LIBS
  if test "x$tcn_ssltk_base" != "x"; then
    if test -d "$tcn_ssltk_base/lib64"; then
      tcn_ssltk_lib="$tcn_ssltk_base/lib64"
    elif test -d "$tcn_ssltk_base/lib"; then
      tcn_ssltk_lib="$tcn_ssltk_base/lib"
    else
      tcn_ssltk_lib="$tcn_ssltk_base"
    fi
    LDFLAGS="$LDFLAGS -L$tcn_ssltk_lib"
  fi
  dnl make sure "other" flags are available so libcrypto and libssl can link
  LIBS="$LIBS `$apr_config --libs`"
  liberrors=""
  if test "$tcn_ssltk_type" = "openssl"; then
    AC_CHECK_LIB(crypto, SSLeay_version, [], [liberrors="yes"])
    AC_CHECK_LIB(ssl, SSL_CTX_new, [], [liberrors="yes"])
    AC_CHECK_FUNCS(ENGINE_init)
    AC_CHECK_FUNCS(ENGINE_load_builtin_engines)
  else
    AC_CHECK_LIB(sslc, SSLC_library_version, [], [liberrors="yes"])
    AC_CHECK_LIB(sslc, SSL_CTX_new, [], [liberrors="yes"])
    AC_CHECK_FUNCS(SSL_set_state)
  fi
  AC_CHECK_FUNCS(SSL_set_cert_store)
  dnl restore
  LDFLAGS=$saved_LDFLAGS
  LIBS=$saved_LIBS
  if test "x$liberrors" != "x"; then
    AC_MSG_ERROR([... Error, SSL/TLS libraries were missing or unusable])
  fi

  dnl (b) hook up include paths
  if test "x$tcn_ssltk_inc" != "x"; then
    APR_ADDTO(TCNATIVE_PRIV_INCLUDES, [$tcn_ssltk_inc])
  fi
  dnl (c) hook up linker paths
  if test "x$tcn_ssltk_lib" != "x"; then
    APR_ADDTO(TCNATIVE_LDFLAGS, ["-L$tcn_ssltk_lib"])
  fi

  dnl Adjust configuration based on what we found above.
  dnl (a) define preprocessor symbols
  if test "$tcn_ssltk_type" = "openssl"; then
    APR_SETVAR(SSL_LIBS, [-lssl -lcrypto])
    APR_ADDTO(CFLAGS, [-DHAVE_OPENSSL])
  fi
  AC_SUBST(SSL_LIBS)
])
