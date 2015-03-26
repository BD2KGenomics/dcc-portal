(function() {
  'use strict';

  angular.module('icgc.phenotype', [
    'icgc.phenotype.directives',
    'icgc.phenotype.services'
  ]);

})();


(function() {
  'use strict';

  var module = angular.module('icgc.phenotype.directives', ['icgc.phenotype.services']);

  module.directive('phenotypeResult', function(SetService, PhenotypeService) {
    return {
      restrict: 'E',
      scope: {
        item: '='
      },
      templateUrl: '/scripts/phenotype/views/phenotype.result.html',
      link: function($scope) {

        // From D3's cat10 scale
        $scope.seriesColours = ['#1f77b4', '#ff7f0e', '#2ca02c'];

        function normalize() {
          // Normalize results: Sort by id, then sort by terms
          $scope.item.results.forEach(function(subAnalysis) {
            subAnalysis.data.forEach(function(d) {
              d.terms = _.sortBy(d.terms, function(term) {
                return term.term;
              });
            });
            subAnalysis.data = _.sortBy(subAnalysis.data, function(d) {
              return d.id;
            });
          });
        }


        function buildAnalyses() {

          // Globals
          $scope.setIds = _.pluck($scope.item.results[0].data, 'id');
          $scope.setFilters = $scope.setIds.map(function(id) {
            return PhenotypeService.entityFilters(id);
          });


          SetService.getMetaData($scope.setIds).then(function(results) {
            $scope.setMap = {};
            results.forEach(function(set) {
              set.advLink = SetService.createAdvLink(set);
              $scope.setMap[set.id] = set;
            });

            // Fetch analyses
            var gender = _.find($scope.item.results, function(subAnalysis) {
              return subAnalysis.name === 'gender';
            });
            var vital = _.find($scope.item.results, function(subAnalysis) {
              return subAnalysis.name === 'vitalStatus';
            });
            var age = _.find($scope.item.results, function(subAnalysis) {
              return subAnalysis.name === 'ageAtDiagnosisGroup';
            });

            $scope.gender = PhenotypeService.buildAnalysis(gender, $scope.setMap);
            $scope.vital = PhenotypeService.buildAnalysis(vital, $scope.setMap);
            $scope.age = PhenotypeService.buildAnalysis(age, $scope.setMap);
            $scope.meanAge = age.data.map(function(d) { return d.summary.mean; });

          });

        }

        $scope.$watch('item', function(n) {
          if (n) {
            normalize();
            buildAnalyses();
          }
        });

      }
    };
  });
})();



(function() {
  'use strict';

  var module = angular.module('icgc.phenotype.services', ['icgc.donors.models']);

  module.service('PhenotypeService', function($filter, Extensions) {

    function getTermCount(analysis, term, donorSetId) {
      var data, termObj;
      data = _.find(analysis.data, function(set) {
        return donorSetId === set.id;
      });

      // Special case
      if (term === '_missing') {
        return data.summary.missing || 0;
      }
      termObj = _.find(data.terms, function(t) {
        return t.term === term;
      });
      if (termObj) {
        return termObj.count;
      }
      return 0;
    }

    function getSummary(analysis, donorSetId) {
      var data;
      data = _.find(analysis.data, function(set) {
        return donorSetId === set.id;
      });
      return data.summary;
    }

    this.entityFilters = function(id) {
      var filters = {
        donor:{}
      };
      filters.donor[Extensions.ENTITY] = {
        is: [id]
      };
      return filters;
    };


    /**
     * Returns UI representation
     */
    this.buildAnalysis = function(analysis, setMap) {
      var uiTable = [];
      var uiSeries = [];
      var terms = _.pluck(analysis.data[0].terms, 'term');
      var setIds = _.pluck(analysis.data, 'id');

      // Create 'no data' term
      terms.push('_missing');

      // Build table row
      terms.forEach(function(term) {
        var row = {};
        row.term = term;

        setIds.forEach(function(donorSetId) {
          var count = getTermCount(analysis, term, donorSetId);
          var summary = getSummary(analysis, donorSetId);
          var advQuery = {};
          advQuery[Extensions.ENTITY] = {
            is: [donorSetId]
          };
          advQuery[analysis.name] = {
            is: [term]
          };

          row[donorSetId] = {};
          row[donorSetId].count = count;
          row[donorSetId].total = (summary.total + summary.missing);
          row[donorSetId].percentage = count/(summary.total + summary.missing);
          row[donorSetId].advQuery = {
            donor: advQuery
          };
        });
        uiTable.push(row);
      });

      // Build graph series
      setIds.forEach(function(setId) {
        uiSeries.push({
          name: setMap[setId].name || setId,
          // data: _.pluck(uiTable.map(function(row) { return row[setId]; }), 'percentage')
          data: uiTable.map(function(row) {
            return {
              y: row[setId].percentage,
              count: row[setId].count
            };
          })
        });
      });

      // Build final result
      return {
        uiTable: uiTable,
        uiGraph: {
          categories: terms.map(function(term) { return $filter('trans')(term, true); }),
          series: uiSeries
        }
      };
    };

  });


})();
