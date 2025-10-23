cd scrubber
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 -cp target\my-app-1.0-SNAPSHOT.jar com.mycompany.app.App 1 x



