echo "-------------------building alt-common"
cd alt-common
mvn clean
mvn compile
mvn package
cd ..
cp alt-common/target/alt-common-1.0-SNAPSHOT.jar ./rtserver-maven/lib/
