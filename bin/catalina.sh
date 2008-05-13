#!/bin/sh

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

# -----------------------------------------------------------------------------
# Start/Stop Script for the CATALINA Server
#
# Environment Variable Prequisites
#
#   CATALINA_HOME   May point at your Catalina "build" directory.
#
#   CATALINA_BASE   (Optional) Base directory for resolving dynamic portions
#                   of a Catalina installation.  If not present, resolves to
#                   the same directory that CATALINA_HOME points to.
#
#   CATALINA_OPTS   (Optional) Java runtime options used when the "start",
#                   or "run" command is executed.
#
#   CATALINA_TMPDIR (Optional) Directory path location of temporary directory
#                   the JVM should use (java.io.tmpdir).  Defaults to
#                   $CATALINA_BASE/temp.
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#                   Required to run the with the "debug" or "javac" argument.
#
#   JRE_HOME        Must point at your Java Development Kit installation.
#                   Defaults to JAVA_HOME if empty.
#
#   JAVA_OPTS       (Optional) Java runtime options used when the "start",
#                   "stop", or "run" command is executed.
#
#   JPDA_TRANSPORT  (Optional) JPDA transport used when the "jpda start"
#                   command is executed. The default is "dt_socket".
#
#   JPDA_ADDRESS    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. The default is 8000.
#
#   JPDA_SUSPEND    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. Specifies whether JVM should suspend
#                   execution immediately after startup. Default is "n".
#
#   JPDA_OPTS       (Optional) Java runtime options used when the "jpda start"
#                   command is executed. If used, JPDA_TRANSPORT, JPDA_ADDRESS,
#                   and JPDA_SUSPEND are ignored. Thus, all required jpda
#                   options MUST be specified. The default is:
#
#                   -Xdebug -Xrunjdwp:transport=$JPDA_TRANSPORT,
#                       address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND
#
#   JSSE_HOME       (Optional) May point at your Java Secure Sockets Extension
#                   (JSSE) installation, whose JAR files will be added to the
#                   system class path used to start Tomcat.
#
#   CATALINA_PID    (Optional) Path of the file which should contains the pid
#                   of catalina startup java process, when start (fork) is used
#
# $Id$
# -----------------------------------------------------------------------------

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
os400=false
darwin=false
case "`uname`" in
CYGWIN*) cygwin=true;;
OS400*) os400=true;;
Darwin*) darwin=true;;
esac

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set CATALINA_HOME if not already set
[ -z "$CATALINA_HOME" ] && CATALINA_HOME=`cd "$PRGDIR/.." ; pwd`

if [ -r "$CATALINA_BASE"/bin/setenv.sh ]; then
  . "$CATALINA_BASE"/bin/setenv.sh
elif [ -r "$CATALINA_HOME"/bin/setenv.sh ]; then
  . "$CATALINA_HOME"/bin/setenv.sh
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$JRE_HOME" ] && JRE_HOME=`cygpath --unix "$JRE_HOME"`
  [ -n "$CATALINA_HOME" ] && CATALINA_HOME=`cygpath --unix "$CATALINA_HOME"`
  [ -n "$CATALINA_BASE" ] && CATALINA_BASE=`cygpath --unix "$CATALINA_BASE"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
  [ -n "$JSSE_HOME" ] && JSSE_HOME=`cygpath --absolute --unix "$JSSE_HOME"`
fi

# For OS400
if $os400; then
  # Set job priority to standard for interactive (interactive - 6) by using
  # the interactive priority - 6, the helper threads that respond to requests
  # will be running at the same priority as interactive jobs.
  COMMAND='chgjob job('$JOBNAME') runpty(6)'
  system $COMMAND

  # Enable multi threading
  export QIBM_MULTI_THREADED=Y
fi

# Get standard Java environment variables
if $os400; then
  # -r will Only work on the os400 if the files are:
  # 1. owned by the user
  # 2. owned by the PRIMARY group of the user
  # this will not work if the user belongs in secondary groups
  BASEDIR="$CATALINA_HOME"
  . "$CATALINA_HOME"/bin/setclasspath.sh 
else
  if [ -r "$CATALINA_HOME"/bin/setclasspath.sh ]; then
    BASEDIR="$CATALINA_HOME"
    . "$CATALINA_HOME"/bin/setclasspath.sh
  else
    echo "Cannot find $CATALINA_HOME/bin/setclasspath.sh"
    echo "This file is needed to run this program"
    exit 1
  fi
fi

# Add on extra jar files to CLASSPATH
if [ -n "$JSSE_HOME" ]; then
  CLASSPATH="$CLASSPATH":"$JSSE_HOME"/lib/jcert.jar:"$JSSE_HOME"/lib/jnet.jar:"$JSSE_HOME"/lib/jsse.jar
fi
CLASSPATH="$CLASSPATH":"$CATALINA_HOME"/bin/bootstrap.jar

if [ -z "$CATALINA_BASE" ] ; then
  CATALINA_BASE="$CATALINA_HOME"
fi

if [ -z "$CATALINA_TMPDIR" ] ; then
  # Define the java.io.tmpdir to use for Catalina
  CATALINA_TMPDIR="$CATALINA_BASE"/temp
fi

# Bugzilla 37848: When no TTY is available, don't output to console
have_tty=0
if [ "`tty`" != "not a tty" ]; then
    have_tty=1
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  JRE_HOME=`cygpath --absolute --windows "$JRE_HOME"`
  CATALINA_HOME=`cygpath --absolute --windows "$CATALINA_HOME"`
  CATALINA_BASE=`cygpath --absolute --windows "$CATALINA_BASE"`
  CATALINA_TMPDIR=`cygpath --absolute --windows "$CATALINA_TMPDIR"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
  [ -n "$JSSE_HOME" ] && JSSE_HOME=`cygpath --absolute --windows "$JSSE_HOME"`
  JAVA_ENDORSED_DIRS=`cygpath --path --windows "$JAVA_ENDORSED_DIRS"`
