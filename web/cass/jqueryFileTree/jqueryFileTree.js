// jQuery File Tree Plugin
//
// Version 1.01
//
// Cory S.N. LaViska
// A Beautiful Site (http://abeautifulsite.net/)
// 24 March 2008
//
// Visit http://abeautifulsite.net/notebook.php?article=58 for more information
//
// Usage: $('.fileTreeDemo').fileTree( options, callback )
//
// Options:  root           - root folder to display; default = /
//           script         - location of the serverside AJAX file to use; default = jqueryFileTree.php
//           folderEvent    - event to trigger expand/collapse; default = click
//           expandSpeed    - default = 500 (ms); use -1 for no animation
//           collapseSpeed  - default = 500 (ms); use -1 for no animation
//           expandEasing   - easing function to use on expand (optional)
//           collapseEasing - easing function to use on collapse (optional)
//           multiFolder    - whether or not to limit the browser to one subfolder at a time
//           loadMessage    - Message to display while initial tree loads (can be HTML)
//
// History:
//
// 1.01 - updated to work with foreign characters in directory/file names (12 April 2008)
// 1.00 - released (24 March 2008)
//
// TERMS OF USE
// 
// This plugin is dual-licensed under the GNU General Public License and the MIT License and
// is copyright 2008 A Beautiful Site, LLC. 
//

//TAD SCAN

var scanOn=false;
var timing='';

function setScannerTiming(t){
	timing=t;
}
function setScannerRunOn(){
	scanOn=true;
}


function setScannerRunOff(){
	scanOn=false;
}

//END TAD SCAN

//TAD TREE RECOMMENDED

var treeRecommended=new Array();
 
function clearRecommendedAndClean(){
	$.ajax(
		{
			url:'/cass/initscan.fn',
			async:false
		}
	).done(
		function(){
		}
	);

	clearRecommended();
	$('#popupRecommended').remove();
	 $('#body_1').append("<ul id='popupRecommended'/>");
	 $('#popupRecommended').css({ position:'absolute',top:$('#popup').position().top ,left:$('#popup').position().left+$('#popup').outerWidth(),'z-index':7000});
	
	
	$('#popupRecommended').append('<H2>Recommended Folders</H2>');
}
 

function clearRecommended(){
	treeRecommended.splice(0,treeRecommended.length);
}




function addRecommended(path,sec,count){
	var esta=false;
	for(var i=0; !esta && i<treeRecommended.length;i++){
		if( treeRecommended[i].Path==path &&  treeRecommended[i].Sec==sec){
			esta=true;
		}
	}	
	if(!esta){ 
		treeRecommended.push({Path:path,Sec:sec,Count:count});
	}
}

function isSelRecommendation(path,sec){
	var esta=false;
	var index=-1;
	 
	/*for(var i=0;!esta && i<recommendedSel.length;i++){
		var r=recommendedSel[i];
		if(r.Path==path && r.Sec==sec) {
			esta=true;
			index=i;
		}
	}*/
	index=mapJQtree.indexOf(path);
	if(index>=0){
		esta=true;
	}
 

	return esta;
}

function selectRecommendation(ck){
	var $this=$(ck);

	if($this.is(':checked')){
		//recommendedSel.push({Path:$this.attr('path'),Sec:$this.attr('sec')});
		var yaEsta=false;
		$('Input[path="'+$this.attr('path')+'"]').each(
			function(){
				$(this).prop('checked','checked');
				if(!yaEsta){
					clearSelectedPath($(this)[0]);
				}
				yaEsta=true;
			}
		);
		if(mapJQtree.indexOf($this.attr('path'))<0){ 
			mapJQtree.push($this.attr('path'));
		}
		
	}else{
		/*var esta=false;
		for(var i=0;!esta && i<recommendedSel.length;i++){
			var r=recommendedSel[i];
			if(r.Path==$this.attr('path') && r.Sec==$this.attr('sec')) {
				recommendedSel.splice(i,1);
				esta=true;
			}
		}*/
		var yaEsta=false;
		$('Input[path="'+$this.attr('path')+'"]').each(
			function (){
				$(this).prop('checked',false); 
				if(!yaEsta){
					clearSelectedPath($(this)[0]);
				}
				yaEsta=true;
			}
		);	
		if(mapJQtree.indexOf($this.attr('path'))>=0){ 
			mapJQtree.splice(mapJQtree.indexOf($this.attr('path')),1);
		}
	}

	intermediate();
}

function SortByRecommended(a, b){
  	var aName = a.Sec.toLowerCase();
  	var bName = b.Sec.toLowerCase(); 
  	var index1=a.Path.lastIndexOf(slashString);
  	var path=a.Path;
	if((a.Path.length==(index1+3))){
		path=a.Path.substring(0,index1);
		index1=path.lastIndexOf(slashString);
	}
	var fileNameA=decodeURIComponent(path.substring(index1+3));

	path=b.Path;
	index1=b.Path.lastIndexOf(slashString);
	if((b.Path.length==(index1+3))){
		path=b.Path.substring(0,index1);
		index1=path.lastIndexOf(slashString);
	}
	var fileNameB=decodeURIComponent(path.substring(index1+3));

  	return ((aName < bName) ? -1 : ((aName > bName) ? 1 : ((fileNameA < fileNameB) ? -1 :((fileNameA > fileNameB) ? 1:0))));
}



//TAD TREE METADATA
var treeMetadata=new Array();
var mapJQtree=new Array();
var blMapJQTree=new Array();
var queueTree=new Array();
var scanTreeMode=-1;
var oneOnlyJQtree=false;
var treeInitialized=false;
var markInitialized=false;

function addMetadataObject(object){
	var o=getMetadataObject(object.Path)
	if(o==null){
		 
		treeMetadata.push(object);
	}
}

