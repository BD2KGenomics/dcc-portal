(function($) {
  'use strict';

  var module = angular.module('icgc.oncogrid', []);

  module.directive('oncogrid', function(Projects, LocationService) {
    return {
      scope: {
        project: '=',
      },
      templateUrl: 'scripts/oncogrid/views/oncogrid.html',
      link: function($scope) {
        
        Projects.one($scope.project).getGenes({ filters: LocationService.filters() }).then(function(geneHits) {
          Projects.one($scope.project).getDonors({ filters: LocationService.filters() }).then(function(donorHits) {
            
            var genes = _.map(geneHits.hits, function(g) { return { 'id' : g.id, 'symbol': g.symbol };});
            var donors = _.map(donorHits.hits, function(d) { return {'donorId': d.id, 'age': d.ageAtDiagnosis} });
            
            var grid = window.oncogrid(donors, genes, [], '#oncogrid-div')
            grid.init();
            grid.renderFirst();
            
          });
        });
       
      }
    };
  });
})();