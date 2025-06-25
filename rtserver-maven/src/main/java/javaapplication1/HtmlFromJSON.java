package javaapplication1;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import utils.ResultElement;
import utils.sURLPack;

public class HtmlFromJSON {
    
    private HtmlFromJSON(){
        
    }
    private static String THUMBNAIL_OUTPUT_DIR = "../cass/pic";
    
    
    public static boolean checkifshow(String _filename, String _filetype) {
        
        //p("checkifshow = '" + _filetype + "' filename '" + _filename + "'");
        boolean bShow = false;
        int npos = _filename.toLowerCase().indexOf(_filetype.toLowerCase());
        //p("sFileNamepos = '" + npos + "'");
        if (npos > 1) bShow = true;
        
        if (_filetype.equals(".mov") && is_movie(_filename)) bShow = true;
        if (_filetype.equals(".mp3") && is_music(_filename)) bShow = true;
        if (_filetype.equals(".jpg") && is_photo(_filename)) bShow = true;
        if (_filetype.equals(".jpeg") && is_photo(_filename)) bShow = true;
        if (_filetype.equals(".png") && is_photo(_filename)) bShow = true;
        if (_filetype.equals(".gif") && is_photo(_filename)) bShow = true;
        if (_filetype.equals(".music") && is_music(_filename)) bShow = true;
        if (_filetype.equals(".photo") && is_photo(_filename)) bShow = true;
        if (_filetype.equals(".video") && is_movie(_filename)) bShow = true;
        if (_filetype.equals(".document") && is_document(_filename)) bShow = true;
        
        //p("bShow = " + bShow);
//            try {
//                Thread.sleep(100);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        return bShow;
    }
    
    private static boolean si_inline(String _sFileNameOri) {
        String sFileNameOri = _sFileNameOri.toLowerCase();
        return  sFileNameOri.endsWith(".pdf") || sFileNameOri.endsWith(".txt") || sFileNameOri.endsWith(".html")|| sFileNameOri.endsWith(".htm") ;
    }
    
    public static boolean is_document(String _string) {
        if (
                _string.toLowerCase().contains(".doc") ||
                _string.toLowerCase().contains(".ppt") ||
                _string.toLowerCase().contains(".xls") ||
                _string.toLowerCase().contains(".pdf")
                )
            return true;
        else
            return false;
    }
    
    public static boolean is_pdf(String _string) {
        if (
                _string.toLowerCase().contains(".pdf")
                )
            return true;
        else
            return false;
    }
    
    public static boolean is_photo(String _string) {
        if (
                _string.toLowerCase().contains(".jpg") ||
                _string.toLowerCase().contains(".png") ||
                _string.toLowerCase().contains(".gif") ||
                _string.toLowerCase().contains(".jpeg")
                )
            return true;
        else
            return false;
    }
    
    private static boolean is_office(String _string) {
        if (
                _string.toLowerCase().contains(".doc") ||
                _string.toLowerCase().contains(".docx") ||
                _string.toLowerCase().contains(".docm") ||
                _string.toLowerCase().contains(".dotx") ||
                _string.toLowerCase().contains(".dotm") ||
                _string.toLowerCase().contains(".xlsx") ||
                _string.toLowerCase().contains(".xls") ||
                _string.toLowerCase().contains(".xlsm") ||
                _string.toLowerCase().contains(".xltx") ||
                _string.toLowerCase().contains(".xltm") ||
                _string.toLowerCase().contains(".xlsb") ||
                _string.toLowerCase().contains(".xlam") ||
                _string.toLowerCase().contains(".pptx") ||
                _string.toLowerCase().contains(".ppt") ||
                _string.toLowerCase().contains(".pptm") ||
                _string.toLowerCase().contains(".potx") ||
                _string.toLowerCase().contains(".potm") ||
                _string.toLowerCase().contains(".ppam") ||
                _string.toLowerCase().contains(".ppsx") ||
                _string.toLowerCase().contains(".ppsm") ||
                _string.toLowerCase().contains(".sldx") ||
                _string.toLowerCase().contains(".sldm") ||
                _string.toLowerCase().contains(".thmx")
                )
            return true;
        else
            return false;
    }
    
    private static boolean is_musicToPlay(String _string) {
        //p("music test:" + _string);
        if (
                _string.toLowerCase().contains(".mp3") ||
                _string.toLowerCase().contains(".m4a"))
            return true;
        else
            return false;
    }
    
    public static boolean is_music(String _string) {
        //p("music test:" + _string);
        if (
                _string.toLowerCase().contains(".wma") ||
                _string.toLowerCase().contains(".mp3") ||
                _string.toLowerCase().contains(".m4a") ||
                _string.toLowerCase().contains(".m4p")
                )
            return true;
        else
            return false;
    }
    public static boolean is_movie(String _string) {
        if (
                _string.toLowerCase().contains(".avi") ||
                _string.toLowerCase().contains(".mov") ||
                _string.toLowerCase().contains(".mts") ||
                _string.toLowerCase().contains(".m4v") ||
                _string.toLowerCase().contains(".wmv") ||
                _string.toLowerCase().contains(".mp4") ||
                _string.toLowerCase().contains(".m2ts")
                )
            return true;
        else
            return false;
    }
    
