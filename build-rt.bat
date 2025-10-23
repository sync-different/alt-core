echo "-------------------building rtserver.jar"
cd rtserver-maven
.\sync.sh
call mvn clean compile package
cd ..
copy .\rtserver-maven\target\rtserver-1.0-SNAPSHOT.jar .\scrubber\repo\rtserver.jar
