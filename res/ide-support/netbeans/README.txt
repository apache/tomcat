================================================================================
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
================================================================================


       Building and Debugging Apache Tomcat under NetBeans


Unlike other IDE's, NetBeans is a pure java swing application. It uses
Apache Ant to build its projects, and works directly with the class files
and jars created by the standard Apache Ant build.xml files. This strength
is also its weakness when working with complex projects such as Tomcat that
already have their own build.xml files, but which do not use the NetBeans
templates.

Any of complex Ant project can still be managed under NetBeans by
defining it to be something called a Free-Form Project. However, because
the build.xml does not conform to all the NetBeans naming and structural
conventions, a certain amount of manual customisation is required to
achieve a useful level of integration.


1. NetBeans can open a Tomcat source tree as a Free-Form Project, which
   will allow you to edit, build, and debug Tomcat and its unit tests
   within the workbench. Even with NetBeans 7.1, integration of a project
   as complex as Tomcat requires significant configuration. The
   configuration involves dealing with several quirky aspects of the way
   NetBeans manages this kind of project. Before you try to open Tomcat
   as a NetBeans project, you are strongly recommended to successfully
   build and run the tests using Apache Ant from a command prompt!
   (see BUILDING.txt in the Tomcat source root directory).

2. Once Tomcat has been built, you can install the default NetBeans
   project configuration by running the following build target:

          ant ide-netbeans

   This uses the Tomcat build.xml to create a new directory called
   nbproject (this name is reserved by NetBeans). The nbproject directory
   will then be primed with a self-consistent set of default definitions
   sufficient to open Tomcat as a Free-Form Project under NetBeans.

   Note: if you ever open the Project Properties from the NetBeans context
   menu, even without making any changes, there is a significant risk
   that NetBeans will modify one or more of these files. You can
   restore the Tomcat default files at any time by running this target:

          ant ide-netbeans-replace

   Only the default files will be overwritten, so any other content
   such as your own local files, and the NetBeans private directory,
   will not be affected.

3. NetBeans needs to know where to find the directory where you keep the
   Tomcat dependencies jars. If you have successfully built Tomcat from
   a command prompt, you will probably have assigned the base.path
   property in your build.properties file.

   Warning: The support for Tomcat in NetBeans will detect and use this
            property. However, if you have left it to default, you MUST
            still define this path in the nb-tomcat-project.properties file!

   Note: The current support for Tomcat in NetBeans does not include the
         components in the modules directory (e.g. tomcate-lite).

4. Start NetBeans... once it has initialised and scanned your other open
   projects, just open an existing project and select the location of
   Tomcat. NetBeans will recognise it as a Free-Form project, then read and
   validate the nbproject/project.xml file. This file defines how to relate
   targets in build.xml to NetBeans project-related actions. It also tells
   NetBeans what classpaths to use for validation and code completion of
   the various source directories.

   Warning: do not be tempted to casually click the properties menu item
            for the Tomcat project. NetBeans might change the contents
            of these files. (The NetBeans New Project wizard also
            automatically creates a Free-Form project.xml which carries
            this same warning).

   Note: the Tomcat project should open successfully and, after the source
         packages have been scanned, they should not be flagged with any
         syntax errors (except in some of the jsp bug unit tests).

5. Verify your work by running the NetBeans project Clean action. It
   should complete successfully. Next, run the Build action (which calls
   the Tomcat deploy build target) and confirm that it successfully
   compiles the Tomcat source files and creates the jars.

6. Next, navigate down to one of the test files and select the compile
   action. This will compile only your chosen file, although the compiler
   will find there is nothing to do unless you have deliberately changed it.

   Note: if you have changed any of the Tomcat source files, they will be
   recompiled first. However, any changes to test files will not be compiled
   unless you select those file and explicitly compile them. If you have any
   doubts about dependencies between unit test classes, you can use the
   compileAllTests project action and any files that have been changed
   will be detected and compiled.

7. You can run an individual unit test class by selecting it and choosing
   the "run selected file" NetBeans action. As the test runs, NetBeans
   should open a unit test results pane that shows you the progress and
   final outcome of the test (Note: this feature does not currently work).

8. Next, open the source of the unit test that ran successfully in step 7.
   Set a breakpoint in one of the test cases, then request NetBeans to
   debug that class. The class will start running, and then will stop as
   it hits your breakpoint. You should be able to display variables, then
   navigate the call stack to open the source files of each method. You
   should also be able to step through the code. Use the continue icon
   to resume execution. When the test completes, you should see the same
   jUnit test result panel as in step 7 (Note: this feature does not
   currently work).

9. You can also use your Tomcat NetBeans Free-Form project to debug an
   external Tomcat instance that is executing on the same, or a different
   machine. (Obviously, the external instance must be running the same
   version of the source code!)

   The external Tomcat instance must be started with its jvm enabled for
   debugging by adding extra arguments to JAVA_OPTS, e.g.
   -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n

   To debug the external Tomcat instance under NetBeans, select the
   "attach debugger" choice from the debug menu. Accept the default
   JPDA debugger with the SocketAttach connector and the dt_socket
   transport. Specify the hostname and port where the Tomcat jvm is
   listening. Your NetBeans workbench should then connect to the external
   Tomcat and display the running threads. You can now set breakpoints and
   begin debugging.
