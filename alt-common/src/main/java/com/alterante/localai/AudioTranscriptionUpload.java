package com.alterante.localai;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class AudioTranscriptionUpload {
    public static void main(String[] args) throws Exception {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        String LINE_FEED = "\r\n";
        String charset = "UTF-8";
        String filePath = "audio.aac"; // Update path

        URL url = new URL("http://localhost:8080/v1/audio/transcriptions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(conn.getOutputStream())) {
            // File part
            request.writeBytes("--" + boundary + LINE_FEED);
            request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + new File(filePath).getName() + "\"" + LINE_FEED);
            request.writeBytes("Content-Type: video/mp4" + LINE_FEED);
            request.writeBytes(LINE_FEED);
            Files.copy(new File(filePath).toPath(), request);
            request.writeBytes(LINE_FEED);

            // Model part
            request.writeBytes("--" + boundary + LINE_FEED);
            request.writeBytes("Content-Disposition: form-data; name=\"model\"" + LINE_FEED);
            request.writeBytes(LINE_FEED);
            request.writeBytes("whisper-base" + LINE_FEED);

            // End boundary
            request.writeBytes("--" + boundary + "--" + LINE_FEED);
            request.flush();
        }

        // Read response
        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine).append("\n");
        }
        in.close();

        System.out.println("Response code: " + responseCode);
        System.out.println("Response: " + response);
    }
}