angular.module('app.controllers').controller('MyFilesController', function (Conf,ngProgressLite,$sce, $routeParams,$modal,$anchorScroll,$filter,$compile,$location,$rootScope,$scope) {
	ngProgressLite.set(Math.random());
	ngProgressLite.start();
	$scope.getCookie=getCookie;
	$rootScope.mnu='myfiles';
	$scope.padding=isIE?10:0;
	$("#modals").html($compile(getPartial("partialModals.htm"))($scope));
	
	$scope.isCluster=QueryString.cluster!=undefined;
	$scope.isLocalhost=document.location.href.indexOf("localhost")>=0;
	$scope.isCloud=document.location.href.indexOf("web.alterante.com")>=0;
	
	if($scope.isCluster){
		$("#aaudioopen").hide();
		$("#afolderaudio").hide();
		$("#aaudiodownload").hide();
	}
	$scope.dec=function(str){
		if(str==undefined) return;
		var aux=decodeURIComponent(str);
		aux=aux.replace(/\+/g, " ");
		return aux;		
	}
	$scope.dec2=function(str){
		if(str==undefined) return;
		var aux=str;
		aux=aux.replace(/\+/g, "%20");
		return aux;		
	}
	$scope.dec3=function(str){
		if(str==undefined) return;
		var aux=str;
		aux=replaceAll('%0A%0D','<br>',str);
		return $scope.dec(aux);		
	}
	$rootScope.alerts = [];
	$rootScope.closeAlert = function(index) {
		$scope.alerts.splice(index, 1);
	};
	$rootScope.addAlert = function(msg,type) {
		$scope.alerts.splice(0, $scope.alerts.length);
		$scope.alerts.push({msg:msg,type:type});
	};
	$rootScope.clearAlerts=function(){
		$scope.alerts.splice(0, $scope.alerts.length);
	}
	$(document).click(
		function(){
			$scope.showOrderList=false;
			$scope.showPlayList=false;
		}
	)
	
	if($location.path().indexOf("grid")>0){
		fileView="grid";
		$rootScope.fileView="grid";
	}else{
		$rootScope.fileView="list";
		fileView="list";
	}
	
	$("#backToTop").hide().click(
		function(){
				$("html,body").animate({scrollTop:0});
		}
	);
	//$("#topbarheader,#topbarcommands2").hide();
	
	$rootScope.order=sortOrder;
	
	if(sortOrder=="Asc"){
		$scope.sDate=true;
		$scope.dateReverse=false;
	}
	if(sortOrder=="Desc"){
		$scope.sDate=true;
		$scope.dateReverse=true;
	}
			
	$("#topbar").hide();
	$("#loading").hide();
	
	
	$scope.lastDate=null;
	$scope.list=null;
	$('#txtSearch').unbind("enterKey");
	$('#txtSearch').bind("enterKey",function(e){
		$rootScope.mnu='myfiles';
		
		$("html,body").animate({scrollTop:0});
		if($location.path().indexOf("MyFiles")<0){
			if(fileView=="list"){
				$location.path("/MyFiles/"+$scope.ftype+"/"+$scope.range);	
			}else{
				$location.path("/MyFiles/grid/"+$scope.ftype+"/"+$scope.range);	
			}
			
		}else{
			$scope.lastDate=null;
			$scope.list=null;
			$(window).data('ajaxready', true);
			$scope.loadQuery();
		}
		
	});

	$(document).unbind("tagshare");
	$(document).bind("tagshare",function(e){
		 $scope.selShare();
	});
				  
	 $scope.sortColumn = function(predicate, reverse) {
		$scope.list = $filter('orderBy')($scope.list, predicate, reverse);
	 };
				  
	
	$rootScope.setOrder=function(order){
		$rootScope.order=order;
		sortOrder=order;
		if(sortOrder=="Asc"){
			$scope.sDate=true;
			$scope.dateReverse=false;
		}
		if(sortOrder=="Desc"){
			$scope.sDate=true;
			$scope.dateReverse=true;
		}
		$('#txtSearch').trigger("enterKey");
		$("html,body").animate({scrollTop:0});
	}
	
					
	$scope.loadQuery=function(inasync){
		ngProgressLite.set(Math.random());
		ngProgressLite.start();
		var async=inasync!=null && inasync!=undefined?inasync:true;//$(window).data('ajaxready')==false;
		$("#loading").show();
		var countObject=30;
		var screenWidth=getWindowData()[0]-120;
		var sreenHeigth=getWindowData()[1]-50;
		if(fileView=="list"){
			countObject=sreenHeigth / 75 + 5;
		}else{
			var numCols=screenWidth/200;
			countObject= sreenHeigth/200;
			countObject=countObject*numCols+5;	
		}
		countObject=Math.floor(countObject);
		console.log("INIT SCROLL:"+new Date().toString()+" M:"+new Date().getMilliseconds());
		$.ajax(
			{
				url:Conf.urls.query.principal+"?order="+$scope.order+(QueryString.cluster!=undefined?"&cluster="+QueryString.cluster:"")+"&screenSize="+$("#listContent").width()+"&ftype="+$scope.ftype+"&foo="+$("#txtSearch").val()+"&days="+($scope.range=='.all'?"":$scope.range)+"&view=json&numobj="+countObject+($scope.lastDate!=null?"&date="+replaceAll('%20','+',escape($scope.lastDate.toString())):""),
				dataType:'json',
				async:async,
				success:function(data){
					if(data.fighters.length==0){
						$(window).data('ajaxready', false);
						$("#loading").hide();
						ngProgressLite.done();
						return;
					}
					$scope.lastDate=data.fighters[data.fighters.length-1].file_date;
					if($scope.list==null){
						$scope.list=data.fighters;	
					}else{
						//$scope.list=$scope.list.concat(data.fighters);	
						const uniqueFighters = data.fighters.filter(fighter => !$scope.list.some(existing => existing.nickname === fighter.nickname)
						);
						$scope.list = $scope.list.concat(uniqueFighters);


					}
					
					loadLeftMenuValues($scope.ftype,$scope.range,data.objFound);
						
					function tags(){
							$('input[tag="tags"]').each(
									function(){
										$(this).tagsinput({tagClass:function(item){return "label-tag";}});
										var tthis=$(this);
										$(this).on('itemAdded', function(event) {
											$scope.applyTag(tthis.attr('md5'),event.item);
										});
										$(this).on('itemRemoved', function(event) {
											$scope.removeTag(tthis.attr('md5'),event.item);
										});
									}
								);
					}
					
					if(async){
						$rootScope.$apply(
							function(){
								try{
									
									//Caso click en eventos
									if($rootScope.playFile!=null){
										$scope.play($rootScope.playFile);
										$rootScope.playFile=null;
									}
									
									setTimeout(tags	,100);
									$(window).data('ajaxready', true);
									$("#loading").hide();
									console.log("FIN SCROLL:"+new Date().toString()+" M:"+new Date().getMilliseconds());
								}catch(e){
									
								}
							}
						);
					}else{
						//Caso click en eventos
						if($rootScope.playFile!=null){
							$scope.play($rootScope.playFile);
							$rootScope.playFile=null;
						}

						setTimeout(tags	,100);
						$(window).data('ajaxready', true);
						$("#loading").hide();
					}	
						
					ngProgressLite.done();
					//});
				}
			}
		)
	}
	
	$scope.applyTag=function(md5,tag){
		if(md5==undefined || md5==null || tag==undefined || tag==null ){
			return;
		}
		
		$("#loading").show();
		$.ajax(
			{
				url:Conf.urls.query.applyTag+"?tag="+tag+"&"+md5+"=on",
				dataType:'json',
				sync:true,
				success:function(data){
					$scope.alerts.splice(0,$scope.alerts.length);
					$scope.alerts.push({type:'success',msg:'Apply Tag in file success'});
					$("#loading").hide();
					$(document).trigger('updateFilterTags');
					for(var i=0; i<$scope.list.length;i++){
						if($scope.list[i].nickname==md5){
							$scope.list[i].file_tags+=","+tag;
							break;
						}
					}
				}
			}
		)
	}
	
	$scope.removeTag=function(md5,tag){
		if(md5==undefined || md5==null || tag==undefined || tag==null ){
			return;
		}
		$("#loading").show();
		$.ajax(
			{
				url:Conf.urls.query.applyTag+"?tag="+tag+"&DeleteTag="+md5,
				dataType:'json',
				sync:true,
				success:function(data){
					$scope.alerts.splice(0,$scope.alerts.length);
					$scope.alerts.push({type:'success',msg:'Remove Tag from file success'});
					$("#loading").hide();
					$(document).trigger('updateFilterTags');
					for(var i=0; i<$scope.list.length;i++){
						if($scope.list[i].nickname==md5){
							$scope.list[i].file_tags=$scope.list[i].file_tags.replace(tag,"");
							break;
						}
					}
				}
			}
		)
	}
	
	$scope.fileSelect=function(md5){
		$("i[md5='"+md5+"']").each(
			function(){
				if($(this).attr("sel")=="true"){
					$(this).attr("sel",false);
					$(this).removeClass("glyphicon-check");
					$(this).addClass("glyphicon-unchecked");
				}else{
					$(this).removeClass("glyphicon-unchecked");
					$(this).addClass("glyphicon-check");
					$(this).attr("sel",true);
				}
				if($rootScope.fileView=="grid"){
					if($(this).attr("sel")=="true"){
						$(this).parent().css("background-color","rgb(73, 144, 226)").find("table[tag='label']").css("background-color","rgb(73, 144, 226)");	
					}else{
						$(this).parent().css("background-color","white").find("table[tag='label']").css("background-color","rgb(232,232,232)");
					}
					
				}
			}
		);
		
		var cntSel=$("i[sel='true']").length;
		if(cntSel>0){
			$scope.alerts.splice(0,$scope.alerts.length);
			$("#topbar").show();
			$scope.filesSelected=cntSel;
		}else{
			$("#topbar").hide();
		}
		
	}
	
	$scope.isImage=function(file){
		return file.file_group=='photo';
	}
	
	$scope.isAudio=function(file){
		return file.file_group=='music';
	}
	
	$scope.selOpCheck=function(md5){
		var cntSel=$("input[tag='optcheck']:checked").length;
		if(cntSel>0){
			$scope.alerts.splice(0,$scope.alerts.length);
			$("#topbar").show();
			$rootScope.filesSelected=cntSel;
		}else{
			$("#topbar").hide();
		}
	}
	
	$rootScope.selAllChange=function(){
		$rootScope.selAllAttr=!$rootScope.selAllAttr;
		if($rootScope.fileView=="list"){
			$("input[tag='optcheck']").each(function(){ $(this).prop("checked",$rootScope.selAllAttr);});
			var cntSel=$("input[tag='optcheck']:checked").length;
			$rootScope.filesSelected=cntSel;
		}else{
			//grid
			
			$("i[tag='optcheck']").each(
				function(){ 
					var md5=$(this).attr("md5");
					$(this).attr("sel",!$rootScope.selAllAttr);
					if($(this).attr("sel")=="true"){
						$(this).attr("sel",false);
						$(this).removeClass("glyphicon-check");
						$(this).addClass("glyphicon-unchecked");
					}else{
						$(this).removeClass("glyphicon-unchecked");
						$(this).addClass("glyphicon-check");
						$(this).attr("sel",true);
					}
					if($(this).attr("sel")=="true"){
						$(this).parent().css("background-color","rgb(73, 144, 226)").find("table[tag='label']").css("background-color","rgb(73, 144, 226)");	
					}else{
						$(this).parent().css("background-color","white").find("table[tag='label']").css("background-color","rgb(232,232,232)");
					}
				}
			);
			
			var cntSel=$("i[sel='true']").length;
			$rootScope.filesSelected=cntSel;
		}
	}
	
	$rootScope.selCancel=function(){
		$("#topbar").hide();
	}
	
	$scope.selOptTag=function(tagname){
		if(!$("li[tagname='"+tagname+"']").hasClass("active")){
			$("li[tagname='"+tagname+"']").addClass("active");
		}else{
			$("li[tagname='"+tagname+"']").removeClass("active");
		}
	}
	
	$rootScope.selTag2=function(){
		$("#loading").show();
		$("#tagview").modal({backdrop:'static',keyboard: false});
		 $.ajax({
			url:'/cass/gettags_webapp.fn',
			dataType:'json',
			async:false,
			success:function(data){
				$("#loading").hide();
				$scope.tags=data.fighters;
				$("#tagfilesInput").unbind("keyup");
				$('#tagfilesInput').keyup(function(e){
					if(e.keyCode == 13)
					{
						 $scope.tags.push({tagname:$('#tagfilesInput').val()});
						 $('#tagfilesInput').val('');
						 $rootScope.$apply(function(){setTimeout(function(){$("#tagfileslist").find("li:last").addClass("active");},100)});
					} 
				});
				
				
			}
		});
		
		$scope.close=function(){
			 $("#tagview").modal('hide');
		}
		
		function tagFileAux(tagname){
			var md5="";
			var selectQuery="i[sel='true']";
			if(fileView=="list"){
				selectQuery="input[tag='optcheck']:checked";
			}
			$(selectQuery).each(
				function(){
					if(md5!=""){
						md5+="&";
					}
					md5+=$(this).attr("md5")+"=on";
				}
			);
			
			$.ajax(
				{
					url:Conf.urls.query.applyTag+"?tag="+tagname+"&"+md5,
					dataType:'json',
					sync:false,
					success:function(data){
						$(document).trigger('updateFilterTags');
					}
				}
			);
		}
		
		$scope.tagFiles=function(){
			if($("#tagfilesInput").val()!=null && $("#tagfilesInput").val()!=undefined && $("#tagfilesInput").val()!=""){
				tagFileAux($('#tagfilesInput').val());
				$('#tagfilesInput').val('');
			}
			
			$("#tagfileslist").find("li.active").each(
				function(){
					var tagname=$(this).attr("tagname");
					tagFileAux(tagname);
				}
			);
			
			
			$scope.alerts.splice(0,$scope.alerts.length);
			$scope.alerts.push({type:'success',msg:'Apply Tag on files success'});
			
			$("#txtSearch").trigger('enterKey');
			$scope.close();
			$("#topbar").hide();
		}
	}
	
	$rootScope.selShare=function(){
		var allowremote=true;
		
		if(!$scope.skipAllowRemote==undefined || $scope.skipAllowRemote==null || !$scope.skipAllowRemote){
			$.ajax(
				{
					url:'/cass/serverproperty.fn?property=allowremote',
					dataType:'text',
					async:false,
					success:function(data){
						if(data=="false"){
							allowremote=false;
						}
					}
				}
			);
		}
		
		if(!allowremote){
			$scope.close=function(){
			 $("#sharemodalnoremote").modal('hide');
			}
			
			$scope.skip=function(){
				$("#sharemodalnoremote").modal('hide');
				$scope.skipAllowRemote=true;
				$scope.selShare();
			}
			
			$scope.enableRA=function(){
				 $("#sharemodalnoremote").modal('hide');
				
				$.ajax(
					{
						url:'/cass/getremoteeula.fn',
						dataType:'json',
						async:false,
						success:function(data){
							$scope.eula=decodeURIComponent(data.licence);
							$scope.eula=$scope.eula.replace(/\+/g, " ");
						}
					}
				);
				
				$scope.close=function(){
					$("#sharemodalnoremoteeula").modal('hide');
				}
				
				$scope.decline=function(){
					$("#sharemodalnoremoteeula").modal('hide');
				}
				
				$scope.accept=function(){
					$.ajax(
					{
						url:'/cass/serverupdateproperty.fn?property=allowremote&pvalue=true',
						dataType:'json',
						async:false,
						success:function(data){
							$("#sharemodalnoremoteeula").modal('hide');
							$scope.selShare(); 
						}
					});
				}
				
				$("#sharemodalnoremoteeula").modal({backdrop:'static',keyboard: false});
			}			
			
			
			$("#sharemodalnoremote").modal({backdrop:'static',keyboard: false});
			
			return;
		}
		
		$scope.skipAllowRemote=false;
		var preloadUsers=false;
		var tagpreselected=tagshare;
		$("#tagfileInputShare").val('');
		if(tagshare!=undefined && tagshare!=null){
			$("#tagfileInputShare").val(tagshare);
			tagpreselected=tagshare;
			tagshare=null;
			preloadUsers=true;
		}else{
			preloadUsers=false;
		}
		$("#sharemodal").modal({backdrop:'static',keyboard: false});
		$scope.shareModalAlerts=[];
		
		$.ajax(
			{
				url:'/cass/getusersandemail.fn',
				dataType:'json',
				async:false,
				success:function(data){
					$scope.users=data.users;
				}
			}
		);
		
		if(preloadUsers){
			$.ajax(
			{
				url:'/cass/getsharesettingstag.fn?sharetype=TAG&sharekey='+tagpreselected,
				dataType:'json',
				async:false,
				success:function(data){
					for(var i=0; i < data.users.length;i++){
						var userPreSelected=data.users[i];
						for(var j=0; j< $scope.users.length;j++){
							var user=$scope.users[j];
							if(user.email==userPreSelected.email){
								user.active=true;
								break;
							}
						}
					}
				}
			});
			$scope.preloadUser=true;
		}else{
			$scope.preloadUser=false;
		}
		
				
		$scope.close=function(){
			 $("#sharemodal").modal('hide');
		}
		
		$scope.addUser=function(){
			$("#shareusermodal").modal({backdrop:'static',keyboard: false});
			$scope.shareUserModalAlerts=[];
			$scope.username="";
			$scope.password="";
			$scope.email="";
			
			$scope.closeUser=function(){
				 $("#shareusermodal").modal('hide');
			}
			
			$scope.addUserSave=function(){
				$scope.shareUserModalAlerts.splice(0,$scope.shareUserModalAlerts.length);
				if($scope.username==null || $scope.username==''){
					$scope.shareUserModalAlerts.push({msg:'Username is mandatory'});
					return;
				}
				if($scope.password==null || $scope.password==''){
					$scope.shareUserModalAlerts.push({msg:'Password is mandatory'});
					return;
				}
				if($scope.email==null || $scope.email==''){
					$scope.shareUserModalAlerts.push({msg:'Email is mandatory'});
					return;
				}else if(!validateEmail($scope.email)){
					$scope.shareUserModalAlerts.push({msg:'Email is invalid'});
					return;
				}
				
				$.ajax(
					{
						url:'/cass/adduser.fn?boxuser='+$scope.username+"&boxpass="+$scope.password+"&useremail="+$scope.email,
						async:false,
						success:function(data){
							
						}
					}
				);
				
				$.ajax(
					{
						url:'/cass/getusersandemail.fn',
						dataType:'json',
						async:false,
						success:function(data){
							$scope.users=data.users;
						}
					}
				);
				
				$scope.closeUser();
			}
		}
		
		
		$scope.tagFilesShare=function(){
				$scope.shareModalAlerts.splice(0,$scope.shareModalAlerts.length);
				var tagname=$("#tagfileInputShare").val();
				if(tagname==null || tagname==''){
					$scope.shareModalAlerts.push({msg:'Tag is mandatory'});
					return;
				}
				
				var md5="";
				var selectQuery="i[sel='true']";
				if(fileView=="list"){
					selectQuery="input[tag='optcheck']:checked";
				}
				$(selectQuery).each(
					function(){
						if(md5!=""){
							md5+="&";
						}
						md5+=$(this).attr("md5")+"=on";
					}
				);
				if(md5!="")
				$.ajax(
					{
						url:Conf.urls.query.applyTag+"?tag="+tagname+"&"+md5,
						dataType:'json',
						async:false,
						success:function(data){
							
						}
					}
				);
				
				
				
				var users="shareusers=";
				$("input[tag='user']:checked").each(
					function(){
						if(users!="shareusers="){
							users+="%3B";
						}
						users+=$(this).attr("user");
					}
				);
				
				if(users=="shareusers="){
					$scope.shareModalAlerts.push({msg:'Please select any user'});
					return;
				}
					
				$.ajax(
					{
						url:'/cass/doshare_webapp.fn?sharetype=TAG&'+users+'&sharekey='+tagname,
						async:false,
						success:function(token){
							$scope.alerts.splice(0,$scope.alerts.length);
							$scope.alerts.push({type:'success',msg:'Share files success'});
							$("#txtSearch").trigger("enterKey");
						}
					}
				);
				$("#sharemodal").modal('hide');
				
				$scope.close=function(){
				  $("#shareusermodal").modal('hide');
				}
				
				if(users!=="shareusers="){
					$.ajax(
						{
							url:'/cass/invitation_webapp.fn?sharetype=TAG&sharekey='+tagname,
							async:false,
							dataType:'json',
							success:function(data){
								$scope.notify=data.invitation;
							}
						}
					);
					
					
						
					$("#sharenotifymodal").modal({backdrop:'static',keyboard: false});
					
					$scope.closeNotify=function(){
						$("#sharenotifymodal").modal('hide');
					}
				}
		}
	}
	
	$rootScope.playPhoto=function(){
		$scope.playPhotoStart=true;
		var images=[];
		for(var i=0; i<$scope.list.length;i++){
			var fileAux=$scope.list[i];
			if($scope.isImage(fileAux)){
				images.push(fileAux);
			}
		}
		if(images.length>0){
			$scope.showImage(images[0],images,$scope);	
		}else{
			$scope.alerts.splice(0,$scope.alerts.length);
			$scope.alerts.push({type:'danger',msg:'No photos in file list'});
		}
	}
	
	
	$rootScope.playMusic=function(){
		var audios=[];
		for(var i=0; i<$scope.list.length;i++){
			var fileAux=$scope.list[i];
			if($scope.isAudio(fileAux)){
				audios.push(fileAux);
			}
		}
		if(audios.length>0){
			$rootScope.addPlayListFiles(audios);
		}else{
			$scope.alerts.splice(0,$scope.alerts.length);
			$scope.alerts.push({type:'danger',msg:'No music in file list'});
		}
		
	}
	
	$scope.play=function(file){
		if($scope.isImage(file)){
			var images=[];
			for(var i=0; i<$scope.list.length;i++){
				var fileAux=$scope.list[i];
				if($scope.isImage(fileAux)){
					images.push(fileAux);
				}
			}
			$scope.showImage(file,images,$scope);
		}else	
		if(file.file_group=='music'){
			$scope.playAudio(file);
		}else if(file.file_ext=='pdf'){
			$scope.showPDF(file);
		}else if(file.file_group=='movie'){
			$scope.showVideo(file);
		}else{
			//downloadURI(file.file_path_webapp,file.name);
			$scope.showDOC(file);
		}
	}
	
	$rootScope.playGeneric=function(file,list,$scope){
		if($scope.isImage(file)){
			var images=[];
			for(var i=0; i<list.length;i++){
				var fileAux=list[i];
				if($scope.isImage(fileAux)){
					images.push(fileAux);
				}
			}
			$scope.showImage(file,images,$scope);
		}else	
		if(file.file_group=='music'){
			$scope.playAudio(file);
		}else if(file.file_ext=='pdf'){
			$scope.showPDF(file);
		}else if(file.file_group=='movie'){
			$scope.showVideo(file);
		}else{
			//downloadURI(file.file_path_webapp,file.name);
			$scope.showDOC(file);
		}
	}
	
	$scope.playAudio=function(file){
		var audio=document.getElementById("audio");
		if(QueryString.cluster==undefined){
			$("#audioFile").attr("src",file.audio_url+'&uuid='+getCookie('uuid'));	
		}else{
			$("#audioFile").attr("src",file.audio_url_remote+'&uuid='+getCookie('uuid'));	
		}
		
		$("#footerAudioBar").show();
		$("#aaudiodownload").attr('href',file.file_path_webapp+'&uuid='+getCookie('uuid'));
		$("#aaudiodownload").attr('download',file.name);
		$("#aaudioopen").show().attr('href',file.file_remote_webapp);
		$("#afolderaudio").show().attr('href',file.file_folder_webapp);
		audio.load(); 
		audio.play();
		$rootScope.showPlayListPanel();
	}
	
	$scope.loadingSystemTags=function(tagInputId,file){
		$scope.isApplyTag=false;
		$(tagInputId).off();
		$(tagInputId).tagsinput({tagClass:function(item){return "label-tag";}});
		$(tagInputId).tagsinput('removeAll');
		var arr=file.file_tags.split(',');
		for(var j=0; j< arr.length;j++){
			$(tagInputId).tagsinput('add', arr[j]);
		}
		$(tagInputId).tagsinput('refresh');
		
		$(tagInputId).on('itemAdded', function(event) {
			$scope.isApplyTag=true;
			$scope.applyTag(file.nickname,event.item);
		});
		$(tagInputId).on('itemRemoved', function(event) {
			$scope.isApplyTag=true;
			$scope.removeTag(file.nickname,event.item);
		});	
	}
	
	$scope.showVideo=function(file){
		$scope.isApplyTag=false;
		var showVideo=true;
		
		$("#leftMenu").hide();
		
		$scope.name=file.name;
		$("#adownload").attr('href',file.file_path_webapp+'&uuid='+getCookie('uuid'));
		$("#adownload").attr('download',file.name);
		$("#avideoopen").attr('href',file.file_remote_webapp);
		$("#afoldervideo").attr('href',file.file_folder_webapp);

		if(file.video_url_webapp!=null){

			/*$('#videoContainer').html(
			'<video autoplay="autoplay" controls="controls" id="video" class="video-js vjs-default-skin"  >'+
			'<source  src="/cass/'+file.video_url_webapp+'&uuid='+getCookie('uuid')+'" type="application/x-mpegURL">'+
			'Your browser does not support the video tag.'+
			'</video>'
			);
			
			if(isSafari){
				document.getElementById("video").play();
			}else{
				if(isFlash()){
					var player = videojs('video');	
					$("#video").width(($(window).innerWidth()*0.95-270)+"px").height(($(window).innerWidth()*0.95-270)*(264/640)+"px");
					$(window).unbind('resize');
					$(window).resize(
						function(){
							$("#video").width(($(window).innerWidth()*0.95-270)+"px").height(($(window).innerWidth()*0.95-270)*(264/640)+"px");
							$scope.commentsHeight=($(window).innerWidth()*0.95-270)*(264/640)+45;
							$rootScope.$apply();
						}
					)
					player.play();
				}else{
					showVideo=false;
					alert('Flash is not present, please install Adobe Flash Player');
				}
			}*/
			
			//Cambio realizado 02/01/2024
			//Utilización de la librería HLS para soporte de videos en Chrome y demás navegadores
			$('#videoContainer').html(
				'<video id="video" class="video-js vjs-default-skin" controls="controls" autoplay="autoplay">'+
				'Your browser does not support the video tag.'+
				'</video>'
			);
			
			var video = document.getElementById('video');
			var videoUrl = '/cass/' + file.video_url_webapp + '&uuid=' + getCookie('uuid');
			
			if (Hls.isSupported()) {
				var hls = new Hls();
				hls.loadSource(videoUrl);
				hls.attachMedia(video);
				hls.on(Hls.Events.MANIFEST_PARSED, function () {
					video.play();
				});
			} else if (video.canPlayType('application/vnd.apple.mpegurl')) {
				video.src = videoUrl;
				video.play();
			} else {
				console.log('The HLS format is not supported by this browser.');
			}
			
		}else{
			$('#videoContainer').html(
				'<video autoplay="autoplay" controls="controls" id="video" >'+
					'<source  src="'+file.file_path_webapp+'&uuid='+getCookie('uuid')+'">'+
					'Your browser does not support the video tag.'+
				'</video>'
			);

			var video=document.getElementById("video");
			$(video).width(($(window).innerWidth()*0.95-270)+"px").height(($(window).innerWidth()*0.95-270)*(264/640)+"px");
			//$('#videoFile').attr('src',file.file_path+'&uuid='+getCookie('uuid'));
			$(window).unbind('resize');
			$(window).resize(
					function(){
						$(video).width(($(window).innerWidth()*0.95-270)+"px").height(($(window).innerWidth()*0.95-270)*(264/640)+"px");
						$scope.commentsHeight=($(window).innerWidth()*0.95-270)*(264/640)+45;
						$rootScope.$apply();
					}
				)
			video.load(); 
			video.play();
				
		}
		
		if(showVideo){
			
			$scope.close = function () {
			   $scope.alerts.splice(0,$scope.alerts.length);
			   clearTimeout($scope.timePull);
			   $(document).unbind('key_escape');
			   $("#leftMenu").show();
			   $("#footerVideoBar").modal('hide');
			   closeVideo();
			   if($scope.isApplyTag){
					$("#txtSearch").trigger("enterKey");   
			   }
			};
			
			
			$(document).bind('key_escape',function(e){
				$scope.close();
			});
			
			closeAudio();
			$scope.commentsHeight=($(window).innerWidth()*0.95-270)*(264/640)+45;
			$scope.commentMD5=file.nickname;
			$scope.msgs=[];
			$scope.lastComment=0;
			$scope.pullComments();
			$scope.showComments="comment";
			
			$("#footerVideoBar").modal({backdrop:'static',keyboard: false});
			
			$scope.loadingSystemTags("#tagsvideo",file);
		}else{
			$("#leftMenu").show();
		}	
	}
	
	$scope.shareFB=function(){
		$scope.fbtext="";
		$("#sharefacebookloading").hide();
		$("#popupblock").hide();
		
		$scope.accept=function(){
			$("#sharefacebookloading").show();
			$.ajax({
				async:true,
				url:'/cass/fbpublish.fn?md5='+$scope.selectedFileMD5+"&fbtext="+$scope.fbtext,
				dataType:'JSON',
				success:function(data){
					if(!data.result){
						try{
							if(popupBloker()){
								$("#popupblock").show();
							}else{
								$("#popupblock").hide();
							}
							$rootScope.$apply();
						}catch(e){
							
						}
						
						getFBToken(
							function(token,response){
								if(token=='error'){
									$scope.alerts.splice(0,$scope.alerts.length);
									$scope.alerts.push({type:'danger',msg:'No generate post,'+JSON.stringify(response)});
									$("#sharefacebook").modal('hide');
									$("#imageview").modal({backdrop:'static',keyboard: false});
								}else
								$.ajax({
									async:true,
									url:'/cass/fbpublish.fn?md5='+$scope.selectedFileMD5+"&fbtext="+$scope.fbtext+"&fbtoken="+token,
									dataType:'JSON',
									success:function(data){
										if(data.result){
											$scope.eventShareFB();
											$scope.alerts.splice(0,$scope.alerts.length);
											$scope.alerts.push({type:'success',msg:'Photo has been posted to Facebook successfully.'});
										}else{
											$scope.alerts.splice(0,$scope.alerts.length);
											$scope.alerts.push({type:'danger',msg:'No generate post'});
										}
										$("#sharefacebook").modal('hide');
										$("#imageview").modal({backdrop:'static',keyboard: false});
									}
								});
							}
						);
					}else{
						$scope.eventShareFB();
						$scope.alerts.splice(0,$scope.alerts.length);
						$scope.alerts.push({type:'success',msg:'Photo has been posted to Facebook successfully.'});
							
						$("#sharefacebook").modal('hide');
						$("#imageview").modal({backdrop:'static',keyboard: false});
					}
				}
			});
		 
		}
		
		$scope.close2=function(){
			$("#sharefacebook").modal('hide');
			$("#imageview").modal({backdrop:'static',keyboard: false});
		}
		
		$("#imageview").modal('hide');
		$("#sharefacebook").modal({backdrop:'static',keyboard: false});
	}
	
	$scope.showImage=function(file,images,$scope){
		$("#leftMenu").hide();
		$scope.showComments='images';
		
		$scope.trustSrc = function(src) {
			return $sce.trustAsResourceUrl(src);
		}		
		
		
		function sliderChange(){
			clearTimeout($scope.imageChangeTimer);
			if($scope.sliderValue>0){
				$scope.imageChangeTimer=setTimeout(
					function(){
						for(var i=0; i< $scope.slides.length;i++){
							$scope.slides[i].active=false;
						} 
						$scope.slides[$scope.indexNextImage].active=true;
						$rootScope.$apply();
					},
				$scope.sliderValue	
				);
			}
		}
		
		var applyTag=false;
		$scope.slides=[];
		var firstTime=true;
		$scope.timePull=0;
		function slideChange(i){
				return function (active,aux,slide) {
					  if (active){
						$scope.indexImage=i;
						var file=$scope.slides[i].file;
						//Cargo la imagen
						$scope.slides[i].image=file.file_path_webapp+'&uuid='+getCookie('uuid');
						var nextImage,backImage,backImage2,nextImage2;
						if(i==0){
							backImage=$scope.slides.length-1;
							backImage2=$scope.slides.length-2;
							if(backImage2<0){
								backImage2=0;
							}
						}else{
							backImage=i-1;
							backImage2=i-2;
							if(backImage2<0){
								backImage2=$scope.slides.length-1;
							}
						}
						if(i==$scope.slides.length-1){
							nextImage=0;
							nextImage2=1;
							if(nextImage2>=$scope.slides.length){
								nextImage2=0;
							}
						}else{
							nextImage=i+1;
							if(nextImage>$scope.slides.length-1){
								nextImage=0;
							}
							nextImage2=i+2;
							if(nextImage2>$scope.slides.length-1){
								nextImage2=0;
							}
						}
						$scope.indexNextImage=nextImage;
						$scope.slides[backImage].image=$scope.slides[backImage].file.file_path_webapp+'&uuid='+getCookie('uuid');
						$scope.slides[nextImage].image=$scope.slides[nextImage].file.file_path_webapp+'&uuid='+getCookie('uuid');
						$scope.slides[backImage2].image=$scope.slides[backImage2].file.file_path_webapp+'&uuid='+getCookie('uuid');
						$scope.slides[nextImage2].image=$scope.slides[nextImage2].file.file_path_webapp+'&uuid='+getCookie('uuid');
						
						$('#header').html((i+1)+" / "+$scope.slides.length+" - "+file.name);
						
						$scope.download=file.file_path_webapp+"&uuid="+getCookie("uuid");
						$scope.selectedFile=file.file_path_webapp+"&uuid="+getCookie("uuid");
						$scope.name=file.name;
						$scope.remote=file.file_remote_webapp;
						$scope.folder=file.file_folder_webapp;
						clearTimeout($scope.timePull);
						$scope.commentMD5=file.nickname;
						$scope.selectedFileMD5=file.nickname;
						$scope.msgs=[];
						$scope.lastComment=0;
						$scope.pullComments();
				
						$('#tagscarrousel').attr('md5',file.nickname);
						$("#imageview" ).find("img[src='"+file.file_path_webapp+'&uuid='+getCookie('uuid')+"']").hide();
						
						if(!firstTime){
							$('#tagscarrousel').off();
							$('#tagscarrousel').tagsinput('removeAll');
							var arr=file.file_tags.split(',');
							for(var j=0; j< arr.length;j++){
								$('#tagscarrousel').tagsinput('add', arr[j]);
							}
							$('#tagscarrousel').tagsinput('refresh');
							$('#tagscarrousel').on('itemAdded', function(event) {
								applyTag=true;
								$scope.applyTag($('#tagscarrousel').attr('md5'),event.item);
							});
							$('#tagscarrousel').on('itemRemoved', function(event) {
								applyTag=true;
								$scope.removeTag($("#tagscarrousel").attr('md5'),event.item);
							});							
						}else{
							$('#tagscarrousel').attr("value","").tagsinput({tagClass:function(item){return "label-tag";}});
							$('#tagscarrousel').tagsinput('removeAll');
							var arr=file.file_tags.split(',');
							for(var j=0; j< arr.length;j++){
								$('#tagscarrousel').tagsinput('add', arr[j]);
							}
							$('#tagscarrousel').off();
							$('#tagscarrousel').on('itemAdded', function(event) {
								applyTag=true;
								$scope.applyTag($('#tagscarrousel').attr('md5'),event.item);
							});
							$('#tagscarrousel').on('itemRemoved', function(event) {
								applyTag=true;
								$scope.removeTag($("#tagscarrousel").attr('md5'),event.item);
							});
							
							firstTime=false;
						}
						
						try{
							$rootScope.$apply();
						}catch(e){}
						
						setTimeout( function(){
							 $("#imageview" ).find("img[src='"+file.file_path_webapp+'&uuid='+getCookie('uuid')+"']").each(
									function(){
										if(parseInt(file.img_height,10)<$scope.imageHeight){
											$(this).css("margin-top",(($scope.imageHeight-parseInt(file.img_height,10))/2) + 'px').show();		
										}else{
											$(this).show();
										}
										if(this.complete){
											sliderChange();
											$("#imgloading").hide();
										}else{
											$("#imgloading").show();
										}
									}		
							).load( 
								function(){
									sliderChange();									
									$("#imgloading").hide();
								}
							)
						},10);
						 
						if(!$scope.mouseIn){
							//$('#imagelistcontainer').animate({scrollTop:$('#imagelistcontainer').scrollTop()+$("#imagelist"+i).offset().top-$("#imagelist"+i).outerHeight()});	
							setTimeout(function(){$('#imagelistcontainer').scrollTo("#imagelist"+i);},100);
						}
						
						
					  }
					}
		}
	
		$scope.first=true;
		function getMoreAux(index){
			
			return function getMore(){
				if(index+1!=$scope.slides.length || $scope.first){
					$scope.first=false;
					return;
				}
				$scope.first=true;
				$scope.loadQuery(false);
				var images=[];
				for(var i=0; i<$scope.list.length;i++){
					var fileAux=$scope.list[i];
					if($scope.isImage(fileAux)){
						images.push(fileAux);
					}
				}
				for(var i=$scope.slides.length; i<images.length;i++){
					var fileAux=images[i];
					$scope.slides.push({file:fileAux,text:fileAux.name,image:"#leazyload",active:fileAux.nickname==file.nickname,nickname:fileAux.nickname});	 	 
					if(fileAux.watchJS){
						fileAux.watchJS();
					}
					fileAux.watchJS=$scope.$watch('slides['+i+'].active', slideChange(i));
					if(i+1==images.length){
						if(fileAux.watchLast){
							fileAux.watchLast();
						}
						fileAux.watchLast=$scope.$watch('slides['+i+'].active', getMoreAux(i));
					}
				}
				var nextImage=0;
				if(index==$scope.slides.length-1){
					nextImage=0;
				}else{
					nextImage=index+1;
					if(nextImage>$scope.slides.length-1){
						nextImage=0;
					}
				}
				$scope.indexNextImage=nextImage;
			}	
		}
		var active=false;
		for(var i=0; i<images.length;i++){
			var fileAux=images[i];
			active=active||fileAux.nickname==file.nickname;
		}
		if(!active){
			images.push(file);
		}
		for(var i=0; i<images.length;i++){
			var fileAux=images[i];
			active=active||fileAux.nickname==file.nickname;
			$scope.slides.push({file:fileAux,text:fileAux.name,image:"#lazyload",active:fileAux.nickname==file.nickname,nickname:fileAux.nickname});	 	 
			if(fileAux.watchJS){
				fileAux.watchJS();
			}
			fileAux.watchJS=$scope.$watch('slides['+i+'].active', slideChange(i));
			if(i+1==images.length){
				if(fileAux.watchLast){
					fileAux.watchLast();
				}
				fileAux.watchLast=$scope.$watch('slides['+i+'].active', getMoreAux(i));
			}
		}
		
		
		
		$scope.imageHeight=(getWindowData()[0]>getWindowData()[1]?getWindowData()[1]:getWindowData()[0])-20;
		$scope.imageWidth=(getWindowData()[0]>getWindowData()[1]?getWindowData()[1]:getWindowData()[0])-10;
		$scope.leftCommand=$scope.imageWidth/2+200;
		
		$scope.close = function () {
		   $scope.alerts.splice(0,$scope.alerts.length);
		   $(document).unbind('key_escape');
		   $(document).unbind('key_arrow_right');
		   $(document).unbind('key_arrow_left');
		   clearTimeout($scope.timePull);
		   $scope.timePull=0;
		   $('#tagscarrousel').off();
		   $("#leftMenu").show();
		   if(applyTag){
				$("#txtSearch").trigger("enterKey");   
		   }
		   $("#imageview").modal('hide');
		};
		
		$scope.setImage=function(idx){
			$scope.sliderValue=0;
			for(var i=0; i< $scope.slides.length;i++){
				$scope.slides[i].active=false;
			} 
			$scope.slides[idx].active=true;
		}
		
		$scope.slider = {
			'options': {
			start: function (event, ui) {  },
			stop: function (event, ui) {   }
			}};	


		$scope.$watch('sliderValue',
			function(){
				sliderChange();
			}
		);		
				
		$scope.sliderValue=0;
		if($scope.playPhotoStart==true){
			$scope.playPhotoStart=false;
			$scope.sliderValue=5000;
		}
		
		$("#imageview").modal({backdrop:'static',keyboard: false});
		setTimeout(function(){$("#commandImageBar").show('slow');},100);
		$(document).bind('key_escape',function(e){
			$scope.close();
		});
		var block=false;
		$(document).bind('key_arrow_right',function(e){
			if(block) return;
			blovk=true;
			for(var i=0; i< $scope.slides.length;i++){
				$scope.slides[i].active=false;
			}
			var idx=$scope.indexImage;
			idx++;
			if($scope.slides.length<=idx){
				idx=0;
			}	
			$scope.slides[idx].active=true;
			$rootScope.$apply(function(){block=false;});
		});
		$(document).bind('key_arrow_left',function(e){
			if(block) return;
			blovk=true;
			for(var i=0; i< $scope.slides.length;i++){
				$scope.slides[i].active=false;
			}
			var idx=$scope.indexImage;
			idx--;
			if(0>idx){
				idx=$scope.slides.length-1;
			}	
			$scope.slides[idx].active=true;
			$rootScope.$apply(function(){block=false;});
		});
	}
	
	$scope.showPDF=function(file){
		$scope.trustSrc = function(src) {
			return $sce.trustAsResourceUrl(src);
		 }
		$scope.showComments="comment";
		$scope.pdf=file.file_path_webapp;
		$scope.download=file.file_path_webapp+"&uuid="+getCookie("uuid");
		$scope.name=file.name;
		$scope.remote=file.file_remote_webapp;
		$scope.folder=file.file_folder_webapp
		$scope.commentsHeight=(getWindowData()[0]>getWindowData()[1]?getWindowData()[1]:getWindowData()[0])-20;
		$scope.commentMD5=file.nickname;
		$scope.msgs=[];
		$scope.lastComment=0;
		$scope.pullComments();
		
		$("#pdfViewer").modal({backdrop:'static',keyboard: false});
		
		$scope.loadingSystemTags("#tagspdf",file);
			
		$scope.close = function () {
			$scope.alerts.splice(0,$scope.alerts.length);
			$(document).unbind('key_escape');
			clearTimeout($scope.timePull);
			if($scope.isApplyTag){
			   $("#txtSearch").trigger("enterKey");   
		    }
		    $("#pdfViewer").modal('hide');
		  
		 };
		  
		 $(document).bind('key_escape',function(e){
			$scope.close();
		});
	}
	
	$scope.showDOC=function(file){
		$scope.showComments="comment";
		$scope.doc=file;
		$scope.download=file.file_path_webapp+"&uuid="+getCookie("uuid");
		$scope.name=file.name;
		$scope.remote=file.file_remote_webapp;
		$scope.folder=file.file_folder_webapp
		$scope.commentsHeight=(getWindowData()[0]>getWindowData()[1]?getWindowData()[1]:getWindowData()[0])-20;
		$scope.commentMD5=file.nickname;
		$scope.msgs=[];
		$scope.lastComment=0;
		$scope.pullComments();
		
		$("#docViewer").modal({backdrop:'static',keyboard: false});
		
		$scope.loadingSystemTags("#tagsdoc",file);
		
		$scope.close = function () {
			$scope.alerts.splice(0,$scope.alerts.length);
			$(document).unbind('key_escape');
			clearTimeout($scope.timePull);
			if($scope.isApplyTag){
			   $("#txtSearch").trigger("enterKey");   
		    }
			$("#docViewer").modal('hide');
		 };
		  
  		$(document).bind('key_escape',function(e){
			$scope.close();
		});

	}
	
	
	$rootScope.showList=function(){
		$rootScope.fileView="list";
		$rootScope.selAllAttr=false;
		$("#selAllAttr").prop("checked",false);
		$("html,body").animate({scrollTop:0});
		$location.path("/MyFiles/"+$scope.ftype+"/"+$scope.range);
	}
	
	$rootScope.showGrid=function(){
		$rootScope.fileView="grid";
		$("#selAllAttr").prop("checked",false);
		$rootScope.selAllAttr=false;
		$("html,body").animate({scrollTop:0});
		$location.path("/MyFiles/grid/"+$scope.ftype+"/"+$scope.range);
	}
	
	$scope.remote=function(file){
		
		$("#idownload").attr('src',file.file_remote_webapp+"&uuid="+getCookie('uuid'));
	}
	
	$scope.folder=function(file){
		$("#idownload").attr('src',file.file_folder_webapp+"&uuid="+getCookie('uuid'));
	}
	
	if($routeParams.ftype!=null && $routeParams.ftype!=undefined){
		$scope.ftype=$routeParams.ftype;
	}else{
		$scope.ftype=".all";
	}
	if($routeParams.range!=null && $routeParams.range!=undefined){
		$scope.range=$routeParams.range;
	}else{
		$scope.range="";
	}
	$scope.loadQuery();
	ngProgressLite.done();
 
	$("#imageview").hide();
	$(window).unbind('scroll');
	$(window).data('ajaxready', true).scroll(function(e) {
		if($location.path().indexOf("MyFiles")<0){
			return;
		}
		if($(window).scrollTop()>60){
			$("#backToTop").show();
			/*if($location.path().indexOf("MyFiles")>-1){
				$("#topbarcommands2").show();
			}else{
				$("#topbarcommands2").hide();
			}*/
			//$("#topbarcommands").addClass("topbarcommands");
			//$("#topbarheader").addClass("topbar");
		}else{
			$("#backToTop").hide();
			//$("#topbarcommands").removeClass("topbarcommands");
			//$("#topbarheader").removeClass("topbar");
		}
		
		if ($(window).data('ajaxready') == false) return;

		if ($(window).scrollTop()+window.innerHeight > ($(document).height()-100)) {
			$(window).data('ajaxready', false);
			$scope.loadQuery();
			$scope.sName=false;
			$scope.sSize=false;
			$scope.nameReverse=false;
			$scope.sizeReverse=false;
			if(sortOrder=="Asc" && $scope.sDate && !$scope.dateReverse ){
				$scope.sDate=true;
				$scope.dateReverse=false;
			}else
			if(sortOrder=="Desc" && $scope.sDate && $scope.dateReverse ){
				$scope.sDate=true;
				$scope.dateReverse=true;
			}else{
				$scope.sDate=false;
				$scope.dateReverse=false;
			}
		}
	});
	
	

	//Comments
	$scope.processMsg=function(txt){
		txt=decodeURIComponent(txt);
	
		var aux=replaceAll("\n","<br>",txt);
		var myRe = new RegExp("tag:(.+)", "g");
		var tags = myRe.exec(aux);
		if(tags!=null && tags.length>0){
			var tag=tags[0];
			var tagName=tag.substr(tag.indexOf(":")+1,tag.length);
			aux=replaceAll(tag,"<a ng-click='showTag(\""+tagName+"\")'>"+tagName+"</a>",aux);
		}
		return "<label>"+aux+"</label>";
	}

	$scope.lastComment=0;
	$scope.pullComments=function(){
		/**
				{
		"messages": [
		{
		"msg_date": "1449595320000",
		"msg_type": "CHAT",
		"msg_user": "admin",
		"msg_body": "kjhk"
		}
		]
		}
		**/
		var firstTime=$scope.lastComment==0;
		var auxTimePull=$scope.timePull;
		$.ajax({
			url:'/cass/chat_pull.fn?md5='+$scope.commentMD5+'&msg_from='+$scope.lastComment,
			async:true,
			dataType:'json',
			success:function(data){
				//Para evitar que queden dos llamadas al cambiar de archivo
				if(auxTimePull!=$scope.timePull && $scope.timePull!=0){
					return;
				}
				
				$scope.likes=data.likes;
				
				if(data.messages.length>0 && username!=null && username!=undefined){
					$scope.lastComment=data.messages[data.messages.length-1].msg_date;
					for(var i=0; i<data.messages.length;i++ ){
						if(data.messages[i].msg_type!='COMMENT'){
							continue;
						}
						
						var lastMsg=null;
						if($scope.msgs.length>0){
							lastMsg=$scope.msgs[$scope.msgs.length-1];
							if(lastMsg.username!=data.messages[i].msg_user){
								$scope.msgs.push({date:convertUTCDateToLocalDate(new Date(parseInt(data.messages[i].msg_date,10))),username:data.messages[i].msg_user,msg:$scope.processMsg(data.messages[i].msg_body)});		
							}else{
								var dateLastMsg=lastMsg.date;
								var dateMsg=convertUTCDateToLocalDate(new Date(parseInt(data.messages[i].msg_date,10)));
								if(dateLastMsg.getDate()==dateMsg.getDate() && dateLastMsg.getHours()==dateMsg.getHours() && dateLastMsg.getMinutes()==dateMsg.getMinutes()){
									lastMsg.msg+="<br>"+$scope.processMsg(data.messages[i].msg_body);
								}else{
									$scope.msgs.push({date:convertUTCDateToLocalDate(new Date(parseInt(data.messages[i].msg_date,10))),username:data.messages[i].msg_user,msg:$scope.processMsg(data.messages[i].msg_body)});		
								}
							}
						}else{
							$scope.msgs.push({date:convertUTCDateToLocalDate(new Date(parseInt(data.messages[i].msg_date,10))),username:data.messages[i].msg_user,msg:$scope.processMsg(data.messages[i].msg_body)});		
						}
					}
					
					
					try{
						$rootScope.$apply();
					}catch(e){}
					
					
					setTimeout(
						function(){
							$('#commentsmsgs').animate({scrollTop:$('#commentsmsgs')[0].scrollHeight});
							$('#commentsmsgs2').animate({scrollTop:$('#commentsmsgs2')[0].scrollHeight});
							$('#commentsmsgs3').animate({scrollTop:$('#commentsmsgs3')[0].scrollHeight});
							$('#commentsmsgs4').animate({scrollTop:$('#commentsmsgs4')[0].scrollHeight});
							
						}
					, firstTime?1000:25);	
					
				}
				
				$scope.timePull=setTimeout($scope.pullComments,30000);
			}
		});
	}
	
	$scope.send=function(){
		if($scope.inputText!=undefined && $scope.inputText!=""){
			
			//$scope.msgs.push({date:Date.UTC(),username:username,msg:$scope.processMsg($scope.inputText)});
			var txt = $scope.inputText.replace(/[\u00A0-\u00FF\u2022-\u2135]/g, function(c) {
				return '&#'+c.charCodeAt(0)+';';
			});
		
			$.ajax({
				url:'/cass/chat_push.fn?md5='+$scope.commentMD5+'&msg_user='+username+'&msg_from='+getUTCLong()+'&msg_type=COMMENT&msg_body='+encodeURIComponent(txt),
				async:false,
				success:function(){}
				}
			);
			
			$scope.inputText="";
			clearTimeout($scope.timePull);
			$scope.timePull=0;
			$scope.pullComments();
		}
	}
	
	$scope.like=function(){
		if($scope.commentMD5!=undefined && $scope.commentMD5!=""){
			
			$.ajax({
				url:'/cass/chat_push.fn?md5='+$scope.commentMD5+'&msg_type=LIKE&msg_user='+username,
				async:false,
				success:function(){}
				}
			);
			clearTimeout($scope.timePull);
			$scope.timePull=0;
			$scope.pullComments();
		}
	}
	
	$scope.eventShareFB=function(){
		if($scope.commentMD5!=undefined && $scope.commentMD5!=""){
			
			$.ajax({
				url:'/cass/chat_push.fn?md5='+$scope.commentMD5+'&msg_type=FB&msg_user='+username,
				async:false,
				success:function(){}
				}
			);
		}
	}


	$scope.checkReturn = function(event) {

		if (event.keyCode == 13 && !event.shiftKey) {
			event.preventDefault();
			$scope.send();
		}
	}
	//Fin Comments
	
	$scope.loadQuery();
	ngProgressLite.done();
	
});


 
