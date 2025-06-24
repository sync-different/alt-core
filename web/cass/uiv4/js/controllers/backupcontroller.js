angular.module('app.controllers').controller('BackupController', function (Conf,ngProgressLite,$state, $routeParams,$anchorScroll,$filter,$compile,$location,$rootScope,$scope) {
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
	$rootScope.mnu='backup';
	
	$scope.addRule=function(){
		var index=$scope.rules.length;
	   	$scope.rules.push({rule:$scope.rules.length,filetypes:$.extend({},$scope.filetypes),devices:$.extend({length:$scope.devices.length},$scope.devices)});
		setTimeout(
			function(){
				$('#tree'+index).treeview({
					  expandIcon: 'glyphicon glyphicon-chevron-right',
					  collapseIcon: 'glyphicon glyphicon-chevron-down',
					   //nodeIcon: 'glyphicon glyphicon-folder-close',		  
					  showCheckbox: true,
					  onNodeChecked: function(event, node) {
						  if(node.nodes!=undefined && node.nodes.length>0){
							  for(var i=0; i<node.nodes.length;i++){
								  var n=node.nodes[i];
								  $(this).treeview("checkNode", [ n.nodeId, { silent: true } ]);								  
							  }
							  $(this).treeview('expandNode', [ node.nodeId, { levels: 2, silent: true } ]);
						  }
					  },
					  onNodeUnchecked: function (event, node) {
						  if(node.nodes!=undefined && node.nodes.length>0){
							  for(var i=0; i<node.nodes.length;i++){
								  var n=node.nodes[i];
								  $(this).treeview("uncheckNode", [ n.nodeId, { silent: true } ]);								  
							  }
						  }
					  },
					  onNodeSelected:function(event, node){
						   $(this).treeview('expandNode', [ node.nodeId, { levels: 2, silent: true } ]);
					  }, 
					  data:$scope.rules[$scope.rules.length-1].filetypes}).treeview('collapseAll', { silent: true });
			}
		,25);
	}
	
	$scope.loadRule=function(ruleIndex,ruleToLoad){
		setTimeout(
			function(){
				var items=$("#tree"+ruleIndex).treeview("getUnchecked");
				var extensions=ruleToLoad.extensions;
				for(var i=0; i<extensions.length;i++){
					var ext=extensions[i];
					for(var j=0; j< items.length;j++){
						var item=items[j];
						if(item.ext==ext){
							$("#tree"+ruleIndex).treeview("checkNode", [ item.nodeId, { silent: true } ]);
							var parent=$("#tree"+ruleIndex).treeview("getParent", [ item.nodeId, { silent: true } ]);
							$("#tree"+ruleIndex).treeview('expandNode', [ parent.nodeId, { levels: 2, silent: true } ]);
						}
					}
				}
				for(var j=0; !!ruleToLoad.devices[j];j++){
					var selDevice=ruleToLoad.devices[j];
					if(!!selDevice.node_id){
						$("#device"+ruleIndex+"_"+selDevice.node_id).prop('checked', true);
					}else{
						$("#device"+ruleIndex+"_").prop('checked', true);
					}
				}
			}
		,1000);
	}
	
	$scope.load=function(){
		$.ajax(
		{
			url:'/cass/nodeinfo.fn',
			async:false,
			dataType:'json',
			success:function(data){
				$scope.devices=data.nodes;
				var i=0;
				for(;!!$scope.devices[i];i++){};
				$scope.devices.length=i;	
			}
		});
		
		$.ajax(
		{
			url:'/cass/getextensions.fn',
			async:false,
			dataType:'json',
			success:function(data){
				$scope.filetypes=[];
				for(var i=0; i<data.filetypes.length;i++){
					var group=data.filetypes[i];
					var node={text:group.group,nodes:[],icon:group.group_icon};
					for(var j=0; j< group.extensions.length;j++){
						var ext=group.extensions[j];
						node.nodes.push({text:ext.ext_name+" ("+ext.ext+")",ext:ext.ext,icon:ext.ext_icon});
					}
					$scope.filetypes.push(node);
				}
			}
		});
		
		
			
		//Load rules defined	
		$scope.rules=[];
		//Call a traer las reglas
		$.ajax(
		{
			url:'/cass/getbackupconfig.fn',
			async:false,
			dataType:'json',
			success:function(data){
				$scope.rulesToSave=data;
			}
		});
		
		if(!!$scope.rulesToSave && $scope.rulesToSave.length>0){
			for(var i=0;i< $scope.rulesToSave.length;i++){
				$scope.addRule();
				$scope.loadRule($scope.rules.length-1,$scope.rulesToSave[i]);
			}
		}
		
		if($scope.rules.length==0){
		   	$scope.addRule();
		}		
	}
	
	$scope.load();
	
	$scope.selCancel=function(){
		$scope.load();
		$("html,body").animate({scrollTop:0});		
	}
	
	$scope.selAcept=function(){
		var rulesToSave=[];
		for(var i=0; i<$scope.rules.length;i++){
			var rule=$scope.rules[i];
			var chekeds=$("#tree"+i).treeview("getChecked");
			var extChecked=[];
			for(var k=0; k<chekeds.length;k++ ){
				if(!!chekeds[k].ext){
					extChecked.push(chekeds[k].ext);
				}
			}
			if(extChecked.length>0){
				var devices=$scope.rules[i].devices;
				var devicesSelected=[];
				for(var j=0; !!devices[j] ;j++){
					if($("#device"+rule.rule+"_"+devices[j].node_id).is(":checked") || ($("#device"+rule.rule+"_").is(":checked") && !devices[j].node_id)){
						devicesSelected.push({node_id:devices[j].node_id,node_type:devices[j].node_type});
					}
				}
				if(devicesSelected.length>0){
					rulesToSave.push({rule:i,extensions:extChecked, devices:devicesSelected});
				}
			}
		}
		
		$.ajax(
			{
				type: "POST",
				url:"/backupconfig.c",
				headers: { 'name': 'backupconfig.c' },
				async:false,
				data:JSON.stringify(rulesToSave),
				success:function(){
					$scope.alerts.splice(0,$scope.alerts.length);
					$scope.alerts.push({type:'success',msg:'Rules apply success'});
					$("html,body").animate({scrollTop:0});		
				}
			}
		);
		
		
	}
	
	
	$scope.widthPercentage=function(p){
		var aux=$scope.dec(p);	
		if(aux.indexOf(',')>0){
			return parseInt(aux.substr(0,aux.indexOf(',')),10)*2;	
		}else{
			return parseInt(aux.substr(0,aux.indexOf('.')),10)*2;	
		}
		
	}
	
	ngProgressLite.done();
});