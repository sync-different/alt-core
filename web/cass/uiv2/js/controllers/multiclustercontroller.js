angular.module('app.controllers').controller('MultiClusterController', function (Conf,ngProgressLite,$state,$sce, $routeParams,$anchorScroll,$filter,$compile,$location,$rootScope,$scope) {
	ngProgressLite.set(Math.random());
	ngProgressLite.start();
	$scope.dec=function(url){
		if(url==undefined || url==null){
			return "";
		}
		url=decodeURIComponent(url);
		return url;	
	}
	$scope.up=toTitleCase;
	$rootScope.mnu='multicluster';
	$("#modals").html($compile(getPartial("partialModalsCluster.htm"))($scope));
	$scope.alertsClusters = [];
	$scope.closeAlert = function(index) {
		$scope.alertsClusters.splice(index, 1);
	};
	$scope.clustersResult=new Object();
	$scope.clustersLastDate=new Object();
	$scope.clustersAjax=new Object();
	if(!$rootScope.clustersDisabeled){
		$rootScope.clustersDisabeled=new Object();
	}
	
	
	$('#txtSearch').unbind("enterKey");
	$('#txtSearch').bind("enterKey",function(e){
		$rootScope.mnu='multicluster';
		
		$("html,body").animate({scrollTop:0});
		$scope.loadQuery();
	});

	function loadClusterQuery(cluster,asyncIN){
		var async=true;
		if(asyncIN!=null && asyncIN!=undefined){
			async=asyncIN;
		}
		$("#spinner"+cluster.cluster).show();
		var date=null;
		if($scope.clustersLastDate[cluster.cluster]!=null && $scope.clustersLastDate[cluster.cluster]!=undefined){
			date=$scope.clustersLastDate[cluster.cluster];
		}
		$.ajax(
		{
			url:'querymulticluster.fn?multiclusterid='+cluster.cluster+'&foo='+encodeURIComponent($("#txtSearch").val())+"&ftype="+$rootScope.mftype+"&days="+($rootScope.mrange=='.all'?"":$rootScope.mrange)+(date!=null?"&date="+replaceAll('%20','+',escape(date.toString())):""),
			async:async,
			dataType:'json',
			success:function(data){
				$rootScope.loadLeftMenuValuesCluster(data.objFound);
				if(data.fighters.length>0){
					$scope.clustersLastDate[cluster.cluster]=data.fighters[data.fighters.length-1].file_date;	
				}
				
				if($scope.clustersResult[cluster.cluster]==null){
					$scope.clustersResult[cluster.cluster]=data;	
				}else{
					$scope.clustersResult[cluster.cluster].fighters=$scope.clustersResult[cluster.cluster].fighters.concat(data.fighters);	
				}
				
				
				function tags(){
					$('input[tag="tags"]').each(
							function(){
								$(this).tagsinput({tagClass:function(item){return "label-tag";}});
								var tthis=$(this);
								$(this).on('itemAdded', function(event) {
									$scope.applyTag(tthis.attr('md5'),event.item,tthis.attr('cluster'));
								});
								$(this).on('itemRemoved', function(event) {
									$scope.removeTag(tthis.attr('md5'),event.item,tthis.attr('cluster'));
								});
							}
						);
				}
					
				try{
					$rootScope.$apply(function(){
						$("#spinner"+cluster.cluster).hide();
						$scope.clustersAjax[cluster]=false;
						setTimeout(tags,100);
					});
				}catch(e){
					setTimeout(tags,100);
					$("#spinner"+cluster.cluster).hide();
					$scope.clustersAjax[cluster]=false;
				}
			}
		});
		
		$("#table"+cluster.cluster).unbind("scroll");
		$("#table"+cluster.cluster).scroll("scroll",
			function(){
				if($scope.clustersAjax[cluster]) return;
				$scope.clustersAjax[cluster]=true;
				if($(this).scrollTop() + $(this).innerHeight() >= $(this)[0].scrollHeight){
					setTimeout(function(cluster){ loadClusterQuery(cluster);}(cluster),5);	
				}else{
					$scope.clustersAjax[cluster]=false;
				}
				
			}
		);
		
	}
	
	$scope.loadClusterQuery=function(cluster){
		$scope.clustersLastDate[cluster.cluster]=null;
		$scope.clustersResult[cluster.cluster]=null;
		$scope.clustersAjax[cluster]=false;
		setTimeout(function(cluster){ loadClusterQuery(cluster);}(cluster),5);
	}
	
	$scope.loadQuery=function(){
		$rootScope.mObjFount=null;
		$rootScope.loadLeftMenuValuesCluster(null);
		for(var i=0; i< $scope.clusters.length;i++){
			var cluster=$scope.clusters[i];
			if($rootScope.clusterQuery!=undefined && $rootScope.clusterQuery!=null){
				if($rootScope.clusterQuery.cluster==cluster.cluster){
					$scope.loadClusterQuery(cluster);
					break;	
				}
			}else{
				$scope.loadClusterQuery(cluster);	
			}
			
		}
		
		$rootScope.clusterQuery=null;
	}
	
	
	
	$scope.loadClusters=function(){
		$rootScope.mObjFount=null;
		$rootScope.loadLeftMenuValuesCluster(null);
		
		$.ajax(
		{
			url:'/cass/getmulticlusters.fn',
			async:false,
			dataType:'json',
			success:function(clusters){
				$scope.clusters=clusters.clusters;
				$rootScope.loadinClusterTags($scope.clusters);
				
			}
		}
		);
	}
	
	$scope.loadClusters();
	
	$scope.applyTag=function(md5,tag,cluster){
		if(md5==undefined || md5==null || tag==undefined || tag==null ){
			return;
		}
		
		$("#loading").show();
		$.ajax(
			{
				url:Conf.urls.query.applyTag+"?tag="+tag+"&"+md5+"=on"+'&multiclusterid='+cluster,
				dataType:'json',
				sync:true,
				success:function(data){
					$rootScope.alerts.splice(0,$scope.alerts.length);
					$rootScope.alerts.push({type:'success',msg:'Apply Tag in file success'});
					$("#loading").hide();
					$rootScope.loadinClusterTags($scope.clusters);
					var list=$scope.clustersResult[cluster].fighters;
					for(var i=0; i< list.length;i++){
						if( list[i].nickname==md5){
							 list[i].file_tags+=","+tag;
							break;
						}
					}
					
					try{
						$rootScope.$apply();
					}catch(e){}
				}
			}
		)
	}
	
	$scope.removeTag=function(md5,tag,cluster){
		if(md5==undefined || md5==null || tag==undefined || tag==null ){
			return;
		}
		$("#loading").show();
		$.ajax(
			{
				url:Conf.urls.query.applyTag+"?tag="+tag+"&DeleteTag="+md5+'&multiclusterid='+cluster,
				dataType:'json',
				sync:true,
				success:function(data){
					$rootScope.alerts.splice(0,$scope.alerts.length);
					$rootScope.alerts.push({type:'success',msg:'Remove Tag from file success'});
					$("#loading").hide();
					$rootScope.loadinClusterTags($scope.clusters);
					var list=$scope.clustersResult[cluster].fighters;
					for(var i=0; i< list.length;i++){
						if( list[i].nickname==md5){
							 list[i].file_tags= list[i].file_tags.replace(tag,"");
							break;
						}
					}
					try{
						$rootScope.$apply();
					}catch(e){}
				}
			}
		)
	}
	
	//FILE VIEW
	
	$scope.isImage=function(file){
		return file.file_group=='photo';
	}
		
	$scope.play=function(file,cluster){
		if($scope.isImage(file)){
			var list=$scope.clustersResult[cluster.cluster].fighters;
			var images=[];
			for(var i=0; i<list.length;i++){
				var fileAux=list[i];
				if($scope.isImage(fileAux)){
					images.push(fileAux);
				}
			}
			$scope.showImage(file,images,cluster);
		}else	
		if(file.file_group=='music'){
			$scope.playAudio(file,cluster);
		}else if(file.file_ext=='pdf'){
			$scope.showPDF(file,cluster);
		}else if(file.file_group=='movie'){
			$scope.showVideo(file,cluster);
		}else{
			//downloadURI(file.file_path_webapp,file.name);
			$scope.showDOC(file,cluster);
		}
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
			$scope.applyTag(file.nickname,event.item,$scope.clusterSelected.cluster);
		});
		$(tagInputId).on('itemRemoved', function(event) {
			$scope.isApplyTag=true;
			$scope.removeTag(file.nickname,event.item,$scope.clusterSelected.cluster);
		});	
	}
	
	$scope.showVideo=function(file,cluster){
		$scope.isApplyTag=false;
		var showVideo=true;
		$scope.clusterSelected=cluster;
		$("#leftMenu").hide();
		
		$scope.name=file.name;
		$("#adownload").attr('href',file.file_path_webapp+'&multiclusterId='+cluster.cluster);
		$("#adownload").attr('download',file.name);
		if(file.video_url_webapp!=null){
			$('#videoContainer').html(
			'<video autoplay="autoplay" controls="controls" id="video" class="video-js vjs-default-skin"  >'+
			'<source  src="/cass/'+file.video_url_webapp+'&multiclusterid='+cluster.cluster+'" type="application/x-mpegURL">'+
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
			}
			
		}else{
			$('#videoContainer').html(
				'<video autoplay="autoplay" controls="controls" id="video" >'+
					'<source  src="'+file.file_path_webapp+'&multiclusterid='+cluster.cluster+'">'+
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
	
	$scope.showImage=function(file,images,cluster){
		$("#leftMenu").hide();
		$scope.clusterSelected=cluster;
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
						$scope.slides[i].image=file.file_path_webapp+'&multiclusterid='+cluster.cluster;
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
						$scope.slides[backImage].image=$scope.slides[backImage].file.file_path_webapp+'&multiclusterid='+cluster.cluster;
						$scope.slides[nextImage].image=$scope.slides[nextImage].file.file_path_webapp+'&multiclusterid='+cluster.cluster;
						$scope.slides[backImage2].image=$scope.slides[backImage2].file.file_path_webapp+'&multiclusterid='+cluster.cluster;
						$scope.slides[nextImage2].image=$scope.slides[nextImage2].file.file_path_webapp+'&multiclusterid='+cluster.cluster;
						
						$('#header').html((i+1)+" / "+$scope.slides.length+" - "+file.name);
						
						$scope.download=file.file_path_webapp+'&multiclusterid='+cluster.cluster;
						$scope.selectedFile=file.file_path_webapp+'&multiclusterid='+cluster.cluster;
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
						$("#imageview" ).find("img[src='"+file.file_path_webapp+'&multiclusterid='+cluster.cluster+"']").hide();
						
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
								$scope.applyTag($('#tagscarrousel').attr('md5'),event.item,cluster.cluster);
							});
							$('#tagscarrousel').on('itemRemoved', function(event) {
								applyTag=true;
								$scope.removeTag($("#tagscarrousel").attr('md5'),event.item,cluster.cluster);
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
								$scope.applyTag($('#tagscarrousel').attr('md5'),event.item,cluster.cluster);
							});
							$('#tagscarrousel').on('itemRemoved', function(event) {
								applyTag=true;
								$scope.removeTag($("#tagscarrousel").attr('md5'),event.item,cluster.cluster);
							});
							
							firstTime=false;
						}
						
						try{
							$rootScope.$apply();
						}catch(e){}
						
						setTimeout( function(){
							 $("#imageview" ).find("img[src='"+file.file_path_webapp+'&multiclusterid='+cluster.cluster+"']").each(
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
				loadClusterQuery(cluster,false);
				var images=[];
				var list=$scope.clustersResult[cluster.cluster].fighters;
				for(var i=0; i<list.length;i++){
					var fileAux=list[i];
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
	
	$scope.showPDF=function(file,cluster){
		$scope.trustSrc = function(src) {
			return $sce.trustAsResourceUrl(src);
		 }
		$scope.showComments="tags";
		$scope.pdf=file.file_path_webapp+"&multiclusterid="+cluster.cluster;
		$scope.download=file.file_path_webapp+"&multiclusterid="+cluster.cluster;
		$scope.name=file.name;
	 
		$scope.commentsHeight=(getWindowData()[0]>getWindowData()[1]?getWindowData()[1]:getWindowData()[0])-20;
		$scope.commentMD5=file.nickname;
		$scope.msgs=[];
		$scope.clusterSelected=cluster;
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
	
	$scope.showDOC=function(file,cluster){
		$scope.showComments="tags";
		$scope.doc=file;
		$scope.download=file.file_path_webapp+"&multiclusterid="+cluster.cluster;
		$scope.name=file.name;
	 	$scope.commentsHeight=(getWindowData()[0]>getWindowData()[1]?getWindowData()[1]:getWindowData()[0])-20;
		$scope.commentMD5=file.nickname;
		$scope.msgs=[];
		$scope.clusterSelected=cluster;
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
	
	$scope.playAudio=function(file,cluster){
		var audio=document.getElementById("audio");
		$("#audioFile").attr("src",file.audio_url_remote+'&uuid='+cluster.uuid);	
		
		$("#footerAudioBar").show();
		$("#aaudiodownload").attr('href',file.file_path_webapp+'&multiclusterid='+cluster.cluster);
		$("#aaudiodownload").attr('download',file.name);
		$("#aaudioopen").hide();
		$("#afolderaudio").hide();
		audio.load(); 
		audio.play();
		$rootScope.showPlayListPanel();
	}
	
	//--COMMENTS Y IKE
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
		} **/
		
		var firstTime=$scope.lastComment==0;
		var auxTimePull=$scope.timePull;
		$.ajax({
			url:'/cass/chat_pull.fn?md5='+$scope.commentMD5+'&msg_from='+$scope.lastComment+'&multiclusterid='+$scope.clusterSelected.cluster,
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
				url:'/cass/chat_push.fn?md5='+$scope.commentMD5+'&msg_user='+username+'&msg_from='+getUTCLong()+'&msg_type=COMMENT&msg_body='+encodeURIComponent(txt)+"&multiclusterid="+$scope.clusterSelected.cluster,
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
				url:'/cass/chat_push.fn?md5='+$scope.commentMD5+'&msg_type=LIKE&msg_user='+username+"&multiclusterid="+$scope.clusterSelected.cluster,
				async:false,
				success:function(){}
				}
			);
			clearTimeout($scope.timePull);
			$scope.timePull=0;
			$scope.pullComments();
		}
	}
	
	$scope.checkReturn = function(event) {

		if (event.keyCode == 13 && !event.shiftKey) {
			event.preventDefault();
			$scope.send();
		}
	}
	
	//END FILE VIEW COMMENTS
	
	
	
	//CRUD CLUSTER
	$scope.removeCluster=function(cluster){
		$scope.popupmsg="Delete this cluster "+cluster.name+"?"
		
		$("#popupmsg2").modal({backdrop:'static',keyboard: false});
		
		$scope.closePopup=function(){
			$("#popupmsg2").modal('hide');
		}
		
		$scope.acceptPopup=function(){
			$.ajax(
			{
				url:'removemulticluster.fn?multiclusterid='+cluster.cluster,
				async:false,
				success:function(){
					$scope.loadClusters();
					$("#popupmsg2").modal('hide');
				}
			});
		}
		
	}
	
	$scope.editCluster=function(cluster){
		$scope.addCluster(cluster);
	}
	
	$scope.addCluster=function(cluster){
		$("#clusterformimgloading").hide();
		$rootScope.clearAlerts();
		$scope.alertsClusters.splice(0,$scope.alertsClusters.length);
		
		if(cluster!=undefined && cluster!=null){
			$scope.clusterURL="https://web.alterante.com/cass/index.htm?cluster="+cluster.cluster;
			$scope.clusterName=cluster.name;
			$scope.user=cluster.user;
			$scope.password=cluster.password;
		}else{
			if($scope.preClusterURL==null||$scope.preClusterURL==undefined || $scope.preClusterURL==''){
				$rootScope.addAlert("Pleas copy the alterante URL in the box","danger");
				return;
			}else if($scope.preClusterURL.indexOf('https://web.alterante.com/cass/index.htm?cluster=')<0){
				$rootScope.addAlert("Pleas copy the valid alterante URL in the box, for example https://web.alterante.com/cass/index.htm?cluster=879fcde6-4ccc-484e-9e1a-185352d85a68","danger");
				return;
			}else{
				var clus=$scope.preClusterURL.split('=')[1];
				var uuid=clus.split("-");
				if( uuid.length!=5 || !((/^[0-9A-Fa-f]{8}$/i.test(uuid[0]))&&(/^[0-9A-Fa-f]{4}$/i.test(uuid[1]))&&(/^[0-9A-Fa-f]{4}$/i.test(uuid[2]))&&(/^[0-9A-Fa-f]{4}$/i.test(uuid[3]))&&(/^[0-9A-Fa-f]{12}$/i.test(uuid[4])))){
					$rootScope.addAlert("Invalid cluster, please copy the valid alterante URL in the box, for example https://web.alterante.com/cass/index.htm?cluster=879fcde6-4ccc-484e-9e1a-185352d85a68","danger");
					return;
				}
			} 
			$scope.clusterURL=$scope.preClusterURL;
			$scope.preClusterURL="";
			$scope.clusterName="";
			$scope.user="";
			$scope.password="";
		}
		
		$("#clusterform").modal({backdrop:'static',keyboard: false});
		$scope.close=function(){
			$("#clusterform").modal('hide');
		}
		$scope.acceptCluster=function(){
			$scope.alertsClusters.splice(0,$scope.alertsClusters.length);
			
			if($scope.clusterURL==undefined || $scope.clusterURL==null || $scope.clusterURL==''){
				$scope.alertsClusters.push({msg:"Cluster URL is mandatory"});
			}
			else if($scope.clusterName==undefined || $scope.clusterName==null || $scope.clusterName==''){
				$scope.alertsClusters.push({msg:"Plase type any name for this cluster"});
			}
			else if($scope.user==undefined || $scope.user==null || $scope.user==''){
				$scope.alertsClusters.push({msg:"Plase type your user in this cluster"});
			}
			else if($scope.password==undefined || $scope.password==null || $scope.password==''){
				$scope.alertsClusters.push({msg:"Plase type your password in this cluster"});
			}else{
				var clusterID=$scope.clusterURL.substring($scope.clusterURL.indexOf("=")+1);
				$("#clusterformimgloading").show();
				$.ajax(
				{
					url:'addmulticluster.fn?multiclusteruser='+encodeURIComponent($scope.user)+'&multiclusterpassword='+encodeURIComponent($scope.password)+'&multiclusterid='+encodeURIComponent(clusterID)+"&multiclustername="+encodeURIComponent($scope.clusterName),
					async:true,
					dataType:'json',
					success:function(data){
						
						try{
							if(data.result){
								$rootScope.alerts.push({msg:'Loading tags..',type:'success'});
								$("#clusterformimgloading").hide();
								$("#clusterform").modal('hide');
						
								$rootScope.$apply(function(){setTimeout($scope.loadClusters,100);});	
							}else{
								$("#clusterformimgloading").hide();
								$scope.alertsClusters.push({msg:'User or password invalid'});
								$rootScope.$apply();
							}
							
						}catch(e){
							
						}
				}
					
				});
			}
			
		}
	}
	
	//END CRUD CLUSTER
	
	setTimeout($scope.loadQuery,25);
	ngProgressLite.done();
});