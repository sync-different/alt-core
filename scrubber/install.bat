call mvn install:install-file -Dfile=./repo/cass-server.jar -DgroupId=com.alterante.cass -DartifactId=cass-server -Dversion=1.0.0 -Dpackaging=jar -DgeneratePom=true
call mvn install:install-file -Dfile=./repo/rtserver.jar -DgroupId=com.alterante.rtserver -DartifactId=rtserver -Dversion=1.0.0 -Dpackaging=jar -DgeneratePom=true
call mvn install:install-file -Dfile=./repo/scrubber.jar -DgroupId=com.alterante.scrubber -DartifactId=scrubber -Dversion=1.0.0 -Dpackaging=jar -DgeneratePom=true





   