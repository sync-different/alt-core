var module =angular.module('app', ['app.controllers','ngRoute','ui.bootstrap','ui.slider','ngSanitize']);

module.config(['$routeProvider',
                  function($routeProvider) {
                    $routeProvider.
                      when('/MyFiles/:ftype/:range', {
                        templateUrl: 'partials/partialMyFiles.htm',
                        controller: 'MyFilesController'
                      }).
					   when('/MyFiles/grid/:ftype/:range', {
                        templateUrl: 'partials/partialMyFilesGrid.htm',
                        controller: 'MyFilesController'
                      }).
					  when('/Home', {
                        templateUrl: 'partials/partialHome.htm',
                        controller: 'HomeController'
                      }).
					   when('/Backup', {
                        templateUrl: 'partials/partialBackup.htm',
                        controller: 'BackupController'
                      }).
					  when('/Shares', {
                        templateUrl: 'partials/partialShares.htm',
                        controller: 'SharesController'
                      }).
					  when('/MultiCluster', {
                        templateUrl: 'partials/partialMultiCluster.htm',
                        controller: 'MultiClusterController'
                      }).
					  when('/MultiCluster/:ftype/:range', {
                        templateUrl: 'partials/partialMultiCluster.htm',
                        controller: 'MultiClusterController'
                      }).
                     otherwise(
                        {
                            redirectTo: "/MyFiles"
                        });
                }]);
