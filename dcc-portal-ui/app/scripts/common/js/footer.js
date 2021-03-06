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

(function() {
  'use strict';

  angular.module('app.common.footer', ['app.common.footer.controllers']);

  angular.module('app.common.footer.controllers', []);

  angular.module('app.common.footer.controllers').controller('FooterCtrl',
    function ($scope, $http, PortalFeature, RouteInfoService) {
    var _ctrl = this;

    $http.get('/api/version').success(function(data) {
      _ctrl.apiVersion = data.api;
      _ctrl.portalVersion = data.portal;
      _ctrl.portalCommit = data.portalCommit;
    });

    _ctrl.mainItems = _.map (['home', 'projects', 'advancedSearch', 'dataAnalysis',
      'dataReleases', 'dataRepositories', 'pcawg'], RouteInfoService.get);

   var cloudLinks = [
            {'link': '/icgc-in-the-cloud', 'title': 'About'},
            {'link': '/icgc-in-the-cloud/aws', 'title': 'Amazon'},
            {'link': '/icgc-in-the-cloud/collaboratory', 'title': 'Collaboratory'}//,
            //{'link': '/icgc-in-the-cloud/guide', 'title': 'User Guide'}

          ];

    $scope.portalFeature = PortalFeature;

    $scope.stagedFeatures = {
      getCloudLinks: function() {
        if (PortalFeature.get('ICGC_CLOUD')) {
          return cloudLinks;
        }
      }
    };
  });

})();
