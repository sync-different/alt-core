angular.module('app.controllers').controller('HomeController', function (Conf,ngProgressLite,$state, $routeParams,$anchorScroll,$filter,$compile,$location,$rootScope,$scope) {
	ngProgressLite.set(Math.random());
	ngProgressLite.start();
	$scope.dec=decodeURIComponent;
	$scope.up=toTitleCase;
	$rootScope.mnu='home';
	
	$scope.load=function(){
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
	
	$scope.load();
	var timerHome=setInterval(function(){$scope.load();$rootScope.$apply();},15000);

	$scope.widthPercentage=function(p){
		var aux=$scope.dec(p);	
		if(aux.indexOf(',')>0){
			return parseInt(aux.substr(0,aux.indexOf(',')),10)*2;	
		}else{
			return parseInt(aux.substr(0,aux.indexOf('.')),10)*2;	
		}
		
	}
	
	$scope.seen=function(longDate){
		if(longDate==undefined || longDate==null){
			return "";
		}
		
		var date1 = new Date(parseInt(longDate,10));
		var date2 = new Date();
		var timeDiff = Math.abs(date2.getTime() - date1.getTime());
		var diffDays = Math.ceil(timeDiff / (1000 * 3600 * 24)); 
		var diffSecond = Math.ceil(timeDiff / (1000)); 
		var diffMinute = Math.ceil(timeDiff / (1000 * 3600));
		var diffHour = Math.ceil(timeDiff / (1000 * 3600*60));
		
		if(diffMinute>0 && diffMinute<60){
			return "Last seen: "+diffMinute+" minute ago";
		}else if(diffMinute>60){
			return "Last seen: "+diffHour+" hour ago";
		}else{
			return "Last seen: "+diffSecond+" second ago";
		}

	}
	
	$scope.openSettings=function(){
		$("#leftMenu").hide();
		$('#modalsettings').modal({backdrop:'static',keyboard: false});
	}
	
	$scope.closeSettings=function(){
		$("#leftMenu").show();
		$("#modalsettings").modal('hide');
	}
	
	$rootScope.$on( "$routeChangeStart", function(event, next, current) {
	  if(next.$$route.controller.indexOf("Home")<0 && timerHome>0){
		  clearInterval(timerHome);
		  timerHome=0;
	  }
	});
	
	ngProgressLite.done();
});

Dropzone.prototype.on("complete", function (file) {

	const progressBar = file.previewElement.querySelector(".progress-bar");
	const progressContainer = file.previewElement.querySelector(".progress");
	if (progressBar) {
		progressBar.classList.remove("active");
	}

	if (progressContainer) {
		progressContainer.classList.remove("progress-striped");
	}

	let successMessage = file.previewElement.querySelector(".text-success");
	if (!successMessage) {

		successMessage = document.createElement("strong");
		successMessage.classList.add("text-success");
		successMessage.textContent = "File uploaded successfully.";

		progressContainer.parentNode.insertBefore(successMessage, progressContainer);
	}
});