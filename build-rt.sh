echo "building extractor"
cd binary-extractor
mvn clean
mvn compile
mvn package
cd ..

echo "-------------------building cass-server.jar"
cd cass-server-maven
mvn clean
mvn compile
mvn package
cd ..
cp cass-server-maven/target/cass-server-1.0.0.jar ./rtserver-maven/lib/cass-server.jar

echo "-------------------building rtserver.jar"
rtserver.jar
cd rtserver-maven
./sync.sh
mvn clean
mvn compile
mvn package
cd ..
cp rtserver-maven/target/rtserver-1.0-SNAPSHOT.jar ./scrubber/repo/rtserver.jar