    public static StringBuilder getHTML(String json, String _keyin, String _datestart, String _filetype, String _daysback, String _numobj, String _numcol, String _password, String sMode, String _screenSize , UserSession us, int _page, String _previousDate){
        
        String sFirst = "";
        String _key = _keyin.toLowerCase();
        
        JSONObject queryResponse =  (JSONObject)JSONValue.parse(json);
        JSONArray counters = (JSONArray)queryResponse.get("objFound");
        JSONObject filters = (JSONObject) counters.get(0);
        String cnt_current = filters.get("nCurrent").toString();
        String cnt_total = filters.get("nTotal").toString();
        String cnt_photo = filters.get("nPhoto").toString();
        String cnt_music = filters.get("nMusic").toString();
        String cnt_video = filters.get("nVideo").toString();
        String cnt_office = filters.get("nDocuments").toString();
        String cnt_doc = filters.get("nDoc").toString();
        String cnt_xls = filters.get("nXls").toString();
        String cnt_ppt = filters.get("nPpt").toString();
        String cnt_pdf = filters.get("nPdf").toString();
        
        filters = (JSONObject) counters.get(1);
        String cnt_alltime = filters.get("nAllTime").toString();
        String cnt_past365d = filters.get("nPast365d").toString();
        String cnt_past30d = filters.get("nPast30d").toString();
        String cnt_past14d = filters.get("nPast14d").toString();
        String cnt_past7d = filters.get("nPast7d").toString();
        String cnt_past3d = filters.get("nPast3d").toString();
        String cnt_past24h = filters.get("nPast24h").toString();
        
        
        
        //tile view
        
//------------------ 1-PrincipioSwitchAntesWhile
        
        
//                p("Mode 4 (Tile view): " + nCount);
        
        StringBuilder res = new StringBuilder();
        
        res.append("<script type=\"text/javascript\">\n");
        res.append("function updatecounters() {\n");
        res.append("$('#n_alltime', window.parent.SIDEBAR.document).html('" + cnt_alltime + "');\n");
        res.append("$('#n_past24h', window.parent.SIDEBAR.document).html('" + cnt_past24h + "');\n");
        res.append("$('#n_past3d', window.parent.SIDEBAR.document).html('" + cnt_past3d + "');\n");
        res.append("$('#n_past7d', window.parent.SIDEBAR.document).html('" + cnt_past7d + "');\n");
        res.append("$('#n_past14d', window.parent.SIDEBAR.document).html('" + cnt_past14d + "');\n");
        res.append("$('#n_past30d', window.parent.SIDEBAR.document).html('" + cnt_past30d + "');\n");
        res.append("$('#n_past365d', window.parent.SIDEBAR.document).html('" + cnt_past365d + "');\n");
        res.append("$('#n_total', window.parent.SIDEBAR.document).html('" + cnt_total + "');\n");
        res.append("$('#n_photo', window.parent.SIDEBAR.document).html('" + cnt_photo + "');\n");
        res.append("$('#n_music', window.parent.SIDEBAR.document).html('" + cnt_music + "');\n");
        res.append("$('#n_video', window.parent.SIDEBAR.document).html('" + cnt_video + "');\n");
        res.append("$('#n_docu', window.parent.SIDEBAR.document).html('" + cnt_office + "');\n");
        res.append("$('#n_doc', window.parent.SIDEBAR.document).html('" + cnt_doc + "');\n");
        res.append("$('#n_xls', window.parent.SIDEBAR.document).html('" + cnt_xls + "');\n");
        res.append("$('#n_ppt', window.parent.SIDEBAR.document).html('" + cnt_ppt + "');\n");
        res.append("$('#n_pdf', window.parent.SIDEBAR.document).html('" + cnt_pdf + "');\n");
        res.append("$('#inputString', window.parent.SIDEBAR.document).val('" + _keyin + "');\n");
        res.append("$('#date').val('" + _datestart + "');\n");
        res.append("}\n");
        res.append("bindEnterSearchBar();\n");
        res.append("</script>\n");
        
        res.append("<span class=\"affix\" style=\"z-index: 200; background-color:#EEEEEE;color:black;border-bottom:1px solid lightgrey\">");
        
        res.append("<form class=\"form-search\" style=\"z-index:100\" id=\"frm1\" action=\"echoClient5.htm\" method=\"get\" onsubmit=\"showLoading();\">");
        
        res.append("<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">");
        res.append("<INPUT id=\"formView\" TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + 4 + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">");
        res.append("<INPUT id=\"formCol\" TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"pw\" VALUE=\"" + _password + "\">");
        
        res.append("<input type=\"hidden\" name=\"dosubmit\" value=\"1\" id=\"dosubmit\"/>");
        res.append("<input type=\"hidden\" name=\"screenSize\" id=\"screenSize\"/>");
        
        String cnt_show = cnt_total;
        if (_filetype.equals(".photo")) cnt_show = cnt_photo;
        if (_filetype.equals(".music")) cnt_show = cnt_music;
        if (_filetype.equals(".video")) cnt_show = cnt_video;
        if (_filetype.equals(".document")) cnt_show = cnt_office;
        if (_filetype.equals(".doc")) cnt_show = cnt_doc;
        if (_filetype.equals(".xls")) cnt_show = cnt_xls;
        if (_filetype.equals(".ppt")) cnt_show = cnt_ppt;
        if (_filetype.equals(".pdf")) cnt_show = cnt_pdf;
        
//                int n_cnt_size = 0;
//                int n_numobj = 0;
//                int n_cnt_show = 0;
//                try {
//                    n_cnt_size = Integer.parseInt(cnt_size);
//                    n_numobj = Integer.parseInt(_numobj);
//                    n_cnt_show = Integer.parseInt(cnt_show);
//                } catch (Exception e) {
//                    p("EXCEPTION: parsing int.");
//                }
//                if (n_cnt_size > n_numobj) cnt_size = _numobj;
//
//
//
//                int nPages = 0;
//                boolean bCont = true;
//                String sStart = _datestart;
//                while (bCont) {
//                    String sPrev = (String)dates_prev.get(sStart);
//                    if (sPrev != null) {
//                        nPages++;
//                        sStart = sPrev;
//                    } else {
//                        bCont = false;
//                    }
//                }
//                int nStart = nPages * n_numobj;
//                int nEnd = nStart + n_cnt_size;
//
//                p("start = " + nStart);
//                p("end = " + nEnd);
//                p("cnt_show = " + n_cnt_show);
//                p("_numobj = " + _numobj);
//
//                if ((nEnd - nStart - 1) > n_numobj) {
//                    nEnd = n_numobj;
//                    bShowNext = true;
//                }
//
//                if (n_cnt_show > n_numobj) {
//                    bShowNext = true;
//                    nEnd = nStart + n_numobj;
//                }
//
//
//
        res.append("<span class=\"affix7\">");
        res.append("&nbsp");
        
        boolean bShowPrev = (_page>0);
        if (bShowPrev) {
            String sRedirLinkPrev = "echoClient5.htm?" +
                    "ftype=" + _filetype + "&" +
                    "days=" + _daysback + "&" +
                    "foo=" + _keyin.replaceAll("\"","&quot;") + "&" +
                    "view=" + sMode + "&" +
                    "numobj=" + _numobj + "&" +
                    "numcol=" + _numcol +"&" +
                    "pw=" + _password +"&" +
                    "screenSize=" + _screenSize +"&"+
                    "page=" + (_page - 1) +"&";
            if(_previousDate!=null && !_previousDate.isEmpty())
                sRedirLinkPrev = sRedirLinkPrev + "date=" + _previousDate;
            
            res.append("<INPUT TYPE=button value=\" < \" onclick=\"showLoading(); golink('" + sRedirLinkPrev + "','" + _key + "',1)\"/>");
        }
        
//                bShowNext = nEnd < n_cnt_show;
        
        JSONArray fighters = (JSONArray)queryResponse.get("fighters");
        int numObjInt = Integer.parseInt(_numobj);
        boolean bShowNext = (Integer.parseInt(cnt_current)/numObjInt) > _page;
        if (bShowNext) {
            
            JSONObject lastFight = (JSONObject) fighters.get(numObjInt-1);
            String lastDate = lastFight.get("file_date").toString();
            
            String dateUTF = null;
            try {
                dateUTF = URLEncoder.encode(lastDate, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(HtmlFromJSON.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            String sRedirLink = "echoClient5.htm?" +
                    "ftype=" + _filetype + "&" +
                    "days=" + _daysback + "&" +
                    "foo=" + _keyin.replaceAll("\"","&quot;") + "&" +
                    "view=" + sMode + "&" +
                    "numobj=" + _numobj + "&" +
                    "numcol=" + _numcol +"&" +
                    "pw=" + _password +"&" +
                    "screenSize=" + _screenSize +"&" +
                    "date=" + dateUTF +"&"+
                    "page=" + (_page + 1) +"&"
                    +"previousDate=" + _datestart +"&";
            
            //res.append("<div class=\"\">");
            res.append("<INPUT TYPE=button value=\" > \" onclick=\"showLoading(); golink('" + sRedirLink + "','" + _key + "',1)\"/>");
            //res.append("</div>");
        }
        
        res.append("</span>");
        
        
        
        res.append("<span class=\"affix3\">");
        
        res.append("<select class=span2 style=\"display:none;\" onchange=\"searchclick();\" name=\"view\" id=\"inputView\">");
        res.append("<option value=\"detail\">Detailed View</option>");
        res.append("<option value=\"show\">Slideshow</option>");
        res.append("<option value=\"show2\">Slideshow2</option>");
        res.append("<option value=\"tile\" selected>Tile View</option>");
        res.append("<option value=\"polar\">Polar View</option>");
        res.append("<option value=\"caro\">Carousel View</option>");
        res.append("</select>");
        
        //res.append("&nbsp&nbsp&nbsp&nbsp");
        //res.append("<i class=\"icon-th\"></i>&nbsp#Columns:&nbsp");
        res.append("&nbsp#Columns:&nbsp");
        
        res.append("<select style=\"top:5px; font-size:10px;width:45px;\" onchange=\"searchclick();\" name=\"numcol\" id=\"inputNumCol\">");
        res.append("<option value=\"1\">1</option>");
        res.append("<option value=\"2\">2</option>");
        res.append("<option value=\"3\">3</option>");
        res.append("<option value=\"4\">4</option>");
        res.append("<option value=\"5\">5</option>");
        res.append("<option value=\"7\">7</option>");
        res.append("<option value=\"9\">9</option>");
        res.append("</select>");
        
        res.append("&nbsp&nbsp");
        //res.append("<i class=\"icon-book\"></i>&nbspResults:&nbsp");
        res.append("&nbspResults:&nbsp");
        
        res.append("<select style=\"font-size:10px;width:60px;\" class=span1 onchange=\"searchclick();\" name=\"numobj\" id=\"inputNumObj\">");
        res.append("<option value=\"25\">25</option>");
        res.append("<option value=\"50\">50</option>");
        res.append("<option value=\"100\">100</option>");
        res.append("<option value=\"250\">250</option>");
        res.append("<option value=\"500\">500</option>");
        res.append("</select>");
        
        res.append("</span>");
        
        
// search box
        
        
        //res.append("<b>Search:&nbsp</b>");
        
        //res.append("<br><br>");
        
        res.append("<span style=\"top:5px; margin-left: 1em\" class=\"\">");
        res.append("<div style=\"font-size:11px;margin-right: 1em;   width: 60%\" class=\"input-append\">");
        
        res.append("<input style=\"font-size:9px; margin-left: 1em; \" type=\"checkbox\" id=\"checkk\" onClick=\"togglesel(this.checked);\"/>");
        res.append("<label onclick=\"var chek = document.getElementById('checkk'); togglesel(chek.checked);\">");
        res.append("&nbspSelect All&nbsp&nbsp");
        res.append("</label>");
        
        res.append("<div style=\"font-size:11px; top:5px; position:relative; width:40%\" class=\"input-append\">");
        //res.append("<input type=\"text\" class=\"search-query span4\" name=\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Searchxxxx\"/>");
        res.append("<INPUT style=\"font-size:11px; position: relative; width: 40% \"  TYPE=\"text\" class=\"search-query\" NAME=\"tag\" ID=\"tag-search\" oninput=\"$('#tag').val($('#tag-search').val());\"  autocomplete=\"off\" placeholder=\"Enter tags here.\"/>");
        //res.append("<INPUT style=\"font-size:11px;\" TYPE=\"submit\" NAME=\"hide selected\" VALUE=\"Apply\">");
        res.append("<button style=\"font-size:11px;\" type=button class=\"btn btn-primary\" onClick=\"submit_tag();\"><i class=\"icon-white icon-tags  \"></i>&nbspApply </button>");
        //res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\"></i>&nbspSearch</button>");
        res.append("</div>");
        
        res.append("<script type=\"text/javascript\">");
        res.append("$('#tag-search').keydown(function (e) {");
        res.append("if (e.keyCode == 13) {");
        res.append(" e.preventDefault();");
        res.append("$('#frm2').submit();");
        res.append("}");
        res.append("}); ");
        res.append("</script>");
        
        res.append("<div class=\"input-append\" style=\"top:5px; margin;left: 1em; font-size:13px; position: relative; width: 65% \">");
        res.append("<input type=\"text\" class=\"search-query\" style=\"margin;left: 1em; font-size:13px; position: relative; width: 65% \"  name =\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Search\"/>");
        //res.append("<span class=\"add-on btn btn-primary\" style=\"top:5px;\"><i class=\"icon-search icon-white\" onclick=\"searchclick();\"></i></span>");
        //res.append("<div class=\"input-append-btn\">");
        res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\" ></i>&nbspSearch</button>");
        res.append("</div>");
        
        res.append("<div>");
        res.append("<div style=\"width: 210px; float:left;\">&nbsp</div>");
        res.append("<div class=\"suggestionsBox\" id=\"suggestions\" style=\"font-size:13px;float:left;margin-left: 10%; width: 40%; display: none; overflow: visible\">");
        res.append("<div class=\"suggestionList\" id=\"autoSuggestionsList\">");
        res.append("&nbsp;");
        res.append("</div>");
        res.append("</div>");
        
        res.append("</div>");
        
        res.append("<div class=\"affix6 pull-center\">");
        res.append("<br>Displaying " + ((_page *  numObjInt)+1)  + "-" + (_page *  numObjInt + numObjInt) + " of " + cnt_current + " results.");
        res.append("</div>");
        
        res.append("</div>");
        
        
        
        
//                res.append("<span class=\"affix4\">");
        
        //res.append("<i class=\"icon-eye-open\"></i>&nbspView Mode:&nbsp&nbsp");
        
//                res.append("<button type=\"submit\" class=\"btn btn-primary\" onclick=\"view_tile();\">");
//                res.append("<i class=\"icon-th icon-white\"></i>");
//                res.append("</button>");
        
//                res.append("<button type=\"submit\" class=\"btn\" onclick=\"view_detail();\">");
//                res.append("<i class=\"icon-list\"></i>");
//                res.append("</button>");
//
//                res.append("<button type=\"submit\" class=\"btn\" onclick=\"view_show();\">");
//                res.append("<i class=\"icon-picture\"></i>");
//                res.append("</button>");
        
//                res.append("</span>");
        
        
        res.append("</span>");
        
        res.append("</form>");
        res.append("</span>");
        
        //res.append("<br><br><br>");
        
        res.append("<form class=\"form-search\"   id=\"frm2\" action=\"bulker.php\" method=get autocomplete=\"off\" onsubmit=\"showLoading();\" >");
        
        res.append("<INPUT style=\"font-size:11px; position: relative; width: 20% \"  TYPE=\"hidden\" class=\"search-query\" NAME=\"tag\" ID=\"tag\" autocomplete=\"off\" placeholder=\"Enter tags here.\"/>");
        
        
//affix2 = view buttons
        
        
        res.append("<span class=\"affix2\">");
        
        res.append("<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + sMode + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">");
        res.append("<INPUT TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">");
        try {
            res.append("<INPUT TYPE=\"hidden\" ID=\"date\" NAME=\"date\" VALUE=\"" + URLEncoder.encode(sFirst, "UTF-8") + "\">");
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(HtmlFromJSON.class.getName()).log(Level.SEVERE, null, ex);
        }
        res.append("<INPUT TYPE=\"hidden\" ID=\"DeleteTag\" NAME=\"DeleteTag\" VALUE=\"\">");
        res.append("<input type=\"hidden\" name=\"screenSize\" id=\"screenSize\"/>");
        
        //res.append("<input style=\"font-size:9px;\" type=\"button\" onClick=\"togglesel(this.checked);\" value=\"Select All\"/>");
        
        //res.append("<i class=\"icon-tags\"></i><b></b>");
        
//                res.append("<div class=\"input-append\">");
//                res.append("<input type=\"text\" class=\"search-query span4\" name =\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Search\"/>");
//                //res.append("<span class=\"add-on btn btn-primary\" style=\"top:5px;\"><i class=\"icon-search icon-white\" onclick=\"searchclick();\"></i></span>");
//                //res.append("<div class=\"input-append-btn\">");
//                res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\" ></i>&nbspSearch</button>");
//                res.append("</div>");
        
        
        
        //res.append("<INPUT TYPE=\"submit\" style=\"position:absolute; height:0px;width:0px;border:none;padding:0px;\" NAME=\"hide selected\" VALUE=\"Apply\">");
        //res.append("<input type=\"button\" onClick=\"togglechk2(this.checked);\" value=\"Clear\"/>");
        res.append("<input id=\"chk0\" style=\"display:none\" type=\"checkbox\" onClick=\"togglechk(this.checked);\">");
        res.append("&nbsp");
        res.append("</span>");
        
        
        res.append("<br><br><br><br>");
        
        res.append("<div id=\"resultElements\">");
        
        
        ArrayList<ResultElement> resultElements = new ArrayList<ResultElement>();
        //p("nTokens:" + nTokens);
        //p("nTokens2:" + nTokens);
        float widthAcum = 0;
        int currentRow = 1;
        int currentColumn = 0;
        
        
        float screenWidth;
        try {
            screenWidth = Float.parseFloat(_screenSize);
        }
        catch(Exception e){
            screenWidth = 1000;
        }
        
        
        
//                firsttime = true;
//                nCurrent = 0;
        
        
        
        int xn = 0;
        boolean bContinue = true;
        
        
        Iterator<Object> It = fighters.iterator();
        while (It.hasNext()) {
//------------------ 3-Principio While
            JSONObject jo = (JSONObject) It.next();
            
            
            xn++;
            //p("processing: " + xn);
            
            String sNamer = jo.get("nickname").toString();
            Integer nCount3 = 0;
            
            
//                    if (!nCount3.equals(nTokens) && _key.indexOf("&") < 0) {
//                        continue;
//                    }
            if (xn >= numObjInt) {
                bContinue = false;
            }
            
            String sFileName = jo.get("name").toString();
            
            String sFileNameOri = sFileName;
            
            //p(sNamer + " - " + nCount3 + " - " + sFileName);
            
            
            String sPic = sNamer + ".jpg";
            
            
            boolean bVector = false;
            String sVector = "";
            
            
//------------------ 4-IsMusic
            
            if (is_music(sFileName)) {
//                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
//                        //p("*** looking for file: " + sFile);
//                        File f = new File(sFile);
//
//                        if (!f.exists()) {
//                            sPic = get_thumb(sFileName);
//                            bVector=true;
//                            sVector = get_vector(sFileName, "100");
//                        }
                
                String sAttr = "";
                String sSongTitle  = "";
                String sSongArtist = "";
                if(jo.get("song_title") != null && jo.get("song_artist") != null){
                    sSongTitle = jo.get("song_title").toString();
                    sSongArtist = jo.get("song_artist").toString();
                }
                String sDate = jo.get("file_date").toString();
                
                
                
                
                if (sSongTitle.length() > 40) {
                    sSongTitle = sSongTitle.substring(0, 39);
                }
                if (sSongArtist.length() > 40) {
                    sSongArtist = sSongArtist.substring(0, 39);
                }
                if (sSongTitle.length() > 0) {
                    if (sSongArtist.length() > 0) {
                        sFileName = sSongTitle + " <br> " + sSongArtist;
                    } else {
                        sFileName = sSongTitle;
                    }
                } else {
                    if (sSongArtist.length() > 0) {
                        sFileName += " <br> " + sSongArtist;
                    }
                    if (sFileName.length() > 40) {
                        sFileName = sFileName.substring(0, 39);
                    }
                }
            }
            
            
            
//------------------ 5-IsPhoto
            
            if (is_pdf(sFileNameOri)) {
//                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
//                        if (fh.exists()) {
//                            //p("PDF exist:" + fh.getCanonicalPath());
//                            bVector = false;
//                        } else {
//                            p("PDF Not exist:" + fh.getCanonicalPath());
//                        }
            }
            
            
            
            String srcPic="pic/"+sPic;
            
//------------------ 5b-IsPhoto
            if(jo.get("file_thumbnail") != null){
                srcPic="data:image/jpg;base64,"+jo.get("file_thumbnail").toString();
            }
            
//                    if(_dobase64 && (is_photo(sFileNameOri) || (is_pdf(sFileNameOri) && !bVector))){
//                        timerAux = new Stopwatch().start();
//                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
//                        if (fh.exists()) {
//                           File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".alt64");
//                           if(fh64.exists()){
//                               //Si ya existe la version base64 la leo
//                               FileInputStream is=new FileInputStream(fh64);
//                               ByteArrayOutputStream out=new ByteArrayOutputStream();
//                               byte[] buf =new byte[1024*1024];
//                               try {
//                                    int n;
//
//                                    while ((n = is.read(buf)) > 0) {
//                                        out.write(buf, 0, n);
//                                    }
//                                } finally {
//                                     is.close();
//                                }
//                                srcPic="data:image/jpg;base64,"+out.toString();
//                                //p("Vista Tile:Se carga imagen desde pic folder");
//                           } else{
//                                //Si existe en pic armo el base64
//                               FileInputStream is=new FileInputStream(fh);
//                               ByteArrayOutputStream out=new ByteArrayOutputStream();
//                               FileWriter writer=new FileWriter(fh64);
//
//                               byte[] buf =new byte[1024*1024];
//                               try {
//                                    int n;
//
//                                    while ((n = is.read(buf)) > 0) {
//                                        out.write(buf, 0, n);
//                                    }
//                                     srcPic= utils.Base64.encodeToString(out.toByteArray(), false);
//                                    writer.write(srcPic.toCharArray());
//                                } finally {
//                                     is.close();
//                                     writer.close();
//                                     out.close();
//                                }
//                                srcPic="data:image/jpg;base64,"+ srcPic;
//                                //p("Vista Tile:Se genera imagen base64");
//                           }
//
//                        }else{
//                            //p("Vista Tile:No se encuentra imagen en PIC, se deja enlace a la imagen");
//                        }
//                    timerAux.stop();
//                    logPerf(timestampPerf, "casss_server","read_row_list2", "6-IsPhotoB64", timerAux.getElapsedTime()+"", _writeLog);
//                    Time[9] += timerAux.getElapsedTime();
//                    }
            
//------------------ 7-checkifshow
            
            boolean bShow;
            if (_filetype.equals(".all")) {
                bShow = true;
            } else {
                String ftype2 = _filetype.trim();
                bShow = checkifshow(sFileNameOri, ftype2);
            }
            
            
            
            if (bShow) {
//------------------ 8-bShowInit_1
                
                //String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                //String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");
                
                //optimization 8/2
                //String sHeightWidth = get_row_attributes(keyspace, "Standard1", sNamer, "img_height", "img_width");
                //String sHeight = sHeightWidth.substring(0, sHeightWidth.indexOf(","));
                //String sWidth = sHeightWidth.substring(sHeightWidth.indexOf(",")+1,sHeightWidth.length());
                
                String sHeightDiv = "200";
                String sWidthDiv = "200";
                String sHeightOrig = "200";
                String sWidthOrig = "200";
                String sWidthThumb = "200";
                String sHeightThumb = "200";
                String sWidthAux = "";
                String sHeightAux = "";
                
                String sDate = "";
                //String sAttr = LocalFuncs.occurences_attr.get(sNamer);
                String sAttr = "";
//                       for(String s: Bind.findSecondaryKeys(LocalFuncs.occurences_attr, sNamer)){
//                            sAttr = s;
//                       }
                
                
                
//                       try {
//                           if (sAttr!= null && sAttr.length() > 0) {
//                                 delimiters = ",";
//                                 st = new StringTokenizer(sAttr, delimiters, true);
//
//                                 if (st.countTokens() == 5)  {
//                                     if (is_music(sFileNameOri)){
//                                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
//                                        if (fh.exists()) {
//                                            BufferedImage bimg = ImageIO.read(fh);
//                                            sHeightDiv = ""+bimg.getHeight();
//                                            sWidthDiv = ""+bimg.getWidth();
//                                        }
//                                     }else {
//                                        sDate = st.nextToken();    //  date
//                                        st.nextToken();            //  ,
//                                        sHeightDiv = st.nextToken();  //  height
//                                        st.nextToken();            //  ,
//                                        sWidthDiv = st.nextToken();   //  width
//                                     }
//
//                                 }
//                                 else {
//                                     if (is_pdf(sFileNameOri)){
//                                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
//                                        if (fh.exists()) {
//                                            BufferedImage bimg = ImageIO.read(fh);
//                                            sHeightDiv = ""+bimg.getHeight();
//                                            sWidthDiv = ""+bimg.getWidth();
//                                        }
//                                     }
//                                     else {
//                                        sDate = st.nextToken();
//                                     }
//                                 }
//                                 //p(sDate + " " + sHeight + " " + sWidth);
//                           } else {
//                               File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
//                                if (fh.exists()) {
//                                    BufferedImage bimg = ImageIO.read(fh);
//                                    sHeightDiv = ""+bimg.getHeight();
//                                    sWidthDiv = ""+bimg.getWidth();
//                                }
//                           }
//                       } catch (Exception e) {
//                           p("*** WARNING: Exception parsing: " + sNamer + " = '" + sAttr + "'");
//                           e.printStackTrace();
//                       }
                
                
                
                
                
//------------------ 12-bShowInit_5
                
                //String sDate = get_row_attribute(keyspace, "Standard1", sNamer, "date_modified");
                
                int nError = 0;
                
//                        try {
//                            sPlayURL = get_row_attribute(keyspace, "Standard1", sNamer, "PlayLink3");
//                        } catch (Exception ex) {
//                            p("   [Ce]");
//                            nError += 1;
//                        }
                
                
                
//------------------ 13-getPlayLink
                
                
                sURLPack sURLpack = new sURLPack();
                boolean bHaveServerLink = false;
                
                //p("   [E]");
                String sOpenURL = sURLpack.sOpenURL;
                String sFolderURL = sURLpack.sFolderURL;
                String sViewURL = sURLpack.sViewURL;
                
                //p("openurl = " + sOpenURL);
                //p("folderurl = " + sFolderURL);
                
                //InetAddress clientIP = InetAddress.getLocalHost();
//                        InetAddress clientIP = getLocalAddress();
//                        String sLocalIP = "127.0.0.1";
//                        if (clientIP != null) {
//                            sLocalIP = clientIP.getHostAddress();;
//                        }
                
                String sPlayURL = "";
//                        if (nError < 1 || _cloudhosted) {
//                            //PLAY LINK
//                            sPlayURL = gen_play_link(sPlayURL, sLocalIP, _key);
//                        } else {
//                            sPlayURL = "ERROR";
//                        }
                
                
                
//------------------ 14-getHashes
//                        timerAux = new Stopwatch().start();
//                        String _hashkey = "hashesm";
//                        String _clientip = _host;
//
//                        ///TODO***
//                        String sHashLink = "";
//                        if (dbmode.equals("cass")) {
//                            sHashLink =  getSuperColumn(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);
//                        } else {
//                            sHashLink =  lf.getHashes(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);
//                        }
//
//                        timerAux.stop();
//                        logPerf(timestampPerf, "casss_server","read_row_list2", "14-getHashes", timerAux.getElapsedTime()+"", _writeLog);
//                        Time[17] += timerAux.getElapsedTime();
                
                
                sPlayURL=sPlayURL.replace("id_","id='serverlnk"+sNamer+"' class='jqueryhidden'");
                
//------------------ 15-getNumberofCopies
//                        timerAux = new Stopwatch().start();
//
//                        String sCopyInfo = null;
////                        for(String s: Bind.findSecondaryKeys(occurences_copies, sNamer)){
////                            sCopyInfo = s;
////                        }
//
//                        //if the client is local, make its IP address equal to server IP address
//                        //p(_clientIP + " " + sLocalIP);
//                        if (_clientIP.equals("0:0:0:0:0:0:0:1") ||
//                                _clientIP.equals("0:0:0:0:0:0:0:1%0") ||
//                                _clientIP.equals("127.0.0.1")) {
//                            _clientIP = sLocalIP;
//                        }
//
//
//
//
//                        if (sCopyInfo.equals("0,0,0")) {
//                            sCopyInfo = lf.getNumberofCopies("paths", sNamer, _clientIP,sLocalIP, false);
//                            p("****NUMCOPIES2****: " + sNamer + " = " + sCopyInfo);
//                        }
//
//                        int nCopies = 0;
//                        int nNodes = 0;
//                        int nCopiesLocal = 0;
//
//                        try {
//                            if (sCopyInfo != null && !sCopyInfo.equals("ERROR")) {
//
//                            String sCopies = "";
//                            String sNodes = "";
//                            String sCopiesLocal = "";
//                            delimiters = ",";
//                            st = new StringTokenizer(sCopyInfo, delimiters, true);
//                            while (st.hasMoreTokens()) {
//                                sCopies = st.nextToken();
//                                st.nextToken();
//                                sNodes = st.nextToken();
//                                st.nextToken();
//                                sCopiesLocal = st.nextToken();
//                            }
//
//                            nCopies = Integer.parseInt(sCopies);
//                            nNodes = Integer.parseInt(sNodes);
//                            nCopiesLocal = Integer.parseInt(sCopiesLocal);
//                        }
//
//                        } catch (Exception e) {
//                            StringWriter sWriter = new StringWriter();
//                            e.printStackTrace(new PrintWriter(sWriter));
//                            logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
//                        }
//
//
//                        timerAux.stop();
//                        logPerf(timestampPerf, "casss_server","read_row_list2", "15-GetNumberOfCopies", timerAux.getElapsedTime()+"", _writeLog);
//                        Time[18] += timerAux.getElapsedTime();
                
//------------------ 16-AfterGetNumberofCopies
//                        timerAux = new Stopwatch().start();
//
//
//
//
//                        //p("There are " + nCopies + " copies of file " + sNamer);
//                        //p("There are " + nNodes + " nodes that has a copy of file " + sNamer);
//                        //p("There are " + nCopiesLocal + " local copies of file " + sNamer);
//
//                        timerAux.stop();
//                        logPerf(timestampPerf, "casss_server","read_row_list2", "16-AfterGetNumberOfCopies", timerAux.getElapsedTime()+"", _writeLog);
//                        Time[19] += timerAux.getElapsedTime();
                
//------------------ 17-isPhoto2
////                        timerAux = new Stopwatch().start();
//
                String sViewURL2 = "";
//                        String sOpenURL2 = "";
//                        String sSendURL2 = "";
//                        String sFolderURL2 = "";
//
//                        if(is_photo(sFileNameOri)) {
//                            sWidthOrig = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");
//                            sHeightOrig = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
//                        }
//                        else {
//                            sWidthOrig = sWidthDiv;
//                            sHeightOrig = sHeightDiv;
//                        }
//                        if (sPlayURL.length() > 0) {
//                            //If there is a copy of file in server, show Send Link
//                            sSendURL2 = "<INPUT TYPE=button value=\"Send 2\" id_ onclick=\"golink('sendfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"','" + _key + "',1,1);\" />";
//                        }
//
//                        //p("openurl = " + sOpenURL);
//                        //p("There are " + nCopies + " copies of file " + sNamer);
//
//                        timerAux.stop();
//                        logPerf(timestampPerf, "casss_server","read_row_list2", "17-isPhoto2", timerAux.getElapsedTime()+"", _writeLog);
//                        Time[20] += timerAux.getElapsedTime();
                
//------------------ 18-GetLinks
//                        timerAux = new Stopwatch().start();
//
//                        if (nCopies > 0) {
//
//                            if (_cloudhosted) {
//
//
//                                //VIEW LINK
//                                sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false);
//
//                                sOpenURL = sURLpack.sOpenURL;
//                                sFolderURL = sURLpack.sFolderURL;
//                                sViewURL = sURLpack.sViewURL;
//
//                                p("**cloud*********** viewurl = " + sViewURL);
//
//                                sViewURL2 = gen_view_link2(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileNameOri, _key, nImgRatio, sLocalIP, false);
//
//                            } else {
//                                //VIEW
                String sButtonText = "Save";
                String sGoLink = "golink";
                if (is_music(sFileNameOri) || is_movie(sFileNameOri)) sButtonText = "Play";
                if (is_photo(sFileNameOri) || si_inline(sFileNameOri)) sButtonText = "View";
                if (is_music(sFileNameOri)) sGoLink = "golink_music";
//
//                                //sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, false);
//
//                                if(is_photo(sFileNameOri) || is_movie(sFileNameOri) || is_musicToPlay(sFileNameOri) || si_inline(sFileNameOri)){
//
//                                    //sViewURL2 = ("<button class=\"btn btn-primary\" onclick=\"golink('" + sURLpack.sViewURL + "','" + _key + "',1,1);\">");
//                                    //sViewURL2 += sButtonText;
//                                    //sViewURL2 += "<i class=\"icon-eye-open icon-white\"></i>";
//                                    //sViewURL2 += "</button>";
                if (is_photo(sFileNameOri)) {
                    sViewURL = "/cass/viewimg2.htm?sNamer=" + sNamer;
                } else if(is_music(sFileNameOri)){
                    Object audio_url = jo.get("audio_url");
                    if(audio_url != null){
                        String urifile = audio_url.toString();
                        int indexCass = urifile.indexOf("getaudio");
                        String casspart = urifile.substring(indexCass,urifile.length());
                        sViewURL = RemoteAccess.bridgeAddress + casspart + "&uuid=" + us.getUuid();
                        
                    }
                }else{
                    String sExt = "";
                    if (sFileNameOri.contains(".")) {
                        sExt = sFileNameOri.substring(sFileNameOri.indexOf("."));
                    }
                    sViewURL = "/cass/getfile.fn?sNamer=" + sNamer + "&sFileExt=" + sExt;
                }
                sViewURL2 = "<BUTTON style=\"\" type=\"button\" class=\"buttonElement\" id_ onclick=\"" + sGoLink + "('" + sViewURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-eye-open icon-white\"></i>&nbsp;" + sButtonText + "</BUTTON>";
//
//                                    //sViewURL2 = "<INPUT TYPE=button value=\"" + sButtonText + "\" id_ onclick=\"golink('" + sURLpack.sViewURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-eye-open icon-white\"></i>" + "</INPUT>";
//                                    if (is_photo(sFileNameOri)) {
//                                        //sSendURL2 = "<INPUT TYPE=button value=\"Send\" id_ onclick=\"golink('" + sURLpack.sSendURL + "','" + _key + "',1,1);\" />";
//                                        String sSendURL = "/cass/sendimg2.htm?sNamer=" + sNamer;
//                                        sSendURL2 = "<BUTTON style=\"\" type=\"button\" class=\"buttonElement\" id_ onclick=\"golink('" + sSendURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-envelope icon-white\"></i>&nbsp;" + "Send" + "</BUTTON>";
//                                    }
//                                } else {
//                                    sViewURL2 = "<BUTTON style=\"\" type=\"button\" class=\"buttonElement\" id_ onclick=\"newTabCommand('getfile.fn?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\"/>" + "<i class=\"icon-file icon-white\"></i>&nbsp;" + sButtonText + "</BUTTON>";
//                                }
//                            }
//
//                            if (nCopiesLocal > 0) {
//                                //OPEN & FOLDER
//                                sOpenURL2 = "<INPUT TYPE=button value=\"Open\" id_ onclick=\"silentCommand('openfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\" />";
//                                sFolderURL2= "<INPUT TYPE=button value=\"Folder\" id_ onclick=\"silentCommand('openfolder.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\" />";
//
//                                sOpenURL2 = "<BUTTON TYPE=button style=\" type=\"button\" class=\"buttonElement\" id_ onclick=\"silentCommand('openfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\">" + "<i class=\"icon-download-alt icon-white\"></i>&nbsp;" + "Open" + "</BUTTON>";
//                                sFolderURL2 = "<BUTTON TYPE=button style=\" type=\"button\" class=\"buttonElement\" id_ onclick=\"silentCommand('openfolder.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\">" + "<i class=\"icon-folder-open icon-white\"></i>&nbsp;" + "Folder" + "</BUTTON>";
//                                //sOpenURL2 = "<BUTTON TYPE=button style=\"font-size:13px; type=\"button\" class=\"btn btn-primary\" id_ onclick=\"golink('" + sURLpack.sOpenURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-download-alt icon-white\"></i>" + "Open" + "</BUTTON>";
//                                //sFolderURL2 = "<BUTTON TYPE=button style=\"font-size:13px; type=\"button\" class=\"btn btn-primary\" id_ onclick=\"golink('" + sURLpack.sFolderURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-folder-open icon-white\"></i>" + "Folder" + "</BUTTON>";
//                            } else {
//                                sOpenURL2 = "<BUTTON TYPE=button style=\" type=\"button\" class=\"buttonElement\" id_ onclick=\"silentCommand('openfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\">" + "<i class=\"icon-download-alt icon-white\"></i>&nbsp;" + "Remote" + "</BUTTON>";
//                                sFolderURL2 = "";
//                            }
//
//                        }
//                        timerAux.stop();
//                        logPerf(timestampPerf, "casss_server","read_row_list2", "18-GetLinks", timerAux.getElapsedTime()+"", _writeLog);
//                        Time[21] += timerAux.getElapsedTime();
                
                
//------------------ 19-UltimaParteW
//                        timerAux = new Stopwatch().start();
                /*
                int lineasEtiquetas=0;
                if(sHashLink.trim().length()>0){
                //Cambio la estructura de la tabla generada para que quede en 1 columna
                int count=0;
                StringBuilder sHashLinkAux=new StringBuilder(sHashLink);
                boolean entre=false;
                int i=0;
                while(sHashLinkAux.substring(i).contains("</td>")){
                i=sHashLinkAux.indexOf("</td>",i)+"</td>".length();
                
                count++;
                
                if(count==1){
                count=0;
                entre=false;
                sHashLinkAux.insert(i,"</tr><tr>");
                lineasEtiquetas++;
                }else{
                entre=true;
                lineasEtiquetas++;
                }
                }
                
                if(entre ){
                sHashLinkAux.insert(sHashLinkAux.toString().indexOf("</table>"),"</tr><tr>");
                }
                
                if(lineasEtiquetas>1){
                sHashLink="<table><tr valign='top'><td  align='right'><div  class='jquerycursorpointer' style='cursor:pointer;color:blue;float:left;witdh:100%;aling:right;z-index=5000;' id='tagsTblExpCont"+sNamer+"' onclick=\"expCont('"+sNamer+"','tagsTbl"+sNamer+"','tagsTblExpCont"+sNamer+"',25);\">+</div>"+
                "</td><td align='center'>"+sHashLinkAux.toString()+"</td></tr></table>";
                
                }else{
                sHashLink=sHashLinkAux.toString();
                }
                //FIN Cambio la estructura de la tabla generada
                }
                */
                
                //Esto es para los nombres largos en los archivos
                String sFileNameAux = sFileName;
                try {
                    sFileNameAux=URLDecoder.decode(sFileName,"UTF-8").trim();
                } catch (Exception e) {
                    //error parsing the file.
                }
                
                if(sFileNameAux.trim().length()>36){
                    String sExt = "";
                    if (sFileNameAux.contains(".")) {
                        sExt = sFileNameAux.substring(sFileNameAux.indexOf("."));
                    }
                    if (!sFileNameAux.contains("<")){
                        sFileNameAux=sFileNameAux.substring(0, 32) + "..." + sExt;
                    }
                }
//                        String sColor = "red";
//                        if (nCopies > 1) {
//                            sColor = "green";
//                        }
                String sColor = "#08c";
                String sColorLocal = "#314";
                
                //default replication factor 3
//                        int REPLICATION_FACTOR = 3;
//                        String sFactor = getConfig("rfactor", "../scrubber/config/www-rtbackup.properties");
//                        if (sFactor.length() > 0 ) {
//                            REPLICATION_FACTOR = Integer.parseInt(sFactor);
//                        }
//
//                        String sColorNodes = "red";
//                        if (nNodes >= REPLICATION_FACTOR) {
//                            sColorNodes = "green";
//                        }
                
//                        String sLocalCopies = "<font class='jquerycursorpointer'  style='cursor:pointer;background-color:" + sColorLocal + ";color:white;'>&nbsp" + nCopiesLocal + "&nbsp</font>";
//                        String sCopies = "<font class='jquerycursorpointer'    style='cursor:pointer;background-color:" + sColor + ";color:white;'>&nbsp" + nCopies + "&nbsp</font>";
//                        String sNodes = "<font class='jquerycursorpointer'  style='cursor:pointer;background-color:" + sColorNodes + ";color:white;'>&nbsp" + nNodes + "&nbsp</font>";
                
                
                DecimalFormat df = new DecimalFormat("0.0");
                
                //Check if image rotated by thumbnailtor
                
                try {
                    if (is_photo(sFileNameOri)){
                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                        
                        if (fh.exists()) {
                            BufferedImage bimg = ImageIO.read(fh);
                            sHeightThumb = ""+bimg.getHeight();
                            sWidthThumb = ""+bimg.getWidth();
                        }
                        
                        if (Float.parseFloat(sWidthOrig) > Float.parseFloat(sHeightOrig)){
                            sHeightAux = ((200.0/Float.parseFloat(sWidthOrig)))*Float.parseFloat(sHeightOrig)+"";
                            sWidthAux = "200";
                        }
                        else {
                            sWidthAux = ((200.0/Float.parseFloat(sHeightOrig)))*Float.parseFloat(sWidthOrig)+"";
                            sHeightAux = "200";
                        }
                        
                        sWidthAux = Math.round(Float.parseFloat(sWidthAux))+"";
                        sHeightAux = Math.round(Float.parseFloat(sHeightAux))+"";
                        
                        float ratioOrig = Float.parseFloat(sWidthAux) / Float.parseFloat(sHeightAux);
                        float ratioThumb = Float.parseFloat(sWidthThumb) / Float.parseFloat(sHeightThumb);
                        
                        String sWidthThumbAux = sWidthDiv;
                        String sWidthOrigAux = sWidthOrig;
                        
                        //p("sWidthAux = "+sWidthAux);
                        //p("sHeightAux = "+sHeightAux);
                        //p("sWidthThumb = "+sWidthThumb);
                        //p("sHeightThumb = "+sHeightThumb);
                        
                        //p("Ratio orig = "+ratioOrig);
                        //p("Ratio thumb = "+ratioThumb);
                        
                        
                        if (Float.parseFloat(df.format(ratioOrig)) != Float.parseFloat(df.format(ratioThumb))){
                            sWidthDiv = sHeightDiv;
                            sHeightDiv = sWidthThumbAux;
                            sWidthOrig = sHeightOrig;
                            sHeightOrig = sWidthOrigAux;
                        }
                    }
                }catch (Exception e) { }
                
                // end check
                
                if (sHeightOrig.length() > 0 && Float.parseFloat(sHeightOrig) < 200){
                    sHeightDiv = "200";
                }
                if (sWidthOrig.length() > 0 && Float.parseFloat(sWidthOrig) < 200){
                    sWidthDiv = "200";
                }
                
                String sHeightNew = "200";
                
                String sWidthNew = null;
                if (!bVector) {
                    if (Float.parseFloat(sHeightDiv) == 200.0){
                        sWidthNew = sWidthDiv;
                        sHeightNew = sHeightDiv;
                    }
                    else {
                        sWidthNew = ((200.0/Float.parseFloat(sHeightDiv)))*Float.parseFloat(sWidthDiv)+"";
                        sHeightNew = "200";
                    }
                }
                else {
                    sWidthNew = sWidthDiv;
                    sHeightNew = sHeightDiv;
                }
                
                
                
                
                if ((widthAcum + Float.parseFloat(sWidthNew) + 2) > screenWidth){
                    widthAcum = Float.parseFloat(sWidthNew) + 2;
                    currentRow++;
                    currentColumn = 1;
                }
                else {
                    widthAcum += Float.parseFloat(sWidthNew) + 2;
                    currentColumn ++;
                }
                
                ResultElement re = new ResultElement(bVector,sFileNameOri,sNamer,_password,sMode,_filetype,_daysback,_numcol,_numobj,sVector,sHeightNew,sWidthNew,srcPic,"",sViewURL2,"","","","",sFileNameAux,"","","",sDate, currentRow, currentColumn, sWidthOrig, sHeightOrig);
//                        ResultElement re = new ResultElement(bVector,sFileNameOri,sNamer,_password,sMode,_filetype,_daysback,_numcol,_numobj,sVector,sHeightNew,sWidthNew,srcPic,sHashLink,sViewURL2,sPlayURL,sSendURL2,sOpenURL2,sFolderURL2,sFileNameAux,sLocalCopies,sCopies,sNodes,sDate, currentRow, currentColumn, sWidthOrig, sHeightOrig);
                
                resultElements.add(re);
                
                
//                        nCount2++;
                int nNumCol = Integer.parseInt(_numcol);
                //p("nNumCol = '" + nNumCol + "'");
                
                /*if (nCount2 >= nNumCol) {
                res.append("<td width='40px'>&nbsp&nbsp</td></tr>");
                res.append("<tr style='text-align:center; vertical-align:middle'>");
                nCount2 = 0;
                }
                */
//                        timerAux.stop();
//                        logPerf(timestampPerf, "casss_server","read_row_list2", "19-UltimaParteW", timerAux.getElapsedTime()+"", _writeLog);
//                        Time[22] += timerAux.getElapsedTime();
                
            }
            
        }
        
        //Resize all elements
        for (ResultElement e : resultElements){
            e.resize(resultElements, screenWidth, currentRow);
        }
        //Generate html element
        for (ResultElement e : resultElements){
            res.append(e.getHtml(screenWidth));
        }
        
        res.append("</form>");
        res.append("</div>");
        
        return res;
    }
    
    public static String getTagsRemote(String tagsString) {
        StringBuilder res = new StringBuilder();
        
        JSONObject tagsJSON =  (JSONObject)JSONValue.parse(tagsString);
        JSONArray fightersJSON = (JSONArray)tagsJSON.get("fighters");
        String results = "";
        for (Object object : fightersJSON) {
            JSONObject jsonobj = (JSONObject) object;
            String key = jsonobj.get("tagname").toString();
            String value = jsonobj.get("tagcnt").toString();
            results += "<INPUT TYPE=button value=\"" + key +" ("+ value+")"+ "\"  onclick=\"clearFilters(); search_query(&#39;"+key+"&#39;);\" style=\"color:#000;background-color:#FCF0AD;margin-bottom: 0.2em;display:block;max-width:90%;word-wrap:break-word;font-size:100%\"/>";
        }
        
        
        String result = results.trim().equals("") ? "<span style=\"color: black\">No tags</span>" : results;
        
        res.append ("<script type='text/javascript'>\n");
        res.append ("$('#loop_tags', window.parent.SIDEBAR.document).html('xxx');\n");
        res.append ("$('#tags_all', window.parent.SIDEBAR.document).html('" + result + "');\n");
        res.append ("</script>");
        
        return res.toString();
    }
    
    public static String getSuggestion(String w, String _user, String json){
        try {
            
            JSONObject queryResponse =  (JSONObject)JSONValue.parse(json);
            JSONArray fighters = (JSONArray)queryResponse.get("fighters");
            
            
            String res = "";
            
            
            res += "Found " + fighters.size() + " matches.";
            Iterator<Object> bit = fighters.iterator();
            int j = 0;
            while (bit.hasNext() && j <=20) {
                JSONObject obj = (JSONObject) bit.next();
                String sAdd = obj.get("name").toString();
                boolean addQuotes = (sAdd.contains(" ")) && (sAdd.charAt(0) != '\"') && (sAdd.charAt(sAdd.length()-1) != '\"');
                String sAddFill = addQuotes ? "&quot;"+sAdd +"&quot;" : sAdd;
                
                res += "<li onClick=\"clearFilters(); clearFiltersVar(); fill('" + sAddFill + "');\">" + sAdd + "</li>";
                j++;
            }
            if (fighters.size() > 20) {
                res += "<li onClick=\"clearFilters(); clearFiltersVar();fill('" + w + "');\">See All</li>";
            }
            
            if (res.length() == 0) {
                res = "<li>Nothing found.</li>";
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
    }
    
}


