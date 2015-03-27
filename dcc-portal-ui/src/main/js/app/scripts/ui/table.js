/*
 * Copyright 2013(c) The Ontario Institute for Cancer Research. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the GNU Public
 * License v3.0. You should have received a copy of the GNU General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

'use strict';

angular.module('icgc.ui.table', [
  'icgc.ui.table.size',
  'icgc.ui.table.counts',
  'icgc.ui.table.sortable',
  'icgc.ui.table.pagination'
]);

/* ************************************
 *   Table Size
 * ********************************* */
angular.module('icgc.ui.table.size', []);

angular.module('icgc.ui.table.size').controller('tableSizeController', function ($scope, LocationService) {
  var sog = LocationService.getJsonParam($scope.type);

  $scope.sizes = [10, 25, 50];

  $scope.selectedSize = sog.size ? sog.size : $scope.sizes[0];

  $scope.changeSize = function () {
    var so = LocationService.getJsonParam($scope.type);

    so.size = $scope.selectedSize;
    so.from = 1;

    LocationService.setJsonParam($scope.type, so);
  };
});

angular.module('icgc.ui.table.size').directive('tableSize', function () {
  return {
    restrict: 'A',
    scope: {
      type: '@'
    },
    replace: true,
    template: '<span>Showing <select data-ng-options="size for size in sizes"' +
              ' data-ng-model="selectedSize" data-ng-change="changeSize(size)"></select> rows</span>',
    controller: 'tableSizeController'
  };
});

/* ************************************
 *   Table Counts
 * ********************************* */
angular.module('icgc.ui.table.counts', []);

angular.module('icgc.ui.table.counts').directive('tableCounts', function () {
  return {
    restrict: 'A',
    scope: {
      page: '=',
      label: '@'
    },
    replace: true,
    template: '<span>' +
              'Showing <strong>{{page.from | number}}</strong> - ' +
              '<strong data-ng-if="page.count==page.size">' +
              '{{page.from + page.size - 1 | number}}</strong> ' +
              '<strong data-ng-if="page.count < page.size">{{page.total | number}}</strong> ' +
              'of <strong>{{page.total | number}}</strong> {{label}}' +
              '</span>'
  };
});


