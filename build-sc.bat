echo "-------------------building scrubber.jar"
cd scrubber-maven
call mvn clean compile package
cd ..
copy .\scrubber-maven\target\scrubber.jar .\scrubber\repo\scrubber.jar
