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

/*
 * This is the Angular service that provides lots of helper functions for PQL translations.
 * It hides the implementation details of PqlTranslationService and PqlQueryObjectService.
 */

(function () {
  'use strict';

  var namespace = 'icgc.common.pql.utils';
  var serviceName = 'PqlUtilService';

  var module = angular.module(namespace, []);

  module.factory(serviceName, function (PqlQueryObjectService, $location, $log) {
    // This is the parameter name for PQL in the URL query params.
    var pqlParameterName = 'query';
    var service = PqlQueryObjectService;

    // Here Pql is persisted in a query param in the URL.
    function getPql() {
      var search = $location.search();
      var pql = (search [pqlParameterName] || '').trim();

      $log.debug ('The URL contains this PQL: [%s].', pql);
      return pql;
    }

    // Retrieves pql persisted in a query param in the URL.
    function setPql (pql) {
      $location.search (pqlParameterName, pql);
      $log.debug ('PQL is updated to [%s].', pql);
    }

    function updatePql () {
      var args = Array.prototype.slice.call (arguments);
      var func = _.head (args);
      var pql = func.apply (null, [getPql()].concat (_.tail (args)));
      setPql (pql);
    }

    function getSort() {
      return service.getSort (getPql());
    }

    function setSort (sort) {
      updatePql (service.setSort, sort);
    }

    function addSort (field, direction) {
      if (! (field && direction)) {return;}

      var sort = getSort();
      sort = _.isArray (sort) ? sort : [];
      sort.push ({field: field, direction: direction});

      setSort (sort);
    }

    function removeSort (field) {
      if (! _.isString (field)) {return;}

      var sort = getSort();
      sort = _.isArray (sort) ? sort : [];
      var updatedSort = _.remove (sort, function (o) {
        return o.field !== field;
      });

      setSort (updatedSort);
    }

    // A builder to allow the UI to build a PQL programmatically.
    var Builder = function (pql) {
      function addTerm (buffer, categoryName, facetName, term) {
        return service.addTerm (buffer, categoryName, facetName, term);
      }

      function addTerms (buffer, categoryName, facetName, terms) {
        return service.addTerms (buffer, categoryName, facetName, terms);
      }

      function removeTerm (buffer, categoryName, facetName, term) {
        return service.removeTerm (buffer, categoryName, facetName, term);
      }

      function removeFacet (buffer, categoryName, facetName) {
        return service.removeFacet (buffer, categoryName, facetName);
      }

      function overwrite (buffer, categoryName, facetName, term) {
        return service.overwrite (buffer, categoryName, facetName, term);
      }

      function includesFacets (buffer) {
        return service.includesFacets (buffer);
      }

      function includes (buffer, field) {
        return service.includes (buffer, field);
      }

      function includesConsequences (buffer) {
        return service.includes (buffer, 'consequences');
      }

      function setLimit (buffer, limit) {
        return service.setLimit (buffer, limit);
      }

      function setSort (buffer, sort) {
        return service.setSort (buffer, sort);
      }

      function buildFilterOnlyPql (buffer) {
        return service.toFilterOnlyStatement (buffer);
      }

      // A list of functions that update filters in PQL.
      var filterModifiers = [addTerm, addTerms, removeTerm, removeFacet, overwrite];

      var initialPql = pql || '';
      var actions = [];

      function addAction (func, args) {
        actions.push ({func: func, args: args});
      }

      function build (actions, startingPql) {
        return _.reduce (actions, function (result, action) {
            return action.func.apply (null, [result].concat (action.args));
          }, startingPql);
      }

      return {
        addTerm: function (categoryName, facetName, term) {
          addAction (addTerm, [categoryName, facetName, term]);
          return this;
        },
        addTerms: function (categoryName, facetName, terms) {
          addAction (addTerms, [categoryName, facetName, terms]);
          return this;
        },
        removeTerm: function (categoryName, facetName, term) {
          addAction (removeTerm, [categoryName, facetName, term]);
          return this;
        },
        removeFacet: function (categoryName, facetName) {
          addAction (removeFacet, [categoryName, facetName]);
          return this;
        },
        overwrite: function (categoryName, facetName, term) {
          addAction (overwrite, [categoryName, facetName, term]);
          return this;
        },
        includesFacets: function () {
          addAction (includesFacets, []);
          return this;
        },
        includesConsequences: function () {
          addAction (includesConsequences, []);
          return this;
        },
        includes: function (field) {
          addAction (includes, [field]);
          return this;
        },
        setLimit: function (limit) {
          addAction (setLimit, [limit]);
          return this;
        },
        setSort: function (sort) {
          addAction (setSort, [sort]);
          return this;
        },
        reset: function (startingPql) {
          if (! _.isEmpty (startingPql)) {initialPql = startingPql;}

          actions = [];
          return this;
        },
        build: function () {
          return build (actions, initialPql);
        },
        // This buildFilters() builds a PQL with filter expression only by only considering filters
        // during the materialization process; all the params (i.e. 'select', 'facets', 'limit', 'sort') are omitted.
        buildFilters: function () {
          var filterOnlyActions = _.remove (actions, function (action) {
            return _.contains (filterModifiers, action.func);
          });

          filterOnlyActions.push ({func: buildFilterOnlyPql, args: []});

          return build (filterOnlyActions, initialPql);
        }
      };
    };

    return {
      paramName: pqlParameterName,
      reset: function () {
        setPql ('');
      },
      addTerm: function (categoryName, facetName, term) {
        updatePql (service.addTerm, categoryName, facetName, term);
      },
      addTerms: function (categoryName, facetName, terms) {
        updatePql (service.addTerms, categoryName, facetName, terms);
      },
      removeTerm: function (categoryName, facetName, term) {
        updatePql (service.removeTerm, categoryName, facetName, term);
      },
      removeFacet: function (categoryName, facetName) {
        updatePql (service.removeFacet, categoryName, facetName);
      },
      overwrite: function (categoryName, facetName, term) {
        updatePql (service.overwrite, categoryName, facetName, term);
      },
      mergeQueries: function (query1, query2) {
        return service.mergeQueries (query1, query2);
      },
      mergePqls: function (pql1, pql2) {
        return service.mergePqls (pql1, pql2);
      },
      getSort: getSort,
      getLimit: function () {
        return service.getLimit (getPql());
      },
      convertQueryToPql: service.convertQueryToPql,
      getFilters: function () {
        return service.getFilters (getPql());
      },
      convertPqlToQuery: service.convertPqlToQueryObject,
      includesFacets: function () {
        updatePql (service.includesFacets);
      },
      includesConsequences: function () {
        updatePql (service.includes, 'consequences');
      },
      includes: function (field) {
        updatePql (service.includes, field);
      },
      setLimit: function (limit) {
        updatePql (service.setLimit, limit);
      },
      limitFromSize: function (from, size) {
        this.setLimit ({from: from, size: size});
      },
      limitSize: function (size) {
        this.setLimit ({size: size});
      },
      removeSort: removeSort,
      setSort: setSort,
      sortAsc: function (field) {
        addSort (field, '+');
      },
      sortDesc: function (field) {
        addSort (field, '-');
      },
      getRawPql: getPql,
      getBuilder: function (pql) {
        return new Builder(pql);
      }
    };
  });
})();