fi

# Set juli LogManager if it is present
if [ -r "$CATALINA_BASE"/conf/logging.properties ]; then
  JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager"
  LOGGING_CONFIG="-Djava.util.logging.config.file=$CATALINA_BASE/conf/logging.properties"
fi

# ----- Execute The Requested Command -----------------------------------------

# Bugzilla 37848: only output this if we have a TTY
if [ $have_tty -eq 1 ]; then
  echo "Using CATALINA_BASE:   $CATALINA_BASE"
  echo "Using CATALINA_HOME:   $CATALINA_HOME"
  echo "Using CATALINA_TMPDIR: $CATALINA_TMPDIR"
  if [ "$1" = "debug" -o "$1" = "javac" ] ; then
    echo "Using JAVA_HOME:       $JAVA_HOME"
  else
    echo "Using JRE_HOME:       $JRE_HOME"
  fi
fi

if [ "$1" = "jpda" ] ; then
  if [ -z "$JPDA_TRANSPORT" ]; then
    JPDA_TRANSPORT="dt_socket"
  fi
  if [ -z "$JPDA_ADDRESS" ]; then
    JPDA_ADDRESS="8000"
  fi
  if [ -z "$JPDA_SUSPEND" ]; then
    JPDA_SUSPEND="n"
  fi
  if [ -z "$JPDA_OPTS" ]; then
    JPDA_OPTS="-agentlib:jdwp=transport=$JPDA_TRANSPORT,address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND"
  fi
  CATALINA_OPTS="$CATALINA_OPTS $JPDA_OPTS"
  shift
fi

if [ "$1" = "debug" ] ; then
  if $os400; then
    echo "Debug command not available on OS400"
    exit 1
  else
    shift
    if [ "$1" = "-security" ] ; then
      echo "Using Security Manager"
      shift
      exec "$_RUNJDB" $JAVA_OPTS "$LOGGING_CONFIG" $CATALINA_OPTS \
        -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
        -sourcepath "$CATALINA_HOME"/../../java \
        -Djava.security.manager \
        -Djava.security.policy=="$CATALINA_BASE"/conf/catalina.policy \
        -Dcatalina.base="$CATALINA_BASE" \
        -Dcatalina.home="$CATALINA_HOME" \
        -Djava.io.tmpdir="$CATALINA_TMPDIR" \
        org.apache.catalina.startup.Bootstrap "$@" start
    else
      exec "$_RUNJDB" $JAVA_OPTS "$LOGGING_CONFIG" $CATALINA_OPTS \
        -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
        -sourcepath "$CATALINA_HOME"/../../java \
        -Dcatalina.base="$CATALINA_BASE" \
        -Dcatalina.home="$CATALINA_HOME" \
        -Djava.io.tmpdir="$CATALINA_TMPDIR" \
        org.apache.catalina.startup.Bootstrap "$@" start
    fi
  fi

