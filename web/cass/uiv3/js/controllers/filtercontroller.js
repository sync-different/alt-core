angular.module('app.controllers').controller('FilterController', function (Conf,ngProgressLite,$state, $routeParams,$anchorScroll,$filter,$compile,$location,$rootScope,$scope) {
	ngProgressLite.set(Math.random());
	ngProgressLite.start();
	$rootScope.selAllAttr=false;
	$scope.ftype=".all";
	$scope.range=".all";
	
	$rootScope.initMFilters=function(){
		$rootScope.mftype=".all";
		$rootScope.mrange=".all";	
	}
	$rootScope.initMFilters();
	
	//Device Folder Explorer

	$scope.loadDevices=function(){
		$.ajax(
		{
			url:'/cass/nodeinfo.fn',
			async:false,
			dataType:'json',
			success:function(data){
				$scope.devices=data.nodes;
			}
		});
	}
	
	$scope.loadDevices();
	
	$rootScope.deviceExplorer=function(device){
		alert("Device:"+device.node_id);
	}
	//Device Folders Explorer
	
	$("#my-dropzone").hide();
	
	var previewNode = document.querySelector("#template");
	var previewTemplate = previewNode.parentNode.innerHTML;
	previewNode.parentNode.removeChild(previewNode);


   	var chunkSize=1024 * 1024*10;
	var fileupload=new Dropzone("#my-dropzone",
		{	
			url:'/cass/file',
			previewTemplate: previewTemplate,
			clickable: ".fileinput-button",
			uploadMultiple:false,
			chunking: true,
            forceChunking: true,
            chunkSize: chunkSize, // 100MB per chunk
            maxFilesize:1024 * 1024 * 1024*5000,// 1000GB per chunk
            parallelChunkUploads: false,
            retryChunks: true,
            retryChunksLimit: 3,
            init: function() {
                let startTime = 0;
                let lastLoaded = 0;


                this.on("uploadprogress", function(file, progress, bytesSent) {
                  const elapsed = (Date.now() - startTime) / 1000; // seconds
                  if (elapsed > 0) {
                    const uploadedMB = bytesSent / (1024 * 1024);
                    const speedMbps = (uploadedMB * 8) / elapsed;
                    const speedKBs= speedMbps * 122.07;
                    document.getElementById("showSpeedUpload").innerHTML='Speed: '+speedKBs.toFixed(0)+ 'KB/s';
                  }
                  lastLoaded = bytesSent;
                });

                this.on("sending", function(file, xhr, formData) {
                  // Change the file name sent to the server
                    startTime = Date.now();
                    lastLoaded = 0;
                    var chunkIndex = this.options.chunkIndex;
                    var lasIndex= Math.ceil(file.size/chunkSize);
                    this.options.paramName="upload."+file.name+'.'+lasIndex+'.'+(++chunkIndex)+".p";
                    var parser = document.createElement('a');
                    parser.href = document.location.href;
                    if (parser.protocol == "http:")
                        this.options.url = "http://"+parser.hostname+":"+netty_port_post+"/formpost";//+file.name;
                    else{
                        this.options.url = "https://"+parser.hostname+"/"+this.options.paramName;
                        // Suppose 'formData' is your FormData instance
                        /*formData.delete('dzchunkindex');
                        formData.delete('dztotalfilesize');
                        formData.delete('dzchunksize');
                        formData.delete('dztotalchunkcount');
                        formData.delete('dzchunkbyteoffset');
                        formData.delete('WebKitFormBoundary5iwA0HqPfTOnv1OH');
                        formData.delete(this.options.paramName);
                        */
                        // Repeat for any other keys you want to remove
                    }
                    this.options.chunkIndex=chunkIndex;

                });

				this.on("processing", function(file) {
                    var chunkIndex=0;
					var lasIndex= Math.ceil(file.size/chunkSize);
                    this.options.paramName="upload."+file.name+'.'+lasIndex+'.'+(++chunkIndex)+".p";
                    this.options.header="Access-Control-Allow-Origin: https://web.alterante.com";
					var parser = document.createElement('a');
					parser.href = document.location.href;
				    if (parser.protocol == "http:")
				    	this.options.url = "http://"+parser.hostname+":"+netty_port_post+"/formpost";//+file.name;
					else
						this.options.url = "https://"+parser.hostname+"/"+this.options.paramName;
                    this.options.chunkIndex=chunkIndex;
                    this.options.chunkError=false;
				});



			}
	});
 	 
	// In your controller or after DOM is ready
    noUiSlider.create(document.getElementById('speedSlider'), {
      start: [10],
      step: null,
      range: {
        'min': 5,
        '0%': 5,
        '33%': 10,
        '66%': 15,
        '100%': 20,
        'max': 20
      },
      snap: true,
      pips: {
        mode: 'values',
        values: [5, 10, 15, 20],
        density: 5
      }
    });

    $scope.onSpeedChange=function(speedValue){
        fileupload.options.chunkSize = speedValue*1024*1024;
    }

    document.getElementById('speedSlider').noUiSlider.on('update', function(values, handle) {
      $scope.onSpeedChange(Number(values[handle]));
    });

	$(document).on('dragover', function(e) {
		var dt = e.originalEvent.dataTransfer;
		if(dt.types != null && (dt.types.indexOf ? dt.types.indexOf('Files') != -1 : dt.types.contains('application/x-moz-file'))) {
			$("#my-dropzone").show(); 
		}
	})
	
	$scope.openUpload=function(){
		$("#my-dropzone").show(); 
	}
	
	$scope.closeUpload=function(){
		fileupload.removeAllFiles();
		$("#my-dropzone").hide(); 
	}
	
	$scope.openCloseLeftMenu=function(tthis){
		if(parseInt($("#leftMenu").css("left"),10)<0){
			$("#leftMenu").animate({left:"0px"});
			$(tthis).removeClass("glyphicon-chevron-right");
			$(tthis).addClass("glyphicon-chevron-left");
		}else{
			$("#leftMenu").animate({left:"-150px"});
			$(tthis).removeClass("glyphicon-chevron-left");
			$(tthis).addClass("glyphicon-chevron-right");

		}
	}
	
	$('#leftMenu,#leftMenu3').on('mouseenter', function() {
		try {
			$("#leftMenu2,#leftMenu4").animate({ left: "80px" });
			$scope.showIcons = false;
			$rootScope.$apply();
		} catch (e) {
		}
	});

    $('#leftMenu2,#leftMenu4').on('mouseleave', function() {
		try {
			$("#leftMenu2,#leftMenu4").animate({ left: "-250px" });
			$scope.showShareMenu = false;
			$scope.showIcons = true;
			$rootScope.$apply();
		} catch (e) {}
	});
	
	$("#deviceExplorer").on('mouseenter', function() {
		try {
			$("#leftMenu2").animate({ left: "-250px" });
			$scope.showShareMenu = false;
			$scope.showIcons = true;
			$rootScope.$apply();
		} catch (e) {}
	});
	
	$rootScope.loadLeftMenuValuesCluster=function(objFound){
		if(!$rootScope.mObjFount || $rootScope.mObjFount==null){
			$rootScope.mObjFount={nTotal:0,nPhoto:0,nMusic:0,nVideo:0,nDocuments:0,nDoc:0,nXls:0,nPpt:0,nPdf:0,nPast24h:0,nPast3d:0,nPast7d:0,nPast14d:0,nPast30d:0,nPast365d:0,nAllTime:0};
		}
		if(!!objFound && objFound!=null){
			$rootScope.mObjFount.nTotal+=parseInt(objFound[0].nTotal,10);
			$rootScope.mObjFount.nPhoto+=parseInt(objFound[0].nPhoto,10);
			$rootScope.mObjFount.nMusic+=parseInt(objFound[0].nMusic,10);
			$rootScope.mObjFount.nVideo+=parseInt(objFound[0].nVideo,10);
			$rootScope.mObjFount.nDocuments+=parseInt(objFound[0].nDocuments,10);
			$rootScope.mObjFount.nDoc+=parseInt(objFound[0].nDoc,10);
			$rootScope.mObjFount.nXls+=parseInt(objFound[0].nXls,10);
			$rootScope.mObjFount.nPpt+=parseInt(objFound[0].nPpt,10);
			$rootScope.mObjFount.nPdf+=parseInt(objFound[0].nPdf,10);
			$rootScope.mObjFount.nPast24h+=parseInt(objFound[1].nPast24h,10);
			$rootScope.mObjFount.nPast3d+=parseInt(objFound[1].nPast3d,10);
			$rootScope.mObjFount.nPast7d+=parseInt(objFound[1].nPast7d,10);
			$rootScope.mObjFount.nPast14d+=parseInt(objFound[1].nPast14d,10);
			$rootScope.mObjFount.nPast30d+=parseInt(objFound[1].nPast30d,10);
			$rootScope.mObjFount.nPast365d+=parseInt(objFound[1].nPast365d,10);
			$rootScope.mObjFount.nAllTime+=parseInt(objFound[1].nAllTime,10);
		}
		
		$("#mn_total").html($rootScope.mObjFount.nTotal);
		$("#mn_photo").html($rootScope.mObjFount.nPhoto);
		$("#mn_music").html($rootScope.mObjFount.nMusic);
		$("#mn_video").html($rootScope.mObjFount.nVideo);
		$("#mn_docu").html($rootScope.mObjFount.nDocuments);
		$("#mn_doc").html($rootScope.mObjFount.nDoc);
		$("#mn_xls").html($rootScope.mObjFount.nXls);
		$("#mn_ppt").html($rootScope.mObjFount.nPpt);
		$("#mn_pdf").html($rootScope.mObjFount.nPdf);
		$("#mn_past24h").html($rootScope.mObjFount.nPast24h);
		$("#mn_past3d").html($rootScope.mObjFount.nPast3d);
		$("#mn_past7d").html($rootScope.mObjFount.nPast7d);
		$("#mn_past14d").html($rootScope.mObjFount.nPast14d);
		$("#mn_past30d").html($rootScope.mObjFount.nPast30d);
		$("#mn_past365d").html($rootScope.mObjFount.nPast365d);
		$("#mn_alltime").html($rootScope.mObjFount.nAllTime);
	}
	
	$scope.show=function(page){
		if(page=='MyFiles'){
			$scope.ftype=".all";
			$scope.range=".all";
			$('#txtSearch').val('.all');
			$scope.loadQuery();
		}else if(page='Home'){
			$location.path('/Home');
		}
		$("html,body").animate({scrollTop:0});
	}
			
	$rootScope.selType=function(type){
		$scope.ftype=type;
		$scope.loadQuery();
	}
	
	$rootScope.selRange=function(range){
		$scope.range=range;
		$scope.loadQuery();
	}
	
	$rootScope.selMRange=function(range){
		$rootScope.mrange=range;
		$scope.loadMQuery();
	}
	
	$rootScope.selMType=function(type){
		$rootScope.mftype=type;
		$scope.loadMQuery();
	}

	
	$rootScope.selTag=function(tag){
		$scope.tag=tag;
		$("#txtSearch").val(tag);
		if($scope.ftype!=".all" || $scope.range!=".all"){
			$scope.ftype='.all';
			$scope.range=".all";
			$scope.loadQuery();
		}else{
			$("#txtSearch").trigger("enterKey");	
		}
		
		$('html,body').animate({ scrollTop: 0 }, 'slow');
		$("#leftMenu2").animate({left:"-250px"});
	}
	
	$scope.selMTag=function(tag,cluster){
		$rootScope.clusterQuery=cluster;
		$("#txtSearch").val(tag);
		if($scope.ftype!=".all" || $scope.range!=".all"){
			$scope.mftype='.all';
			$scope.mrange=".all";
			$scope.loadClusterQuery();
		}else{
			$("#txtSearch").trigger("enterKey");	
		}
		
		$('html,body').animate({ scrollTop: 0 }, 'slow');
		$("#leftMenu4").animate({left:"-250px"});
	}
	
	$(document).on("selTag",
		function(){
			$scope.selTag(selectedTag);
		}
	)
	
	
	$scope.clearActive=function(){
		$('#leftMenu').find('li').each(function(){$(this).removeClass('active')});
		$('#leftMenu2').find('li').each(function(){$(this).removeClass('active').find('i').each(function(){$(this).hide();});});
		$('#leftMenu3').find('li').each(function(){$(this).removeClass('active')});
		$('#leftMenu4').find('li').each(function(){$(this).removeClass('active').find('i').each(function(){$(this).hide();});});
	}
	
	$scope.activeMenu=function(){
		$('#leftMenu2').find('li[tagtype="'+$scope.ftype+'"]').each(function(){$(this).addClass('active').find('i').each(function(){$(this).show();});});
		$('#leftMenu2').find('li[tagrange="'+$scope.range+'"]').each(function(){$(this).addClass('active').find('i').each(function(){$(this).show();});});
		$('#leftMenu').find('li[tagtype="'+$scope.ftype+'"]').each(function(){$(this).addClass('active')});
		$('#leftMenu').find('li[tagrange="'+$scope.range+'"]').each(function(){$(this).addClass('active')});
		
		$('#leftMenu4').find('li[mtagtype="'+$scope.mftype+'"]').each(
		function(){
			$(this).addClass('active').find('i').each(
			function(){
					$(this).show();});
		});
		$('#leftMenu4').find('li[mtagrange="'+$scope.mrange+'"]').each(function(){$(this).addClass('active').find('i').each(function(){$(this).show();});});
		$('#leftMenu3').find('li[mtagtype="'+$scope.mftype+'"]').each(function(){$(this).addClass('active')});
		$('#leftMenu3').find('li[mtagrange="'+$scope.mrange+'"]').each(function(){$(this).addClass('active')});
	}
					
	$scope.loadQuery=function(){
		$scope.clearActive();
		$scope.activeMenu();
		loadLeftMenuValues($scope.ftype,$scope.range);
		if(fileView=="list"){
			$location.path("/MyFiles/"+$scope.ftype+"/"+$scope.range);	
		}else{
			$location.path("/MyFiles/grid/"+$scope.ftype+"/"+$scope.range);	
		}
		
		$("#leftMenu2,#leftMenu4").animate({left:"-250px"});
		$scope.showIcons=true;
	}
	
	$scope.loadMQuery=function(){
		$scope.clearActive();
		$scope.activeMenu();
		$location.path("/MultiCluster/"+$scope.mftype+"/"+$scope.mrange);	
		
		$("#leftMenu2,#leftMenu4").animate({left:"-250px"});
		$scope.showIcons=true;
	}
	
	$scope.loadinTags=function(){
		//TAGS
		 $.ajax({
			url:'/cass/gettags_webapp.fn?_='+Math.random(),
			dataType:'json',
			async:true,
			success:function(data){
				console.log(data);
				$scope.tags=data.fighters;
				$rootScope.tags=data.fighters;
				$scope.username=data.username;
				$rootScope.isAdmin=data.isAdmin=="true";
				username=data.username;
				try{
					$rootScope.$apply();
				}catch(e){
				}	 
			}
		});
	}
	
	$rootScope.loadinClusterTags=function(clusters){
		$scope.clusters=clusters;
		 
		$scope.tagsCluster=new Object();
		for(var i=0; i<$scope.clusters.length;i++ ){
			var cluster=$scope.clusters[i].cluster;
			//TAGS
			 $.ajax({
				url:'/cass/gettags_webapp.fn?_='+Math.random()+'&multiclusterid='+cluster,
				dataType:'json',
				async:false,
				success:function(data){
					$scope.tagsCluster[cluster]=data.fighters;
				}
			});
		}
		
		try{
			$rootScope.alerts.splice(0,$rootScope.alerts.length);
			$rootScope.$apply();	
		}catch(e){}
	}
	
	$scope.suggestTags=function(){
		 $.ajax({
			url:'/cass/suggest.fn?ftype='+$scope.ftype+'&days='+$scope.range+'&foo='+$('#txtSearch').val()+'&view=json&numobj=10',
			dataType:'json',
			async:true,
			success:function(data){
				$scope.showSuggest2=true;
				$scope.suggest2=data.fighters;
				try{
					$rootScope.$apply();
					
				}catch(e){}
				
			}
		});
	}
	
 
	
	$scope.search=function(name){
		$('#txtSearch').val(name);
		var all=false;
		if($scope.ftype!='.all' || $scope.range!=".all"){
			$scope.ftype=".all";
			$scope.range=".all";
			all=true;
		}
		$scope.showSuggest2=false;
		if(all){
			$scope.loadQuery();	
		}else{
			$('#txtSearch').trigger("enterKey");
		}
		$("html,body").animate({scrollTop:0});
	}
	
	$scope.openSettings=function(){
		$("#MAIN").attr("src","/cass/setup.htm?spage=1");
		$('#modalsettings').modal();
	}
	
	$scope.closeSettings=function(){
		$("#MAIN").attr("src","");
		$("#modalsettings").modal('hide');
	}
	
	$scope.openAbout=function(){
		$('#modalabout').modal();
	}
	
	$scope.closeAbout=function(){
		$("#modalabout").modal('hide');
	}
	
	$scope.openShares=function(){
		$location.path("/Shares");
	}
	
	$scope.openBackup=function(){
		$location.path("/Backup");
	}
	
	$scope.openMultiCluster=function(){
		$location.path("/MultiCluster");
	}
	
	$scope.openShare=function(tag,$event){
		$event.stopPropagation();
		tagshare=tag;
		$(document).trigger("tagshare");
	}
	
	$(document).off('click');
	$(document).on('click',
		function(){
			$scope.showSuggest2=false;
			$scope.showMinMenu=false;
			$scope.showShareMenu=false;
			$scope.$apply();
		}
	);
	
	$('#txtSearch').off("keyup");				
	$('#txtSearch').on("keyup", function(e) {
		if (e.which === 13) {
			$scope.showSuggest2 = false;
			$scope.showMinMenu = false;
			if ($('#txtSearch').val() === null || $('#txtSearch').val() === '') {
				$('#txtSearch').val('.all');
			}
			$(this).trigger("enterKey");
		} else if (!($('#txtSearch').val() === null || $('#txtSearch').val() === '')) {
			$scope.suggestTags();
		}
	});

	$(document).off('keyup');
	$(document).on('keyup', function(e) {
		if (e.which === 27) {
			$("#modalsettings,#modalabout").modal('hide');
			$(document).trigger("key_escape");
		} else if (e.which === 39) {
			$(document).trigger("key_arrow_right");
		} else if (e.which === 37) {
			$(document).trigger("key_arrow_left");
		}
	});
	
	$(document).off("updateFilterTags");
	$(document).on("updateFilterTags",function(e){
		$scope.loadinTags();
	});
	 
	$scope.openClusterDialog=function(){
		$scope.popupmsghome="Desea guardar este cluster?, si lo guarda luego podra consultarlo por la consulta multicluster";
		
		$("#popupmsghome").modal({backdrop:'static',keyboard: false});
		
		$scope.closePopup=function(){
			$("#popupmsghome").modal('hide');
		}
		
		$scope.acceptPopup=function(){
			$("#popupmsghome").modal('hide');
			$("#clusterformhome").modal({backdrop:'static',keyboard: false});
			$scope.alertsClusters=[];
			$scope.closeAlert=function(index){
				$scope.alertsClusters.splice(index,1);
			}
			$scope.acceptCluster=function(){
				if($scope.clusterName==undefined || $scope.clusterName==null || $scope.clusterName==''){
					$scope.alertsClusters.push({msg:'Please type any name for this cluster'});
				}else if($scope.clusterUser==undefined || $scope.clusterUser==null || $scope.clusterUser==''){
					$scope.alertsClusters.push({msg:'Please type your local user'});
				}else{
					$("#clusterformhome").modal('hide');
					 $.ajax({
						url:'/cass/saveloginmulticluster.fn?multiclustername='+encodeURIComponent($scope.clusterName)+'&multiclusteruser='+$scope.clusterUser,
						dataType:'json',
						async:true,
						success:function(data){
							
						}
					});
				}
			}
			
			$scope.close=function(){
				$("#clusterformhome").modal('hide');
			}
		}
	}
	
	$scope.loadQuery();
	$scope.loadinTags();
	ngProgressLite.done();
	$scope.isCluster=QueryString.cluster!=undefined;
	if($scope.isCluster && document.location.href.indexOf("localhost")>-1){
		$scope.openClusterDialog();
	}
});