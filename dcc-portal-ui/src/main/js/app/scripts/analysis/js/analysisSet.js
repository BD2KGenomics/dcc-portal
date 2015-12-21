(function () {
  'use strict';

  var module = angular.module('icgc.analysis.controllers');

  module.controller('SavedSetController', function($scope, $window, $location, $timeout, $modal,
    SetService, Settings, LocationService, RouteInfoService) {

    var _this = this;
    var syncSetTimeout;

    _this.syncError = false;
    _this.entitySets = SetService.getAll();
    _this.selectedSets = [];
    _this.checkAll = false;
    _this.dataRepoTitle = RouteInfoService.get ('dataRepositories').title;

    Settings.get().then(function(settings) {
      _this.downloadEnabled = settings.downloadEnabled || false;
    });

    // Selected sets
    _this.update = function() {
      _this.selectedSets = [];
      _this.entitySets.forEach(function(set) {
        if (set.checked === true) {
          _this.selectedSets.push(set);
        }
      });
    };

    _this.newAnalysis = function() {
      $location.path('analysis');
    };

    /* Select all / de-select all */
    _this.toggleSelectAll = function() {
      _this.checkAll = !_this.checkAll;

      _this.entitySets.forEach(function(set) {
        set.checked = _this.checkAll;
      });
    };

    _this.getEntitySetShareParams = function(item) {
      // Only do this assignment operation once - otherwise return the
      // cached value.
      if (angular.isDefined(item.entitySetShareParams)) {
        return item.entitySetShareParams;
      }


      var shareParams = {
        url: LocationService.buildURLFromPath('search' +
                                              (item.advType !== '' ? ('/' +  item.advType) : '')),
        filters: JSON.stringify(item.advFilters)
      };

      item.entitySetShareParams = shareParams;

      return item.entitySetShareParams;
    };

    _this.exportSet = function(id) {
      SetService.exportSet(id);
    };

    _this.downloadDonorData = function(id) {
      $modal.open({
        templateUrl: '/scripts/downloader/views/request.html',
        controller: 'DownloadRequestController',
        resolve: {
          filters: function() { return {donor:{entitySetId:{is:[id]}}}; }
        }
      });
    };


    _this.addCustomGeneSet = function() {
      var inst = $modal.open({
        templateUrl: '/scripts/genelist/views/upload.html',
        controller: 'GeneListController'
      });

      // Successful submit
      inst.result.then(function() {
        $timeout.cancel(syncSetTimeout);
        synchronizeSets(10);
      });
    };


    /* To remove unretrievable sets from local store*/
    _this.removeInvalidSets = function() {
      var toRemove = _.filter(_this.entitySets, function(set) {
        return set.state !== 'FINISHED';
      });
      if (toRemove.length > 0) {
        _this.syncError = false;
        SetService.removeSeveral(_.pluck(toRemove, 'id'));
        _this.checkAll = false; // reset
        synchronizeSets(1);
      }
    };


    /* User inititated set removal */
    _this.removeSets = function() {
      var confirmRemove = $window.confirm('Are you sure you want to remove selected sets?');
      if (!confirmRemove || confirmRemove === false) {
        return;
      }

      var toRemove = _this.selectedSets;
      if (toRemove.length > 0) {
        _this.syncError = false;
        SetService.removeSeveral(_.pluck(toRemove, 'id'));
        _this.checkAll = false; // reset
      }
    };


    function synchronizeSets(numTries) {
      var pendingLists, pendingListsIDs, promise;
      pendingLists = _.filter(_this.entitySets, function(d) {
        return d.state !== 'FINISHED';
      });
      pendingListsIDs = _.pluck(pendingLists, 'id');

      if (pendingLists.length <= 0) {
        return;
      }

      if (numTries <= 0) {
        console.log('Stopping, numTries runs out');
        _this.syncError = true;
        return;
      }

      promise = SetService.getMetaData(pendingListsIDs);
      promise.then(function(results) {
        SetService.updateSets(results);
        syncSetTimeout = $timeout(function() {
          synchronizeSets(--numTries);
        }, 3000);
      });
    }

    $scope.$on('destroy', function() {
      $timeout.cancel(syncSetTimeout);
    });

    synchronizeSets(10);
  });


})();
