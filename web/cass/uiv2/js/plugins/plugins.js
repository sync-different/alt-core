
jQuery.fn.scrollTo = function(elem, speed) { 
    $(this).animate({
        scrollTop:  $(this).scrollTop() - $(this).offset().top + $(elem).offset().top 
    }, speed == undefined ? 500 : speed); 
    return this; 
};

	
	
function downloadURI(uri, name) 
{
    var link = document.createElement("a");
    link.download = name;
    link.href = uri;
    link.click();
}

function toTitleCase(str)
{
    return str.replace(/\w\S*/g, function(txt){return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();});
}

function isFlash( ) {
	var a='Shockwave';
	var b ='Flash'; 
    // try creating an AxtiveXObject (internet explorer use this object)
    try {
        a = new ActiveXObject(a+b+'.'+a+b);
    } 
    // if fails, is not ie, so check for the navigator.plugins object
    catch ( e ) {
        a = navigator.plugins[a+' '+b];
    }
	
    // return true/false
    return!!a;

}

/*
 * ngProgressLite - small && slim angular progressbars
 * http://github.com/voronianski/ngprogress-lite
 * Dmitri Voronianski http://pixelhunter.me
 * (c) 2013 MIT License
 */

(function (window, angular, undefined) {
	'use strict';

	angular.module('ngProgressLite', []).provider('ngProgressLite', function () {

		// global configs
		var settings = this.settings = {
			minimum: 0.08,
			speed: 300,
			ease: 'ease',
			trickleRate: 0.02,
			trickleSpeed: 500,
			template: '<div class="ngProgressLite"><div class="ngProgressLiteBar"><div class="ngProgressLiteBarShadow"></div></div></div>'
		};

		this.$get = ['$document', function ($document) {
			var $body = $document.find('body');
			var $progressBarEl, status, cleanForElement;

			var privateMethods = {
				render: function () {
					if (this.isRendered()) {
						return $progressBarEl;
					}

					$body.addClass('ngProgressLite-on');
					$progressBarEl = angular.element(settings.template);
					$body.append($progressBarEl);
					cleanForElement = false;

					return $progressBarEl;
				},

				remove: function () {
					$body.removeClass('ngProgressLite-on');
					$progressBarEl.remove();
					cleanForElement = true;
				},

				isRendered: function () {
					return $progressBarEl && $progressBarEl.children().length > 0 && !cleanForElement;
				},

				trickle: function () {
					return publicMethods.inc(Math.random() * settings.trickleRate);
				},

				clamp: function (num, min, max) {
					if (num < min) { return min; }
					if (num > max) { return max; }
					return num;
				},

				toBarPercents: function (num) {
					return num * 100;
				},

				positioning: function (num, speed, ease) {
					return { 'width': this.toBarPercents(num) + '%', 'transition': 'all ' + speed + 'ms '+ ease };
				}
			};

			var publicMethods = {
				set: function (num) {
					var $progress = privateMethods.render();

					num = privateMethods.clamp(num, settings.minimum, 1);
					status = (num === 1 ? null : num);

					setTimeout(function () {
						$progress.children().eq(0).css(privateMethods.positioning(num, settings.speed, settings.ease));
					}, 100);

					if (num === 1) {
						setTimeout(function () {
							$progress.css({ 'transition': 'all ' + settings.speed + 'ms linear', 'opacity': 0 });
							setTimeout(function () {
								privateMethods.remove();
							}, settings.speed);
						}, settings.speed);
					}

					return publicMethods;
				},

				get: function() {
					return status;
				},

				start: function () {
					if (!status) {
						publicMethods.set(0);
					}

					var worker = function () {
						setTimeout(function () {
							if (!status) { return; }
							privateMethods.trickle();
							worker();
						}, settings.trickleSpeed);
					};

					worker();
					return publicMethods;
				},

				inc: function (amount) {
					var n = status;

					if (!n) {
						return publicMethods.start();
					}

					if (typeof amount !== 'number') {
						amount = (1 - n) * privateMethods.clamp(Math.random() * n, 0.1, 0.95);
					}

					n = privateMethods.clamp(n + amount, 0, 0.994);
					return publicMethods.set(n);
				},

				done: function () {
					if (status) {
						publicMethods.inc(0.3 + 0.5 * Math.random()).set(1);
					}
				}
			};

			return publicMethods;
		}];
	});

})(window, window.angular);

angular.module('app', ['ngProgressLite'])
    .config(['ngProgressLiteProvider', function (ngProgressLiteProvider) {
        ngProgressLiteProvider.settings.speed = 1500;
    }]);

	
	/*
 Sticky-kit v1.1.2 | WTFPL | Leaf Corcoran 2015 | http://leafo.net
*/
(function(){var b,f;b=this.jQuery||window.jQuery;f=b(window);b.fn.stick_in_parent=function(d){var A,w,J,n,B,K,p,q,k,E,t;null==d&&(d={});t=d.sticky_class;B=d.inner_scrolling;E=d.recalc_every;k=d.parent;q=d.offset_top;p=d.spacer;w=d.bottoming;null==q&&(q=0);null==k&&(k=void 0);null==B&&(B=!0);null==t&&(t="is_stuck");A=b(document);null==w&&(w=!0);J=function(a,d,n,C,F,u,r,G){var v,H,m,D,I,c,g,x,y,z,h,l;if(!a.data("sticky_kit")){a.data("sticky_kit",!0);I=A.height();g=a.parent();null!=k&&(g=g.closest(k));
if(!g.length)throw"failed to find stick parent";v=m=!1;(h=null!=p?p&&a.closest(p):b("<div />"))&&h.css("position",a.css("position"));x=function(){var c,f,e;if(!G&&(I=A.height(),c=parseInt(g.css("border-top-width"),10),f=parseInt(g.css("padding-top"),10),d=parseInt(g.css("padding-bottom"),10),n=g.offset().top+c+f,C=g.height(),m&&(v=m=!1,null==p&&(a.insertAfter(h),h.detach()),a.css({position:"",top:"",width:"",bottom:""}).removeClass(t),e=!0),F=a.offset().top-(parseInt(a.css("margin-top"),10)||0)-q,
u=a.outerHeight(!0),r=a.css("float"),h&&h.css({width:a.outerWidth(!0),height:u,display:a.css("display"),"vertical-align":a.css("vertical-align"),"float":r}),e))return l()};x();if(u!==C)return D=void 0,c=q,z=E,l=function(){var b,l,e,k;if(!G&&(e=!1,null!=z&&(--z,0>=z&&(z=E,x(),e=!0)),e||A.height()===I||x(),e=f.scrollTop(),null!=D&&(l=e-D),D=e,m?(w&&(k=e+u+c>C+n,v&&!k&&(v=!1,a.css({position:"fixed",bottom:"",top:c}).trigger("sticky_kit:unbottom"))),e<F&&(m=!1,c=q,null==p&&("left"!==r&&"right"!==r||a.insertAfter(h),
h.detach()),b={position:"",width:"",top:""},a.css(b).removeClass(t).trigger("sticky_kit:unstick")),B&&(b=f.height(),u+q>b&&!v&&(c-=l,c=Math.max(b-u,c),c=Math.min(q,c),m&&a.css({top:c+"px"})))):e>F&&(m=!0,b={position:"fixed",top:c},b.width="border-box"===a.css("box-sizing")?a.outerWidth()+"px":a.width()+"px",a.css(b).addClass(t),null==p&&(a.after(h),"left"!==r&&"right"!==r||h.append(a)),a.trigger("sticky_kit:stick")),m&&w&&(null==k&&(k=e+u+c>C+n),!v&&k)))return v=!0,"static"===g.css("position")&&g.css({position:"relative"}),
a.css({position:"absolute",bottom:d,top:"auto"}).trigger("sticky_kit:bottom")},y=function(){x();return l()},H=function(){G=!0;f.off("touchmove",l);f.off("scroll",l);f.off("resize",y);b(document.body).off("sticky_kit:recalc",y);a.off("sticky_kit:detach",H);a.removeData("sticky_kit");a.css({position:"",bottom:"",top:"",width:""});g.position("position","");if(m)return null==p&&("left"!==r&&"right"!==r||a.insertAfter(h),h.remove()),a.removeClass(t)},f.on("touchmove",l),f.on("scroll",l),f.on("resize",
y),b(document.body).on("sticky_kit:recalc",y),a.on("sticky_kit:detach",H),setTimeout(l,0)}};n=0;for(K=this.length;n<K;n++)d=this[n],J(b(d));return this}}).call(this);
/*Tree https://github.com/jonmiles/bootstrap-treeview*/
/* =========================================================
 * bootstrap-treeview.js v1.2.0
 * =========================================================
 * Copyright 2013 Jonathan Miles
 * Project URL : http://www.jondmiles.com/bootstrap-treeview
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================= */

