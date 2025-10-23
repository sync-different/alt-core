echo "-------------------building alt-common"
cd alt-common
call mvn clean compile package
cd ..
echo "cp"
copy alt-common\target\alt-common-1.0-SNAPSHOT.jar .\rtserver-maven\lib\