function addMetadataObjectR(object){
	var esta=false;
	var index=0;
	var sinHijosNoRecursivos=true;
//	console.log("addR:"+object.Path);
	if(object.Path=='c%3A%5Calterant%C3%A9%C3%B1%5Cini_client%5C'){
		var c="";
	}

	for(var i=0;(!esta||sinHijosNoRecursivos)&& i<treeMetadata.length;i++){
		if(treeMetadata[i].Path.toLowerCase()==object.Path.toLowerCase()){
			index=i;
			esta=true;
		}
		if(treeMetadata[i].Path.toLowerCase()!= object.Path.toLowerCase() && treeMetadata[i].Path.toLowerCase().indexOf(object.Path.toLowerCase())==0 && treeMetadata[i].Recursive=="0"){
			sinHijosNoRecursivos=false;
		}
	}
	if(sinHijosNoRecursivos){ 
	
		if(!esta){
			treeMetadata.push(object);
			index=treeMetadata.length-1;
		}

		
		treeMetadata[index]=object;
		queueTree.push(object);
	}
	 
}

function removeObjectMetadata(Path){
	var esta=false;
	var object=null;
	for(var i=0;!esta && i<treeMetadata.length;i++){
		if(treeMetadata[i].Path==Path){
			treeMetadata.splice(i,1);
			esta=true;
		}
	}
	
}

function addMetadata(path,meta){
	var o=getMetadataObject(path);
	if(o!=null && getMetadata(path,meta.Sec)==null){
		o.Metadatos.push(meta)
	}
}

function getMetadata(path,sec){
	
	var esta=false;
	var object=null;
	for(var i=0;!esta && i<treeMetadata.length;i++){
		if(treeMetadata[i].Path==path){
			var secAux=treeMetadata[i].Metadatos;
			for(var j=0; !esta && j<secAux.length;j++){
				if(secAux[j].Sec.toLowerCase()==sec.toLowerCase()){
					esta=true;
					object=secAux[j];
				}
			}
			
		}
	}
	
	return object;
}

function getMetadataObject(path){
	var esta=false;
	var object=null;
	for(var i=0;!esta && i<treeMetadata.length;i++){
		if(treeMetadata[i].Path==path){
			object=treeMetadata[i];
			esta=true;
		}
		 
	}
	return object;
}
function displayMetadata(el){
	if(scanTreeMode==2){
		var o= getMetadataObject($(el).attr('path'));
		popupTreeMetadata(el,
			function(){
				var mdata="<table>";
				for(var i=0; i<o.Metadatos.length;i++){
					var sec=o.Metadatos[i];
					var ok=false;
					for(var j=0; !ok && j<sec.Exts.length;j++){
						ok=sec.Exts[j].Count>0;
					}
					if(ok){
						mdata+="<tr><td colspan='2'><b>"+sec.Sec+"</td></tr><tr>"
						var fila=0;
						for(var j=0; j<sec.Exts.length;j++){
							if(sec.Exts[j].Count>0){
								mdata+="<td><label ='checkbox'>"+sec.Exts[j].Count+" - "+sec.Exts[j].Ext+" ("+sec.Exts[j].Dsc+")"+"</label></td>"		
								fila++;
							}
							if(fila>0 && fila%3==0 && (j+1)<sec.Exts.length){
								mdata+="</tr><tr>";
							}else if((j+1)>=sec.Exts.length){
								mdata+="</tr>";	
							}
						}
					}
				}
				if(mdata=='<table>' && o.Recursive=="1"){
					mdata+="<tr><td colspan='2'><b>No files to scan</td></tr><tr>"
				}
				mdata+="</table>";
				$('#popupTreeMetadata').html(mdata);
				
			}
		);
	}
}
function setSTM(stm){
	scanTreeMode=stm;
}
function disposeMetadata( ){
	$('#popupTreeMetadata').remove();
}

function popupTreeMetadata(el,f){
	$('#popupTreeMetadata').remove();
	var div=$('<div id="popupTreeMetadata"  />');
	//div.css({ position:'absolute',top:$(el).position().top+$(dirElTree).position().top,left:$(el).position().left+$(dirElTree).position().left+$(el).outerWidth(),'z-index':7000});
	div.css({ position:'absolute',top:$(el).offset().top ,left:$(el).offset().left+$(el).outerWidth(),'z-index':7000});
	$('#body_1').append(div);
	div.hide();
	div.fadeIn(300,f);
}

function replaceAll(find, replace, str) {
if(find!='+') return str.replace(new RegExp(find, 'g'), replace);
else if(str.length>0 && str[0]=='+') return replace+ replaceAll(find, replace, str.substring(1));
else if(str.length==0) return '';
else return str[0]+replaceAll(find, replace, str.substring(1));
}

