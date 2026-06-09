cd scrubber
# -Djava.net.preferIPv4Stack=true: force the IPv4 stack so loopback HTTP calls (e.g. ClientService
# setnode -> http://127.0.0.1:8081) connect to IPv4 127.0.0.1 (which the server listens on, 0.0.0.0)
# instead of IPv6 ::1 (refused on IPv4-only servers). MUST be a JVM launch flag — the
# System.setProperty("java.net.preferIPv4Stack","true") at WebServer.java:1288 is a NO-OP because
# the property is read once at java.net init, before that code runs. Surfaced on dual-stack Linux
# PROD (Mac loopback happened to resolve IPv4 anyway). See B22 / TODO_FIXED.
java -Djava.net.preferIPv4Stack=true -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 -cp target/my-app-1.0-SNAPSHOT.jar com.mycompany.app.App

