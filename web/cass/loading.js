function showLoading(){
  var loadingBar = "<div style=\"background-color:rgb(252,240,173); height: 2em;color:black; border-bottom:0px solid lightgrey; position: absolute; top:0.5em; width: 15em; left:40% \">";
  loadingBar += " <div align=\"center\" style=\"padding-top:0.2em\"><strong>Loading...</strong></div>  </div>";
  var mainresult = top.frames["MAIN1"].document.getElementById("MAINRESULT").contentDocument.getElementById('frm1');
  $(mainresult).append(loadingBar);
}