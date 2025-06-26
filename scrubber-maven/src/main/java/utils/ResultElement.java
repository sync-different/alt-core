/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package utils;

import java.util.ArrayList;
import static utils.Cass7Funcs.is_photo;

/**
 *
 * @author Andres
 */
public class ResultElement {
    
    private boolean bVector;
    private String sFilenameOri;
    private String sNamer;
    private String _password;
    private String sMode;
    private String _filetype;
    private String _daysback;
    private String _numcol;
    private String _numobj;
    private String sVector;
    private String sHeightNew;
    private String sWidthNew;
    private String srcPic;
    private String sHashLink;
    private String sViewURL2;
    private String sPlayURL;
    private String sSendURL2;
    private String sOpenURL2;
    private String sFolderURL2;
    private String sFileNameAux;
    private String sLocalCopies;
    private String sCopies;
    private String sNodes;
    private String sDate;
    private int row; 
    private int column;
    private String sWidth;
    private String sHeight;
    private boolean lastColumn;
    private int rowElements;
    private String sHeightOrig;
    private String sWidthOrig;
    
    private static final int space_between_elements = 2;

    public boolean isbVector() {
        return bVector;
    }

    public void setbVector(boolean bVector) {
        this.bVector = bVector;
    }

    public String getsFilenameOri() {
        return sFilenameOri;
    }

    public void setsFilenameOri(String sFilenameOri) {
        this.sFilenameOri = sFilenameOri;
    }

    public String getsNamer() {
        return sNamer;
    }

    public void setsNamer(String sNamer) {
        this.sNamer = sNamer;
    }

    public String getPassword() {
        return _password;
    }

    public void setPassword(String _password) {
        this._password = _password;
    }

    public String getsMode() {
        return sMode;
    }

    public void setsMode(String sMode) {
        this.sMode = sMode;
    }

    public String getFiletype() {
        return _filetype;
    }

    public void setFiletype(String _filetype) {
        this._filetype = _filetype;
    }

    public String getDaysback() {
        return _daysback;
    }

    public void setDaysback(String _daysback) {
        this._daysback = _daysback;
    }

    public String getNumcol() {
        return _numcol;
    }

    public void setNumcol(String _numcol) {
        this._numcol = _numcol;
    }

    public String getNumobj() {
        return _numobj;
    }

    public void setNumobj(String _numobj) {
        this._numobj = _numobj;
    }

    public String getsVector() {
        return sVector;
    }

    public void setsVector(String sVector) {
        this.sVector = sVector;
    }

    public String getsHeightNew() {
        return sHeightNew;
    }

    public void setsHeightNew(String sHeightNew) {
        this.sHeightNew = sHeightNew;
    }

    public String getsWidthNew() {
        return sWidthNew;
    }

    public void setsWidthNew(String sWidthNew) {
        this.sWidthNew = sWidthNew;
    }

    public String getSrcPic() {
        return srcPic;
    }

    public void setSrcPic(String srcPic) {
        this.srcPic = srcPic;
    }

    public String getsHashLink() {
        return sHashLink;
    }

    public void setsHashLink(String sHashLink) {
        this.sHashLink = sHashLink;
    }

    public String getsViewURL2() {
        return sViewURL2;
    }

    public void setsViewURL2(String sViewURL2) {
        this.sViewURL2 = sViewURL2;
    }

    public String getsPlayURL() {
        return sPlayURL;
    }

    public void setsPlayURL(String sPlayURL) {
        this.sPlayURL = sPlayURL;
    }

    public String getsSendURL2() {
        return sSendURL2;
    }

    public void setsSendURL2(String sSendURL2) {
        this.sSendURL2 = sSendURL2;
    }

    public String getsOpenURL2() {
        return sOpenURL2;
    }

    public void setsOpenURL2(String sOpenURL2) {
        this.sOpenURL2 = sOpenURL2;
    }

    public String getsFolderURL2() {
        return sFolderURL2;
    }

    public void setsFolderURL2(String sFolderURL2) {
        this.sFolderURL2 = sFolderURL2;
    }

    public String getsFileNameAux() {
        return sFileNameAux;
    }

    public void setsFileNameAux(String sFileNameAux) {
        this.sFileNameAux = sFileNameAux;
    }

    public String getsLocalCopies() {
        return sLocalCopies;
    }

