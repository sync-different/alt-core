angular.module('app.controllers').controller('SharesController', function (Conf,ngProgressLite,$state, $routeParams,$anchorScroll,$filter,$compile,$location,$rootScope,$scope) {
	ngProgressLite.set(Math.random());
	ngProgressLite.start();
	$rootScope.mnu='shares';
	$("#loading").show();
	$scope.$on('$viewContentLoaded', function() {
		ngProgressLite.done();
	});
});