elif [ "$1" = "run" ]; then

  shift
  if [ "$1" = "-security" ] ; then
    echo "Using Security Manager"
    shift
    exec "$_RUNJAVA" $JAVA_OPTS "$LOGGING_CONFIG" $CATALINA_OPTS \
      -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
      -Djava.security.manager \
      -Djava.security.policy=="$CATALINA_BASE"/conf/catalina.policy \
      -Dcatalina.base="$CATALINA_BASE" \
      -Dcatalina.home="$CATALINA_HOME" \
      -Djava.io.tmpdir="$CATALINA_TMPDIR" \
      org.apache.catalina.startup.Bootstrap "$@" start
  else
    exec "$_RUNJAVA" $JAVA_OPTS "$LOGGING_CONFIG" $CATALINA_OPTS \
      -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
      -Dcatalina.base="$CATALINA_BASE" \
      -Dcatalina.home="$CATALINA_HOME" \
      -Djava.io.tmpdir="$CATALINA_TMPDIR" \
      org.apache.catalina.startup.Bootstrap "$@" start
  fi

elif [ "$1" = "start" ] ; then

  shift
  touch "$CATALINA_BASE"/logs/catalina.out
  if [ "$1" = "-security" ] ; then
    echo "Using Security Manager"
    shift
    "$_RUNJAVA" $JAVA_OPTS "$LOGGING_CONFIG" $CATALINA_OPTS \
      -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
      -Djava.security.manager \
      -Djava.security.policy=="$CATALINA_BASE"/conf/catalina.policy \
      -Dcatalina.base="$CATALINA_BASE" \
      -Dcatalina.home="$CATALINA_HOME" \
      -Djava.io.tmpdir="$CATALINA_TMPDIR" \
      org.apache.catalina.startup.Bootstrap "$@" start \
      >> "$CATALINA_BASE"/logs/catalina.out 2>&1 &

      if [ ! -z "$CATALINA_PID" ]; then
        echo $! > $CATALINA_PID
      fi
  else
    "$_RUNJAVA" $JAVA_OPTS "$LOGGING_CONFIG" $CATALINA_OPTS \
      -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
      -Dcatalina.base="$CATALINA_BASE" \
      -Dcatalina.home="$CATALINA_HOME" \
      -Djava.io.tmpdir="$CATALINA_TMPDIR" \
      org.apache.catalina.startup.Bootstrap "$@" start \
      >> "$CATALINA_BASE"/logs/catalina.out 2>&1 &

      if [ ! -z "$CATALINA_PID" ]; then
        echo $! > $CATALINA_PID
      fi
  fi

elif [ "$1" = "stop" ] ; then

  shift
  FORCE=0
  if [ "$1" = "-force" ]; then
    shift
    FORCE=1
  fi

  "$_RUNJAVA" $JAVA_OPTS \
    -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
    -Dcatalina.base="$CATALINA_BASE" \
    -Dcatalina.home="$CATALINA_HOME" \
    -Djava.io.tmpdir="$CATALINA_TMPDIR" \
    org.apache.catalina.startup.Bootstrap "$@" stop

  if [ $FORCE -eq 1 ]; then
    if [ ! -z "$CATALINA_PID" ]; then
       echo "Killing: `cat $CATALINA_PID`"
       kill -9 `cat $CATALINA_PID`
    else
       echo "Kill failed: \$CATALINA_PID not set"
    fi
  fi

elif [ "$1" = "version" ] ; then

    "$_RUNJAVA"   \
      -classpath "$CATALINA_HOME/lib/catalina.jar" \
      org.apache.catalina.util.ServerInfo

else

  echo "Usage: catalina.sh ( commands ... )"
  echo "commands:"
  if $os400; then
    echo "  debug             Start Catalina in a debugger (not available on OS400)"
    echo "  debug -security   Debug Catalina with a security manager (not available on OS400)"
  else
    echo "  debug             Start Catalina in a debugger"
    echo "  debug -security   Debug Catalina with a security manager"
  fi
  echo "  jpda start        Start Catalina under JPDA debugger"
  echo "  run               Start Catalina in the current window"
  echo "  run -security     Start in the current window with security manager"
  echo "  start             Start Catalina in a separate window"
  echo "  start -security   Start in a separate window with security manager"
  echo "  stop              Stop Catalina"
  echo "  stop -force       Stop Catalina (followed by kill -KILL)"
  echo "  version           What version of tomcat are you running?"
  exit 1

fi
