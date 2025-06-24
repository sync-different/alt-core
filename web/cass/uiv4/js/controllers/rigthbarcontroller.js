angular.module('app.controllers').controller('RightBarController', function (Conf,ngProgressLite,$state, $routeParams,$anchorScroll,$filter,$compile,$location,$rootScope,$scope) {
	$rootScope.playlistfiles=[];
	$scope.index=0;
	$rootScope.showRightMenu=false;
	$scope.showChat="chat";	
	
	$scope.openTab=function(){
		setTimeout(
			function(){
				$('#cahtmsgs').scrollTop($('#cahtmsgs')[0].scrollHeight);
				$('#events').scrollTop($('#events')[0].scrollHeight);
			}
		, 25);	
	}
	
	$rootScope.changeShowRightMenu=function(){
		$rootScope.showRightMenu=!$rootScope.showRightMenu;
		if($rootScope.showRightMenu){
			clearTimeout(timePull);
			$scope.pullMessages(false);
			if($scope.newMessages){
				$scope.showChat="chat";
			}else{
				if($rootScope.playlistfiles.length>0){
					$scope.showChat="playlist";	
				}
			}
			
			$scope.openTab();
		}
	}
	
	$scope.dec=function(str){
		if(str==undefined) return;
		var aux=decodeURIComponent(str);
		aux=aux.replace(/\+/g, " ");
		return aux;		
	}
	$("#rightMenu").css("height",getWindowData()[1]-50);
	$("#playlistcontainer,#events,#cahtmsgs").css("height",getWindowData()[1]-170);
	
	$(window).on('resize', function() {
		$("#rightMenu").css("height", getWindowData()[1] - 50);
		$("#playlistcontainer, #events, #cahtmsgs").css("height", getWindowData()[1] - 170);
	});
	
	$scope.msgs=[];
	$scope.events=[];
	
	$scope.showTag=function(tagname){
		selectedTag=tagname;
		$(document).trigger("selTag");
	}
	
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
	
	
	
	
	$scope.lastMessage=0;
	$scope.pullMessages=function(async){
		
		if(async==undefined || async==null){
			async=true;
		}
		
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
		$.ajax({
			url:'/cass/chat_pull.fn?msg_from='+$scope.lastMessage,
			async:async,
			dataType:'json',
			success:function(data){
				if(data.messages.length>0 && username!=null && username!=undefined){
					$scope.lastMessage=data.messages[data.messages.length-1].msg_date;
					var warning=false;
					for(var i=0; i<data.messages.length;i++ ){
						if(data.messages[i].msg_type!='CHAT'){
							if(data.messages[i].msg_type=='EVENT'){
								 
								//var jsonBody=$.parseJSON(replaceAll("'#'","\"",data.messages[i].msg_body));
								var jsonBody = JSON.parse(replaceAll("'#'", "\"", data.messages[i].msg_body));
								$scope.events.push({file:jsonBody,date:convertUTCDateToLocalDate(new Date(parseInt(data.messages[i].msg_date,10))),username:data.messages[i].msg_user,fileName:jsonBody.fileName,thumbnail:jsonBody.thumbnail,msg:jsonBody.msg});		
								if(data.messages[i].msg_user!=username ){
									warning=true;
								}
						
							}
							continue;
						}
						if(data.messages[i].msg_user!=username ){
							warning=true;
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
							$('#cahtmsgs').scrollTop($('#cahtmsgs')[0].scrollHeight);
							$('#events').scrollTop($('#events')[0].scrollHeight);
						}
					, 25);	
					
					if(warning){
						if(!$scope.showRightMenu){
							var audio=document.getElementById("beep");
							audio.load(); 
							audio.play();
							newMessages();
						}
					}
				}
				
				timePull=setTimeout($scope.pullMessages,$rootScope.showRightMenu?5000:15000);
			}
		});
	}
	var timePull=setTimeout($scope.pullMessages,100);
	
	$scope.send=function(){
		if($scope.inputText!=undefined && $scope.inputText!=""){
			
			//$scope.msgs.push({date:Date.UTC(),username:username,msg:$scope.processMsg($scope.inputText)});
			var txt = $scope.inputText.replace(/[\u00A0-\u00FF\u2022-\u2135]/g, function(c) {
				return '&#'+c.charCodeAt(0)+';';
			});
		
			$.ajax({
				url:'/cass/chat_push.fn?msg_user='+username+'&msg_from='+getUTCLong()+'&msg_type=CHAT&msg_body='+encodeURIComponent(txt),
				async:false,
				success:function(){}
				}
			);
			
			$scope.inputText="";
			clearTimeout(timePull);
			$scope.pullMessages(true);
		}
	}
	
	$scope.clearChats=function(){
		$.ajax({
				url:'/cass/chat_clear.fn',
				async:false,
				success:function(){
					$scope.alerts.splice(0,$scope.alerts.length);
					$scope.alerts.push({type:'success',msg:'Clear all messages on server.'});
					$scope.msgs = [];
            		$scope.lastComment = 0;
            		$scope.$applyAsync(); 
				}
			});
	}
	
	$scope.checkReturn = function(event) {

		if (event.keyCode == 13 && !event.shiftKey) {
			event.preventDefault();
			$scope.send();
		}
	}
	 
	$scope.newMessages=false;
	function newMessages(){
		var colorMessages=false;
		function light(){
				$scope.newMessages=true;
				colorMessages=!colorMessages;
				if(colorMessages){
					$("#rightMenuIcon").css("color","rgb(230,230,230)");
				}else{
					$("#rightMenuIcon").css("color","black");
				}
				if(!$scope.showRightMenu){
					
					setTimeout(light,500);			
				}else{
					$scope.newMessages=false;
					$("#rightMenuIcon").css("color","black");
				}
		}
			
		setTimeout(light,500);
	} 
	
	$rootScope.addPlayListFiles=function(audios){
		var play=$rootScope.playlistfiles.length==0;
		$rootScope.playlistfiles=$rootScope.playlistfiles.concat(audios);
		if(play){
			$scope.playMusic();
		}
	}
	
	$scope.play=function(file){
		if(!file.play) return;
		
		$rootScope.playFile=file;
		$("#txtSearch").trigger("enterKey");		
	}
	
	$rootScope.showPlayListPanel=function(){
		$rootScope.showRightMenu=true;
		$scope.showChat=false;
	}
	
	$rootScope.offPlayListPanel=function(){
		$rootScope.showRightMenu=false;
	}
	
	$scope.playMusic=function(){
		 
		if($rootScope.playlistfiles.length>0){
			var audio=document.getElementById("audio");
			if($scope.audioEventListener!=undefined){
				audio.removeEventListener('ended',$scope.audioEventListener);	
				closeAudio();
			}
			
			$scope.audioEventListener=function(e){
				$scope.playRunning=false;
				$scope.nextPlayListIndex();
				$scope.playAudio($rootScope.playlistfiles[$scope.index]);
				$rootScope.$apply();
				$("#playlistcontainer").scrollToElem("#audio"+$scope.index);
			}
			audio.addEventListener('ended',$scope.audioEventListener);
			
			$scope.playAudio($rootScope.playlistfiles[$scope.index]);
		} 
	}
	
	$scope.nextPlayListIndex=function(){
		$scope.index++;
		if($scope.index >= $rootScope.playlistfiles.length){
			$scope.index = 0;
		}
	}
	
	$scope.setPlayIndex=function(index){
		$scope.index=index;
		$scope.playMusic();
	}
		
	$scope.deletePlayIndex=function(index){
		var p=$scope.playRunning && $scope.index==index;
		if($scope.index==index){
			closeAudio();
			$scope.playRunning=false;
		}
		if($scope.index>index){
			$scope.index--;
		}
		$rootScope.playlistfiles.splice(index,1);
		if($scope.index >= $rootScope.playlistfiles.length){
			$scope.index = 0;
		}
		if(p){
			$scope.playMusic();
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
		$("#aaudioopen").attr('href',file.file_remote_webapp);
		$("#afolderaudio").attr('href',file.file_folder_webapp);
		audio.load(); 
		audio.play();
		$scope.playRunning=true;
	}
	
	$scope.playListClear=function(){
		closeAudio();
		$scope.index=0;
		$scope.playlistfiles.splice(0,$scope.playlistfiles.length);
	}
	
	
});