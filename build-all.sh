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
cp cass-server-maven/target/cass-server-1.0.0.jar ./scrubber/repo/cass-server.jar

echo "-------------------building rtserver.jar"
cd rtserver-maven
./sync.sh
mvn clean
mvn compile
mvn package
cd ..
cp rtserver-maven/target/rtserver-1.0-SNAPSHOT.jar ./scrubber/repo/rtserver.jar


echo "-------------------building scrubber.jar"
cd scrubber-maven
mvn clean
mvn compile
mvn package
cd ..
cp scrubber-maven/target/scrubber.jar ./scrubber/repo/scrubber.jar

echo "-------------------building uber jar"
cd scrubber
./sync.sh
./install.sh
mvn clean
mvn compile
mvn package

