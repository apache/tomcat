mvn install:install-file \
    -Dfile=output/embed/tomcat-embed-core.jar \
   -DgroupId=org.apache.tomcat.embed.events \
   -DartifactId=tomcat-embed-core-events \
   -Dversion=10 \
   -Dpackaging=jar \
   -DgeneratePom=true
