#cass-server.jar
cd cass-server-maven
mvn clean
mvn compile
mvn package
cd ..
cp cass-server-maven/target/cass-server-1.0.0.jar ./rtserver-maven/lib/cass-server.jar

#rtserver.jar
cd rtserver-maven
mvn clean
mvn compile
mvn package
cd ..
cp rtserver-maven/target/rtserver-1.0-SNAPSHOT.jar ./scrubber/repo/rtserver.jar

#uber jar
cd scrubber
mvn clean
mvn compile
mvn package

