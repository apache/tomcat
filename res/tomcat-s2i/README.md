<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->

# Tomcat - Source-to-Image Builder
This repository provides a CentOS-based Docker image that enables Source-to-Image (S2I) building for Tomcat. It builds the sources of your webapp and deploys it to a fully functional containerized Tomcat Server. The generated image can then easily be run locally or deployed to your Openshift Server.

## Build the Image
You can build the Tomcat-S2I Docker image by cloning the present repository and running `docker build`:
```bash
$ docker build . -t [IMAGE_BUILDER_TAG]
```
Where `[IMAGE_BUILDER_TAG]` is the tag you would like to assign to the tomcat-s2i image builder.

## Usage
To use the provided image, you'll first need to install [Openshift S2I](https://github.com/openshift/source-to-image#installation). Then simply run the following command:
```bash
$ s2i build [SOURCE_URL] [IMAGE_BUILDER_TAG] [IMAGE_TAG]
```
Where `[SOURCE_URL]` is either the URL to your Git repository or the path to your local sources and `[IMAGE_TAG]` is simply the tag you would like to assign to the generated image.

Once built, you can run it locally:
```bash
$ docker run -p 8080:8080 [IMAGE_TAG]
```
Or push it to a Docker Registry for further use on your Openshift Server:
```bash
$ docker push [IMAGE_TAG]
```