;(function ($, window, document, undefined) {

	/*global jQuery, console*/

	'use strict';

	var pluginName = 'treeview';

	var _default = {};

	_default.settings = {

		injectStyle: true,

		levels: 2,

		expandIcon: 'glyphicon glyphicon-plus',
		collapseIcon: 'glyphicon glyphicon-minus',
		emptyIcon: 'glyphicon',
		nodeIcon: '',
		selectedIcon: '',
		checkedIcon: 'glyphicon glyphicon-check',
		uncheckedIcon: 'glyphicon glyphicon-unchecked',

		color: undefined, // '#000000',
		backColor: undefined, // '#FFFFFF',
		borderColor: undefined, // '#dddddd',
		onhoverColor: '#F5F5F5',
		selectedColor: '#FFFFFF',
		selectedBackColor: '#428bca',
		searchResultColor: '#D9534F',
		searchResultBackColor: undefined, //'#FFFFFF',

		enableLinks: false,
		highlightSelected: true,
		highlightSearchResults: true,
		showBorder: true,
		showIcon: true,
		showCheckbox: false,
		showTags: false,
		multiSelect: false,

		// Event handlers
		onNodeChecked: undefined,
		onNodeCollapsed: undefined,
		onNodeDisabled: undefined,
		onNodeEnabled: undefined,
		onNodeExpanded: undefined,
		onNodeSelected: undefined,
		onNodeUnchecked: undefined,
		onNodeUnselected: undefined,
		onSearchComplete: undefined,
		onSearchCleared: undefined
	};

	_default.options = {
		silent: false,
		ignoreChildren: false
	};

	_default.searchOptions = {
		ignoreCase: true,
		exactMatch: false,
		revealResults: true
	};

	var Tree = function (element, options) {

		this.$element = $(element);
		this.elementId = element.id;
		this.styleId = this.elementId + '-style';

		this.init(options);

		return {

			// Options (public access)
			options: this.options,

			// Initialize / destroy methods
			init: $.proxy(this.init, this),
			remove: $.proxy(this.remove, this),

			// Get methods
			getNode: $.proxy(this.getNode, this),
			getParent: $.proxy(this.getParent, this),
			getSiblings: $.proxy(this.getSiblings, this),
			getSelected: $.proxy(this.getSelected, this),
			getUnselected: $.proxy(this.getUnselected, this),
			getExpanded: $.proxy(this.getExpanded, this),
			getCollapsed: $.proxy(this.getCollapsed, this),
			getChecked: $.proxy(this.getChecked, this),
			getUnchecked: $.proxy(this.getUnchecked, this),
			getDisabled: $.proxy(this.getDisabled, this),
			getEnabled: $.proxy(this.getEnabled, this),

			// Select methods
			selectNode: $.proxy(this.selectNode, this),
			unselectNode: $.proxy(this.unselectNode, this),
			toggleNodeSelected: $.proxy(this.toggleNodeSelected, this),

			// Expand / collapse methods
			collapseAll: $.proxy(this.collapseAll, this),
			collapseNode: $.proxy(this.collapseNode, this),
			expandAll: $.proxy(this.expandAll, this),
			expandNode: $.proxy(this.expandNode, this),
			toggleNodeExpanded: $.proxy(this.toggleNodeExpanded, this),
			revealNode: $.proxy(this.revealNode, this),

			// Expand / collapse methods
			checkAll: $.proxy(this.checkAll, this),
			checkNode: $.proxy(this.checkNode, this),
			uncheckAll: $.proxy(this.uncheckAll, this),
			uncheckNode: $.proxy(this.uncheckNode, this),
			toggleNodeChecked: $.proxy(this.toggleNodeChecked, this),

			// Disable / enable methods
			disableAll: $.proxy(this.disableAll, this),
			disableNode: $.proxy(this.disableNode, this),
			enableAll: $.proxy(this.enableAll, this),
			enableNode: $.proxy(this.enableNode, this),
			toggleNodeDisabled: $.proxy(this.toggleNodeDisabled, this),

			// Search methods
			search: $.proxy(this.search, this),
			clearSearch: $.proxy(this.clearSearch, this)
		};
	};

	Tree.prototype.init = function (options) {

		this.tree = [];
		this.nodes = [];

		if (options.data) {
			if (typeof options.data === 'string') {
				options.data = $.parseJSON(options.data);
			}
			this.tree = $.extend(true, [], options.data);
			delete options.data;
		}
		this.options = $.extend({}, _default.settings, options);

		this.destroy();
		this.subscribeEvents();
		this.setInitialStates({ nodes: this.tree }, 0);
		this.render();
	};

	Tree.prototype.remove = function () {
		this.destroy();
		$.removeData(this, pluginName);
		$('#' + this.styleId).remove();
	};

	Tree.prototype.destroy = function () {

		if (!this.initialized) return;

		this.$wrapper.remove();
		this.$wrapper = null;

		// Switch off events
		this.unsubscribeEvents();

		// Reset this.initialized flag
		this.initialized = false;
	};

	Tree.prototype.unsubscribeEvents = function () {

		this.$element.off('click');
		this.$element.off('nodeChecked');
		this.$element.off('nodeCollapsed');
		this.$element.off('nodeDisabled');
		this.$element.off('nodeEnabled');
		this.$element.off('nodeExpanded');
		this.$element.off('nodeSelected');
		this.$element.off('nodeUnchecked');
		this.$element.off('nodeUnselected');
		this.$element.off('searchComplete');
		this.$element.off('searchCleared');
	};

	Tree.prototype.subscribeEvents = function () {

		this.unsubscribeEvents();

		this.$element.on('click', $.proxy(this.clickHandler, this));

		if (typeof (this.options.onNodeChecked) === 'function') {
			this.$element.on('nodeChecked', this.options.onNodeChecked);
		}

		if (typeof (this.options.onNodeCollapsed) === 'function') {
			this.$element.on('nodeCollapsed', this.options.onNodeCollapsed);
		}

		if (typeof (this.options.onNodeDisabled) === 'function') {
			this.$element.on('nodeDisabled', this.options.onNodeDisabled);
		}

		if (typeof (this.options.onNodeEnabled) === 'function') {
			this.$element.on('nodeEnabled', this.options.onNodeEnabled);
		}

		if (typeof (this.options.onNodeExpanded) === 'function') {
			this.$element.on('nodeExpanded', this.options.onNodeExpanded);
		}

		if (typeof (this.options.onNodeSelected) === 'function') {
			this.$element.on('nodeSelected', this.options.onNodeSelected);
		}

		if (typeof (this.options.onNodeUnchecked) === 'function') {
			this.$element.on('nodeUnchecked', this.options.onNodeUnchecked);
		}

		if (typeof (this.options.onNodeUnselected) === 'function') {
			this.$element.on('nodeUnselected', this.options.onNodeUnselected);
		}

		if (typeof (this.options.onSearchComplete) === 'function') {
			this.$element.on('searchComplete', this.options.onSearchComplete);
		}

		if (typeof (this.options.onSearchCleared) === 'function') {
			this.$element.on('searchCleared', this.options.onSearchCleared);
		}
	};

	/*
		Recurse the tree structure and ensure all nodes have
		valid initial states.  User defined states will be preserved.
		For performance we also take this opportunity to
		index nodes in a flattened structure
	*/
	Tree.prototype.setInitialStates = function (node, level) {

		if (!node.nodes) return;
		level += 1;

		var parent = node;
		var _this = this;
		$.each(node.nodes, function checkStates(index, node) {

			// nodeId : unique, incremental identifier
			node.nodeId = _this.nodes.length;

			// parentId : transversing up the tree
			node.parentId = parent.nodeId;

			// if not provided set selectable default value
			if (!node.hasOwnProperty('selectable')) {
				node.selectable = true;
			}

			// where provided we should preserve states
			node.state = node.state || {};

			// set checked state; unless set always false
			if (!node.state.hasOwnProperty('checked')) {
				node.state.checked = false;
			}

			// set enabled state; unless set always false
			if (!node.state.hasOwnProperty('disabled')) {
				node.state.disabled = false;
			}

			// set expanded state; if not provided based on levels
			if (!node.state.hasOwnProperty('expanded')) {
				if (!node.state.disabled &&
						(level < _this.options.levels) &&
						(node.nodes && node.nodes.length > 0)) {
					node.state.expanded = true;
				}
				else {
					node.state.expanded = false;
				}
			}

			// set selected state; unless set always false
			if (!node.state.hasOwnProperty('selected')) {
				node.state.selected = false;
			}

			// index nodes in a flattened structure for use later
			_this.nodes.push(node);

			// recurse child nodes and transverse the tree
			if (node.nodes) {
				_this.setInitialStates(node, level);
			}
		});
	};

	Tree.prototype.clickHandler = function (event) {

		if (!this.options.enableLinks) event.preventDefault();

		var target = $(event.target);
		var node = this.findNode(target);
		if (!node || node.state.disabled) return;
		
		var classList = target.attr('class') ? target.attr('class').split(' ') : [];
		if ((classList.indexOf('expand-icon') !== -1)) {

			this.toggleExpandedState(node, _default.options);
			this.render();
		}
		else if ((classList.indexOf('check-icon') !== -1)) {
			
			this.toggleCheckedState(node, _default.options);
			this.render();
		}
		else {
			
			if (node.selectable) {
				this.toggleSelectedState(node, _default.options);
			} else {
				this.toggleExpandedState(node, _default.options);
			}

			this.render();
		}
	};

	// Looks up the DOM for the closest parent list item to retrieve the
	// data attribute nodeid, which is used to lookup the node in the flattened structure.
	Tree.prototype.findNode = function (target) {

		var nodeId = target.closest('li.list-group-item').attr('data-nodeid');
		var node = this.nodes[nodeId];

		if (!node) {
			console.log('Error: node does not exist');
		}
		return node;
	};

	Tree.prototype.toggleExpandedState = function (node, options) {
		if (!node) return;
		this.setExpandedState(node, !node.state.expanded, options);
	};

	Tree.prototype.setExpandedState = function (node, state, options) {

		if (state === node.state.expanded) return;

		if (state && node.nodes) {

			// Expand a node
			node.state.expanded = true;
			if (!options.silent) {
				this.$element.trigger('nodeExpanded', $.extend(true, {}, node));
			}
		}
		else if (!state) {

			// Collapse a node
			node.state.expanded = false;
			if (!options.silent) {
				this.$element.trigger('nodeCollapsed', $.extend(true, {}, node));
			}

			// Collapse child nodes
			if (node.nodes && !options.ignoreChildren) {
				$.each(node.nodes, $.proxy(function (index, node) {
					this.setExpandedState(node, false, options);
				}, this));
			}
		}
	};

	Tree.prototype.toggleSelectedState = function (node, options) {
		if (!node) return;
		this.setSelectedState(node, !node.state.selected, options);
	};

	Tree.prototype.setSelectedState = function (node, state, options) {

		if (state === node.state.selected) return;

		if (state) {

			// If multiSelect false, unselect previously selected
			if (!this.options.multiSelect) {
				$.each(this.findNodes('true', 'g', 'state.selected'), $.proxy(function (index, node) {
					this.setSelectedState(node, false, options);
				}, this));
			}

			// Continue selecting node
			node.state.selected = true;
			if (!options.silent) {
				this.$element.trigger('nodeSelected', $.extend(true, {}, node));
			}
		}
		else {

			// Unselect node
			node.state.selected = false;
			if (!options.silent) {
				this.$element.trigger('nodeUnselected', $.extend(true, {}, node));
			}
		}
	};

	Tree.prototype.toggleCheckedState = function (node, options) {
		if (!node) return;
		this.setCheckedState(node, !node.state.checked, options);
	};

	Tree.prototype.setCheckedState = function (node, state, options) {

		if (state === node.state.checked) return;

		if (state) {

			// Check node
			node.state.checked = true;

			if (!options.silent) {
				this.$element.trigger('nodeChecked', $.extend(true, {}, node));
			}
		}
		else {

			// Uncheck node
			node.state.checked = false;
			if (!options.silent) {
				this.$element.trigger('nodeUnchecked', $.extend(true, {}, node));
			}
		}
	};

	Tree.prototype.setDisabledState = function (node, state, options) {

		if (state === node.state.disabled) return;

		if (state) {

			// Disable node
			node.state.disabled = true;

			// Disable all other states
			this.setExpandedState(node, false, options);
			this.setSelectedState(node, false, options);
			this.setCheckedState(node, false, options);

			if (!options.silent) {
				this.$element.trigger('nodeDisabled', $.extend(true, {}, node));
			}
		}
		else {

			// Enabled node
			node.state.disabled = false;
			if (!options.silent) {
				this.$element.trigger('nodeEnabled', $.extend(true, {}, node));
			}
		}
	};

	Tree.prototype.render = function () {

		if (!this.initialized) {

			// Setup first time only components
			this.$element.addClass(pluginName);
			this.$wrapper = $(this.template.list);

			this.injectStyle();

			this.initialized = true;
		}

		this.$element.empty().append(this.$wrapper.empty());

		// Build tree
		this.buildTree(this.tree, 0);
	};

	// Starting from the root node, and recursing down the
	// structure we build the tree one node at a time
	Tree.prototype.buildTree = function (nodes, level) {

		if (!nodes) return;
		level += 1;

		var _this = this;
		$.each(nodes, function addNodes(id, node) {

			var treeItem = $(_this.template.item)
				.addClass('node-' + _this.elementId)
				.addClass(node.state.checked ? 'node-checked' : '')
				.addClass(node.state.disabled ? 'node-disabled': '')
				.addClass(node.state.selected ? 'node-selected' : '')
				.addClass(node.searchResult ? 'search-result' : '') 
				.attr('data-nodeid', node.nodeId)
				.attr('style', _this.buildStyleOverride(node));

			// Add indent/spacer to mimic tree structure
			for (var i = 0; i < (level - 1); i++) {
				treeItem.append(_this.template.indent);
			}

			// Add expand, collapse or empty spacer icons
			var classList = [];
			if (node.nodes) {
				classList.push('expand-icon');
				if (node.state.expanded) {
					classList.push(_this.options.collapseIcon);
				}
				else {
					classList.push(_this.options.expandIcon);
				}
			}
			else {
				classList.push(_this.options.emptyIcon);
			}

			treeItem
				.append($(_this.template.icon)
					.addClass(classList.join(' '))
				);


			

			// Add check / unchecked icon
			if (_this.options.showCheckbox) {

				var classList = ['check-icon'];
				if (node.state.checked) {
					classList.push(_this.options.checkedIcon); 
				}
				else {
					classList.push(_this.options.uncheckedIcon);
				}

				treeItem
					.append($(_this.template.icon)
						.addClass(classList.join(' '))
					);
			}
			
			// Add node icon
			if (_this.options.showIcon) {
				
				var classList = ['node-icon'];

				classList.push(node.icon || _this.options.nodeIcon);
				if (node.state.selected) {
					classList.pop();
					classList.push(node.selectedIcon || _this.options.selectedIcon || 
									node.icon || _this.options.nodeIcon);
				}

				treeItem
					.append($(_this.template.icon)
						.addClass(classList.join(' '))
					);
			}
			
			// Add text
			if (_this.options.enableLinks) {
				// Add hyperlink
				treeItem
					.append($(_this.template.link)
						.attr('href', node.href)
						.append(node.text+"&nbsp;&nbsp;&nbsp;")
					);
			}
			else {
				// otherwise just text
				treeItem
					.append(node.text);
			}

			

			
			// Add tags as badges
			if (_this.options.showTags && node.tags) {
				$.each(node.tags, function addTag(id, tag) {
					treeItem
						.append($(_this.template.badge)
							.append(tag)
						);
				});
			}

			// Add item to the tree
			_this.$wrapper.append(treeItem);

			// Recursively add child ndoes
			if (node.nodes && node.state.expanded && !node.state.disabled) {
				return _this.buildTree(node.nodes, level);
			}
		});
	};

	// Define any node level style override for
	// 1. selectedNode
	// 2. node|data assigned color overrides
	Tree.prototype.buildStyleOverride = function (node) {

		if (node.state.disabled) return '';

		var color = node.color;
		var backColor = node.backColor;

		if (this.options.highlightSelected && node.state.selected) {
			if (this.options.selectedColor) {
				color = this.options.selectedColor;
			}
			if (this.options.selectedBackColor) {
				backColor = this.options.selectedBackColor;
			}
		}

		if (this.options.highlightSearchResults && node.searchResult && !node.state.disabled) {
			if (this.options.searchResultColor) {
				color = this.options.searchResultColor;
			}
			if (this.options.searchResultBackColor) {
				backColor = this.options.searchResultBackColor;
			}
		}

		return 'color:' + color +
			';background-color:' + backColor + ';';
	};

	// Add inline style into head
	Tree.prototype.injectStyle = function () {

		if (this.options.injectStyle && !document.getElementById(this.styleId)) {
			$('<style type="text/css" id="' + this.styleId + '"> ' + this.buildStyle() + ' </style>').appendTo('head');
		}
	};

	// Construct trees style based on user options
	Tree.prototype.buildStyle = function () {

		var style = '.node-' + this.elementId + '{';

		if (this.options.color) {
			style += 'color:' + this.options.color + ';';
		}

		if (this.options.backColor) {
			style += 'background-color:' + this.options.backColor + ';';
		}

		if (!this.options.showBorder) {
			style += 'border:none;';
		}
		else if (this.options.borderColor) {
			style += 'border:1px solid ' + this.options.borderColor + ';';
		}
		style += '}';

		if (this.options.onhoverColor) {
			style += '.node-' + this.elementId + ':not(.node-disabled):hover{' +
				'background-color:' + this.options.onhoverColor + ';' +
			'}';
		}

		return this.css + style;
	};

	Tree.prototype.template = {
		list: '<ul class="list-group"></ul>',
		item: '<li class="list-group-item"></li>',
		indent: '<span class="indent"></span>',
		icon: '<span style="font-size:13px;" class="icon">&nbsp;&nbsp;&nbsp;</span>',
		link: '<a href="#" style="color:inherit;"></a>',
		badge: '<span class="badge"></span>'
	};

	Tree.prototype.css = '.treeview .list-group-item{cursor:pointer}.treeview span.indent{margin-left:10px;margin-right:10px}.treeview span.icon{width:12px;margin-right:5px}.treeview .node-disabled{color:silver;cursor:not-allowed}'


	/**
		Returns a single node object that matches the given node id.
		@param {Number} nodeId - A node's unique identifier
		@return {Object} node - Matching node
	*/
	Tree.prototype.getNode = function (nodeId) {
		return this.nodes[nodeId];
	};

	/**
		Returns the parent node of a given node, if valid otherwise returns undefined.
		@param {Object|Number} identifier - A valid node or node id
		@returns {Object} node - The parent node
	*/
	Tree.prototype.getParent = function (identifier) {
		var node = this.identifyNode(identifier);
		return this.nodes[node.parentId];
	};

	/**
		Returns an array of sibling nodes for a given node, if valid otherwise returns undefined.
		@param {Object|Number} identifier - A valid node or node id
		@returns {Array} nodes - Sibling nodes
	*/
	Tree.prototype.getSiblings = function (identifier) {
		var node = this.identifyNode(identifier);
		var parent = this.getParent(node);
		var nodes = parent ? parent.nodes : this.tree;
		return nodes.filter(function (obj) {
				return obj.nodeId !== node.nodeId;
			});
	};

	/**
		Returns an array of selected nodes.
		@returns {Array} nodes - Selected nodes
	*/
	Tree.prototype.getSelected = function () {
		return this.findNodes('true', 'g', 'state.selected');
	};

	/**
		Returns an array of unselected nodes.
		@returns {Array} nodes - Unselected nodes
	*/
	Tree.prototype.getUnselected = function () {
		return this.findNodes('false', 'g', 'state.selected');
	};

	/**
		Returns an array of expanded nodes.
		@returns {Array} nodes - Expanded nodes
	*/
	Tree.prototype.getExpanded = function () {
		return this.findNodes('true', 'g', 'state.expanded');
	};

	/**
		Returns an array of collapsed nodes.
		@returns {Array} nodes - Collapsed nodes
	*/
	Tree.prototype.getCollapsed = function () {
		return this.findNodes('false', 'g', 'state.expanded');
	};

	/**
		Returns an array of checked nodes.
		@returns {Array} nodes - Checked nodes
	*/
	Tree.prototype.getChecked = function () {
		return this.findNodes('true', 'g', 'state.checked');
	};

	/**
		Returns an array of unchecked nodes.
		@returns {Array} nodes - Unchecked nodes
	*/
	Tree.prototype.getUnchecked = function () {
		return this.findNodes('false', 'g', 'state.checked');
	};

	/**
		Returns an array of disabled nodes.
		@returns {Array} nodes - Disabled nodes
	*/
	Tree.prototype.getDisabled = function () {
		return this.findNodes('true', 'g', 'state.disabled');
	};

	/**
		Returns an array of enabled nodes.
		@returns {Array} nodes - Enabled nodes
	*/
	Tree.prototype.getEnabled = function () {
		return this.findNodes('false', 'g', 'state.disabled');
	};


	/**
		Set a node state to selected
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.selectNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setSelectedState(node, true, options);
		}, this));

		this.render();
	};

	/**
		Set a node state to unselected
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.unselectNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setSelectedState(node, false, options);
		}, this));

		this.render();
	};

	/**
		Toggles a node selected state; selecting if unselected, unselecting if selected.
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.toggleNodeSelected = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.toggleSelectedState(node, options);
		}, this));

		this.render();
	};


	/**
		Collapse all tree nodes
		@param {optional Object} options
	*/
	Tree.prototype.collapseAll = function (options) {
		var identifiers = this.findNodes('true', 'g', 'state.expanded');
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setExpandedState(node, false, options);
		}, this));

		this.render();
	};

	/**
		Collapse a given tree node
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.collapseNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setExpandedState(node, false, options);
		}, this));

		this.render();
	};

	/**
		Expand all tree nodes
		@param {optional Object} options
	*/
	Tree.prototype.expandAll = function (options) {
		options = $.extend({}, _default.options, options);

		if (options && options.levels) {
			this.expandLevels(this.tree, options.levels, options);
		}
		else {
			var identifiers = this.findNodes('false', 'g', 'state.expanded');
			this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
				this.setExpandedState(node, true, options);
			}, this));
		}

		this.render();
	};

	/**
		Expand a given tree node
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.expandNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setExpandedState(node, true, options);
			if (node.nodes && (options && options.levels)) {
				this.expandLevels(node.nodes, options.levels-1, options);
			}
		}, this));

		this.render();
	};

	Tree.prototype.expandLevels = function (nodes, level, options) {
		options = $.extend({}, _default.options, options);

		$.each(nodes, $.proxy(function (index, node) {
			this.setExpandedState(node, (level > 0) ? true : false, options);
			if (node.nodes) {
				this.expandLevels(node.nodes, level-1, options);
			}
		}, this));
	};

	/**
		Reveals a given tree node, expanding the tree from node to root.
		@param {Object|Number|Array} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.revealNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			var parentNode = this.getParent(node);
			while (parentNode) {
				this.setExpandedState(parentNode, true, options);
				parentNode = this.getParent(parentNode);
			};
		}, this));

		this.render();
	};

	/**
		Toggles a nodes expanded state; collapsing if expanded, expanding if collapsed.
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.toggleNodeExpanded = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.toggleExpandedState(node, options);
		}, this));
		
		this.render();
	};


	/**
		Check all tree nodes
		@param {optional Object} options
	*/
	Tree.prototype.checkAll = function (options) {
		var identifiers = this.findNodes('false', 'g', 'state.checked');
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setCheckedState(node, true, options);
		}, this));

		this.render();
	};

	/**
		Check a given tree node
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.checkNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setCheckedState(node, true, options);
		}, this));

		this.render();
	};

	/**
		Uncheck all tree nodes
		@param {optional Object} options
	*/
	Tree.prototype.uncheckAll = function (options) {
		var identifiers = this.findNodes('true', 'g', 'state.checked');
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setCheckedState(node, false, options);
		}, this));

		this.render();
	};

	/**
		Uncheck a given tree node
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.uncheckNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setCheckedState(node, false, options);
		}, this));

		this.render();
	};

	/**
		Toggles a nodes checked state; checking if unchecked, unchecking if checked.
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.toggleNodeChecked = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.toggleCheckedState(node, options);
		}, this));

		this.render();
	};


	/**
		Disable all tree nodes
		@param {optional Object} options
	*/
	Tree.prototype.disableAll = function (options) {
		var identifiers = this.findNodes('false', 'g', 'state.disabled');
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setDisabledState(node, true, options);
		}, this));

		this.render();
	};

	/**
		Disable a given tree node
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.disableNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setDisabledState(node, true, options);
		}, this));

		this.render();
	};

	/**
		Enable all tree nodes
		@param {optional Object} options
	*/
	Tree.prototype.enableAll = function (options) {
		var identifiers = this.findNodes('true', 'g', 'state.disabled');
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setDisabledState(node, false, options);
		}, this));

		this.render();
	};

	/**
		Enable a given tree node
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.enableNode = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setDisabledState(node, false, options);
		}, this));

		this.render();
	};

	/**
		Toggles a nodes disabled state; disabling is enabled, enabling if disabled.
		@param {Object|Number} identifiers - A valid node, node id or array of node identifiers
		@param {optional Object} options
	*/
	Tree.prototype.toggleNodeDisabled = function (identifiers, options) {
		this.forEachIdentifier(identifiers, options, $.proxy(function (node, options) {
			this.setDisabledState(node, !node.state.disabled, options);
		}, this));

		this.render();
	};


	/**
		Common code for processing multiple identifiers
	*/
	Tree.prototype.forEachIdentifier = function (identifiers, options, callback) {

		options = $.extend({}, _default.options, options);

		if (!(identifiers instanceof Array)) {
			identifiers = [identifiers];
		}

		$.each(identifiers, $.proxy(function (index, identifier) {
			callback(this.identifyNode(identifier), options);
		}, this));	
	};

	/*
		Identifies a node from either a node id or object
	*/
	Tree.prototype.identifyNode = function (identifier) {
		return ((typeof identifier) === 'number') ?
						this.nodes[identifier] :
						identifier;
	};

	/**
		Searches the tree for nodes (text) that match given criteria
		@param {String} pattern - A given string to match against
		@param {optional Object} options - Search criteria options
		@return {Array} nodes - Matching nodes
	*/
	Tree.prototype.search = function (pattern, options) {
		options = $.extend({}, _default.searchOptions, options);

		this.clearSearch({ render: false });

		var results = [];
		if (pattern && pattern.length > 0) {

			if (options.exactMatch) {
				pattern = '^' + pattern + '$';
			}

			var modifier = 'g';
			if (options.ignoreCase) {
				modifier += 'i';
			}

			results = this.findNodes(pattern, modifier);

			// Add searchResult property to all matching nodes
			// This will be used to apply custom styles
			// and when identifying result to be cleared
			$.each(results, function (index, node) {
				node.searchResult = true;
			})
		}

		// If revealResults, then render is triggered from revealNode
		// otherwise we just call render.
		if (options.revealResults) {
			this.revealNode(results);
		}
		else {
			this.render();
		}

		this.$element.trigger('searchComplete', $.extend(true, {}, results));

		return results;
	};

	/**
		Clears previous search results
	*/
	Tree.prototype.clearSearch = function (options) {

		options = $.extend({}, { render: true }, options);

		var results = $.each(this.findNodes('true', 'g', 'searchResult'), function (index, node) {
			node.searchResult = false;
		});

		if (options.render) {
			this.render();	
		}
		
		this.$element.trigger('searchCleared', $.extend(true, {}, results));
	};

	/**
		Find nodes that match a given criteria
		@param {String} pattern - A given string to match against
		@param {optional String} modifier - Valid RegEx modifiers
		@param {optional String} attribute - Attribute to compare pattern against
		@return {Array} nodes - Nodes that match your criteria
	*/
	Tree.prototype.findNodes = function (pattern, modifier, attribute) {

		modifier = modifier || 'g';
		attribute = attribute || 'text';

		var _this = this;
		return $.grep(this.nodes, function (node) {
			var val = _this.getNodeValue(node, attribute);
			if (typeof val === 'string') {
				return val.match(new RegExp(pattern, modifier));
			}
		});
	};

	/**
		Recursive find for retrieving nested attributes values
		All values are return as strings, unless invalid
		@param {Object} obj - Typically a node, could be any object
		@param {String} attr - Identifies an object property using dot notation
		@return {String} value - Matching attributes string representation
	*/
	Tree.prototype.getNodeValue = function (obj, attr) {
		var index = attr.indexOf('.');
		if (index > 0) {
			var _obj = obj[attr.substring(0, index)];
			var _attr = attr.substring(index + 1, attr.length);
			return this.getNodeValue(_obj, _attr);
		}
		else {
			if (obj.hasOwnProperty(attr)) {
				return obj[attr].toString();
			}
			else {
				return undefined;
			}
		}
	};

	var logError = function (message) {
		if (window.console) {
			window.console.error(message);
		}
	};

	// Prevent against multiple instantiations,
	// handle updates and method calls
	$.fn[pluginName] = function (options, args) {

		var result;

		this.each(function () {
			var _this = $.data(this, pluginName);
			if (typeof options === 'string') {
				if (!_this) {
					logError('Not initialized, can not call method : ' + options);
				}
				else if (!$.isFunction(_this[options]) || options.charAt(0) === '_') {
					logError('No such method : ' + options);
				}
				else {
					if (!(args instanceof Array)) {
						args = [ args ];
					}
					result = _this[options].apply(_this, args);
				}
			}
			else if (typeof options === 'boolean') {
				result = _this;
			}
			else {
				$.data(this, pluginName, new Tree(this, $.extend(true, {}, options)));
			}
		});

		return result || this;
	};

})(jQuery, window, document);

