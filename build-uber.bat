echo "-------------------building uber jar"
cd scrubber
REM .\sync.bat
call .\install.bat
call mvn clean compile package

