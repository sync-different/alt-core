mvn install:install-file \
   -Dfile=./repo/binary-extractor-1.0-SNAPSHOT.jar \
   -DgroupId=com.alterante.utils \
   -DartifactId=alterante \
   -Dversion=1.0.0 \
   -Dpackaging=jar \
   -DgeneratePom=true

mvn install:install-file \
   -Dfile=./repo/rtserver.jar \
   -DgroupId=com.alterante.rtserver \
   -DartifactId=rtserver \
   -Dversion=1.0.0 \
   -Dpackaging=jar \
   -DgeneratePom=true

mvn install:install-file \
   -Dfile=./repo/cass-server.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=cass-server \
   -Dversion=1.0.0 \
   -Dpackaging=jar \
   -DgeneratePom=true

mvn install:install-file \
   -Dfile=./repo/scrubber.jar \
   -DgroupId=com.alterante.scrubber \
   -DartifactId=scrubber \
   -Dversion=1.0.0 \
   -Dpackaging=jar \
   -DgeneratePom=true

GROUP_ID_PATH="com/alterante/rtserver/rtserver/1.0.0"
JAR_NAME="rtserver-1.0.0.jar"
UBER_JAR_NAME="./target/my-app-1.0-SNAPSHOT.jar"

#if [ ! -f "$HOME/.m2/repository/$GROUP_ID_PATH/$JAR_NAME" ]; then
if [ ! -f "$UBER_JAR_NAME" ]; then
  mvn install:install-file \
     -Dfile=./repo/WinRun4J.jar \
     -DgroupId=org.boris.win4j \
     -DartifactId=win4j \
     -Dversion=0.4.5 \
     -Dpackaging=jar \
     -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/netty-all-4.1.68.Final.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=netty \
   -Dversion=4.1.68 \
   -Dpackaging=jar \
   -DgeneratePom=true

#  mvn install:install-file \
#   -Dfile=./repo/apache-cassandra-thrift-1.2.0.jar \
#   -DgroupId=com.alterante.cass \
#   -DartifactId=thrift \
#   -Dversion=1.2.0 \
#   -Dpackaging=jar \
#   -DgeneratePom=true

#  mvn install:install-file \
#   -Dfile=./repo/libthrift-0.7.0.jar \
#   -DgroupId=com.alterante.cass \
#   -DartifactId=libthrift \
#   -Dversion=0.7.0 \
#   -Dpackaging=jar \
#   -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/mapdb-1.0.9.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=mapdb \
   -Dversion=1.0.9 \
   -Dpackaging=jar \
   -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/mail.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=mail \
   -Dversion=1.0.0 \
   -Dpackaging=jar \
   -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/commons-httpclient-3.1.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=httpclient \
   -Dversion=3.1 \
   -Dpackaging=jar \
   -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/commons-logging-1.1.3.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=commons-logging \
   -Dversion=1.1.3 \
   -Dpackaging=jar \
   -DgeneratePom=true

  #mvn install:install-file \
  # -Dfile=./repo/commons-codec-1.8.jar \
  # -DgroupId=com.alterante.cass \
  # -DartifactId=commons-codec \
  # -Dversion=1.8 \
  # -Dpackaging=jar \
  # -DgeneratePom=true

 mvn install:install-file \
   -Dfile=./repo/slf4j-log4j12-1.5.8.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=sl4jlog \
   -Dversion=1.5.8 \
   -Dpackaging=jar \
   -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/dom4j-1.6.1.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=dom4j \
   -Dversion=1.6.1 \
   -Dpackaging=jar \
   -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/httpcore5-5.3.1.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=httpcore \
   -Dversion=5.3.1 \
   -Dpackaging=jar \
   -DgeneratePom=true

  mvn install:install-file \
   -Dfile=./repo/jakarta.activation-2.0.1.jar \
   -DgroupId=com.alterante.cass \
   -DartifactId=jakarta \
   -Dversion=2.0.1 \
   -Dpackaging=jar \
   -DgeneratePom=true

  mvn install:install-file \
     -Dfile=./repo/json-smart-1.2.jar \
     -DgroupId=com.alterante.cass \
     -DartifactId=jsonsmart \
     -Dversion=1.2 \
     -Dpackaging=jar \
     -DgeneratePom=true

  mvn install:install-file \
     -Dfile=./repo/mailer.jar \
     -DgroupId=com.alterante.cass \
     -DartifactId=mailer \
     -Dversion=1.0.0 \
     -Dpackaging=jar \
     -DgeneratePom=true
mvn install:install-file \
   -Dfile=./repo/CloudBackup.jar \
   -DgroupId=com.alterante.scrubber \
   -DartifactId=cloudbackup \
   -Dversion=1.0.0 \
   -Dpackaging=jar \
   -DgeneratePom=true

mvn install:install-file \
   -Dfile=./repo/httpclient5-5.4.1.jar \
   -DgroupId=com.alterante.scrubber \
   -DartifactId=httpclient \
   -Dversion=5.4.1 \
   -Dpackaging=jar \
   -DgeneratePom=true

mvn install:install-file \
   -Dfile=./repo/httpcore5-h2-5.3.1.jar \
   -DgroupId=com.alterante.scrubber \
   -DartifactId=httpcoreh2 \
   -Dversion=5.3.1 \
   -Dpackaging=jar \
   -DgeneratePom=true


mvn install:install-file \
   -Dfile=./repo/jaudiotagger-2.2.0-20130321.162819-3.jar \
   -DgroupId=com.alterante.scrubber \
   -DartifactId=jaudiotagger \
   -Dversion=2.2.0 \
   -Dpackaging=jar \
   -DgeneratePom=true

mvn install:install-file \
   -Dfile=./repo/PDFRenderer-0.9.1.jar \
   -DgroupId=com.alterante.scrubber \
   -DartifactId=pdfrenderer \
   -Dversion=0.9.1 \
   -Dpackaging=jar \
   -DgeneratePom=true

mvn install:install-file \
   -Dfile=./repo/thumbnailator-0.4.5-all.jar \
   -DgroupId=com.alterante.scrubber \
   -DartifactId=thumbnailator \
   -Dversion=0.4.5 \
   -Dpackaging=jar \
   -DgeneratePom=true


fi






   