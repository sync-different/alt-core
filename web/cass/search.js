function clearFiltersVar(){
    top.frames["MAIN1"].document.getElementById("MAINFILTER").contentDocument.getElementById('inputNumDays').value = "";
    top.frames["MAIN1"].document.getElementById("MAINFILTER").contentDocument.getElementById('inputType').value = ".all";
    $('#ftype').val(".all");
    $('#ndays').val("");
}

function bindEnterSearchBar(){
  document.getElementById('inputString').onkeypress = function(e){
    if (!e) e = window.event;
    var keyCode = e.keyCode || e.which;
    if (keyCode == '13'){
      clearFilters();
      clearFiltersVar();
      return false;
    }
  }
}