function getMetadataAsyncronica(){
	if(scanTreeMode==-1){
		$.ajax(
			{
				url:'/cass/getscanmode.fn',
				async:false
			}
		).done(
			function(data){
				scanTreeMode=parseInt(data);
				if(scanTreeMode>1){
					$.ajax(
						{
							url:'/cass/initscan.fn',
							async:true
						}
					).done(
						function(){
						}
					);	
				}
			}
		);
	}else{
		$.ajax(
			{
				url:'/cass/getscanmode.fn',
				async:true
			}
		).done(
			function(data){
				scanTreeMode=parseInt(data);
			}
		);
	}
	
	$.ajax(
	{
		async:true,
		url:'getscannews.fn'
	}
	).done(
		function(data){
		var pathsACK="";
			if(data!='scanoff'){
				if($.trim(data)!=""){
					try{
						eval(data);
						//Recommended Start
						 $('#popupRecommended').remove();
						 $('#popupdiv').append("<ul id='popupRecommended'/>");
						 $('#popupRecommended').css({ position:'absolute',top:$('#popup').position().top ,left:$('#popup').position().left+$('#popup').outerWidth(),'z-index':7000});
						
						treeRecommended.sort(SortByRecommended);
						$('#popupRecommended').append('<H2>Recommended Folders'+(scanOn?'<div id="scanning" style="display:inline;">...</div>':(timing.length>0?timing:''))+'</H2>');

						for(var i=0;i<treeRecommended.length;i++){
							var sec=replaceAll(" ","_",treeRecommended[i].Sec);
							if($('#li'+sec).length==0){
								
								$('#popupRecommended').append('<li id="li'+sec+'" >'+treeRecommended[i].Sec+'</li>');
								$('#li'+sec).append('<ul id="ul'+sec+'"></li>');
							}
							var charaux=slashString;
							var index1=treeRecommended[i].Path.lastIndexOf(charaux);
							var fileName="";
							var path=treeRecommended[i].Path;
							if((treeRecommended[i].Path.length==(index1+3))){
								path=treeRecommended[i].Path.substring(0,index1);
								index1=path.lastIndexOf(charaux);
							}
							fileName=decodeURIComponent(path.substring(index1+3));
							$('#ul'+sec).append("<li><input type='checkbox' id='rc"+treeRecommended[i].Path+"' path='"+treeRecommended[i].Path+"' sec='"+treeRecommended[i].Sec+"'onclick='selectRecommendation(this)' "+(isSelRecommendation(treeRecommended[i].Path,treeRecommended[i].Sec)?"checked":"")+" />"+fileName+" "+treeRecommended[i].Count+" files "+"("+decodeURIComponent(treeRecommended[i].Path)+")</li>");
							
						}
						
						
						//Recommended End
						
						
						var pathsACK="";
						for(var i=0; i< queueTree.length;i++){	
							if(i>0)pathsACK+=";";
							pathsACK+=queueTree[i].Path;
							var id=replaceAll('+','_',replaceAll('%','_',queueTree[i].Path));
							if(id.indexOf('test')>0){
								var n=0;
							}

							if(  document.getElementById('li'+id)!=null && document.getElementById('li'+id)!='undefined' ){
								try{
									var $thisParent=$(document.getElementById('li'+id));
									var $this=$(document.getElementById('a'+id));
									if(hasClass($thisParent)){
										expand($this);
										collapsed($this);
									}else{
										collapsed($this);
										expand($this);
									}
									
								}catch(e){
//									console.log(e.toString());
								}
							}
							$(document.getElementById('sc'+id)).remove();
						}
				
						queueTree.splice(0,queueTree.length);
						for(var i=0; i< treeMetadata.length;i++){
							if(treeMetadata[i].Recursive=="1"){
								/*var cSubDirFinished=0;
								for(var j=0; j< treeMetadata.length;j++){
									if(treeMetadata[i].Path.indexOf(treeMetadata[j].Path)==0 && treeMetadata[j].Recursive=="1"){
										cSubDirFinished++;
									}
								}*/
								//if( treeMetadata[i].Folders==0){
									var id=replaceAll('+','_',replaceAll('%','_',treeMetadata[i].Path));
									if(  document.getElementById('li'+id)!=null && document.getElementById('li'+id)!='undefined' ){
										try{
											var $thisParent=$(document.getElementById('li'+id));
											var $this=$(document.getElementById('a'+id));
											if(hasClass($thisParent)){
												expand($this);
												collapsed($this);
											}else{
												collapsed($this);
												expand($this);
											}
											
										}catch(e){
//											console.log(e.toString());
										}
									}
									$(document.getElementById('sc'+id)).remove();
								//}
								
							}
						}
						$.ajax(
						{
							async:true,
							url:'getscannewsack.fn?sFolder='+pathsACK
						});
					}catch(e){
//						console.log(e.toString());
					}
					setTimeout(getMetadataAsyncronica,2000);
				}else{


					setTimeout(getMetadataAsyncronica,2000);
				}
				
				
			}else{
					displaySelectedFolders();
					setTimeout(getMetadataAsyncronica,5000);
			}
				
			
		}
	
	).fail(
		function (){
			setTimeout(getMetadataAsyncronica,3000);
		}
	); 
}

function InitScan(sFolder){
	$.ajax(
		{
			url:'/cass/startscan.fn?sFolder='+sFolder,
			async:true,
			success:function(data){
			}
		}
	);
}

var slash = /%5C/g;
var slashString = "%5C";
var slashSelected = false;

function selectSlash(folder){
	if (folder != null){
		if ((folder != null) && (folder.length > 0)){
			if ((folder.substring(0,1) == '%') || (folder.substring(0,1) == '/')){
				slash = /%2F/g;
				slashString = "%2F";
			}
			else {
				slash = /%5C/g;
				slashString = "%5C";
			}
			slashSelected = true;
		}
	}
}

function displaySelectedFolders(){
	
	if(scanTreeMode==0)
	try{ 

	$('#popupRecommended').remove();
	$('#popupdiv').append("<ul id='popupRecommended'/>");
	$('#popupRecommended').css({ position:'absolute',left:$('#popup').position().left+$('#popup').outerWidth(),'z-index':500});
	
	$('#popupRecommended').append('<H3>Selected Folders</H3>');


	var charaux=slashString;

	for(var i=0;i<mapJQtree.length;i++){
		var index1=mapJQtree[i].lastIndexOf(charaux);
		var fileName="";
		var path=mapJQtree[i];
		if((mapJQtree[i].length==(index1+3))){
			path=mapJQtree[i].substring(0,index1);
			index1=path.lastIndexOf(charaux);
		}
		fileName=replaceAll('+',' ',decodeURIComponent(path.substring(index1+3)));
		$('#popupRecommended').append("<li>"+fileName+"("+replaceAll('+',' ',decodeURIComponent(mapJQtree[i]))+")</li>");
	}

	/*$('#popupRecommended').append('<H2>Blacklist</H2>');
	for(var i=0;i<blMapJQTree.length;i++){
		$('#popupRecommended').append("<li>"+decodeURIComponent(blMapJQTree[i])+"</li>");
	}*/


}catch(e){}
}

function esHijo(subfolder, folder){
	var countSubfolder = subfolder.match(slash) == null ? 0 : (subfolder.match(slash)).length;
	var countFolder = folder.match(slash) == null ? 0 : (folder.match(slash)).length;
	if (countFolder >= countSubfolder)
		return false;
	else {
		return subfolder.indexOf(folder) == -1 ? false : true;
	}
}

