(function() {
  'use strict';

  var module = angular.module('icgc.oncogrid', []);

  module.directive('oncogridProject', function(Projects, Occurrences, LocationService) {
    return {
      scope: {
        project: '=',
      },
      templateUrl: 'scripts/oncogrid/views/oncogrid.html',
      link: function($scope) {

        var filter = {
          'mutation': { 'functionalImpact': { 'is': ['High', 'Low'] } }
        };

        Projects.one($scope.project).getGenes({ filters: filter, size: 50 }).then(function(geneHits) {
          Projects.one($scope.project).getDonors({ filters: filter, size: 100 }).then(function(donorHits) {

            var filters = LocationService.filters();

            var genes = _.map(geneHits.hits, function(g) { return { 'id': g.id, 'symbol': g.symbol }; });
            var donors = _.map(donorHits.hits, function(d) { return { 'donorId': d.id, 'age': (d.ageAtDiagnosis === undefined ? 0 : d.ageAtDiagnosis) }; });

            var geneIds = _.map(geneHits.hits, function(g) { return g.id; });
            var donorIds = _.map(donorHits.hits, function(d) { return d.id; });

            filter.gene = { 'id': { 'is': geneIds } };
            filter.donor = { 'id': { 'is': donorIds } };

            Occurrences.getAll({ filters: filter, from: 1, size: 100 })
              .then(function(observationHits) {

                var observations = _(observationHits).map(function(o) {
                  return { donorId: o.donorId, gene: o.geneId, consequence: o.consequenceType };
                })
                  .filter(function(o) {
                    return geneIds.indexOf(o.gene) > 0 && donorIds.indexOf(o.donorId) > 0;
                  }).value();

                var params = {
                  height: 400,
                  width: 700
                };

                var grid = window.oncogrid(donors, genes, observations, '#oncogrid-div', params);
                grid.init();
                //grid.heatMap = true;
                grid.renderFirst();

                $scope.removeCleanDonors = function() {
                  var criteria = function(d) {
                    return d.score === 0;
                  };

                  grid.removeDonors(criteria);
                };

                $scope.removeCleanGenes = function() {
                  var criteria = function(d) {
                    return d.score === 0;
                  };

                  grid.removeGenes(criteria);
                };

                $scope.clusterData = function() {
                  grid.cluster();
                };

                $scope.sortByAge = function() {
                  grid.sortDonors(function(a, b) {
                    return a.age - b.age;
                  });
                };
                
                $scope.heatMap = function() {
                  grid.toggleHeatmap();
                };

              });

          });
        });

      }
    };
  });
})();