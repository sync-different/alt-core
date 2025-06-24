function updateTags(){
	//alert('Update tags');
    top.frames["MAIN1"].document.getElementById("MAINFILTER").contentDocument.getElementById('loop_tags').innerHTML='xxx';
    $.ajax({async:false,url:'/cass/gettags.fn'}).done(
        function(data){
			top.frames["MAIN1"].document.getElementById("MAINFILTER").contentDocument.getElementById('tags_all').innerHTML=data;
           	//$('#tags_all').html(data);
           	if ((data != null) && (data != ""))
            	$('#loop_tags').html("true");
        });
}

function startAjaxCallTags(){
	//alert('startajax');
    top.frames["MAIN1"].document.getElementById("MAINFILTER").contentDocument.getElementById('loop_tags').innerHTML='xxx';
	//$('#loop_tags', window.parent.SIDEBAR.document).html('xxx');
}
