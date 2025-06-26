#cass-server.jar
#cd cass-server-maven
#mvn clean
#mvn compile
#mvn package
#cd ..
#cp cass-server-maven/target/cass-server-1.0.0.jar ./rtserver-maven/lib/cass-server.jar

#rtserver.jar
#cd rtserver-maven
#mvn clean
#mvn compile
#mvn package
#cd ..
#cp rtserver-maven/target/rtserver-1.0-SNAPSHOT.jar ./scrubber/repo/rtserver.jar

#scrubber.jar
cd scrubber-maven
mvn clean
mvn compile
mvn package
cd ..
cp scrubber-maven/target/scrubber.jar ./scrubber/repo/scrubber.jar

#uber jar
#cd scrubber
#./install.sh
#mvn clean
#mvn compile
#mvn package
