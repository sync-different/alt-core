var odir='';
var hdir=function(){};
var $thisDir=null;
var dirMap=null;
var blMap=null;
var dirOneOnly=false;
var dirEl=null;
var selUnitOld=null;
var dirfCB=function(){};

if(jQuery) (function($){
	
	$.extend($.fn, {
		fileTreeAlterante: function(el,map,excludeunits,exclusiveInclude, oneOnly,mapBL,rootfolder,h) {
			var o={root:'',script:'/cass/getfolders.fn'};
			odir=o;
			dirMap=map;
			if(h != "undefined" && h != null){
				hdir=h;
			}else{
				hdir=function(){};
			}
			if(el!=null && el !='undefined'){
				dirEl=el;
			}else{
				dirEl=$(this)[0];
			}
			if(oneOnly!=null && oneOnly!='undefined'){
				dirOneOnly=oneOnly;
			}else{
				dirOneOnly=false;
			}
			
			if(mapBL!=null && mapBL!='undefined'){
				blMap=mapBL;
			}else{
				blMap=new Array();
			}
			
			selUnitOld=null;
			$thisDir=$(this);
			
			cursorWait();
			
			$.ajax({async:true,url:'/cass/getfolders.fn?sFolder=units'}).done( 
			function(data){
				var app='<table><tr valign="top"><td><select onchange="selUnit();" id="unit"><option value="unit">Select Device</option>';
			
                                if(rootfolder){
                                    app='<table><tr valign="top"><td> Choose subfolder of ' + decodeURIComponent(rootfolder);
                                }
                                        
				var aAux=$.parseJSON(data);
				var selected="unit";
				var unix=aAux[0].indexOf('/')==0;
				if(exclusiveInclude!=null && exclusiveInclude!='undefined' && exclusiveInclude.length>0){
						var ok=true;
						
						for(var j=0; j<exclusiveInclude.length && ok;j++ ){
							for(var i=0;i<aAux.length && ok;i++){
								if(unix){
									 
									ok = !(decodeURIComponent(exclusiveInclude[j].indexOf(aAux[i]))==0);
								}else{
									ok = !(exclusiveInclude[j].substring(0,1).toLowerCase()==aAux[i].substring(0,1).toLowerCase());
								}
								if(!ok){
									if(!unix){
										app+='<option value="'+aAux[i].toLowerCase()+'">'+aAux[i]+'</option>';
										selected=aAux[i].toLowerCase();
									}else{
										app+='<option value="'+aAux[i]+'">'+aAux[i]+'</option>';
										selected=aAux[i];
									}
								}
							}
						}
				}else{
					for(var i=0;i<aAux.length;i++){ 
						var ok=true;
						
						//Se controla que no se tenga que exluir la unidad
						if(excludeunits!=null && excludeunits!='undefined'){
							for(var j=0; j<excludeunits.length && ok;j++ ){
								if(unix){
									ok = !(decodeURIComponent(excludeunits[j]).indexOf(aAux[i])==0);
								}else{
									ok = !(excludeunits[j].toLowerCase()==aAux[i].substring(0,1).toLowerCase());
								}
							}
						}
						if(ok){
							if(!unix){
								app+='<option value="'+aAux[i].toLowerCase()+'">'+aAux[i]+'</option>';
							}else{
								app+='<option value="'+aAux[i]+'">'+aAux[i]+'</option>';
							}
						}
					}
				}
//				app+='</select></td><td><button id="prebutton" type="button" class="btn btn-primary" onclick="closeTree();">Close</button></td></tr></table>';
				$thisDir.append(app);
				
				
				//Si ya tiene seleccion se bloquea el cambio de unidad	
				if(dirMap.length>0){
					
					if(unix){		
						if(decodeURIComponent(dirMap[0]).indexOf('/Volumes')==0 || decodeURIComponent(dirMap[0]).indexOf('/media')==0){
							$('#unit').val(decodeURIComponent(dirMap[0]).substring(0,decodeURIComponent(dirMap[0]).indexOf('/',9) ));
						}else{
							$('#unit').val('/');
						}
						
					}else{
                                                $('#unit option[value="unit"]').remove();
						$('#unit').val(decodeURIComponent(dirMap[0]).substring(0,3).toLowerCase());
					}
				 
					//$('#unit').prop('disabled', 'disabled');
					selUnit(rootfolder);
				}else if(selected!='unit'){
					$('#unit').val(selected);
					//$('#unit').prop('disabled', 'disabled');                                        
					selUnit(rootfolder);
				}
				
				setTimeout(cursorAuto,500);
			});	
			
			
			
		}});
		
})(jQuery);

