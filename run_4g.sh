cd scrubber
# Heap-OOM repro test (PROJECT_INDEXING test 2 / 2026-05-29).
# -Xmx4g deliberately caps the JVM heap to probe whether the death-spiral
# trigger is JVM heap exhaustion under heavy video indexing load.
# Note: this caps HEAP only, NOT physical RAM / OS page cache. If the spiral
# is page-cache eviction of MapDB mmap (the leading hypothesis), -Xmx alone
# will NOT reproduce it — that needs real physical-RAM starvation. This run
# tests the competing heap-OOM hypothesis. See internal/PROJECT_INDEXING.md.
# -Djava.net.preferIPv4Stack=true: force IPv4 loopback (setnode -> 127.0.0.1, not ::1). Must be a
# launch flag; the System.setProperty at WebServer.java:1288 is a no-op. See B22 / TODO_FIXED.
java -Djava.net.preferIPv4Stack=true -Xmx4g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 -cp target/my-app-1.0-SNAPSHOT.jar com.mycompany.app.App