function hayAlgunPadreSeleccionado(folder){
	var hayAlgunPadre = false;
	for(var i=0;i< mapJQtree.length;i++){
		if(esHijo(folder,mapJQtree[i])) {
			hayAlgunPadre=true;
		}	
	}
	return hayAlgunPadre;
}

function agregarFolderScan(folder){
	//alert(folder);
	mapJQtree.push(folder);
}

function agregarFolderBlacklist(folder){
	blMapJQTree.push(folder);
}

function tieneHijosEnBlacklist(folder){
	var hayHijosBlacklist = false;
	for(var i=0;i< blMapJQTree.length;i++){
		if(esHijo(blMapJQTree[i],folder)) {
			hayHijosBlacklist=true;
		}	
	}
	return hayHijosBlacklist;
}

function sacarHijosScan(folder){
	var hijosEnScan = getHijosScan(folder);
	for(var i=0;i< hijosEnScan.length;i++){
		mapJQtree.splice(mapJQtree.indexOf(hijosEnScan[i]), 1);
	}
}

function getHijosScan(folder){
	var hijos = new Array();
	for(var i=0;i< mapJQtree.length;i++){
		if(esHijo(mapJQtree[i],folder)) {
			hijos.push(mapJQtree[i]);
		}	
	}
	return hijos;
}

function sacarHijosBlacklist(folder){
	var hijosEnBlacklist = getHijosBlacklist(folder);
	for(var i=0;i< hijosEnBlacklist.length;i++){
		blMapJQTree.splice(blMapJQTree.indexOf(hijosEnBlacklist[i]), 1);
	}
}

function getHijosBlacklist(folder){
	var hijos = new Array();
	for(var i=0;i< blMapJQTree.length;i++){
		if(esHijo(blMapJQTree[i],folder)) {
			hijos.push(blMapJQTree[i]);
		}	
	}
	return hijos;
}

function estaEnBlacklist(folder){
	return blMapJQTree.indexOf(folder) == -1 ? false : true;
}

function sacarBlacklist(folder){
	if (estaEnBlacklist(folder)){
		blMapJQTree.splice(blMapJQTree.indexOf(folder), 1);
	}
}

function estaEnScan(folder){
	return mapJQtree.indexOf(folder) == -1 ? false : true;
}

function sacarScan(folder){
	if (estaEnScan(folder)){
		mapJQtree.splice(mapJQtree.indexOf(folder), 1);
	}
}

function printFoldersScan(){
	alert('Folders SCAN:');
	for(var i=0;i< mapJQtree.length;i++){
		alert(mapJQtree[i]);
	}
}

function printFoldersBlacklist(){
	alert('Folders BLACKLIST:');
	for(var i=0;i< blMapJQTree.length;i++){
		alert(blMapJQTree[i]);
	}
}

function agregarHijosDirectos(folder){
	var hijos = getHijos(escape(folder));
	for (var i=0; i<hijos.length; i++){
		if(!estaEnScan(hijos[i])){
			agregarFolderScan(hijos[i]);
		}
		if (!estaEnBlacklist(hijos[i])){
			sacarBlacklist(hijos[i]);
		}
	}
}

function tieneHijoEnScanNoDirecto(folder){
	var hijos = getHijosScan(folder);
	var nivelPadre = (folder.match(slash)).length;
	for(var i=0; i< hijos.length; i++){
		var nivelHijo = (hijos[i].match(slash)).length;
		if (nivelHijo > (nivelPadre+1)) 
			return true;
	}
	return false;
}

function tieneHijoEnBlacklistNoDirecto(folder){
	var hijos = getHijosBlacklist(folder);
	var nivelPadre = (folder.match(slash)).length;
	for(var i=0; i< hijos.length; i++){
		var nivelHijo = (hijos[i].match(slash)).length;
		if (nivelHijo > (nivelPadre+1)) 
			return true;
	}
	return false;
}

function getHijosDirectosBlacklist(folder,hijos){
	var hijosBlacklist = new Array();
	for(var i=0; i< hijos.length; i++){
		if (estaEnBlacklist(hijos[i])){
			hijosBlacklist.push(hijos[i]);
		}
	}
	return hijosBlacklist;
}

function getHijosDirectosScan(folder,hijos){
	var hijosScan = new Array();
	for(var i=0; i< hijos.length; i++){
		if (estaEnScan(hijos[i])){
			hijosScan.push(hijos[i]);
		}
	}
	return hijosScan;
}

function hayOtrosFoldersSeleccionados(folder){
	var otherFolders = false;
	for(var i=0;i< mapJQtree.length;i++){
		if (mapJQtree[i] != folder)
			otherFolders = true;
	}
	return otherFolders;
}

function limpiarSelectedFolders(){
	for(var i=0;i< mapJQtree.length;i++){
		if (mapJQtree[i] != folder)
			otherFolders = true;
	}
}


function clearSelectedPath(ck,recursive){
	var folder = $(ck).attr('path');

	if(!ck.checked){ 
		if (!oneOnlyJQtree) {
			var hijos = getHijos(folder);
			if ((hijos.length > 0) && ((getHijosScan(folder).length > 0)  || (hijos.length > getHijosDirectosBlacklist(folder,hijos).length))){
				confirm_ui('Unselect subfolders?',false,folder,ck,false);
			}
			else {
				deseleccionarCheck(ck);
				if (estaEnScan(folder)){
					sacarScan(folder);
				}
				agregarFolderBlacklist(folder);

				displaySelectedFolders();
				actualizarChecks(folder);
			}
		}
		else {
			sacarScan(folder);
    		displaySelectedFolders();
			actualizarChecks(null);
		}
		
	} else{
		if (!oneOnlyJQtree) {
			var hijos = getHijos(folder);
			if (((tieneHijosEnBlacklist(folder)) || (tieneHijoEnScanNoDirecto(folder))) || ((getHijosDirectosBlacklist(folder,hijos) > 0) && (hijos.length > getHijosDirectosScan(folder,hijos).length))) {
				confirm_ui('Select subfolders?', true, folder,ck,false);
			}
			else {
				seleccionarCheck(ck);
				sacarHijosScan(folder);
				sacarBlacklist(folder);
				sacarHijosBlacklist(folder);
				displaySelectedFolders();
				actualizarChecks(folder);
			}
		}
		else {
			if (hayOtrosFoldersSeleccionados(folder)) {
				//confirm_ui('Unselect other folders?',false,folder,ck,true);
				mapJQtree.length = 0;
	    		mapJQtree.push(folder);
	    		displaySelectedFolders();
				actualizarChecks(null);
			}
			else {
				$('Input[path="'+$(ck).attr('path')+'"]').prop('checked','checked');
				agregarFolderScan(folder);
				displaySelectedFolders();
				actualizarChecks(folder);
			}
		}

	}

}