/*!
 * bootstrap-tags 1.1.5
 * https://github.com/maxwells/bootstrap-tags
 * Copyright 2013 Max Lahey; Licensed MIT
 */
!function(a){!function(){window.Tags||(window.Tags={}),jQuery(function(){return a.tags=function(b,c){var d,e,f,g,h,i,j,k=this;null==c&&(c={});for(d in c)g=c[d],this[d]=g;if(this.bootstrapVersion||(this.bootstrapVersion="3"),this.readOnly||(this.readOnly=!1),this.suggestOnClick||(this.suggestOnClick=!1),this.suggestions||(this.suggestions=[]),this.restrictTo=null!=c.restrictTo?c.restrictTo.concat(this.suggestions):!1,this.exclude||(this.exclude=!1),this.displayPopovers=null!=c.popovers?!0:null!=c.popoverData,this.popoverTrigger||(this.popoverTrigger="hover"),this.tagClass||(this.tagClass="btn-info"),this.tagSize||(this.tagSize="md"),this.promptText||(this.promptText="Enter tags..."),this.caseInsensitive||(this.caseInsensitive=!1),this.readOnlyEmptyMessage||(this.readOnlyEmptyMessage="No tags to display..."),this.maxNumTags||(this.maxNumTags=-1),this.beforeAddingTag||(this.beforeAddingTag=function(){}),this.afterAddingTag||(this.afterAddingTag=function(){}),this.beforeDeletingTag||(this.beforeDeletingTag=function(){}),this.afterDeletingTag||(this.afterDeletingTag=function(){}),this.definePopover||(this.definePopover=function(a){return'associated content for "'+a+'"'}),this.excludes||(this.excludes=function(){return!1}),this.tagRemoved||(this.tagRemoved=function(){}),this.pressedReturn||(this.pressedReturn=function(){}),this.pressedDelete||(this.pressedDelete=function(){}),this.pressedDown||(this.pressedDown=function(){}),this.pressedUp||(this.pressedUp=function(){}),this.$element=a(b),null!=c.tagData?this.tagsArray=c.tagData:(f=a(".tag-data",this.$element).html(),this.tagsArray=null!=f?f.split(","):[]),c.popoverData)this.popoverArray=c.popoverData;else for(this.popoverArray=[],j=this.tagsArray,h=0,i=j.length;i>h;h++)e=j[h],this.popoverArray.push(null);return this.getTags=function(){return k.tagsArray},this.getTagsContent=function(){return k.popoverArray},this.getTagsWithContent=function(){var a,b,c,d;for(a=[],b=c=0,d=k.tagsArray.length-1;d>=0?d>=c:c>=d;b=d>=0?++c:--c)a.push({tag:k.tagsArray[b],content:k.popoverArray[b]});return a},this.getTag=function(a){var b;return b=k.tagsArray.indexOf(a),b>-1?k.tagsArray[b]:null},this.getTagWithContent=function(a){var b;return b=k.tagsArray.indexOf(a),{tag:k.tagsArray[b],content:k.popoverArray[b]}},this.hasTag=function(a){return k.tagsArray.indexOf(a)>-1},this.removeTagClicked=function(b){return"A"===b.currentTarget.tagName&&(k.removeTag(a("span",b.currentTarget.parentElement).html()),a(b.currentTarget.parentNode).remove()),k},this.removeLastTag=function(){return k.tagsArray.length>0&&(k.removeTag(k.tagsArray[k.tagsArray.length-1]),k.canAddByMaxNum()&&k.enableInput()),k},this.removeTag=function(a){if(k.tagsArray.indexOf(a)>-1){if(k.beforeDeletingTag(a)===!1)return;k.popoverArray.splice(k.tagsArray.indexOf(a),1),k.tagsArray.splice(k.tagsArray.indexOf(a),1),k.renderTags(),k.afterDeletingTag(a),k.canAddByMaxNum()&&k.enableInput()}return k},this.canAddByRestriction=function(a){return this.restrictTo===!1||-1!==this.restrictTo.indexOf(a)},this.canAddByExclusion=function(a){return(this.exclude===!1||-1===this.exclude.indexOf(a))&&!this.excludes(a)},this.canAddByMaxNum=function(){return-1===this.maxNumTags||this.tagsArray.length<this.maxNumTags},this.addTag=function(a){var b;if(k.canAddByRestriction(a)&&!k.hasTag(a)&&a.length>0&&k.canAddByExclusion(a)&&k.canAddByMaxNum()){if(k.beforeAddingTag(a)===!1)return;b=k.definePopover(a),k.popoverArray.push(b||null),k.tagsArray.push(a),k.afterAddingTag(a),k.renderTags(),k.canAddByMaxNum()||k.disableInput()}return k},this.addTagWithContent=function(a,b){if(k.canAddByRestriction(a)&&!k.hasTag(a)&&a.length>0){if(k.beforeAddingTag(a)===!1)return;k.tagsArray.push(a),k.popoverArray.push(b),k.afterAddingTag(a),k.renderTags()}return k},this.renameTag=function(a,b){return k.tagsArray[k.tagsArray.indexOf(a)]=b,k.renderTags(),k},this.setPopover=function(a,b){return k.popoverArray[k.tagsArray.indexOf(a)]=b,k.renderTags(),k},this.clickHandler=function(a){return k.makeSuggestions(a,!0)},this.keyDownHandler=function(a){var b,c;switch(b=null!=a.keyCode?a.keyCode:a.which){case 13:return a.preventDefault(),k.pressedReturn(a),e=a.target.value,-1!==k.suggestedIndex&&(e=k.suggestionList[k.suggestedIndex]),k.addTag(e),a.target.value="",k.renderTags(),k.hideSuggestions();case 46:case 8:if(k.pressedDelete(a),""===a.target.value&&k.removeLastTag(),1===a.target.value.length)return k.hideSuggestions();break;case 40:if(k.pressedDown(a),""!==k.input.val()||-1!==k.suggestedIndex&&null!=k.suggestedIndex||k.makeSuggestions(a,!0),c=k.suggestionList.length,k.suggestedIndex=k.suggestedIndex<c-1?k.suggestedIndex+1:c-1,k.selectSuggested(k.suggestedIndex),k.suggestedIndex>=0)return k.scrollSuggested(k.suggestedIndex);break;case 38:if(k.pressedUp(a),k.suggestedIndex=k.suggestedIndex>0?k.suggestedIndex-1:0,k.selectSuggested(k.suggestedIndex),k.suggestedIndex>=0)return k.scrollSuggested(k.suggestedIndex);break;case 9:case 27:return k.hideSuggestions(),k.suggestedIndex=-1}},this.keyUpHandler=function(a){var b;return b=null!=a.keyCode?a.keyCode:a.which,40!==b&&38!==b&&27!==b?k.makeSuggestions(a,!1):void 0},this.getSuggestions=function(b,c){var d=this;return this.suggestionList=[],this.caseInsensitive&&(b=b.toLowerCase()),a.each(this.suggestions,function(a,e){var f;return f=d.caseInsensitive?e.substring(0,b.length).toLowerCase():e.substring(0,b.length),d.tagsArray.indexOf(e)<0&&f===b&&(b.length>0||c)?d.suggestionList.push(e):void 0}),this.suggestionList},this.makeSuggestions=function(b,c,d){return null==d&&(d=null!=b.target.value?b.target.value:b.target.textContent),k.suggestedIndex=-1,k.$suggestionList.html(""),a.each(k.getSuggestions(d,c),function(a,b){return k.$suggestionList.append(k.template("tags_suggestion",{suggestion:b}))}),k.$(".tags-suggestion").mouseover(k.selectSuggestedMouseOver),k.$(".tags-suggestion").click(k.suggestedClicked),k.suggestionList.length>0?k.showSuggestions():k.hideSuggestions()},this.suggestedClicked=function(a){return e=a.target.textContent,-1!==k.suggestedIndex&&(e=k.suggestionList[k.suggestedIndex]),k.addTag(e),k.input.val(""),k.makeSuggestions(a,!1,""),k.input.focus(),k.hideSuggestions()},this.hideSuggestions=function(){return k.$(".tags-suggestion-list").css({display:"none"})},this.showSuggestions=function(){return k.$(".tags-suggestion-list").css({display:"block"})},this.selectSuggestedMouseOver=function(b){return a(".tags-suggestion").removeClass("tags-suggestion-highlighted"),a(b.target).addClass("tags-suggestion-highlighted"),a(b.target).mouseout(k.selectSuggestedMousedOut),k.suggestedIndex=k.$(".tags-suggestion").index(a(b.target))},this.selectSuggestedMousedOut=function(b){return a(b.target).removeClass("tags-suggestion-highlighted")},this.selectSuggested=function(b){var c;return a(".tags-suggestion").removeClass("tags-suggestion-highlighted"),c=k.$(".tags-suggestion").eq(b),c.addClass("tags-suggestion-highlighted")},this.scrollSuggested=function(a){var b,c,d,e;return c=k.$(".tags-suggestion").eq(a),d=k.$(".tags-suggestion").eq(0),b=c.position(),e=d.position(),null!=b?k.$(".tags-suggestion-list").scrollTop(b.top-e.top):void 0},this.adjustInputPosition=function(){var b,c,d,e,f,g;return f=k.$(".tag").last(),g=f.position(),c=null!=g?g.left+f.outerWidth(!0):0,d=null!=g?g.top:0,e=k.$element.width()-c,a(".tags-input",k.$element).css({paddingLeft:Math.max(c,0),paddingTop:Math.max(d,0),width:e}),b=null!=g?g.top+f.outerHeight(!0):22,k.$element.css({paddingBottom:b-k.$element.height()})},this.renderTags=function(){var b;return b=k.$(".tags"),b.html(""),k.input.attr("placeholder",0===k.tagsArray.length?k.promptText:""),a.each(k.tagsArray,function(c,d){return d=a(k.formatTag(c,d)),a("a",d).click(k.removeTagClicked),a("a",d).mouseover(k.toggleCloseColor),a("a",d).mouseout(k.toggleCloseColor),k.displayPopovers&&k.initializePopoverFor(d,k.tagsArray[c],k.popoverArray[c]),b.append(d)}),k.adjustInputPosition()},this.renderReadOnly=function(){var b;return b=k.$(".tags"),b.html(0===k.tagsArray.length?k.readOnlyEmptyMessage:""),a.each(k.tagsArray,function(c,d){return d=a(k.formatTag(c,d,!0)),k.displayPopovers&&k.initializePopoverFor(d,k.tagsArray[c],k.popoverArray[c]),b.append(d)})},this.disableInput=function(){return this.$("input").prop("disabled",!0)},this.enableInput=function(){return this.$("input").prop("disabled",!1)},this.initializePopoverFor=function(b,d,e){return c={title:d,content:e,placement:"bottom"},"hoverShowClickHide"===k.popoverTrigger?(a(b).mouseover(function(){return a(b).popover("show"),a(".tag").not(b).popover("hide")}),a(document).click(function(){return a(b).popover("hide")})):c.trigger=k.popoverTrigger,a(b).popover(c)},this.toggleCloseColor=function(b){var c,d;return d=a(b.currentTarget),c=d.css("opacity"),c=.8>c?1:.6,d.css({opacity:c})},this.formatTag=function(a,b,c){var d;return null==c&&(c=!1),d=b.replace("<","&lt;").replace(">","&gt;"),k.template("tag",{tag:d,tagClass:k.tagClass,isPopover:k.displayPopovers,isReadOnly:c,tagSize:k.tagSize})},this.addDocumentListeners=function(){return a(document).mouseup(function(a){var b;return b=k.$(".tags-suggestion-list"),0===b.has(a.target).length?k.hideSuggestions():void 0})},this.template=function(a,b){return Tags.Templates.Template(this.getBootstrapVersion(),a,b)},this.$=function(b){return a(b,this.$element)},this.getBootstrapVersion=function(){return Tags.bootstrapVersion||this.bootstrapVersion},this.initializeDom=function(){return this.$element.append(this.template("tags_container"))},this.init=function(){return this.$element.addClass("bootstrap-tags").addClass("bootstrap-"+this.getBootstrapVersion()),this.initializeDom(),this.readOnly?(this.renderReadOnly(),this.removeTag=function(){},this.removeTagClicked=function(){},this.removeLastTag=function(){},this.addTag=function(){},this.addTagWithContent=function(){},this.renameTag=function(){},this.setPopover=function(){}):(this.input=a(this.template("input",{tagSize:this.tagSize})),this.suggestOnClick&&this.input.click(this.clickHandler),this.input.keydown(this.keyDownHandler),this.input.keyup(this.keyUpHandler),this.$element.append(this.input),this.$suggestionList=a(this.template("suggestion_list")),this.$element.append(this.$suggestionList),this.renderTags(),this.canAddByMaxNum()||this.disableInput(),this.addDocumentListeners())},this.init(),this},a.fn.tags=function(b){var c,d;return d={},c="number"==typeof b?b:-1,this.each(function(e,f){var g;return g=a(f),null==g.data("tags")&&g.data("tags",new a.tags(this,b)),c===e||0===e?d=g.data("tags"):void 0}),d}})}.call(this),function(){window.Tags||(window.Tags={}),Tags.Helpers||(Tags.Helpers={}),Tags.Helpers.addPadding=function(a,b,c){return null==b&&(b=1),null==c&&(c=!0),c?0===b?a:Tags.Helpers.addPadding("&nbsp"+a+"&nbsp",b-1):a}}.call(this),function(){var a;window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),(a=Tags.Templates)["2"]||(a["2"]={}),Tags.Templates["2"].input=function(a){var b;return null==a&&(a={}),b=function(){switch(a.tagSize){case"sm":return"small";case"md":return"medium";case"lg":return"large"}}(),"<input type='text' class='tags-input input-"+b+"' />"}}.call(this),function(){var a;window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),(a=Tags.Templates)["2"]||(a["2"]={}),Tags.Templates["2"].tag=function(a){return null==a&&(a={}),"<div class='tag label "+a.tagClass+" "+a.tagSize+"' "+(a.isPopover?"rel='popover'":"")+">    <span>"+Tags.Helpers.addPadding(a.tag,2,a.isReadOnly)+"</span>    "+(a.isReadOnly?"":"<a><i class='remove icon-remove-sign icon-white' /></a>")+"  </div>"}}.call(this),function(){var a;window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),(a=Tags.Templates)["3"]||(a["3"]={}),Tags.Templates["3"].input=function(a){return null==a&&(a={}),"<input type='text' class='form-control tags-input input-"+a.tagSize+"' />"}}.call(this),function(){var a;window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),(a=Tags.Templates)["3"]||(a["3"]={}),Tags.Templates["3"].tag=function(a){return null==a&&(a={}),"<div class='tag label "+a.tagClass+" "+a.tagSize+"' "+(a.isPopover?"rel='popover'":"")+">    <span>"+Tags.Helpers.addPadding(a.tag,2,a.isReadOnly)+"</span>    "+(a.isReadOnly?"":"<a><i class='remove glyphicon glyphicon-remove-sign glyphicon-white' /></a>")+"  </div>"}}.call(this),function(){var a;window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),(a=Tags.Templates).shared||(a.shared={}),Tags.Templates.shared.suggestion_list=function(a){return null==a&&(a={}),'<ul class="tags-suggestion-list dropdown-menu"></ul>'}}.call(this),function(){var a;window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),(a=Tags.Templates).shared||(a.shared={}),Tags.Templates.shared.tags_container=function(a){return null==a&&(a={}),'<div class="tags"></div>'}}.call(this),function(){var a;window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),(a=Tags.Templates).shared||(a.shared={}),Tags.Templates.shared.tags_suggestion=function(a){return null==a&&(a={}),"<li class='tags-suggestion'>"+a.suggestion+"</li>"}}.call(this),function(){window.Tags||(window.Tags={}),Tags.Templates||(Tags.Templates={}),Tags.Templates.Template=function(a,b,c){return null!=Tags.Templates[a]&&null!=Tags.Templates[a][b]?Tags.Templates[a][b](c):Tags.Templates.shared[b](c)}}.call(this)}(window.jQuery);