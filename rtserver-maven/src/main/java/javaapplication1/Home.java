/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package javaapplication1;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import processor.DatabaseEntry;
import processor.RecordStats;
import utils.Cass7Funcs;
import utils.Node;

/**
 *
 * @author Andres
 */
public class Home {
    
    public static String getWelcomeDiv(String name, int connectedUsers,String backupinfo){
        String html = "";
        
        boolean showBackupInProgress = false;
        String[] backup = backupinfo.split(",");
        if (backup.length == 3){
            if (!backup[1].trim().equals(backup[2].trim())){
                showBackupInProgress = true;
            }
        }
        
        html += "<div class=\"header-home\"> ";
        html += "<div style=\" padding: 0.5em; padding-left: 180px;overflow: auto;\">";
        html += "<div style=\"float: right\"><button href=\"#\" onclick=\"scan_async(); return false;\" class=\"btn btn-primary\" >Refresh</button></div>";
        html += "<div style=\"float: left\">Welcome back, "+name+"</div>";
        html += "</div>";
        html += "   <img src=\"logo-molecula.png\" style=\"left: 20px;  margin-top: -20px; width: 163px; height: 105px; position: absolute\"  width=\"163px\" height=\"105px\" />";
        html += "</div> ";
        html += "<div class=\"content-home\" style=\"margin-bottom: 1em;background-color: rgb(204,204,204); \">";
        
        html += "   <div style=\" padding: 10px; padding-left: 180px; \">"
                + getUnreadAlerts()
                + getNetworkStatus(connectedUsers-1)
                + getScanIndexStatus();
        if (showBackupInProgress){
            html += getBackupStatus();
        }
        html += "</div> ";
        html += "</div>";
        return html;
    }
    
    public static String getAlertsDiv(){
        String html = "";
        html += " <div class=\"accordion\" id=\"accordion1\">\n" +
                "  <div class=\"accordion-group\">\n" +
                "    <div class=\"accordion-heading header-home\">\n" +
                "      <a class=\"accordion-toggle\" style=\"color: #fff; text-decoration: none;\"  data-toggle=\"collapse\" data-parent=\"#accordion1\" href=\"#collapseAlerts\">\n" +
                "        Alerts\n" +
                "      </a>\n" +
                "    </div>\n" +
                "    <div id=\"collapseAlerts\" class=\"accordion-body collapseAlerts in\">\n" +
                "      <div class=\"accordion-inner content-home\">\n" +
                "        " +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>" +
                "</div> ";
        return html;
    }
    
    public static String getNetworkUsersDiv(){
        String html = "";
        html += " <div class=\"accordion\" id=\"accordion2\">\n" +
                "  <div class=\"accordion-group\">\n" +
                "    <div class=\"accordion-heading header-home\">\n" +
                "      <a class=\"accordion-toggle\" style=\"color: #fff; text-decoration: none;\"  data-toggle=\"collapse\" data-parent=\"#accordion2\" href=\"#collapseNetwork\">\n" +
                "        Network & Users\n" +
                "      </a>\n" +
                "    </div>\n" +
                "    <div id=\"collapseNetwork\" class=\"accordion-body collapseNetwork in\">\n" +
                "      <div class=\"accordion-inner content-home\">\n" +
                "        " +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>" +
                "</div> ";
        return html;
    }
    
    public static String getFileScanIndexDiv(){
        String html = "";
        html += " <div class=\"accordion\" id=\"accordion3\">\n" +
                "  <div class=\"accordion-group\">\n" +
                "    <div class=\"accordion-heading header-home\">\n" +
                "      <a class=\"accordion-toggle\" style=\"color: #fff; text-decoration: none;\"  data-toggle=\"collapse\" data-parent=\"#accordion3\" href=\"#collapseScan\">\n" +
                "        File Scan & Index\n" +
                "      </a>\n" +
                "    </div>\n" +
                "    <div id=\"collapseScan\" class=\"accordion-body collapseScan in\">\n" +
                "      <div class=\"accordion-inner content-home\" style=\"overflow: auto;\">\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>" +
                "</div> ";
        return html;
    }
    
    public static String getUnreadAlerts(){
        return "<div class=\"line-content\" style=\"background-color: rgb(230,230,230);\">You have 0 unread alert</div>";
    }
    
    public static String getNetworkStatus(int connectedUsers){
        return "<div class=\"line-content\" style=\"background-color: rgb(238,238,238);\">There are "+connectedUsers+" other users connected to your Server</div>";
    } 
    
    public static String getScanIndexStatus(){
        return "<div class=\"line-content\" style=\"background-color: rgb(230,230,230);\">Scan is in progress</div>";
    }
    