function seleccionarCheck(ck){
	//alert('bp 1');
	var folder = $(ck).attr('path');

	$('Input[path="'+$(ck).attr('path')+'"]').prop('checked','checked');
	//alert('bp 2');
	var padresScan = getPadresEnScan(folder);
	if (padresScan.length == 0){
		agregarFolderScan(folder);
	}
	else {
		var padresBlacklist = getPadresEnBlacklist(folder);
		if(padresBlacklist.length == 0){
			//No agregar, los padres en scan ya escanean este directorio.
		}
		else {
			nivel = (folder.match(slash)).length;
			var x = getFolderPadreMasAbajo(padresBlacklist,nivel);
			var y = getFolderPadreMasAbajo(padresScan,nivel);
			if (x.indexOf(y) > -1){
				agregarFolderScan(folder);
			}
			else {
				//No agregar, los padres en scan ya escanean este directorio.
			} 
		}
	}
}

function deseleccionarCheck(ck){
	$('Input[path="'+$(ck).attr('path')+'"]').prop('checked',false);
}

function confirm_ui(message,select,folder,ck,onlyOne) {

	var callback = null;
	$('#dialog').html(message);
	$("#dialog").dialog("option", "position", "center");
	$("#dialog").dialog({
		closeOnEscape: false,
		dialogClass: 'no-close',
	  buttons : {
	    "Yes" : function() {
	    	if (!onlyOne){
	    		$(this).dialog("close");
	    		callback_confirm_ui(select,true, folder,ck);
	    	}
	    	else {
	    		mapJQtree.length = 0;
	    		mapJQtree.push(folder);
	    		displaySelectedFolders();
				actualizarChecks(null);
	    		$(this).dialog("close");
	    	}
	    },
	    "No" : function() {
	    	if (!onlyOne){
	      		$(this).dialog("close");
	      		callback_confirm_ui(select,false, folder,ck);
	      	} else {
	      		displaySelectedFolders();
				actualizarChecks(folder);
				$(this).dialog("close");
	      	}
	    },
	    Cancel : function() {
    		displaySelectedFolders();
			actualizarChecks(folder);
			$(this).dialog("close");
	    }
	  }
	});

	$("#dialog").dialog("open");
}

function callback_confirm_ui(select,value, folder,ck){
	if (select) {
		seleccionarCheck(ck);
		if(value){
			sacarHijosBlacklist(folder);
			sacarHijosScan(folder);
			sacarBlacklist(folder);
		}
		else {
			var hijos = getHijos(folder);
			for (var i=0; i<hijos.length; i++){
				if ((!estaEnScan(hijos[i])) && (!estaEnBlacklist(hijos[i]))){
					agregarFolderBlacklist(hijos[i]);
				}
				if(estaEnScan(hijos[i])){
					sacarScan(hijos[i]);
				}
			}
			sacarBlacklist(folder);
		}
	}
	else {
		deseleccionarCheck(ck);
		if(value){
			sacarScan(folder);
			agregarFolderBlacklist(folder);
			//Sacar hijos de scan
			var hijosScan = getHijosScan(folder);
			for (var i=0; i< hijosScan.length; i++){
				sacarScan(hijosScan[i]);
			}
		} 
		else {
			if (estaEnBlacklist(folder)){
				sacarBlacklist(folder);
			}
			else {
				agregarFolderBlacklist(folder);
			}
			if (estaEnScan(folder)){
				sacarScan(folder);
			}
			agregarHijosDirectos(folder);
		}
	} 

	displaySelectedFolders();
	actualizarChecks(folder);
}

function getPadresEnBlacklist(folder){
	var padres = new Array();
	for(var i=0;i< blMapJQTree.length;i++){
		if(esHijo(folder,blMapJQTree[i])) {
			padres.push(blMapJQTree[i]);
		}	
	}
	return padres;
}

function getPadresEnScan(folder){
	var padres = new Array();
	for(var i=0;i< mapJQtree.length;i++){
		if(esHijo(folder,mapJQtree[i])) {
			padres.push(mapJQtree[i]);
		}	
	}
	return padres;
}

function getFolderMasAbajo(lista){
	if (lista.length > 0){
		var masAbajo = lista[0];
		var count = masAbajo.match(slash) == null ? 0 : (masAbajo.match(slash)).length;
		if (lista.length > 1){
			for (var i=1; i<lista.length; i++){
				var aux = lista[i];
				var countAux = aux.match(slash) == null ? 0 : (aux.match(slash)).length;
				if (countAux > count){
					masAbajo = aux;
				}
				
			}
			return masAbajo;
		}
		else{
			return masAbajo;
			
		}
	}
	else{
		return null;
	}
	
}

function getFolderPadreMasAbajo(lista,nivel){
	if (lista.length > 0){
		var masAbajo = lista[0];
		var count = masAbajo.match(slash) == null ? 0 : (masAbajo.match(slash)).length;
		if (lista.length > 1){
			for (var i=1; i<lista.length; i++){
				var aux = lista[i];
				var countAux = aux.match(slash) == null ? 0 : (aux.match(slash)).length;
				if (((countAux > count) && (countAux < nivel)) || (count >= nivel)){
					masAbajo = aux;
				}
				
			}
			return masAbajo;
		}
		else{
			return masAbajo;
			
		}
	}
	else{
		return null;
	}
	
}