function tree(el,map,excludeunits,exclusiveInclude,onlyOne,mapBL,fCB,rootfolder){
		dirfCB=fCB;
//		$('#buttos').css({position:'fixed',left:'60%',top:'40%'});
		popup(el,
			function(){
				$('#popup').fileTreeAlterante(el,map,excludeunits,exclusiveInclude,onlyOne,mapBL,rootfolder);
			}
		);
	}
	
function cursorAuto(){
	$('*', window.parent.parent.NAVBAR.document).css("cursor", "auto");
	$('*', window.parent.SIDEBAR.document).css("cursor", "auto");
	$('*', window.parent.MAIN.document).css("cursor", "auto");
	$('*', window.parent.parent.MAIN1.document).css("cursor", "auto");
}

function cursorWait(){
        if(window.parent.NAVBAR){
            $('*', window.parent.parent.NAVBAR.document).css("cursor", "wait");
        }
        if(window.parent.SIDEBAR){
            $('*', window.parent.SIDEBAR.document).css("cursor", "wait");
        }
        if(window.parent.MAIN){
            $('*', window.parent.MAIN.document).css("cursor", "wait");
        }
        if(window.parent.parent.MAIN1){
            $('*', window.parent.parent.MAIN1.document).css("cursor", "wait");
        }
}

function popup(el,f){
	cursorWait();	
	$('#popup').remove();
	var div=$('<div id="popup"  />');
	div.css({ position:'absolute','z-index':6000});
	$("#popupdiv").append(div);
//	div.hide();
//	div.show(f);
        f();
        $( "#popupdiv" ).dialog( "open" );
}
	

function getSelectedPaths(){
	return dirMap;
}

 

function selUnit(rootfolder){
	
	var ok=true;	
	if(selUnitOld!=null){
		var folderSelected=false;
		for(var i=0;!folderSelected && i<dirMap.length;i++){
			if(dirMap[i].toLowerCase().indexOf(selUnitOld.toLowerCase())==0){
				folderSelected=true;
			}
		}
		if(folderSelected){
			var ok=confirm("Are you sure?, selected data lost");
			if(ok){
				dirMap.splice(0,dirMap.length);
				clearRecommendedAndClean();
			}else{
                            $('#unit').val(odir.root);
                        }
		}
	}

	if(ok){ 	
                if(rootfolder) {
                    odir.root= decodeURIComponent(rootfolder);
                }else{
                    odir.root=$('#unit').val();
                }
		
		if(odir.root!='unit'){
			selUnitOld=encodeURIComponent(odir.root);
			$('.jqueryFileTree').remove();
			cursorWait();
			$.ajax(
			{ async:false,
			  url:'initscan.fn'	
			}).done(
				function(data){
					$.ajax(
						{
							async:true,
							url:'startscan.fn?sFolder='+ encodeURIComponent(odir.root)
						}
					).done(function(data){
						$thisDir.fileTree(dirOneOnly,dirEl,odir,dirMap,blMap,hdir);
						
					});
				}
			);
			
			
		}else{
			$('.jqueryFileTree').remove();
			$('#popupRecommended').remove();

			clearRecommended();
			 
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
}

function closeTree(){
	if($thisDir!=null){
                $("#popupdiv" ).dialog( "close");
		$thisDir.remove();
		$('#popupRecommended').remove();
		clearRecommended();
		 
		$.ajax(
			{
				url:'/cass/initscan.fn',
				async:true
			}
		).done(
			function(){
			}
		);	

//		$('#buttos').css({position:'relative'});
		if(dirfCB!=null && dirfCB!='undefined'){
			dirfCB();
		}
		var unSelected=
		$('Input[ck="10"]').each(function(){
			if($(this).attr('path').toLowerCase()==blMapJQTree[i].toLowerCase()){
				$(this).prop('checked',false);
			}
		});
	}
}
 
