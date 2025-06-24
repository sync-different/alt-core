angular.module('app.conf', []).factory('Conf', function() {
  return {
      urls: {
          query: {
              principal: "/cass/query.fn",
              applyTag: '/cass/applytags.fn'
          }
      },
      Util: {
          Mail: {
              validateEmail: function(email) {
                  var re = /^([\w-]+(?:\.[\w-]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$/i;
                  return re.test(email);
              }
          }
      }
  };
});

var moduleControllers = angular.module('app.controllers', ['ngProgressLite', 'ngSanitize', 'app.conf', 'ui.slider', 'ui.router']);
var moduleServices = angular.module('app.services', ['ngProgressLite', 'ngSanitize', 'app.conf']);

moduleControllers.filter('to_trusted', ['$sce', function($sce) {
  return function(text) {
      return $sce.trustAsHtml(text);
  };
}]);

moduleControllers.directive('selectOnClick', function() {
  return {
      restrict: 'A',
      link: function(scope, element) {
          element.on('click', function() {
              if (this.tagName === 'INPUT' || this.tagName === 'TEXTAREA') {
                  this.select();
              }
          });
      }
  };
});

moduleControllers.directive('ngEnter', function() {
  return function(scope, element, attrs) {
      element.on("keydown keypress", function(event) {
          if (event.which === 13) {
              scope.$apply(function() {
                  scope.$eval(attrs.ngEnter);
              });
              event.preventDefault();
          }
      });
  };
});

moduleControllers.directive('onCarouselChange', function($parse) {
  return {
      require: 'uib-carousel',
      link: function(scope, element, attrs, carouselCtrl) {
          var fn = $parse(attrs.onCarouselChange);
          var origSelect = carouselCtrl.select;
          carouselCtrl.select = function(nextSlide, direction) {
              if (nextSlide !== this.currentSlide) {
                  fn(scope, {
                      nextSlide: nextSlide,
                      direction: direction
                  });
              }
              return origSelect.apply(this, arguments);
          };
      }
  };
});

moduleControllers.directive('compileData', function($compile) {
  return {
      scope: true,
      link: function(scope, element, attrs) {
          var elmnt;
          attrs.$observe('template', function(myTemplate) {
              if (myTemplate) {
                  elmnt = $compile(myTemplate)(scope);
                  element.empty();
                  element.append(elmnt);
              }
          });
      }
  };
});