function tieneHijoEnScan(folder){
	for(var i=0;i< mapJQtree.length;i++){
		if ((mapJQtree[i].indexOf(folder) != -1) && (mapJQtree[i] != folder))
			return true;
	}
	return false;
}

function actualizarSubfoldersSelected(){
	var empezar = 'Input[ck="10"]';
	$(empezar).each(function(){

		var folder = $(this).attr('path');

		var padresScan = getPadresEnScan(folder);
		var padresBlacklist = getPadresEnBlacklist(folder);
		//Mark subfolders selected
		if (tieneHijoEnScan(folder)){
			if ($(this).parent().hasClass('subfoldersexpanded')){
				$(this).parent().removeClass('subfoldersexpanded').addClass('subDirSelExpanded');
			}
			else if ($(this).parent().hasClass('subfolderscollapsed')){
				$(this).parent().removeClass('subfolderscollapsed').addClass('subDirSelCollapsed');
			}
		}
		else {
			if ($(this).parent().hasClass('subDirSelExpanded')){
				$(this).parent().removeClass('subDirSelExpanded').addClass('subfoldersexpanded');
			}
			else if ($(this).parent().hasClass('subDirSelCollapsed')){
				$(this).parent().removeClass('subDirSelCollapsed').addClass('subfolderscollapsed');
			}
		}
	});
}

function actualizarChecks(raiz){
	if (!markInitialized){
		raiz = null;
		markInitialized = true;
	}
	var empezar = 'Input[ck="10"]';
	if (raiz != null)
		empezar = 'Input[path^="'+raiz+'"]';
	
	$(empezar).each(function(){
		var folder = $(this).attr('path');
		if (!oneOnlyJQtree) {
			if ((estaEnScan(folder)) && (!(estaEnBlacklist(folder)))){
				$(this).prop('checked','checked');
				
				
			}
			else {
				if (estaEnBlacklist(folder)){
					$(this).prop('checked',false);
				}
				else {
					var padresBlacklist = getPadresEnBlacklist(folder);
					if (padresBlacklist.length > 0){
						var padresScan = getPadresEnScan(folder);
						if (padresScan.length == 0) {
							$(this).prop('checked',false);
						}
						else {
							var x = getFolderMasAbajo(padresBlacklist);
							var y = getFolderMasAbajo(padresScan);
							if (x.indexOf(y) > -1){
								$(this).prop('checked',false);
							}
							else {
								$(this).prop('checked','checked');
							} 
						}
					}
					else {
						var padresScan = getPadresEnScan(folder);
						if (padresScan.length > 0){
							$(this).prop('checked','checked');
						}
						else {
							$(this).prop('checked',false);
						}
					}
				}
			}
		} else {
			if (estaEnScan(folder)){
				$(this).prop('checked','checked');
			}
			else {
				$(this).prop('checked',false);
			}
		}


	});

	actualizarSubfoldersSelected();
	
}

function markChildren(parentPath){
	/*$('Input[pathparent="'+parentPath+'"]').each(
		function (){
			$(this)[0].checked=true;
			$(this).prop('checked','checked');
			clearSelectedPath($(this)[0]);
		}
	);*/
}

function unMarkChildren(parentPath){
	/*$('Input[pathparent="'+parentPath+'"]').each(
		function (){
			$(this)[0].checked=false;
			$(this).prop('checked',false);
			clearSelectedPath($(this)[0],true);
		}
	);*/
}


function scanning(){
		if($('#scanning').length>0){
			if($('#scanning').html()=="..."){
				$('#scanning').html('');
			}else
			if($('#scanning').html()==""){
				$('#scanning').html('.');
			}else
			if($('#scanning').html()=="."){
				$('#scanning').html('..');
			}
			else
			if($('#scanning').html()==".."){
				$('#scanning').html('...');
			}

		}

		setTimeout(scanning,500);
}

//getMetadataAsyncronica();
setTimeout(scanning,500);

var dirElTree=null;
//END TAD TREE METADATA

//FUNCIONES PROPIAS DEL TREE
 function hasClass($thisParent){
	return $thisParent.hasClass('collapsed') ||
		   $thisParent.hasClass('subfolderscollapsed') ||
		    $thisParent.hasClass('subDirSelCollapsed') ||
			$thisParent.hasClass('subfolderscollapsedvarios') ||
			 $thisParent.hasClass('subfolderscollapsedoffice') ||
			 $thisParent.hasClass('subfolderscollapsedfoto') ||
			 $thisParent.hasClass('subfolderscollapsedvideo') ||
			 $thisParent.hasClass('subfolderscollapsedaudio') ||
			 $thisParent.hasClass('subfolderscollapsedothers') ||
			  $thisParent.hasClass('subfolderscollapsedvarios_ss') ||
			 $thisParent.hasClass('subfolderscollapsedoffice_ss') ||
			 $thisParent.hasClass('subfolderscollapsedfoto_ss') ||
			 $thisParent.hasClass('subfolderscollapsedvideo_ss') ||
			 $thisParent.hasClass('subfolderscollapsedaudio_ss') ||
			 $thisParent.hasClass('subfolderscollapsedothers_ss');
}


