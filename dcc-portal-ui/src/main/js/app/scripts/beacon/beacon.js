/*
 * Copyright 2015(c) The Ontario Institute for Cancer Research. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the GNU Public
 * License v3.0. You should have received a copy of the GNU General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 'AS IS' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

(function() {
  'use strict';

  
  var module = angular.module('icgc.beacon', [
    'icgc.beacon.controllers'
  ]);
   
  module.config(function ($stateProvider) {
    $stateProvider.state('beacon', {
      url: '/ga4gh/beacon',
      templateUrl: 'scripts/beacon/views/beacon.html',
      controller: 'BeaconCtrl as BeaconCtrl'
    });
  });

})();

// Chromosome lengths
var lengths = {'1': 249250621, '2': 243199373, '3': 198022430, '4': 191154276,
               '5': 180915260, '6': 171115067, '7': 159138663, '8': 146364022,
               '9': 141213431, '10': 135534747, '11': 135006516, '12': 133851895,
               '13': 115169878, '14': 107349540, '15': 102531392, '16': 90354753,
               '17': 81195210, '18': 78077248, '19': 59128983, '20': 63025520,
               '21': 48129895, '22': 51304566, 'X': 155270560, 'Y': 59373566, 'MT': 16569};

(function() {
  'use strict';

  var module = angular.module('icgc.beacon.controllers', []);

  module.controller('BeaconCtrl', function ($scope, Page, Restangular) {
    Page.setTitle('Beacon');
    Page.setPage('beacon');

    var projectsPromise = Restangular.one('projects')
      .get({
        'field' : 'id',
        'sort':'id',
        'order':'asc',
        'size' : 100
      },{'Accept':'application/json'});

    projectsPromise.then(function(data){
      $scope.projects = data.hits;
      $scope.projects.unshift({id:'-- ALL DATASETS'});
      $scope.params = {
        project:$scope.projects[0],
        chr:'1',
        reference:'GRCh37',
      };
    });

    $scope.hasInvalidParams = true;
    $scope.errorMessage = '';
    $scope.result = {
      exists:false,
      value:''
    };
    $scope.chromosomes = Object.keys(lengths);

    $scope.checkParams = function(){
      $scope.hasInvalidParams = true;
      //reset error messages
      $scope.errorMessage = '';

      //first if anything required is empty or missing, stop checking
      if(!($scope.params.reference && $scope.params.position && $scope.params.allele)){
        $scope.hasInvalidParams = true;
        return;
      }

      // check that the position is less than length of chromosome
      if($scope.params.position > lengths[$scope.params.chr]){
        $scope.errorMessage = 'Position must be less than Chromosome '+$scope.params.chr+'\'s length: '+lengths[$scope.params.chr];
        return;
      }

      // check that the reference is GRCh37 (all we support)
      if($scope.params.reference !== 'GRCh37'){
        $scope.errorMessage = 'Currently only GRCh37 is supported';
        return;
      }

      //made it to the end, no errors found
      $scope.hasInvalidParams = false;
    };

    $scope.submitQuery = function() {
      var promise = Restangular.one('beacon', 'query')
      .get({
        'chromosome' : $scope.params.chr,
        'reference':$scope.params.reference,
        'position':$scope.params.position,
        'allele':$scope.params.allele ? $scope.params.allele : '',
        'dataset':$scope.params.project.id === 'ALL' ? '':$scope.params.project.id
      },{'Accept':'application/json'});

      promise.then(function(data){
        $scope.result.exists = true;
        $scope.result.value = data.response.exists;
        var url = data.getRequestedUrl();
        $scope.requestedUrl = url.substring(0,url.indexOf('?'))+'/query'+url.substring(url.indexOf('?'));
      });
    };

    $scope.resetQuery = function() {
      $scope.params = {
        project:$scope.projects[0],
        chr:'1',
        reference:'GRCh37',
      };
      $scope.hasInvalidParams = true;
      $scope.errorMessage = '';
      $scope.result = {
        exists:false,
        value:''
      };
      $scope.requestedUrl = null;
    };
  });

  module.directive('positionValidator', function() {
    return {
      require: '?ngModel',
      link: function(scope, element, attrs, ngModelCtrl) {
        if(!ngModelCtrl) {
          return;
        }

        ngModelCtrl.$parsers.push(function(val) {
          if (angular.isUndefined(val)) {
            val = '';
          }
          var clean = val.replace( /[^0-9]+/g, '');
          clean = clean.replace(/^0+/,'');
          if (val !== clean) {
            ngModelCtrl.$setViewValue(clean);
            ngModelCtrl.$render();
          }
          return clean;
        });

        element.bind('keypress', function(event) {
          if(event.keyCode === 32) {
            event.preventDefault();
          }
        });
      }
    };
  });

  module.directive('alleleValidator', function() {
    return {
      require: '?ngModel',
      link: function(scope, element, attrs, ngModelCtrl) {
        if(!ngModelCtrl) {
          return;
        }

        ngModelCtrl.$parsers.push(function(val) {
          if (angular.isUndefined(val)) {
            val = '';
          }
          var clean = val.replace( /[^ACTGactg]+/g, '');
          clean = clean.toUpperCase();
          if (val !== clean) {
            ngModelCtrl.$setViewValue(clean);
            ngModelCtrl.$render();
          }
          return clean;
        });

        element.bind('keypress', function(event) {
          if(event.keyCode === 32) {
            event.preventDefault();
          }
        });
      }
    };
  });

})();
