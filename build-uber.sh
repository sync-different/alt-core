echo "-------------------building uber jar"
cd scrubber
./sync.sh
./install.sh
mvn clean
mvn compile
mvn package