function expand($this){
	//alert("10_"+mapJQtree.length);
	actualizarChecks($this.attr('rel'));
	var office=haySeccion($this,'Documents');
	var videos=haySeccion($this,'Video Files');
	var audios=haySeccion($this,'Audio Files');
	var fotos=haySeccion($this,'Image Files');
	var other=haySeccion($this,'Other');
	var or=office||videos||audios||fotos||other;
	var andAux=[office,videos,audios,fotos,other];
	var count=0;
	var ss= (getMetadataObject($this.attr('rel'))!=null && getMetadataObject($this.attr('rel')).Folders>0)?"":"_ss";
	for(var j=0;  j<andAux.length;j++){
		if(andAux[j]) count++;
	}
	var and=count>1;
	if(or){//Por lo menos una categoria
		$this.parent().removeClass('subfolderscollapsed'+getCSSName()+ss).addClass('subfoldersexpanded'+getCSSName()+ss);
		
	}else if( (getMetadataObject($this.attr('rel'))!=null && getMetadataObject($this.attr('rel')).Folders>0) || $this.parent().hasClass('subfolderscollapsed')){
		$this.parent().removeClass('subfolderscollapsed').addClass('subfoldersexpanded');
	}else if( $this.parent().hasClass('subDirSelCollapsed')){
		$this.parent().removeClass('subDirSelCollapsed').addClass('subDirSelExpanded');
	}else{
		$this.parent().removeClass('collapsed').addClass('expanded');
	}
	//alert("11_"+mapJQtree.length);
	function getCSSName(){
		if(and){
			return "varios";
		}
		if(office){
			return "office";
		}
		if(videos){
			return "video";
		}				
		if(fotos){
			return "foto";
		}						
		if(other){
			return "others";
		}
		return "";
	}
}

function haySeccion($this,secTxt){
	var sec=getMetadata($this.attr('rel'),secTxt);
	var hay=false;
	if(sec!=null && sec!='undefined' && sec.Exts!=null && sec.Exts!='undefined' )
	for(var j=0; !hay && j<sec.Exts.length;j++){
		hay= sec.Exts[j].Count>0;
	}
	return hay;
}


function collapsed($this){
	var office=haySeccion($this,'Documents');
	var videos=haySeccion($this,'Video Files');
	var audios=haySeccion($this,'Audio Files');
	var fotos=haySeccion($this,'Image Files');
	var other=haySeccion($this,'Other');
	var or=office||videos||audios||fotos||other;
	var andAux=[office,videos,audios,fotos,other];
	var count=0;
	var ss= getMetadataObject($this.attr('rel')).Folders>0?"":"_ss";
	for(var j=0;  j<andAux.length;j++){
		if(andAux[j]) count++;
	}
	var and=count>1;
	if(or){//Por lo menos una categoria
		$this.parent().removeClass('subfoldersexpanded'+getCSSName()+ss).addClass('subfolderscollapsed'+getCSSName()+ss);
		
	}else if(( getMetadataObject($this.attr('rel'))!=null && getMetadataObject($this.attr('rel')).Folders>0 )|| $this.parent().hasClass('subfoldersexpanded')){
		$this.parent().removeClass('subfoldersexpanded').addClass('subfolderscollapsed');
	}else if($this.parent().hasClass('subDirSelExpanded')){
		$this.parent().removeClass('subDirSelExpanded').addClass('subDirSelCollapsed');
	}else{
		$this.parent().removeClass('expanded').addClass('collapsed');
	}
	
	
	
	function getCSSName(){
		if(and){
			return "varios";
		}
		if(office){
			return "office";
		}
		if(videos){
			return "video";
		}				
		if(fotos){
			return "foto";
		}						
		if(other){
			return "others";
		}
		return "";
	}
}


function markSelected(){
	
	$('.selSubFolderName').each(
		function(){
			$(this).removeClass('selSubFolderName');
		}
	);
	
	for(var i=0; i<mapJQtree.length;i++){
		var c=0;
		$('Input[ck="10"]').each(function(){
			if($(this).attr('path').toLowerCase()==mapJQtree[i].toLowerCase()){
				$(this).prop('checked','checked');
			}
		});
	}
	
	for(var i=0; i<blMapJQTree.length;i++){
		var c=0;
		$('Input[ck="10"]').each(function(){
			if($(this).attr('path').toLowerCase()==blMapJQTree[i].toLowerCase()){
				$(this).prop('checked',false);
			}
		});
		 
	}
	
	intermediate();
	
	
}

function intermediate(){
		
	$('.subDirSelCollapsed').each( function(){
			if(!$(this).hasClass('subfolderscollapsed')){
				$(this).removeClass('subDirSelCollapsed').addClass('subfolderscollapsed');
			}else{
				$(this).removeClass('subDirSelCollapsed');
			}
		}
	);
	
	$('.subDirSelExpanded').each( function(){
			if(!$(this).hasClass('subfoldersexpanded')){
				$(this).removeClass('subDirSelExpanded').addClass('subfoldersexpanded');
			}else{
				$(this).removeClass('subDirSelExpanded');
			}
			
		}
	);
	
	$('.selSubFolderName').each(
		function(){
			$(this).removeClass('selSubFolderName');
		}
	);
	
	$('Input[ck="10"]').each(function(){
			if($(this).prop('checked')){
				var ok=false;
				$(this).parent().find('A').each(
					function(){
						if(!ok){
							$(this).addClass('selSubFolderName');
							ok=true;
						}
					}
				);
			}
		});
	
	for(var i=0;i<mapJQtree.length;i++){
		$('Input[ck=10]').each( function(){
			if(mapJQtree[i].toLowerCase()!=$(this).attr('path').toLowerCase()){
				if( mapJQtree[i].toLowerCase().indexOf($(this).attr('path').toLowerCase())==0){
					if($(this).parent().hasClass('subfolderscollapsed')){
						$(this).parent().removeClass('subfolderscollapsed').addClass('subDirSelCollapsed');
						var ok=false;
						$(this).parent().find('A').each(
							function(){
								if(!ok){
									$(this).addClass('selSubFolderName');
									ok=true;
								}
							}
						);
							
					}
					if($(this).parent().hasClass('subfoldersexpanded')){
						$(this).parent().removeClass('subfoldersexpanded').addClass('subDirSelExpanded');
							var ok=false;
							$(this).parent().find('A').each(
								function(){
									if(!ok){
										$(this).addClass('selSubFolderName');
										ok=true;
									}
								}
							);
					}
					
				}
			}
		});
	}

}

