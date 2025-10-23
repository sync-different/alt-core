echo "Installing system dependencies to local Maven repository..."

echo "Installing Alt-common..."
call mvn install:install-file -Dfile=lib/alt-common-1.0-SNAPSHOT.jar -DgroupId=com.alterante.utils -DartifactId=alt-common -Dversion=1.0 -Dpackaging=jar

echo "Installing Cass-Server..."
call mvn install:install-file -Dfile=lib/cass-server.jar -DgroupId=com.alterante -DartifactId=cass-server -Dversion=1.0 -Dpackaging=jar

echo "System dependencies installed to local Maven repository."
echo "You can now use pom-compatible.xml which references these dependencies properly."
