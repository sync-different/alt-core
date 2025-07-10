	var fileView="list";
	var sortOrder="Asc";
	var netty_port_post=8085;
	var tagshare=null;
	var username=null;
	var isSafari = !!navigator.userAgent.match(/Version\/[\d\.]+.*Safari/);
	if (navigator.userAgent.indexOf('AppleWebKit') != -1) {
		isSafari = true;
	}
	
	//var isIE= navigator.userAgent.indexOf("Edge") != -1  || navigator.appVersion.indexOf("MSIE") != -1 || ((navigator.appName == "Netscape") && (navigator.appVersion.indexOf('Trident') > -1)); //Funciones obsoletas
	var isIE = /Trident|MSIE|Edge/.test(navigator.userAgent);

	var facebookInit=false
	if(isIE){
		$.ajaxSetup({
		  xhrFields: {
			withCredentials: true
		  }
		});
	}

	$.ajax(
		{
			url:'/cass/nodeinfo.fn?_='+Math.random(),
			async:true,
			dataType:'json',
			success:function(data){
				for(var i=0;i< data.nodes.length;i++){
					var node=data.nodes[i];	
					if(node.node_type=='server'){
						if(node.node_nettyport_post!=undefined){
							netty_port_post=node.node_nettyport_post;
						}
					}
				}
			}
		});
		
	
	var QueryString = function () {
	  // This function is anonymous, is executed immediately and 
	  // the return value is assigned to QueryString!
	  var query_string = {};
	  var query = window.location.search.substring(1);
	  var vars = query.split("&");
	  for (var i=0;i<vars.length;i++) {
		var pair = vars[i].split("=");
			// If first entry with this name
		if (typeof query_string[pair[0]] === "undefined") {
		  query_string[pair[0]] = decodeURIComponent(pair[1]);
			// If second entry with this name
		} else if (typeof query_string[pair[0]] === "string") {
		  var arr = [ query_string[pair[0]],decodeURIComponent(pair[1]) ];
		  query_string[pair[0]] = arr;
			// If third or later entry with this name
		} else {
		  query_string[pair[0]].push(decodeURIComponent(pair[1]));
		}
	  } 
		return query_string;
	}();
	
	
	function fbLogin(callback) {
		var uri=encodeURIComponent("http://localhost:8081/cass/setfacebooktoken.fn") ;
		//document.location.href="https://www.facebook.com/dialog/oauth?client_id=447887422078794&redirect_uri="+uri+"&response_type=token&scope=publish_actions";
		var win=window.open("https://www.facebook.com/dialog/oauth?client_id=447887422078794&redirect_uri="+uri+"&response_type=token&scope=publish_actions");
		
		win.onbeforeunload=function(){callback()}; 
	}
	
	function popupBloker()
	{
		var blnBloqueado;
		var ventana = window.open('','','width=1,height=1,left=0,top=0,scrollbars=no');
		if(ventana){
			ventana.close()
			blnBloqueado = false
		}
		else
			blnBloqueado = true
		
		
		return blnBloqueado;
	}

	function getFBToken( callback){
		
		function getToken(){
			facebookInit=true;
			FB.init({
				  appId      : '447887422078794',
				  xfbml      : true,
				  version    : 'v2.5'
				});

				FB.getLoginStatus(function(response) {
				  if (response.status === 'connected') {
					console.log('Logged in.');
					var accessToken = response.authResponse.accessToken;
					console.log(accessToken);
					callback(accessToken);
				  }
				  else {
					FB.login(function(response) {
						if (response.status === 'connected') {
							var accessToken = response.authResponse.accessToken;
							console.log(accessToken);
							callback(accessToken);
						}else{
							callback("error",response);
						}
						
						}, {scope: 'publish_actions'});

				  }
				  
				});
		}
		
		
		if(facebookInit){
			getToken();
		}else{
			window.fbAsyncInit = function() {
				getToken();
			};
			(function(d, s, id){
				 var js, fjs = d.getElementsByTagName(s)[0];
				 if (d.getElementById(id)) {return;}
				 js = d.createElement(s); js.id = id;
				 js.src = "//connect.facebook.net/en_US/sdk.js";
				 fjs.parentNode.insertBefore(js, fjs);
			}(document, 'script', 'facebook-jssdk'));
		}	
		
	}
	
	function removeBlank(arr){
		var aux=[];
		for(var i=0; i< arr.length;i++){
			if(arr[i]!=null && arr[i]!=""){
				aux.push(arr[i]);
			}
		}
		return aux;
	}
	
	function getPartial(partialname){
		var res="";
		$.ajax({
			  type: "GET",
			  url: "partials/"+partialname,
			  async:false,
			  dataType:"text",
			  success: function(data){
				res=data;  
			  }
			});
		
		return res;
	}
	
	function loadLeftMenuValues(ftype,range,objFound){
		try{
			if(objFound==null || objFound==undefined)
			 $.ajax(
				{
					url:'/cass/sidebar.fn?ftype='+ftype+'&foo='+$("#txtSearch").val()+"&days="+(range=='.all'?"":range),
					dataType:'json',
					async:false,
					success:function(data){
						objFound=data.objFound;
					}
				});
				
				$("#n_total").html(objFound[0].nTotal);
				$("#n_photo").html(objFound[0].nPhoto);
				$("#n_music").html(objFound[0].nMusic);
				$("#n_video").html(objFound[0].nVideo);
				$("#n_docu").html(objFound[0].nDocuments);
				$("#n_doc").html(objFound[0].nDoc);
				$("#n_xls").html(objFound[0].nXls);
				$("#n_ppt").html(objFound[0].nPpt);
				$("#n_pdf").html(objFound[0].nPdf);
				$("#n_past24h").html(objFound[1].nPast24h);
				$("#n_past3d").html(objFound[1].nPast3d);
				$("#n_past7d").html(objFound[1].nPast7d);
				$("#n_past14d").html(objFound[1].nPast14d);
				$("#n_past30d").html(objFound[1].nPast30d);
				$("#n_past365d").html(objFound[1].nPast365d);
				$("#n_alltime").html(objFound[1].nAllTime);
				 
		}catch(e){
		}		
	}
	
	
	
	
	jQuery.fn.scrollToElem = function(elem, speed) { 
		$(this).animate({
			scrollTop:  $(this).scrollTop() - $(this).offset().top + $(elem).offset().top 
		}, speed == undefined ? 1000 : speed); 
		return this; 
	};
	
	function convertUTCDateToLocalDate(date) {
		var newDate = new Date(date.getTime());

		var offset = new Date().getTimezoneOffset() / 60;
		var hours = date.getHours();

		newDate.setHours(hours - offset);

		return date;   
	}

	function getUTCLong(){
		var now = new Date();
		var offset = new Date().getTimezoneOffset() / 60;
		var utc = new Date(Date.UTC(
			now.getUTCFullYear(),
			now.getUTCMonth(),
			now.getUTCDate(),
			now.getUTCHours()+offset,
			now.getUTCMinutes(),
			now.getUTCSeconds(),
			now.getUTCMilliseconds()	
		));
		
		return utc.getTime();
	}
	
	function validateEmail(email) {
		var re = /^([\w-]+(?:\.[\w-]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$/i;
		return re.test(email);
    }
		
	function download(nickname){
		$("#d"+nickname).click();
	}
	
	function getCookie(name) {
		var re = new RegExp(name + "=([^;]+)");
		var value = re.exec(document.cookie);
		return (value != null) ? decodeURIComponent(value[1]) : null;
	}
  
	$(function() {
		$("#footerAudioBar").hide();
		$("#footerVideoBar").hide();
		$.ajaxSetup({ cache: false });
	
		$(document).on('ajaxSuccess', function(event, xhr, settings) {
			try {
				var data = xhr.responseText;
				if (data == null || data == undefined || data == '') {
					document.location.href = "/cass/uiv3/indexv2.htm";
				}
			} catch (e) {
			}
		});
	
		$(document).on('ajaxComplete', function(event, xhr, settings) {
			try {
				var data = xhr.responseText;
				if (data == null || data == undefined || data == '') {
					document.location.href = "/cass/uiv3/indexv2.htm";
				}
			} catch (e) {
			}
		});
	
		$(document).on('ajaxError', function(event, xhr, settings) {
			try {
				var data = xhr.responseText;
				if (data == null || data == undefined || data == '') {
					document.location.href = "/cass/uiv3/indexv2.htm";
				}
			} catch (e) {
			}
		});
	
		$(document).on('ajaxSend', function() {
		});
	});	
	
	function closeVideo(){
		$("#footerVideoBar").hide();
		try{
			var player = videojs('video');
			player.dispose();
		}catch(e){
		}
		
		$("#videoContainer").html('');
	}
	function closeAudio(){
		$("#footerAudioBar").hide();
		$("#audioFile").attr('src','');
		document.getElementById('audio').pause();
		
	}
	function getDocHeight() {
		var D = document;
		return Math.max(
			D.body.scrollHeight, D.documentElement.scrollHeight,
			D.body.offsetHeight, D.documentElement.offsetHeight,
			D.body.clientHeight, D.documentElement.clientHeight
		);
	}
	function replaceAll(find, replace, str) {
	  return str.replace(new RegExp(find, 'g'), replace);
	}
	
	function getWindowData(){
		var widthViewport,heightViewport,xScroll,yScroll,widthTotal,heightTotal;
		if (typeof window.innerWidth != 'undefined'){
		 	widthViewport= window.innerWidth-17;
			heightViewport= window.innerHeight-17;
		}else if(typeof document.documentElement != 'undefined' && typeof document.documentElement.clientWidth !='undefined' && document.documentElement.clientWidth != 0){
			widthViewport=document.documentElement.clientWidth;
			heightViewport=document.documentElement.clientHeight;
		}else{
			widthViewport= document.getElementsByTagName('body')[0].clientWidth;
			heightViewport=document.getElementsByTagName('body')[0].clientHeight;
		}
		xScroll=self.pageXOffset || (document.documentElement.scrollLeft+document.body.scrollLeft);
		yScroll=self.pageYOffset || (document.documentElement.scrollTop+document.body.scrollTop);
		widthTotal=Math.max(document.documentElement.scrollWidth,document.body.scrollWidth,widthViewport);
		heightTotal=Math.max(document.documentElement.scrollHeight,document.body.scrollHeight,heightViewport);
		return [widthViewport,heightViewport,xScroll,yScroll,widthTotal,heightTotal];
	}

