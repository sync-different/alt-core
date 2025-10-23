echo "-------------------building cass-server.jar"
cd cass-server-maven
call .\install-local-deps.bat
call mvn clean package
cd ..
copy .\cass-server-maven/target/cass-server-1.0.0.jar .\rtserver-maven\lib\cass-server.jar
copy .\cass-server-maven/target/cass-server-1.0.0.jar .\scrubber\repo\cass-server.jar
