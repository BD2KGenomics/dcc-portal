/*
 * Copyright 2015(c) The Ontario Institute for Cancer Research. All rights reserved.
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


(function () {
  'use strict';

  angular.module('icgc.sets', [
    'icgc.sets.directives',
    'icgc.sets.services'
  ]);
})();


(function () {
  'use strict';

  var module = angular.module('icgc.sets.directives', []);

  module.directive('setUpload', function(LocationService, SetService, Settings) {
    return {
      restruct: 'E',
      scope: {
        setModal: '=',
        setType: '=',
        setUnion: '=',
        setLimit: '='
      },
      templateUrl: '/scripts/sets/views/sets.upload.html',
      link: function($scope) {

        $scope.setDescription = null;
        $scope.setSize = 0;
        $scope.isValid = true;

        // Validate size, name
        $scope.validateInput = function() {
          if (_.isEmpty($scope.setName) === true) {
            $scope.isValid = false;
            return;
          }

          if ($scope.setLimit) {
            if (isNaN($scope.setSize) === true) {
              $scope.isValid = false;
              return;
            }

            if ($scope.setSize <= 0 || $scope.setSize > $scope.setSizeLimit) {
              $scope.isValid = false;
              return;
            }
          }
          $scope.isValid = true;
        };

        $scope.submitNewSet = function() {
          var params = {}, sortParam;

          params.type = $scope.setType;
          params.name = $scope.setName;
          params.description = $scope.setDescription;
          params.size = $scope.setSize;

          if (angular.isDefined($scope.setLimit)) {
            params.filters = LocationService.filters();
            sortParam = LocationService.getJsonParam($scope.setType + 's');

            if (angular.isDefined(sortParam)) {
              params.sortBy = sortParam.sort;
              if (sortParam.order === 'asc') {
                params.sortOrder = 'ASCENDING';
              } else {
                params.sortOrder = 'DESCENDING';
              }
            }
          }

          if (angular.isDefined($scope.setUnion)) {
            params.union = $scope.setUnion;
          }

          if (angular.isDefined($scope.setLimit)) {
            SetService.addSet($scope.setType, params);
          } else {
            SetService.addDerivedSet($scope.setType, params);
          }

          // Reset
          $scope.setDescription = null;
          $scope.setType = null;
        };

        $scope.cancel = function() {
          $scope.setDescription = null;
          $scope.setType = null;
          $scope.setModal = false;
        };

        $scope.$watch('setModal', function(n) {
          if (n) {
            Settings.get().then(function(settings) {
              $scope.setSize = Math.min($scope.setLimit || 0, settings.maxNumberOfHits);
              $scope.setSizeLimit = $scope.setSize;
              $scope.setName = 'My ' + $scope.setType + ' set';
              $scope.uiFilters = LocationService.filters();
            });
          }
        });
      }
    };
  });


  module.directive('setOperation', function($location, $timeout, $filter, Page, LocationService,
    Settings, SetService, SetOperationService, Extensions) {

    return {
      restrict: 'E',
      scope: {
        item: '='
      },
      templateUrl: '/scripts/sets/views/sets.result.html',
      link: function($scope, $element) {
        var vennDiagram;

        $scope.selectedTotalCount = 0;
        $scope.current = [];
        $scope.selected = [];

        $scope.dialog = {
          setModal: false
        };

        function toggleSelection(intersection, count) {
          var existIdex = _.findIndex($scope.selected, function(subset) {
            return SetOperationService.isEqual(intersection, subset);
          });

          if (existIdex === -1) {
            $scope.selected.push(intersection);
            $scope.selectedTotalCount += count;
          } else {
            _.remove($scope.selected, function(subset) {
              return SetOperationService.isEqual(intersection, subset);
            });
            if (SetOperationService.isEqual(intersection, $scope.current) === true) {
              $scope.current = [];
            }
            $scope.selectedTotalCount -= count;
          }
          vennDiagram.toggle(intersection);
        }

        function wait(id, numTries, callback) {
          console.log('trying .... ', numTries);
          if (numTries <= 0) {
            Page.stopWork();
            return;
          }

          SetService.getMetaData([id]).then(function(data) {
            if (data[0].state === 'FINISHED') {
              Page.stopWork();
              callback();
            } else {
              $timeout( function() {
                wait(id, --numTries, callback);
              }, 800);
            }
          });
        }


        // Compute the union of single item, of currently selected
        function computeUnion(item) {
          var union = [];
          if (angular.isDefined(item) ) {
            union.push({
              intersection: item.intersection,
              exclusions: item.exclusions
            });
          } else {
            $scope.selected.forEach(function(selectedIntersection) {
              for (var i2=0; i2 < $scope.data.length; i2++) {
                if (SetOperationService.isEqual($scope.data[i2].intersection, selectedIntersection)) {
                  union.push( $scope.data[i2] );
                  break;
                }
              }
            });
          }
          return union;
        }


        // Export the subset(s), materialize the set along the way
        $scope.export = function(item) {
          var params, type, name;
          type = $scope.item.type.toLowerCase();
          name = 'Input ' + type + ' set';

          params = {
            union: computeUnion(item),
            type: $scope.item.type.toLowerCase(),
            name: name
          };
          Page.startWork();
          SetService.materialize(type, params).then(function(data) {
            function exportSet() {
              SetService.exportSet(data.id);
            }
            wait(data.id, 10, exportSet);
          });
        };


        // Redirect to advanced search to show the subset(s), materialize the set along the way
        $scope.redirect = function(item) {
          var params, type, name, path = '/search';

          type = $scope.item.type.toLowerCase();
          name = 'Input ' + type + ' set';

          // Determine which tab we should land on
          if (['gene', 'mutation'].indexOf(type) >= 0) {
            path += '/' + type.charAt(0);
          }

          params = {
            union: computeUnion(item),
            type: $scope.item.type.toLowerCase(),
            name: name,
            isTransient: true
          };

          Page.startWork();
          SetService.materialize(type, params).then(function(data) {
            function redirect2Advanced() {
              var filters = {};
              filters[type] = {};
              filters[type][Extensions.ENTITY] = { is: [data.id] };

              $location.path(path).search({filters: angular.toJson(filters)});
            }
            wait(data.id, 10, redirect2Advanced);
          });
        };


        // $scope.createAdvLink = SetService.createAdvLink;

        $scope.calculateUnion = function(item) {
          $scope.dialog.setUnion = computeUnion(item);
          $scope.dialog.setType = $scope.item.type.toLowerCase();
        };

        $scope.selectAll = function() {
          $scope.selected = [];
          $scope.selectedTotalCount = 0;
          $scope.data.forEach(function(set) {
            $scope.selected.push(set.intersection);
            vennDiagram.toggle(set.intersection, true);
            $scope.selectedTotalCount += set.count;
          });
        };

        $scope.selectNone = function() {
          $scope.data.forEach(function(set) {
            vennDiagram.toggle(set.intersection, false);
          });
          $scope.selected = [];
          $scope.selectedTotalCount = 0;
        };


        $scope.toggleSelection = toggleSelection;


        $scope.isSelected = function(ids) {
          var existIdex = _.findIndex($scope.selected, function(subset) {
            return SetOperationService.isEqual(ids, subset);
          });
          return existIdex >= 0;
        };

        $scope.displaySetOperation = SetOperationService.displaySetOperation;
        $scope.getSetShortHand = SetOperationService.getSetShortHand;

        $scope.tableMouseEnter = function(ids) {
          vennDiagram.toggleHighlight(ids, true);
          $scope.current = ids;
        };

        $scope.tableMouseOut = function(ids) {
          vennDiagram.toggleHighlight(ids, false);
          $scope.current = [];
        };

        function initVennDiagram() {
          var config = {
            // Because SVG urls are based on <base> tag, we need absolute path
            urlPath: $location.path(),

            mouseoverFunc: function(d) {
              $scope.$apply(function() {
                $scope.current = d.data;
              });
            },

            mouseoutFunc: function() {
              $scope.$apply(function() {
                $scope.current = [];
              });
            },

            clickFunc: function(d) {
              $scope.$apply(function() {
                toggleSelection(d.data, d.count);
              });
            }
          };

          $scope.setType = $scope.item.type.toLowerCase();


          // Normalize and sort for tabluar display
          $scope.item.result.forEach(function(subset) {
            subset.intersection.sort();
            subset.exclusions.sort();
          });
          $scope.data = _.sortBy($scope.item.result, function(subset) {
            var secondary = subset.exclusions.length > 0 ? subset.exclusions[0] : '';
            return subset.intersection.length + '' + secondary;
          }).reverse();

          $scope.vennData = SetOperationService.transform($scope.data);

          $scope.setList = [];
          $scope.data.forEach(function(set) {
            set.intersection.forEach(function(id) {
              if (_.contains($scope.setList, id) === false) {
                $scope.setList.push(id);
              }
            });
          });

          config.valueLabelFunc = function(val) {
            return $filter('number')(val);
          };

          config.setLabelFunc = function(id) {
            return SetOperationService.getSetShortHand(id, $scope.setList);
          };

          SetService.getMetaData($scope.setList).then(function(results) {
            $scope.setMap = {};
            results.forEach(function(set) {
              set.advLink = SetService.createAdvLink(set);
              $scope.setMap[set.id] = set;
            });

            vennDiagram = new dcc.Venn23($scope.vennData, config);
            vennDiagram.render( $element.find('.canvas')[0]);
          });
        }

        $scope.$watch('item', function(n) {
          if (n && n.result) {
            Settings.get().then(function(settings) {

              // The maximum allowed items from union operation
              $scope.unionMaxLimit = settings.maxNumberOfHits * settings.maxMultiplier;
              initVennDiagram();
            });
          }
        });

      }
    };
  });
})();


