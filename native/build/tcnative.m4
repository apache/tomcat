dnl
dnl TCN_FIND_APR: figure out where APR is located
dnl
AC_DEFUN(TCN_FIND_APR,[

  dnl use the find_apr.m4 script to locate APR. sets apr_found and apr_config
  APR_FIND_APR(,,,[1])
  if test "$apr_found" = "no"; then
    AC_MSG_ERROR(APR could not be located. Please use the --with-apr option.)
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
        if test -f ${JAVA_HOME}/include/jni_md.h; then
          JAVA_OS=""
        else
          for f in ${JAVA_HOME}/include/*/jni_md.h; do
            if test -f $f; then
              JAVA_OS=`dirname ${f}`
              JAVA_OS=`basename ${JAVA_OS}`
              echo " ${JAVA_OS}"
            fi
          done
          if test -z "${JAVA_OS}"; then
            AC_MSG_RESULT(Cannot find jni_md.h in ${JAVA_HOME}/${OS})
            AC_MSG_ERROR(You should retry --with-os-type=SUBDIR)
          fi
        fi
      ])
  ])
