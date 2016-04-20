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


(function () {
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

          var geneLink = baseSearch + JSON.stringify($scope.geneFilter);
          $scope.geneLink = geneLink;
        }

        function materializeSets() {
          var donorPromise = Donors.getList({
            filters: $scope.donorFilter,
            size: 100
          }).then(function (data) {
            $scope.donors = data.hits;
          });

          var genePromise = Genes.getList({
            filters: $scope.geneFilter,
            size: 100
          }).then(function (data) {
            $scope.genes = data.hits;
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
              }
            },
            from: 1,
            size: 100
          }).then(function (data) {
            $scope.occurrences = data;
          });

          return $q.all([donorPromise, genePromise, occurrencePromise]);
        }

        function initOnco() {

          var donors = _.map($scope.donors, function (d) { return { 'donorId': d.id, 'age': (d.ageAtDiagnosis === undefined ? 0 : d.ageAtDiagnosis) }; });
          var genes = _.map($scope.genes, function (g) { return { 'id': g.id, 'symbol': g.symbol }; });

          var donorIds = _.map($scope.donors, function (g) { return g.id; });
          var geneIds = _.map($scope.genes, function (d) { return d.id; });

          var observations = _($scope.occurrences).map(function (o) {
            return { id: o.mutationId, donorId: o.donorId, gene: o.geneId, consequence: o.consequenceType };
          }).filter(function (o) {
            return geneIds.indexOf(o.gene) > 0 && donorIds.indexOf(o.donorId) > 0;
          }).value();

          var params = {
            height: 400,
            width: 700
          };

          var grid = window.oncogrid(donors, genes, observations, '#oncogrid-div', params);
          grid.init();
          grid.heatMap = true;
          grid.renderFirst();
        }

        $scope.$watch('item', function (n) {
          if (n) {
            createLinks();
            materializeSets().then(function () {
              initOnco();
            });
          }
        });

        $scope.$on('$destroy', function() {
          grid.destroy();
        });
        
      }
    };
  });

})();