    public void setsLocalCopies(String sLocalCopies) {
        this.sLocalCopies = sLocalCopies;
    }

    public String getsCopies() {
        return sCopies;
    }

    public void setsCopies(String sCopies) {
        this.sCopies = sCopies;
    }

    public String getsNodes() {
        return sNodes;
    }

    public void setsNodes(String sNodes) {
        this.sNodes = sNodes;
    }

    public String getsDate() {
        return sDate;
    }

    public void setsDate(String sDate) {
        this.sDate = sDate;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public String getsWidth() {
        return sWidth;
    }

    public void setsWidth(String sWidth) {
        this.sWidth = sWidth;
    }

    public String getsHeight() {
        return sHeight;
    }

    public void setsHeight(String sHeight) {
        this.sHeight = sHeight;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public boolean isLastColumn() {
        return lastColumn;
    }

    public void setLastColumn(boolean lastColumn) {
        this.lastColumn = lastColumn;
    }

    public int getRowElements() {
        return rowElements;
    }

    public void setRowElements(int rowElements) {
        this.rowElements = rowElements;
    }

    public String getsHeightOrig() {
        return sHeightOrig;
    }

    public void setsHeightOrig(String sHeightOrig) {
        this.sHeightOrig = sHeightOrig;
    }

    public String getsWidthOrig() {
        return sWidthOrig;
    }

    public void setsWidthOrig(String sWidthOrig) {
        this.sWidthOrig = sWidthOrig;
    }

    public ResultElement(boolean bVector,String sFilenameOri, String sNamer, String _password, String sMode, String _filetype, String _daysback, String _numcol, String _numobj, String sVector, String sHeight, String sWidth, String srcPic, String sHashLink, String sViewURL2, String sPlayURL, String sSendURL2, String sOpenURL2, String sFolderURL2, String sFileNameAux, String sLocalCopies, String sCopies, String sNodes, String sDate, int row, int column, String sWidthOrig, String sHeightOrig){
        this.bVector = bVector;
        this.sFilenameOri = sFilenameOri;
        this.sNamer = sNamer;
        this._password = _password;
        this.sMode = sMode;
        this._filetype = _filetype;
        this._daysback = _daysback;
        this._numcol = _numcol;
        this._numobj = _numobj;
        this.sVector = sVector;
        this.sHeight = sHeight;
        this.sWidth = sWidth;
        this.srcPic = srcPic;
        this.sHashLink = sHashLink;
        this.sViewURL2 = sViewURL2;
        this.sPlayURL = sPlayURL;
        this.sSendURL2 = sSendURL2;
        this.sOpenURL2 = sOpenURL2;
        this.sFolderURL2 = sFolderURL2;
        this.sFileNameAux = sFileNameAux;
        this.sLocalCopies = sLocalCopies;
        this.sCopies = sCopies;
        this.sNodes = sNodes;
        this.sDate = sDate;
        this.row = row;
        this.column = column;
        this.sWidthOrig = sWidthOrig;
        this.sHeightOrig = sHeightOrig;
    }
    
    public void resize(ArrayList<ResultElement> elements, float screenWidth, int numRows){
        ArrayList<ResultElement> sameRow = new ArrayList<ResultElement>();
        float rowWidth = 0;
        for (ResultElement e : elements){
            if (e.getRow() == this.row){
                sameRow.add(e);
            }
        }
        for (ResultElement e : sameRow){
            rowWidth += Float.parseFloat(e.getsWidth());
        }
        rowWidth += ((sameRow.size()-1)*space_between_elements);
        
        this.rowElements = sameRow.size();
        
        if (row != numRows){

            float whiteSpace = screenWidth - rowWidth;
            float factor = 1;
            
            if (whiteSpace > 0){
                factor = screenWidth / (screenWidth - whiteSpace);
                
                this.sWidthNew = (Math.floor(Float.parseFloat(sWidth)*factor))+"";
                if (column == sameRow.size()){
                    this.lastColumn = true;
                    if (((((Float.parseFloat(sWidth)*factor) - Math.floor(Float.parseFloat(sWidth)*factor))) > 0)){
                        this.sWidthNew = (Float.parseFloat(sWidthNew) + 1)+""; 
                    }
                } else {
                    this.lastColumn = false;
                }
                this.sHeightNew = (Math.floor(Float.parseFloat(sHeight)*factor))+"";
                //this.sHeightNew = sHeight+"";
            }
            else {
                this.sWidthNew = sWidth;
                this.sHeightNew = sHeight;
            }
        }
        else {
            this.sWidthNew = sWidth;
            this.sHeightNew = sHeight;
            if (column == sameRow.size()){
                this.lastColumn = true;
            } else {
                this.lastColumn = false;
            }
            
        }
    }
    
    public String getHtml(float screenWidth){
        String html = "";
        
        if (this.column == 1){
            html += "<div style=\"width: "+(screenWidth+(this.rowElements*4))+"px\">";
        }
        
        String sWidthImage = Math.round(Float.parseFloat(sWidthNew))+"";
        String sHeightImage = Math.round(Float.parseFloat(sHeightNew))+"";
        try{
            if (Float.parseFloat(sHeightNew) > Float.parseFloat(sHeightOrig)){
                sHeightImage = Math.round(Float.parseFloat(sHeightOrig))+"";
                sWidthImage = Math.round(Float.parseFloat(sWidthOrig))+"";
            }
        }
        catch (Exception e){}
        
        if (!bVector) {
            
         html += "<div row=\""+row+"\" style=' float: left; margin-left: "+space_between_elements+"px; margin-top: 2px; margin-bottom: 1px' "+"  onmouseout='hideButtonsTile(\""+sNamer+ "\", "+(bVector || !is_photo(sFilenameOri)? "true" : "false")+");' onmouseover='showButtonsTile(\""+sNamer+ "\", "+(bVector? "true" : "false")+")'><div class='jquerycursorpointer' id='div"+sNamer+"' hreff ='test4.php?foo=" + sNamer +
                                    "&pw=" + _password + 
                                    "&view=" + sMode + 
                                    "&ftype=" + _filetype +  
                                    "&days=" + _daysback + 
                                    "&numcol=" + _numcol +
                                    "&numobj=" + _numobj +
                                    "' ondblclick='link(this)' style='box-sizing:border-box;-moz-box-sizing:border-box;-webkit-box-sizing:border-box; z-index: -1;  background-color: rgb(204,204,204); text-align: center;height: "+sHeightNew+"px;width: "+sWidthNew+"px; display: table; text-align: center' ><div style='display:table-cell; vertical-align: middle'><img class='jquerycursorpointer' style='height:"+sHeightImage+"px; width: "+sWidthImage+"px'  src='" + srcPic + "'></div>";
                                       
                        } else {
                 html += "<div row=\""+row+"\" style=' float: left; margin-left: "+space_between_elements+"px; margin-top: 3px'  "+"onmouseout='hideButtonsTile(\""+sNamer+ "\", "+(bVector || !is_photo(sFilenameOri)? "true" : "false")+");' onmouseover='showButtonsTile(\""+sNamer+ "\", "+(bVector? "true" : "false")+")' ><div id='div"+sNamer+"' class='jquerycursorpointer'   hreff ='test4.php?foo=" + sNamer +
                                    "&pw=" + _password + 
                                    "&view=" + sMode + 
                                    "&ftype=" + _filetype +  
                                    "&days=" + _daysback + 
                                    "&numcol=" + _numcol +
                                    "&numobj=" + _numobj +
                                    "' ondblclick='link(this)' style='box-sizing:border-box;-moz-box-sizing:border-box;-webkit-box-sizing:border-box; z-index: -1;display:table; width: "+sWidthNew+"px; height: "+sHeightNew+"px; background-color: rgb(204,204,204); text-align: center' \"><span style=\"font-size: 0px\">&nbsp;</span>" + sVector + "";
                                     
                        }
        
        html += "</div> ";
        String menuElement = menu_element(sFilenameOri, sNamer,sHashLink, sViewURL2,  sPlayURL, sSendURL2, sOpenURL2, sFolderURL2, sFileNameAux, sLocalCopies, sCopies, sNodes, sDate, bVector, sWidthNew);
        html += menuElement;
        html += "</div>";
        
        if (this.lastColumn){
            html += "</div>";
        }
        return html;

    }
    
    private String menu_element(String sFilenameOri, String sNamer, String sHashLink, String sViewURL2, String sPlayURL, String sSendURL2, String sOpenURL2, String sFolderURL2, String sFileNameAux, String sLocalCopies, String sCopies, String sNodes, String sDate, boolean bVector, String sWidth){
        String html = "";

            
        html = "<div id='name"+sNamer+"' ondblclick=\"window.location.href=$('#div"+sNamer+"').attr('hreff')\" style=\""+(!bVector && is_photo(sFilenameOri)? "display: none;" : "")+" background-color: rgba(82, 82, 82, 0.7); text-align: center; z-index: 100; font-size: 11px; color: white;position: absolute; margin-top: -50px; width: "+sWidth+"px; overflow: hidden;  text-shadow: 0.1em 0.1em #333;\">"+sFileNameAux;
        
        //Number of copies
        html += "   <div>";

        html += "       <div id='numCopies"+sNamer+"' style='float: right;display: none; margin-right: 4px; margin-bottom: 2px'>";
        html +=              sLocalCopies + "&nbsp" + sCopies + "&nbsp" + sNodes;
        html += "       </div>";
        html += "   <div expCont='OFF'  id='tagsTbl"+sNamer+"' style='display: none; float: left;width:auto;overflow:hidden; width: 100%'>" + 
                        sHashLink + 
                        "</div>";
        html += "   </div>";
        html += "</div>";

        
        html += "<div id='select"+sNamer+"' ondblclick=\"window.location.href=$('#div"+sNamer+"').attr('hreff')\" style='box-sizing:border-box;-moz-box-sizing:border-box;-webkit-box-sizing:border-box;display: none; z-index: 90; margin-top: -"+sHeightNew+"px; position: relative; width: 100%;border:5px solid rgb(0,136,204); height: "+sHeightNew+"px; top: 0px'>";
        html += "   <input type ='checkbox' class='checkbox' style='display: none' name='" + sNamer + "'>";
        html += " <div style='float: right;width: 20px; height: 20px; background-color: rgb(0,136,204); padding-left: 2px'><i class=\"icon-ok icon-white\"></i></div>";
        html += "</div>";
        /*html += "<div  style='height: 1z-index: 100; margin-top: -200px; position: relative; display: none'><table  class='jquerycursorpointer'   style=' cursor:pointer;width=150px;text-overflow:ellipsis;white-space:nowrap; ' >"
                        + "<tr>" + 
                        "<td   ><input type ='checkbox' class='checkbox' style='' name='" + sNamer + "'></td>" + 
                        "</tr></table></div>";    */        
        
        html += "<script type='text/javascript'>";
        html += "posTagsDiv('"+sNamer+"', "+(!bVector && is_photo(sFilenameOri)? "false" : "true")+"); ";
        html += "posNameDiv('"+sNamer+"'); ";
        
        String onclickCheck = "   checkElement('"+sNamer+"');";
        onclickCheck += "   e.stopPropagation();";
        
        html += "$(\"#div"+sNamer+"\").click(function(e) { ";
        html += onclickCheck;
        html += "});";
        html += "$(\"#name"+sNamer+"\").click(function(e) { ";
        html += onclickCheck;
        html += "});";
        html += "$(\"#select"+sNamer+"\").click(function(e) { ";
        html += onclickCheck;
        html += "});";
        html += "</script>";
        
        String folderUp = sFolderURL2;
        String folderDown = "";
        String openUp = sOpenURL2;
        String openDown = "";
        boolean lineThree = false;
        if (!sSendURL2.equals("")){
            openUp = "";
            openDown = sOpenURL2;
            folderDown = sFolderURL2;
            folderUp = "";
            lineThree = true;     
        }

        html += "<div  style='z-index: 150;width: "+sWidth+"px; ' class='jqueryhidden'  id='bnts"+sNamer+"' >"+
                                "<div align=\"left\" style='margin-bottom: 5px; width: 100%;' >" +
                                    sViewURL2 +
                                    sPlayURL +
                                    sSendURL2 + 
                                    openUp +
                                    folderUp +
                                  "</div>"; 
                if (!openDown.equals("") || !folderDown.equals("")){
                                   html += "<div align=\"left\" style='margin-bottom: 10px; width: 100%;'>" +  
                                     openDown +
                                     folderDown +  
                                 "</div>"; 
                                            }
                                html += "</div>"; 
        if (lineThree){
            html += "<script type='text/javascript'>$('#bnts"+sNamer+"').find('button').css({'width': '45%'});</script>";
        }
        
        return html;
    }
}
