/*
 * Copyright 2016(c) The Ontario Institute for Cancer Research. All rights reserved.
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

  angular.module('icgc.oncogrid', ['icgc.oncogrid.directives']);

})();


(function ($, OncoGrid) {
  'use strict';

  var module = angular.module('icgc.oncogrid.directives', []);

  module.directive('oncogridAnalysis', function (Donors, Genes, Occurrences, $q) {
    return {
      restrict: 'E',
      scope: {
        item: '='
      },
      templateUrl: '/scripts/oncogrid/views/oncogrid-analysis.html',
      link: function ($scope) {
        var baseSearch = '/search?filters=';


        function createLinks() {
          $scope.geneSet = $scope.item.geneSet;
          $scope.donorSet = $scope.item.donorSet;

          $scope.donorFilter = {
            donor: {
              id: {
                is: ['ES:' + $scope.donorSet]
              }
            }
          };

          var donorLink = baseSearch + JSON.stringify($scope.donorFilter);
          $scope.donorLink = donorLink;

          $scope.geneFilter = {
            gene: {
              id: {
                is: ['ES:' + $scope.geneSet]
              }
            }
          };
          
          $scope.curatedFilter = {
            gene: {
              id: {
                is: ['ES:' + $scope.geneSet]
              },
              curatedSetId: {
                is: ['GS1']
              }
            }
          };

          var geneLink = baseSearch + JSON.stringify($scope.geneFilter);
          $scope.geneLink = geneLink;
        }

        $scope.materializeSets = function() {
          var donorPromise = Donors.getAll({
            filters: $scope.donorFilter,
            size: 100
          }).then(function (data) {
            $scope.donors = data;
          });

          var genePromise = Genes.getAll({
            filters: $scope.geneFilter,
            size: 100
          }).then(function (data) {
            $scope.genes = data;
          });

          var geneCuratedSetPromise = Genes.getAll({
            filters: $scope.curatedFilter,
            size: 100
          }).then(function (data) {
            $scope.curatedList = _.map(data, function(g) {
              return g.id;
            });
          });

          var occurrencePromise = Occurrences.getAll({
            filters: {
              donor: {
                id: {
                  is: ['ES:' + $scope.donorSet]
                }
              },
              gene: {
                id: {
                  is: ['ES:' + $scope.geneSet]
                }
              },
              mutation: {
                functionalImpact: {
                  is : ['High']
                }
              }
            },
            from: 1,
            size: 100
          }).then(function (data) {
            $scope.occurrences = data;
          });

          return $q.all([donorPromise, genePromise, geneCuratedSetPromise, occurrencePromise]);
        };

        $scope.initOnco =  function() {

          var donors = _.map($scope.donors,
              function (d) {
                return { 
                  'id': d.id,
                  'age': (d.ageAtDiagnosis === undefined ? 0 : d.ageAtDiagnosis),
                  'sex': (d.gender === undefined ? 'unknown' : d.gender),
                  'vitalStatus': (d.vitalStatus === undefined? false : (d.vitalStatus === 'alive' ? true : false)),
                  'cnsmExists': d.cnsmExists,
                  'stsmExists': d.stsmExists
                };
              });
          
          var genes = _.map($scope.genes, function (g) {
            return {
              'id': g.id,
              'symbol': g.symbol,
              'totalDonors': g.affectedDonorCountTotal,
              'cgc': $scope.curatedList.indexOf(g.id) >= 0
            };
          });

          var donorIds = _.map($scope.donors, function (g) { return g.id; });
          var geneIds = _.map($scope.genes, function (d) { return d.id; });

          var observations = _($scope.occurrences).map(function (o) {
            return { id: o.mutationId, donorId: o.donorId, geneId: o.geneId, consequence: o.consequenceType };
          }).filter(function (o) {
            return geneIds.indexOf(o.geneId) > 0 && donorIds.indexOf(o.donorId) > 0;
          }).value();

          var donorTracks = [
            { 'name': 'Age at Diagnosis', 'fieldName': 'age', 'type': 'int' },
            { 'name': 'Vital Status', 'fieldName': 'vitalStatus', 'type': 'vital' },
            { 'name': 'Sex', 'fieldName': 'sex', 'type': 'sex' },
            { 'name': 'CNSM Exists', 'fieldName': 'cnsmExists', 'type': 'bool'},
            { 'name': 'STSM Exists', 'fieldName': 'stsmExists', 'type': 'bool'}
          ];

          var donorOpacity = function (d) {
            if (d.type === 'int') {
              return d.value / 100;
            } else if (d.type === 'vital') {
              return 1;
            } else if (d.type === 'sex') {
              return 1;
            } else if (d.type === 'bool') {
              return d.value ? 1 : 0;
            } else {
              return 0;
            }
          };
          
          var geneTracks = [
            { 'name': '# Donors affected ', 'fieldName': 'totalDonors', 'type': 'int' },
            { 'name': 'Curated Gene Census ', 'fieldName': 'cgc', 'type': 'bool' }
          ];
          
          var maxDonorsAffected = _.max(genes, function(g) { return g.totalDonors; }).totalDonors;

          var geneOpacity = function (g) {
            if (g.type === 'int') {
              return g.value / maxDonorsAffected;
            } else if (g.type === 'bool') {
              return g.value ? 1 : 0;
            } else {
              return 1;
            }
          };

          var params = {
            donors: donors,
            genes: genes,
            observations: observations,
            element: '#oncogrid-div',
            height: 500, 
            width: 750,
            heatMap: true,
            trackHeight: 15,
            donorTracks: donorTracks,
            donorOpacityFunc: donorOpacity,
            geneTracks: geneTracks,
            geneOpacityFunc: geneOpacity
          };

          $scope.grid = new OncoGrid(params);
          $scope.grid.render();
        };

        $scope.$watch('item', function (n) {
          if (n) {
            if (typeof $scope.grid !== 'undefined' && $scope.grid !== null) {
              $scope.grid.destroy();
            }
            $('#oncogrid-spinner').toggle(true);
            createLinks();
            $scope.materializeSets().then(function () {
              $('#oncogrid-spinner').toggle(false);
              $scope.initOnco();
            });
          }
        });

        $scope.removeCleanDonors = function () {
          var criteria = function (d) {
            return d.score === 0;
          };

          $scope.grid.removeDonors(criteria);
        };

        $scope.removeCleanGenes = function () {
          var criteria = function (d) {
            return d.score === 0;
          };

          $scope.grid.removeGenes(criteria);
        };

        $scope.clusterData = function () {
          $scope.grid.cluster();
        };

        $scope.sortByAge = function () {
          $scope.grid.sortDonors(function (a, b) {
            return a.age - b.age;
          });
        };

        $scope.heatMap = function () {
          $scope.grid.toggleHeatmap();
        };
        
        $scope.printGrid = function () {
          var gridDiv = document.getElementById('oncogrid-div').outerHTML;
          document.body.innerHTML = gridDiv;
          window.print();
        };

        $scope.$on('$destroy', function () {
          $scope.grid.destroy();
        });

      }
    };
  });

})(jQuery, OncoGrid);