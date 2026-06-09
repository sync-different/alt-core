package processor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import utils.Appendage;
import utils.NetUtils;
import utils.sURLPack;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

public class FfmpegExecutor {

    static String appendage = "";
    static String appendageRW = "";

    // BEGIN ANSI
    static boolean bConsole = true;

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";

    protected static void pw(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.FfmpegExecutor-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.FfmpegExecutor-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.FfmpegExecutor_" + threadID + "] " + s);
    }

    // END ANSI
    
    public void execute(File _input, String _md5, String _outputfolderPath, String _projectsFolderPath, boolean _isWindows){
        BufferedWriter logwriter = null;
    
        try {
                
                //String streamingFolderPath = _alt_path + "\\rtserver\\streaming\\";

                String outputm3u8Path;
                if(_isWindows){
                    outputm3u8Path = _outputfolderPath + "\\OUTPUT.m3u8";
                }else{
                    outputm3u8Path = _outputfolderPath + "/OUTPUT.m3u8";
                }
                
                File outputm3u8File = new File(outputm3u8Path);
                
                
                String outputtsPath;
                if(_isWindows){
                    outputtsPath = _outputfolderPath + "\\OUTPUT-%05d.ts";
                }else{
                    outputtsPath = _outputfolderPath + "/OUTPUT-%05d.ts";
                }
                
                File outputtsFile = new File(outputtsPath);
                             
                
                String destinationPath;
                if (_isWindows) {
                    destinationPath = _projectsFolderPath + "\\rtserver\\streaming\\"+ _md5;
                }else{
                    destinationPath = _projectsFolderPath + "/rtserver/streaming/"+ _md5;
                }
                
                File destinationPathFile = new File(destinationPath);
                destinationPathFile.mkdirs();
                            

                String logFilePath;
                if (_isWindows) {
                    logFilePath = _projectsFolderPath + "\\rtserver\\streaming\\"+ _md5 + "\\log.txt";
                }else{
                    logFilePath = _projectsFolderPath + "/rtserver/streaming/"+ _md5 + "/log.txt";
                }
                File logFile = new File(logFilePath);
                p(logFile.getCanonicalPath());

                logwriter = new BufferedWriter(new FileWriter(logFile));

                
                if (_isWindows) {
                    String ffmpegexePath = _projectsFolderPath + "ffmpeg.exe";

                    File ffmpegexeFile = new File(ffmpegexePath);

                    File _outputthumbFile = new File(_outputfolderPath + "/thumbnail.jpg");

                    List<String> arguments = getCommandWin("\\ffmpeg_win.txt", _projectsFolderPath, ffmpegexeFile, _input, outputm3u8File, _md5, outputtsFile, _outputthumbFile);
                    
                    p("----calling ffmpeg------");
                    p(arguments.toString());
                    p("----calling ffmpeg[end]------");
                    
                    Process proc = new ProcessBuilder(arguments).redirectErrorStream(true).start();
                    
                    BufferedReader stdInput2 = new BufferedReader(new InputStreamReader(proc.getInputStream()));    

                    BufferedReader stdError2 = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

                    String s2 = null;
                    while ((s2 = stdInput2.readLine()) != null) {
                        logwriter.write("[O]" + s2 + "\n");
                        p("[O]" + s2);
                    }

                    while ((s2 = stdError2.readLine()) != null) {
                        logwriter.write("[E]" + s2 + "\n");
                        p("[E]" + s2);
                    }
                    
                    List<String> arguments2 = getCommandWin("\\ffmpeg_win_thumb.txt", _projectsFolderPath, ffmpegexeFile, _input, outputm3u8File, _md5, outputtsFile, _outputthumbFile);
                    
                    p("----calling ffmpeg--thumbnail----");
                    p(arguments2.toString());
                    p("----calling ffmpeg--thumnail---[end]------");
                    
                    Process proc2 = new ProcessBuilder(arguments2).redirectErrorStream(true).start();
                    
                    BufferedReader stdInput3 = new BufferedReader(new InputStreamReader(proc2.getInputStream()));    

                    BufferedReader stdError3 = new BufferedReader(new InputStreamReader(proc2.getErrorStream()));

                    String s3 = null;
                    while ((s3 = stdInput3.readLine()) != null) {
                        logwriter.write("[O]" + s3 + "\n");
                        p("[O]" + s3);
                    }

                    while ((s3 = stdError3.readLine()) != null) {
                        logwriter.write("[E]" + s3 + "\n");
                        p("[E]" + s3);
                    }
                    
                
                }else{//mac
                    String ffmpegexePath = _projectsFolderPath + "/ffmpeg";
                    
                    File ffmpegexeFile = new File(ffmpegexePath);
                    File _outputthumbFile = new File(_outputfolderPath + "/thumbnail.jpg");
                    File _outputtAudioFile = new File(_outputfolderPath + "/audio.aac");
                    File _outputAudioJSOnFile = new File(_outputfolderPath + "/audio.json");

                    String command = getCommandMac(_projectsFolderPath, ffmpegexeFile, _input, outputm3u8File, _md5, outputtsFile, _outputthumbFile, _outputtAudioFile,_outputAudioJSOnFile);
                    
//                    String pp = ffmpegexePath + " -y -ss 0 -i " + "'" + _input.getPath() + "' -sn -c copy -map 0:0 -map 0:1 -codec:v libx264 -vf scale=-2:320 -codec:a libvo_aacenc -ac 2 -f ssegment -segment_list '"+ outputm3u8File.getCanonicalPath() +
//                            "' -segment_time 8 -segment_list_entry_prefix '/getts.fn?md5=" + _md5 + "&ts=' '" + outputtsFile.getCanonicalPath() + "'";                                    
                                    
                    String scriptName = destinationPathFile.getCanonicalPath() + "/ffmpegscript.sh";
                    FileWriter scriptFile = new FileWriter(scriptName, false);

                    scriptFile.write(command + "\n");
                    scriptFile.close();

                    p(command);

                    Process p = null;
                    BufferedReader stdError = null;
                    BufferedReader stdOutput = null;
                    String s = null;

                    p = new ProcessBuilder("chmod", "+x", scriptName).start();

                    stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                            s = null;

                    while ((s = stdError.readLine()) != null) {
                        logwriter.write("[E]" + s + "\n");
                        p("[E]" + s);
                    }

                    stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    while ((s = stdOutput.readLine()) != null) {
                        p("[O]" + s);
                        logwriter.write("[O]" + s + "\n");
                    }

                    p = new ProcessBuilder(scriptName).start();
                    p(outputm3u8File.getCanonicalPath());

                    stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                    s = null;

                    while ((s = stdError.readLine()) != null) {
                        logwriter.write("[E]" + s + "\n");
                        p("[E]" + s);
                    }

                    stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    while ((s = stdOutput.readLine()) != null) {
                        p("[O]" + s);
                        logwriter.write("[O]" + s + "\n");
                    }
                    
                }
            } catch (IOException ex) {
                Logger.getLogger(FfmpegExecutor.class.getName()).log(Level.SEVERE, null, ex);
            }finally {
                try {
                    logwriter.close();
                } catch (Exception e) {
                }
            }

    }

    /**
     * POSIX-safe single-quote a value for inclusion in the generated ffmpegscript.sh.
     * Wraps in single quotes and escapes any embedded single quote as '\'' (close-quote,
     * literal-quote, reopen-quote). This makes ALL shell metacharacters inside the value
     * inert ('`$;|&()<>* etc.) — closing the FfmpegExecutor filename command-injection
     * (RCE-FINDINGS Finding 3 / smoke phase 14): a video uploaded as v'`touch x`'.mp4 can
     * no longer break out of the quoting. Use for EVERY interpolated value, not just the
     * attacker-controllable input path, as defense in depth.
     *
     * NOTE on Matcher.quoteReplacement: replaceAll/replace treat '\' and '$' specially in the
     * REPLACEMENT string, so we build the command with explicit replace() of literal tokens and
     * quoteReplacement-guarded values to avoid a second injection via the replacement mechanics.
     */
    private static String shq(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String getCommandMac(String _projectsFolderPath, File ffmpegexeFile, File _input, File outputm3u8File, String _md5, File outputtsFile, File outputthumbnailFile,File outputAudioFile,File outputAudioJsonFile) throws UnsupportedEncodingException, IOException {
        String ffmpegtxtPath = _projectsFolderPath + "/ffmpeg.txt";
        File ffmpegtxtFile = new File(ffmpegtxtPath);
        byte[] encoded = Files.readAllBytes(Paths.get(ffmpegtxtFile.getCanonicalPath()));
        String commandFile = new String(encoded, "UTF-8");
        String command = "nice -n 20 " + commandFile.trim();
        // SECURITY: every interpolated value is POSIX-single-quote-escaped via shq() so a quote/
        // metachar in any path (esp. the uploaded filename in _input) cannot break out of the
        // shell quoting. quoteReplacement() guards against '\'/'$' in the replacement string.
        command = command.replace("$$ffmpegexePath$$", shq(ffmpegexeFile.getCanonicalPath()));
        command = command.replace("$$inputPath$$", shq(_input.getPath()));
        command = command.replace("$$outputm3u8FilePath$$", shq(outputm3u8File.getCanonicalPath()));

            Appendage app = new Appendage();
            appendage = app.getAppendage();
            appendageRW = app.getAppendageRW();
        String clusteridUUIDPath = appendage + "../scrubber/data/clusterid";
        p("[ffmpegexecutor] clusteridUUIDPath: " + clusteridUUIDPath);
        String clusteridUUID = NetUtils.getUUID(clusteridUUIDPath);
        command = command.replace("$$prefix$$", shq("/getts.fn?md5=" + _md5 + "&multiclusterid=" + clusteridUUID + "&ts="));
        command = command.replace("$$outputtsFile$$", shq(outputtsFile.getCanonicalPath()));
        command = command.replace("$$thumbFile$$", shq(outputthumbnailFile.getCanonicalPath()));
        command = command.replace("$$outputPathAudio$$", shq(outputAudioFile.getCanonicalPath()));
        command = command.replace("$$outputTextJson$$", shq(outputAudioJsonFile.getCanonicalPath()));
        command = command.replace("$$inputPathAudio$$", shq("@" + outputAudioFile.getCanonicalPath()));

        return command;
    }

    private List<String> getCommandWin(String _ffmpegFile, String _projectsFolderPath, File ffmpegexeFile, File _input, File outputm3u8File, String _md5, File outputtsFile, File outputthumbnailFile) throws UnsupportedEncodingException, IOException {
        String ffmpegtxtPath = _projectsFolderPath + _ffmpegFile;
        File ffmpegtxtFile = new File(ffmpegtxtPath);
        byte[] encoded = Files.readAllBytes(Paths.get(ffmpegtxtFile.getCanonicalPath()));
        String commandFile = new String(encoded, "UTF-8");
        String command = "cmd /C start /B /low " + commandFile.trim();
        List<String> finalarg = new ArrayList<String>();
        String[] args = command.split(" ");
        String clusteridUUIDPath = "../scrubber/data/clusterid";
        String clusteridUUID = NetUtils.getUUID(clusteridUUIDPath);
        
        p("-----getCommandWin------");
        p("outputm3u8File   : '" + outputm3u8File.getCanonicalPath()+ "'");
        p("outputtsFile     : '" + outputtsFile.getCanonicalPath() + "'");
        p("thumbfile        : '" + outputthumbnailFile.getCanonicalPath() + "'");
        p("-----getCommandWin-end-----");
        
        for (String arg : args) {
            if(arg.equals("$$ffmpegexePath$$")) arg = ffmpegexeFile.getCanonicalPath();
            if(arg.equals("$$inputPath$$")) arg = _input.getPath();
            if(arg.equals("$$outputm3u8FilePath$$")) arg = outputm3u8File.getCanonicalPath();
            if(arg.equals("$$prefix$$")) arg = "\"/getts.fn?md5=" + _md5 +"&multiclusterid="+clusteridUUID+ "&ts=\"";
            if(arg.equals("$$outputtsFile$$")) arg = outputtsFile.getCanonicalPath();
            if(arg.equals("$$thumbFile$$")) arg = outputthumbnailFile.getCanonicalPath();

            finalarg.add(arg);
        }

        return finalarg;
    }
    
    
}
