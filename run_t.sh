cd scrubber
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -cp target/my-app-1.0-SNAPSHOT.jar com.mycompany.app.App 0 t



