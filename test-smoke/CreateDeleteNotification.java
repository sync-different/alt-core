import processor.DatabaseEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Smoke-test helper (Phase 7): writes a .D_ deletion notification — a
 * serialized DELETE DatabaseEntry, the same record FileScanner emits when a
 * file disappears — so ProcessorService runs the deletion pipeline
 * (processDeleteFileRecordData_p2p) for an uploaded test file.
 *
 * Usage:
 *   java -cp <uber.jar><sep><classdir> CreateDeleteNotification \
 *        <md5> <nodeUuid> <absolutePath> <outFile>
 *
 * nodeUuid must be the NODE uuid (scrubber/data/.uuid), not a session uuid:
 * Super2/paths columns are keyed "<nodeUuid>:<path>/" (LocalFuncs.deleteObject).
 *
 * ProcessorService deserializes any non-.zip/.b/.p file in incoming/, so the
 * record is written to a temp file first and moved in, never left partial.
 */
public class CreateDeleteNotification {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: CreateDeleteNotification <md5> <nodeUuid> <absolutePath> <outFile>");
            System.exit(2);
        }
        DatabaseEntry entry = new DatabaseEntry(args[0], UUID.fromString(args[1]), args[2]);

        Path out = new File(args[3]).toPath();
        Path tmp = Files.createTempFile("smoke-delete-", ".ser");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp.toFile()))) {
            oos.writeObject(entry);
        }
        try {
            Files.move(tmp, out, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println(out.toAbsolutePath());
    }
}