angular.module('icgc.ui.table.pagination', [])

  .constant('paginationConfig', {
    boundaryLinks: true,
    directionLinks: true,
    firstText: '<<<',
    previousText: '<',
    nextText: '>',
    lastText: '>>>',
    maxSize: 5
  })

  .directive('paginationControls', function (paginationConfig, LocationService, $filter) {
    return {
      restrict: 'E',
      scope: {
        type: '@',
        data: '=',
        //maxSize: '=',
        onSelectPage: '&'
      },
      templateUrl: 'template/pagination.html',
      replace: true,
      link: function (scope, element, attrs) {
        var boundaryLinks, directionLinks, firstText, previousText, nextText, lastText;

        // Setup configuration parameters
        boundaryLinks = angular.isDefined(attrs.boundaryLinks) ?
          scope.$eval(attrs.boundaryLinks) : paginationConfig.boundaryLinks;
        directionLinks = angular.isDefined(attrs.directionLinks) ?
          scope.$eval(attrs.directionLinks) : paginationConfig.directionLinks;
        firstText = angular.isDefined(attrs.firstText) ? attrs.firstText : paginationConfig.firstText;
        previousText = angular.isDefined(attrs.previousText) ? attrs.previousText : paginationConfig.previousText;
        nextText = angular.isDefined(attrs.nextText) ? attrs.nextText : paginationConfig.nextText;
        lastText = angular.isDefined(attrs.lastText) ? attrs.lastText : paginationConfig.lastText;

        scope.maxSize = angular.isDefined(attrs.maxSize) ? attrs.maxSize : paginationConfig.maxSize;

        // Create page object used in template
        function makePage(number, text, isActive, isDisabled) {
          if (angular.isNumber(text)) {
            text = $filter('number')(text);
          }

          return {
            number: number,
            text: text,
            active: isActive,
            disabled: isDisabled
          };
        }

        scope.$watch('data.pagination', function () {
          var max, maxSize, startPage, number, page, previousPage, nextPage, firstPage, lastPage;

          if (!scope.data) {
            return;
          }

          scope.pages = [];

          //set the default maxSize to pages
          maxSize = (scope.maxSize && scope.maxSize < scope.data.pagination.pages) ?
            scope.maxSize : scope.data.pagination.pages;
          startPage = scope.data.pagination.page - Math.floor(maxSize / 2);

          //adjust the startPage within boundary
          if (startPage < 1) {
            startPage = 1;
          }
          if ((startPage + maxSize - 1) > scope.data.pagination.pages) {
            startPage = startPage - ((startPage + maxSize - 1) - scope.data.pagination.pages);
          }

          // Add page number links
          for (number = startPage, max = startPage + maxSize; number < max; number++) {
            page = makePage(number, number, scope.isActive(number), false);
            scope.pages.push(page);
          }

          // Add previous & next links
          if (directionLinks) {
            previousPage = makePage(scope.data.pagination.page - 1, previousText, false, scope.noPrevious());
            scope.pages.unshift(previousPage);

            nextPage = makePage(scope.data.pagination.page + 1, nextText, false, scope.noNext());
            scope.pages.push(nextPage);
          }

          // Add first & last links
          if (boundaryLinks) {
            firstPage = makePage(1, firstText, false, scope.noPrevious());
            scope.pages.unshift(firstPage);

            lastPage = makePage(scope.data.pagination.pages, lastText, false, scope.noNext());
            scope.pages.push(lastPage);
          }

          if (scope.data.pagination.page > scope.data.pagination.pages) {
            scope.selectPage(scope.data.pagination.pages);
          }
        });
        scope.noPrevious = function () {
          return scope.data.pagination.page === 1;
        };
        scope.noNext = function () {
          return scope.data.pagination.page === scope.data.pagination.pages;
        };
        scope.isActive = function (page) {
          return scope.data.pagination.page === page;
        };

        scope.selectPage = function (page) {
          if (!scope.isActive(page) && page > 0 && page <= scope.data.pagination.pages) {
            scope.data.pagination.page = page;
            scope.updateParams(scope.type, page);
            scope.onSelectPage();
          }
        };

        scope.updateParams = function (type, page) {
          var sType, from = (scope.data.pagination.size * (page - 1) + 1);

          if (type) {
            sType = LocationService.getJsonParam(type);
            if (sType) {
              sType.from = from;
              LocationService.setJsonParam(type, sType);
            } else {
              LocationService.setJsonParam(type, '{"from":"' + from + '"}');
            }
          } else {
            LocationService.setJsonParam('from', from);
          }
        };
      }
    };
  });



angular.module('icgc.ui.table.sortable', []).directive('sortable', function ($location, LocationService) {
  return {
    restrict: 'A',
    scope: {
      active: '@',
      reversed: '@',
      field: '@',
      type: '@',
      callback: '&'
    },
    transclude: true,
    templateUrl: 'template/sortable.html',
    link: function (scope) {
      var defaultActive, defaultReversed;

      defaultActive = scope.active;
      defaultReversed = scope.reversed;

      scope.$watch(function () {
        return LocationService.getJsonParam(scope.type);
      }, function (so) {
        scope.active = defaultActive;
        scope.reversed = defaultReversed;
        if (so.sort) {
          scope.active = so.sort === scope.field;
          scope.reversed = so.order === 'desc';
        }

        if (angular.isDefined(scope.callback) && angular.isFunction(scope.callback)) {
          scope.callback();
        }
      }, true);

      scope.onClick = function () {
        var so = LocationService.getJsonParam(scope.type);

        if (so.hasOwnProperty('sort') && so.sort === scope.field) {
          scope.reversed = !scope.reversed;
          so.order = scope.reversed ? 'desc' : 'asc';
        } else {
          if (scope.active) {
            scope.reversed = !scope.reversed;
          } else {
            scope.reversed = true;
          }
          so.sort = scope.field;
          so.order = scope.reversed ? 'desc' : 'asc';
        }

        // reset to first page
        if (so.from) {
          so.from = 1;
        }

        LocationService.setJsonParam(scope.type, so);
      };
    }
  };
});




/* ************************************
 *   Table Sortable
 * ********************************* */
/*
angular.module('icgc.ui.table.sortable', []);

angular.module('icgc.ui.table.sortable').controller('tableSortableController', function () {
  console.log('tableSortableController');
});

angular.module('icgc.ui.table.sortable').directive('tableSortable', function () {
  return {
    restrict: 'A',
    link: function (scope, elem, attrs) {
      console.log('tableSortable', attrs);
    }
  };
});
*/
