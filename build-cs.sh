echo "-------------------building cass-server.jar"
cd cass-server-maven
./install-local-deps.sh
mvn clean
mvn compile
mvn package
cd ..
cp cass-server-maven/target/cass-server-1.0.0.jar ./rtserver-maven/lib/cass-server.jar
cp cass-server-maven/target/cass-server-1.0.0.jar ./scrubber/repo/cass-server.jar