    public static String getBackupStatus(){
        return "<div class=\"line-content\" style=\"background-color: rgb(238,238,238);\">Backup is in progress</div>";
    }
    
    public static String getScanIndexInfo(int idxfiles, int batches, ArrayList<Node> nodes){
        String html = "";
        float percentage = 100 - ((idxfiles * 100) / (float) batches);
        ArrayList<File> incomingAll = getIncomingFiles();
        html += " <div> ";
            html += "<div style=\"overflow: auto;\">";
            html += "<div class=\"percentage-index\">"
                    + "<div><b>Index</b></div>"
                    + "<div class=\"progress\">\n" +
                    "   <div class=\"bar bar-info\" style=\"width: "+percentage+"%;\"></div>\n" +
                    "</div>"
                    + "<div style=\"margin-top: -1em\">"+String.format("%.1f", percentage)+"% complete</div>"
                    + "</div>";
            html += "<div class=\"column-scan\"><b>IDX files</b><br>"+idxfiles+"</div>";
            html += "<div class=\"column-scan\"><b>Batches</b><br>"+batches+"</div>";
            html += "<div class=\"column-scan\"><b>Incoming files</b><br>"+incomingAll.size()+"</div>";
            html += "</div>";
            
            html += "<div class=\"nodes-scan-div\">";
            
            Cass7Funcs c7 = new Cass7Funcs();
            c7.loadFileExtensions();
            for (Node n : nodes) {
                ArrayList<DatabaseEntry> incomingFiles = getIncomingFilesNode(n.getUuid(),incomingAll);
                html += "<div class=\"node-scan\">"
                        + "<div class=\"node-scan-img-div\"><div class=\"node-scan-img\" style=\"float: left;\"><font class='jquerycursorpointer'  style=\" font-family: 'My Font'; font-size:" + 40 + "px\">" + "&#xe161" + "</font></div></div>";
                html += "  <div class=\"node-scan-info\" style=\"float: left;\"><b>"+n.getName()+"</b><br>"+n.getIp()+":"+n.getPort()+"</div>";
                html += " <div class=\"node-enqueue\">Enqueue files<br><b>"+incomingFiles.size()+"</b></div>";
                html += " <div class=\"node-bar\">";
                int i = 0;
                for (DatabaseEntry fde : incomingFiles) {
                    if (i < 10){
                        if ((fde != null) && (fde.dbe_img_thumbnail != null)){

                            html += "<div class=\"node-bar-element\">";
                            html += "<img style=' position: absolute; margin: auto;top: 0;left: 0;right: 0;bottom: 0;' height='80px' width='80px' id='base64image'  src='data:image/jpeg;base64, "+utils.Base64.encodeToString(fde.dbe_img_thumbnail, false)+"' />";
                            html += "</div>";
                        }
                        else {
                            if (fde != null){
                                html += "<div class=\"node-bar-element\" style=\"display:table;text-align: center; padding-top: 5px;\">"+c7.get_vector(fde.dbe_filename,"30")+"</div>";
                            }
                            else {
                                html += "<div class=\"node-bar-element\"></div>";
                            }
                        }
                    }
                    i++;
                }
                html += "</div>";
                html += "</div>";
            }
            html += "</div>";
        html += " </div> ";
        
        return html;
    }
    
     public static ArrayList<File> getIncomingFiles(){
        File f = new File("incoming");
        ArrayList<File> incoming = new ArrayList<File>();
        if (f.exists()) {
            File[] files = f.listFiles();       
            for (File f2: files) {
                
                if ((!f2.getName().toLowerCase().endsWith(".txt")) && (!f2.getName().toLowerCase().endsWith(".tmp"))){
                    incoming.add(f2);
                }
            }
            
        } 
        return incoming;
    }
    
    public static ArrayList<DatabaseEntry> getIncomingFilesNode(String node, ArrayList<File> incomingAll){
        ArrayList<DatabaseEntry> incoming = new ArrayList<DatabaseEntry>();

        for (File f2: incomingAll) {
            if (f2.getName().startsWith(node)){

                DatabaseEntry file_record = null;
                try {
                    FileInputStream fileIn = new FileInputStream(f2);
                    ObjectInputStream in = new ObjectInputStream(fileIn);
                    file_record = (DatabaseEntry) in.readObject();
                    in.close();
                } catch (OutOfMemoryError oome) {

                } catch (Exception e) {
                    e.printStackTrace();
                }

                incoming.add(file_record);
            }
        }
            
        return incoming;
    }
   

    
}
