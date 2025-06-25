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
import utils.NetUtils;


public class FfmpegExecutor {
    
    
    
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
                System.out.println(logFile.getCanonicalPath());

                logwriter = new BufferedWriter(new FileWriter(logFile));

                
                if (_isWindows) {
                    String ffmpegexePath = _projectsFolderPath + "..\\ffmpeg.exe";

                    File ffmpegexeFile = new File(ffmpegexePath);
                    
                    File _outputthumbFile = new File(_outputfolderPath + "/thumbnail.jpg");

                    List<String> arguments = getCommandWin("\\..\\ffmpeg_win.txt", _projectsFolderPath, ffmpegexeFile, _input, outputm3u8File, _md5, outputtsFile, _outputthumbFile);
                    
                    System.out.println("----calling ffmpeg------");
                    System.out.println(arguments.toString());
                    System.out.println("----calling ffmpeg[end]------");
                    
                    Process proc = new ProcessBuilder(arguments).redirectErrorStream(true).start();
                    
                    BufferedReader stdInput2 = new BufferedReader(new InputStreamReader(proc.getInputStream()));    

                    BufferedReader stdError2 = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

                    String s2 = null;
                    while ((s2 = stdInput2.readLine()) != null) {
                        logwriter.write("[O]" + s2 + "\n");
                        System.out.println("[O]" + s2);
                    }

                    while ((s2 = stdError2.readLine()) != null) {
                        logwriter.write("[E]" + s2 + "\n");
                        System.out.println("[E]" + s2);
                    }
                    
                    List<String> arguments2 = getCommandWin("\\..\\ffmpeg_win_thumb.txt", _projectsFolderPath, ffmpegexeFile, _input, outputm3u8File, _md5, outputtsFile, _outputthumbFile);
                    
                    System.out.println("----calling ffmpeg--thumbnail----");
                    System.out.println(arguments2.toString());
                    System.out.println("----calling ffmpeg--thumnail---[end]------");
                    
                    Process proc2 = new ProcessBuilder(arguments2).redirectErrorStream(true).start();
                    
                    BufferedReader stdInput3 = new BufferedReader(new InputStreamReader(proc2.getInputStream()));    

                    BufferedReader stdError3 = new BufferedReader(new InputStreamReader(proc2.getErrorStream()));

                    String s3 = null;
                    while ((s3 = stdInput3.readLine()) != null) {
                        logwriter.write("[O]" + s3 + "\n");
                        System.out.println("[O]" + s3);
                    }

                    while ((s3 = stdError3.readLine()) != null) {
                        logwriter.write("[E]" + s3 + "\n");
                        System.out.println("[E]" + s3);
                    }
                    
                
                }else{//mac
                    String ffmpegexePath = _projectsFolderPath + "/../"  + "ffmpeg";
                    
                    File ffmpegexeFile = new File(ffmpegexePath);
                    File _outputthumbFile = new File(_outputfolderPath + "/thumbnail.jpg");
                    
                    String command = getCommandMac(_projectsFolderPath, ffmpegexeFile, _input, outputm3u8File, _md5, outputtsFile, _outputthumbFile);
                    
//                    String pp = ffmpegexePath + " -y -ss 0 -i " + "'" + _input.getPath() + "' -sn -c copy -map 0:0 -map 0:1 -codec:v libx264 -vf scale=-2:320 -codec:a libvo_aacenc -ac 2 -f ssegment -segment_list '"+ outputm3u8File.getCanonicalPath() +
//                            "' -segment_time 8 -segment_list_entry_prefix '/getts.fn?md5=" + _md5 + "&ts=' '" + outputtsFile.getCanonicalPath() + "'";                                    
                                    
                    String scriptName = destinationPathFile.getCanonicalPath() + "/ffmpegscript.sh";
                    FileWriter scriptFile = new FileWriter(scriptName, false);

                    scriptFile.write(command + "\n");
                    scriptFile.close();

                    System.out.println(command);

                    Process p = null;
                    BufferedReader stdError = null;
                    BufferedReader stdOutput = null;
                    String s = null;

                    p = Runtime.getRuntime().exec("chmod +x " + scriptName);

                    stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                            s = null;

                    while ((s = stdError.readLine()) != null) {
                        logwriter.write("[E]" + s + "\n");
                        System.out.println("[E]" + s);
                    }

                    stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    while ((s = stdOutput.readLine()) != null) {
                        System.out.println("[O]" + s);
                        logwriter.write("[O]" + s + "\n");
                    }

                    p = Runtime.getRuntime().exec(scriptName);
                    System.out.println(outputm3u8File.getCanonicalPath());

                    stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                    s = null;

                    while ((s = stdError.readLine()) != null) {
                        logwriter.write("[E]" + s + "\n");
                        System.out.println("[E]" + s);
                    }

                    stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    while ((s = stdOutput.readLine()) != null) {
                        System.out.println("[O]" + s);
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

    private String getCommandMac(String _projectsFolderPath, File ffmpegexeFile, File _input, File outputm3u8File, String _md5, File outputtsFile, File outputthumbnailFile) throws UnsupportedEncodingException, IOException {
        String ffmpegtxtPath = _projectsFolderPath + "/../ffmpeg.txt";
        File ffmpegtxtFile = new File(ffmpegtxtPath);
        byte[] encoded = Files.readAllBytes(Paths.get(ffmpegtxtFile.getCanonicalPath()));
        String commandFile = new String(encoded, "UTF-8");
        String command = "nice -n 20 " + commandFile.trim();
        command = command.replace("$$ffmpegexePath$$", "'" + ffmpegexeFile.getCanonicalPath() + "'");
        command = command.replace("$$inputPath$$", "'" + _input.getPath() + "'");
        command = command.replace("$$outputm3u8FilePath$$", "'" + outputm3u8File.getCanonicalPath() + "'");
        String clusteridUUIDPath = "../scrubber/data/clusterid";
        String clusteridUUID = NetUtils.getUUID(clusteridUUIDPath);
        command = command.replace("$$prefix$$", "'" + "/getts.fn?md5=" + _md5 +"&multiclusterid="+clusteridUUID+ "&ts=" + "'");
        command = command.replace("$$outputtsFile$$", "'" + outputtsFile.getCanonicalPath() + "'");
        command = command.replace("$$thumbFile$$", "'" + outputthumbnailFile.getCanonicalPath() + "'");

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
        
        System.out.println("-----getCommandWin------");
        System.out.println("outputm3u8File   : '" + outputm3u8File.getCanonicalPath()+ "'");
        System.out.println("outputtsFile     : '" + outputtsFile.getCanonicalPath() + "'");
        System.out.println("thumbfile        : '" + outputthumbnailFile.getCanonicalPath() + "'");
        System.out.println("-----getCommandWin-end-----");
        
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
