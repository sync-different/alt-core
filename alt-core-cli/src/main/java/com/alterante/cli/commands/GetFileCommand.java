package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.Main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetFileCommand {

    private static final long DEFAULT_CHUNK_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public void run(HttpSession session, String[] args) throws Exception {
        String md5 = Main.getArg(args, "md5");
        String filename = Main.getArg(args, "filename");
        String output = Main.getArg(args, "output");
        String ext = Main.getArg(args, "ext");
        boolean noChunk = Main.hasFlag(args, "nochunk");
        boolean noAuth = Main.hasFlag(args, "noauth");

        String chunkSizeArg = Main.getArg(args, "chunksize");
        long chunkSize = DEFAULT_CHUNK_SIZE;
        if (chunkSizeArg != null) {
            chunkSize = Long.parseLong(chunkSizeArg) * 1024 * 1024; // MB to bytes
        }

        if (md5 == null) {
            System.err.println("Usage: getfile --md5 <hash> [--filename <name>] [--output <dir>] [--ext <ext>]");
            System.err.println("       --nochunk       Force single-request download");
            System.err.println("       --chunksize <MB> Override chunk size (default: 10)");
            System.exit(1);
        }

        // Step 1: Get file info for size and name
        long fileSize = -1;
        String serverFilename = null;
        try {
            String infoPath = "/cass/getfileinfo.fn?md5=" + HttpSession.encode(md5);
            HttpResponse<String> infoResponse = session.get(infoPath, !noAuth);
            String infoBody = infoResponse.body().trim();

            if (infoBody.startsWith("{")) {
                // Parse file_size
                Matcher sizeMatcher = Pattern.compile("\"file_size\"\\s*:\\s*(\\d+)").matcher(infoBody);
                if (sizeMatcher.find()) {
                    fileSize = Long.parseLong(sizeMatcher.group(1));
                }
                // Parse name
                Matcher nameMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(infoBody);
                if (nameMatcher.find()) {
                    serverFilename = nameMatcher.group(1);
                }
            }
        } catch (Exception e) {
            // If fileinfo fails, fall back to direct download
            System.err.println("Warning: Could not get file info, using direct download (" + e.getMessage() + ")");
        }

        // Use server filename if none provided
        if (filename == null && serverFilename != null) {
            filename = serverFilename;
        }

        // Derive extension from filename if not explicitly set
        if (ext == null && filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf(".") + 1);
        }

        // Build base path
        StringBuilder basePath = new StringBuilder("/cass/getfile.fn?sNamer=" + HttpSession.encode(md5));
        if (filename != null) {
            basePath.append("&sFileName=").append(HttpSession.encode(filename));
        }
        if (ext != null) {
            basePath.append("&sFileExt=").append(HttpSession.encode(ext));
        }

        // Determine output file path
        File outFile = resolveOutputFile(output, filename, md5, ext);

        // Step 2: Download
        boolean useChunked = !noChunk && fileSize > chunkSize;

        if (useChunked) {
            chunkedDownload(session, basePath.toString(), outFile, fileSize, chunkSize, !noAuth);
        } else {
            directDownload(session, basePath.toString(), outFile, md5, !noAuth);
        }
    }

    private void directDownload(HttpSession session, String path, File outFile, String md5, boolean auth) throws Exception {
        HttpResponse<byte[]> response = session.getBytes(path, auth);
        byte[] body = response.body();

        // Detect server error: if body is just the MD5 hash, the file was not found
        if (body.length < 100) {
            String bodyStr = new String(body).trim();
            if (bodyStr.equals(md5)) {
                System.err.println("Error: File not found on server (md5: " + md5 + ")");
                System.exit(1);
            }
        }

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(body);
        }
        System.out.println("Saved: " + outFile.getAbsolutePath() + " (" + formatSize(body.length) + ")");
    }

    private void chunkedDownload(HttpSession session, String basePath, File outFile, long totalSize, long chunkSize, boolean auth) throws Exception {
        int totalChunks = (int) Math.ceil((double) totalSize / chunkSize);
        long startTime = System.currentTimeMillis();
        long downloaded = 0;

        System.out.println("Downloading " + formatSize(totalSize) + " in " + totalChunks + " chunks (" + formatSize(chunkSize) + " each)");

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            for (int i = 0; i < totalChunks; i++) {
                long offset = (long) i * chunkSize;
                String chunkPath = basePath + "&filechunk_size=" + chunkSize + "&filechunk_offset=" + offset;

                byte[] chunkData = downloadChunkWithRetry(session, chunkPath, auth, i + 1, totalChunks);
                fos.write(chunkData);

                downloaded += chunkData.length;
                long elapsed = System.currentTimeMillis() - startTime;
                double speedMBps = elapsed > 0 ? (downloaded / (1024.0 * 1024.0)) / (elapsed / 1000.0) : 0;
                int pct = (int) ((downloaded * 100) / totalSize);

                System.out.print("\rDownloading: " + pct + "% (" + formatSize(downloaded) + "/" + formatSize(totalSize) + ") "
                        + String.format("%.1f MB/s", speedMBps) + " chunk " + (i + 1) + "/" + totalChunks + "    ");
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double elapsedSec = elapsed / 1000.0;
        double speedMBps = elapsedSec > 0 ? (downloaded / (1024.0 * 1024.0)) / elapsedSec : 0;

        System.out.println();
        System.out.println("Saved: " + outFile.getAbsolutePath() + " (" + formatSize(downloaded) + ") in "
                + String.format("%.1fs", elapsedSec) + " (" + String.format("%.1f MB/s", speedMBps) + ")");
    }

    private byte[] downloadChunkWithRetry(HttpSession session, String chunkPath, boolean auth, int chunkNum, int totalChunks) throws Exception {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<byte[]> response = session.getBytes(chunkPath, auth);

                if (response.statusCode() == 502 && attempt < MAX_RETRIES) {
                    long delay = RETRY_DELAY_MS * (1L << attempt);
                    System.out.println("\nChunk " + chunkNum + "/" + totalChunks + ": 502 error, retrying in " + delay + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                    Thread.sleep(delay);
                    continue;
                }

                if (response.statusCode() != 200) {
                    throw new IOException("Server returned status " + response.statusCode() + " for chunk " + chunkNum);
                }

                return response.body();
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_DELAY_MS * (1L << attempt);
                    System.out.println("\nChunk " + chunkNum + "/" + totalChunks + ": " + e.getMessage() + ", retrying in " + delay + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                    Thread.sleep(delay);
                } else {
                    throw new IOException("Failed to download chunk " + chunkNum + " after " + MAX_RETRIES + " attempts", e);
                }
            }
        }
        throw new IOException("Failed to download chunk " + chunkNum); // should not reach
    }

    private File resolveOutputFile(String output, String filename, String md5, String ext) {
        String saveName = filename != null ? filename : md5 + (ext != null ? "." + ext : "");
        if (output != null) {
            File outPath = new File(output);
            if (outPath.isDirectory()) {
                return new File(outPath, saveName);
            }
            return outPath;
        }
        return new File(saveName);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
