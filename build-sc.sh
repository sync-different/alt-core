echo "-------------------building scrubber.jar"
cd scrubber-maven
mvn clean
mvn compile
mvn package
cd ..
cp scrubber-maven/target/scrubber.jar ./scrubber/repo/scrubber.jar
