/*
 jQuery UI Slider plugin wrapper
*/
angular.module('ui.slider', []).value('uiSliderConfig', {}).directive('uiSlider', ['uiSliderConfig', '$timeout', function (uiSliderConfig, $timeout) {
    uiSliderConfig = uiSliderConfig || {};
    return {
        require: 'ngModel',
        compile: function () {
            return function (scope, elm, attrs, ngModel) {

                function parseNumber(n, decimals) {
                    return decimals ? parseFloat(n) : parseInt(n);
                }

                var options = angular.extend(scope.$eval(attrs.uiSlider) || {}, uiSliderConfig);
                var prevRangeValues = { min: null, max: null };
                var properties = ['min', 'max', 'step'];
                var useDecimals = !angular.isUndefined(attrs.useDecimals);

                var init = function () {
                    if (angular.isArray(ngModel.$viewValue) && options.range !== true) {
                        console.warn('Change your range option of ui-slider. When assigning ngModel an array of values then the range option should be set to true.');
                        options.range = true;
                    }

                    angular.forEach(properties, function (property) {
                        if (angular.isDefined(attrs[property])) {
                            options[property] = parseNumber(attrs[property], useDecimals);
                        }
                    });

                    if (!elm[0].noUiSlider) {
                        noUiSlider.create(elm[0], Object.assign({}, options, {
                            start: ngModel.$viewValue || 0,
                            range: { min: options.min || 0, max: options.max || 100 }
                        }));
                    }
                    init = angular.noop;
                };

                angular.forEach(properties, function (property) {
                    attrs.$observe(property, function (newVal) {
                        if (!!newVal) {
                            init();
                            elm[0].noUiSlider.updateOptions({ [property]: parseNumber(newVal, useDecimals) });
                            ngModel.$render();
                        }
                    });
                });

                attrs.$observe('disabled', function (newVal) {
                    init();
                    elm[0].noUiSlider.updateOptions({ disabled: !!newVal });
                });

                scope.$watch(attrs.uiSlider, function (newVal) {
                    init();
                    if (newVal !== undefined) {
                        elm[0].noUiSlider.updateOptions(newVal);
                    }
                }, true);

                $timeout(init, 0, true);

                if (elm[0].noUiSlider) {
                    elm[0].noUiSlider.on('update', function (values) {
                        ngModel.$setViewValue(options.range ? values.map(parseFloat) : parseFloat(values[0]));
                        scope.$applyAsync();
                    });
                }

                ngModel.$render = function () {
                    init();
                    var method = options.range ? 'set' : 'set';

                    if (!options.range && isNaN(ngModel.$viewValue) && !(ngModel.$viewValue instanceof Array)) {
                        ngModel.$viewValue = 0;
                    } else if (options.range && !angular.isDefined(ngModel.$viewValue)) {
                        ngModel.$viewValue = [0, 0];
                    }

                    if (options.range) {
                        if (angular.isDefined(options.min) && options.min > ngModel.$viewValue[0]) {
                            ngModel.$viewValue[0] = options.min;
                        }
                        if (angular.isDefined(options.max) && options.max < ngModel.$viewValue[1]) {
                            ngModel.$viewValue[1] = options.max;
                        }
                        if (ngModel.$viewValue[0] > ngModel.$viewValue[1]) {
                            if (prevRangeValues.min >= ngModel.$viewValue[1]) {
                                ngModel.$viewValue[0] = prevRangeValues.min;
                            }
                            if (prevRangeValues.max <= ngModel.$viewValue[0]) {
                                ngModel.$viewValue[1] = prevRangeValues.max;
                            }
                        }
                        prevRangeValues.min = ngModel.$viewValue[0];
                        prevRangeValues.max = ngModel.$viewValue[1];
                    }

                    elm[0].noUiSlider.set(ngModel.$viewValue);
                };

                scope.$watch(attrs.ngModel, function () {
                    if (options.range === true) {
                        ngModel.$render();
                    }
                }, true);

                function destroy() {
                    if (elm[0].noUiSlider) {
                        elm[0].noUiSlider.destroy();
                    }
                }

                elm.on('$destroy', destroy);
            };
        }
    };
}]);