function cursorAuto(){
	$('*', window.parent.parent.NAVBAR.document).css("cursor", "auto");
	$('*', window.parent.SIDEBAR.document).css("cursor", "auto");
	$('*', window.parent.MAIN.document).css("cursor", "auto");
	$('*', window.parent.parent.MAIN1.document).css("cursor", "auto");
}

function cursorWait(){
	$('*', window.parent.parent.NAVBAR.document).css("cursor", "wait");
	$('*', window.parent.SIDEBAR.document).css("cursor", "wait");
	$('*', window.parent.MAIN.document).css("cursor", "wait");
	$('*', window.parent.parent.MAIN1.document).css("cursor", "wait");
}


function getHijos(folder) {
	var listaHijos = new Array();
	$.ajax({async:false,url:'/cass/getfolders.fn'+"?sFolder="+folder+"&bFolderSel=off"}).done(
	function(data){
		var lista = data.match(/\"Path\":\"(.*?)\"/g);
		if (lista != null){
			for (var i=0; i<lista.length; i++){
				var pathAux = lista[i].split(":")[1];
				var path = pathAux.replace(/\"/g,'');
				listaHijos.push(path);
			}
		}
	});
	return listaHijos;
}



if(jQuery) (function($){
	
	$.extend($.fn, {
		fileTree: function(oneOnly,el,o, map,blMap,h) {
			// Defaults
			
			cursorWait();
			if( !o ) var o = {};
			if( o.root == undefined ) o.root = '/';
			if( o.script == undefined ) o.script = 'jqueryFileTree.php';
			if( o.folderEvent == undefined ) o.folderEvent = 'click';
			if( o.expandSpeed == undefined ) o.expandSpeed= 10;
			if( o.collapseSpeed == undefined ) o.collapseSpeed= 10;
			if( o.expandEasing == undefined ) o.expandEasing = null;
			if( o.collapseEasing == undefined ) o.collapseEasing = null;
			if( o.multiFolder == undefined ) o.multiFolder = true;
			if( o.loadMessage == undefined ) o.loadMessage = 'Loading...';
			dirElTree=el;
			treeMetadata=new Array();
			if(map!=null && map !="undefined"){
				mapJQtree=map;
			}else{
				mapJQtree=new Array();
			}
			if(blMap!=null && blMap !="undefined"){
				blMapJQTree=blMap;
			}else{
				blMapJQTree=new Array();
			}
			
			oneOnlyJQtree=oneOnly;
			

			$(this).each( function() {
				
				function showTree(c, t,checked,$this) {
					//alert('entro aca');
					//alert("4_"+mapJQtree.length);
					$(c).addClass('wait');
					$(".jqueryFileTree.start").remove();
					
					$.ajax({async:true,url:o.script+"?sFolder="+t+"&bFolderSel="+((checked && !oneOnlyJQtree)?"on":"off")}).done(
					function(data){
						//alert("5_1"+mapJQtree.length);
						$(c).find('.start').html('');
						//alert("5_2"+mapJQtree.length);
						//alert(data);
						$(c).removeClass('wait').append(data);
						//alert("5_3"+mapJQtree.length);
						if( o.root == decodeURIComponent(t.replace(/\+/g,  " "))) 
							$(c).find('UL:hidden').show(); 
						else 
							$(c).find('UL:hidden').slideDown({ duration: o.expandSpeed, easing: o.expandEasing });
						//alert("5_4"+mapJQtree.length);
						bindTree(c);
						//alert("5_5"+mapJQtree.length);
						if($this!=null && $this!='undefined'){
							expand($this);
						}
						//alert("5_7"+mapJQtree.length);
						//alert("6_"+mapJQtree.length);
						//markSelected();				
					
						//alert("7_"+mapJQtree.length);

						setTimeout(cursorAuto,500);
						if (!slashSelected){
							selectSlash(t);
						}
						displaySelectedFolders();
						actualizarChecks(t);
						 //alert("8_"+mapJQtree.length);
					});
				}
				
				function bindTree(t) {
				    $(t).find('LI').bind(o.folderEvent,
						function(event){ 
							event.preventDefault();
							event.stopPropagation();
							$(this).find('> A').click();
						}
						);
					
					$(t).find('Input[type=checkbox]').click(function(e){e.stopPropagation();});
					 
					$(t).find('LI A').bind(o.folderEvent,aux );
					
					function aux(event) {
						event.preventDefault();
						event.stopPropagation();
						cursorWait();
						//alert("1_"+mapJQtree.length);
						if( $(this).parent().hasClass('directory') ) {
							if(  hasClass( $(this).parent()) ) {
								// Expand
								
								if( !o.multiFolder ) {
									$(this).parent().parent().find('UL').slideUp({ duration: o.collapseSpeed, easing: o.collapseEasing });
									$(this).parent().parent().find('LI.directory').removeClass('expanded').addClass('collapsed');
								}
								$(this).parent().find('UL').remove(); // cleanup
								var checked=$('Input[path="'+$(this).attr('rel')+'"]').is(':checked');
								
								//alert('Aca es el problema');
								showTree( $(this).parent(), escape($(this).attr('rel')),checked , $(this));
								//alert("2_"+mapJQtree.length);
							} else {
								// Collapse
								$(this).parent().find('UL').slideUp({ duration: o.collapseSpeed, easing: o.collapseEasing });
								 
								collapsed($(this));
								cursorAuto();
							}
							//alert("3_"+mapJQtree.length);
						} else {
							h($(this).attr('rel'));
						}
						return false;
					}
					
					// Prevent A from triggering the # on non-click events
					if( o.folderEvent.toLowerCase != 'click' ) $(t).find('LI A').bind('click', function() { return false; });
				}
				// Loading message
				
				$(this).append('<ul id="jqueryFileTree" class="jqueryFileTree start"><li class="wait">' + o.loadMessage + '<li></ul>');
				// Get the initial file list
				markInitialized = false;
				showTree( $(this), escape(o.root),false );
				 
				
			});
		}
	});
	
})(jQuery);