# binary-extractor is NOT a buildable module in this checkout: ../binary-extractor/ is untracked,
# has no pom.xml and no .java sources (only stray .class files), so ../binary-extractor/target/
# never contains a jar. The authoritative artifact is the tracked ./repo/binary-extractor-1.0-SNAPSHOT.jar
# (installed into .m2 by install.sh as com.alterante.utils:alterante:1.0.0).
# Refresh ONLY if a freshly-built jar actually exists; otherwise keep the committed repo jar.
# A bare `cp` here failed every build under `set -e` because the source jar doesn't exist.
SRC_BE="../binary-extractor/target/binary-extractor-1.0-SNAPSHOT.jar"
if [ -f "$SRC_BE" ]; then
  echo "sync.sh: refreshing binary-extractor jar from $SRC_BE"
  cp "$SRC_BE" ./repo
else
  echo "sync.sh: no built binary-extractor jar at $SRC_BE — using committed ./repo/binary-extractor-1.0-SNAPSHOT.jar"
fi



