/*
 * Copyright 2013(c) The Ontario Institute for Cancer Research. All rights reserved.
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

'use strict';

angular.module('highcharts.services', []);

angular.module('highcharts.services').service('HighchartsService', function ($q, LocationService) {
  var _this = this;

  Highcharts.setOptions({
    chart: {
      backgroundColor: 'transparent'
    },
    colors: [
      '#1693C0', '#24B2E5',
      '#E9931C', '#EDA94A',
      '#166AA2', '#1C87CE',
      '#D33682', '#DC609C',
      '#6D72C5', '#9295D3',
      '#CE6503', '#FB7E09',
      '#1A9900', '#2C0'
    ],
    yAxis: {
      gridLineColor: '#E0E0E0',
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    xAxis: {
      gridLineColor: '#E0E0E0',
      labels: {
        style: {
          fontSize: '10px'
        }
      }
    },
    title: {
      style: {
        color: 'hsl(0, 0%, 20%)',
        fontFamily: '"Open Sans", "Helvetica Neue", Helvetica, Arial, sans-serif',
        fontWeight: 300,
        fontSize: '1.3rem'
      }
    },
    tooltip: {
      useHTML: true,
      borderWidth: 0,
      borderRadius: 0,
      backgroundColor: 'transparent',
      shadow: false,
      style: {
        //  fontSize: '1rem'
      }
    },
    legend: {
      enabled: false
    },
    loading: {
      style: {
        backgroundColor: '#f5f5f5'
      },
      labelStyle: {
        top: '40%'
      }
    }
  });

  this.colours = Highcharts.getOptions().colors;
  this.primarySiteColours = {
    'Liver': this.colours[0],
    'Pancreas': this.colours[2],
    'Kidney': this.colours[4],
    'Head and neck': this.colours[6],
    'Brain': this.colours[8],
    'Blood': this.colours[10],
    'Prostate': this.colours[12],
    'Ovary': this.colours[1],
    'Lung': this.colours[3],
    'Colorectal': this.colours[5],
    'Breast': this.colours[7],
    'Uterus': this.colours[9],
    'Stomach': this.colours[11],
    'Esophagus': this.colours[13],
    'Skin': this.colours[0],
    'Cervix': this.colours[2],
    'Bone': this.colours[4],
    'Bladder': this.colours[6]
  };
  this.projectColours = {
    'LIRI-JP': Highcharts.Color(this.primarySiteColours.Liver).brighten(0.1).get(),
    'LINC-JP': Highcharts.Color(this.primarySiteColours.Liver).brighten(0.2).get(),
    'LIHC-US': Highcharts.Color(this.primarySiteColours.Liver).brighten(0.3).get(),
    'LICA-FR': Highcharts.Color(this.primarySiteColours.Liver).brighten(0.4).get(),
    'LIAD-FR': Highcharts.Color(this.primarySiteColours.Liver).brighten(0.5).get(),
    'PAEN-AU': Highcharts.Color(this.primarySiteColours.Pancreas).brighten(0.1).get(),
    'PACA-CA': Highcharts.Color(this.primarySiteColours.Pancreas).brighten(0.2).get(),
    'PACA-AU': Highcharts.Color(this.primarySiteColours.Pancreas).brighten(0.3).get(),
    'PAAD-US': Highcharts.Color(this.primarySiteColours.Pancreas).brighten(0.4).get(),
    'RECA-EU': Highcharts.Color(this.primarySiteColours.Kidney).brighten(0.1).get(),
    'RECA-CN': Highcharts.Color(this.primarySiteColours.Kidney).brighten(0.2).get(),
    'KIRP-US': Highcharts.Color(this.primarySiteColours.Kidney).brighten(0.3).get(),
    'KIRC-US': Highcharts.Color(this.primarySiteColours.Kidney).brighten(0.4).get(),
    'THCA-US': Highcharts.Color(this.primarySiteColours['Head and neck']).brighten(0.1).get(),
    'THCA-SA': Highcharts.Color(this.primarySiteColours['Head and neck']).brighten(0.2).get(),
    'ORCA-IN': Highcharts.Color(this.primarySiteColours['Head and neck']).brighten(0.3).get(),
    'HNSC-US': Highcharts.Color(this.primarySiteColours['Head and neck']).brighten(0.4).get(),
    'PBCA-DE': Highcharts.Color(this.primarySiteColours.Brain).brighten(0.1).get(),
    'NBL-US': Highcharts.Color(this.primarySiteColours.Brain).brighten(0.2).get(),
    'LGG-US': Highcharts.Color(this.primarySiteColours.Brain).brighten(0.3).get(),
    'GBM-US': Highcharts.Color(this.primarySiteColours.Brain).brighten(0.4).get(),
    'MALY-DE': Highcharts.Color(this.primarySiteColours.Blood).brighten(0.1).get(),
    'LAML-US': Highcharts.Color(this.primarySiteColours.Blood).brighten(0.2).get(),
    'CMDI-UK': Highcharts.Color(this.primarySiteColours.Blood).brighten(0.3).get(),
    'CLLE-ES': Highcharts.Color(this.primarySiteColours.Blood).brighten(0.4).get(),
    'ALL-US': Highcharts.Color(this.primarySiteColours.Blood).brighten(0.5).get(),
    'LAML-KR': Highcharts.Color(this.primarySiteColours.Blood).brighten(0.6).get(),
    'PRAD-US': Highcharts.Color(this.primarySiteColours.Prostate).brighten(0.1).get(),
    'PRAD-CA': Highcharts.Color(this.primarySiteColours.Prostate).brighten(0.2).get(),
    'EOPC-DE': Highcharts.Color(this.primarySiteColours.Prostate).brighten(0.3).get(),
    'PRAD-UK': Highcharts.Color(this.primarySiteColours.Prostate).brighten(0.4).get(),
    'OV-US': Highcharts.Color(this.primarySiteColours.Ovary).brighten(0.1).get(),
    'OV-AU': Highcharts.Color(this.primarySiteColours.Ovary).brighten(0.2).get(),
    'LUSC-US': Highcharts.Color(this.primarySiteColours.Lung).brighten(0.1).get(),
    'LUAD-US': Highcharts.Color(this.primarySiteColours.Lung).brighten(0.2).get(),
    'LUSC-KR': Highcharts.Color(this.primarySiteColours.Lung).brighten(0.3).get(),
    'READ-US': Highcharts.Color(this.primarySiteColours.Colorectal).brighten(0.1).get(),
    'COAD-US': Highcharts.Color(this.primarySiteColours.Colorectal).brighten(0.2).get(),
    'BRCA-US': Highcharts.Color(this.primarySiteColours.Breast).brighten(0.1).get(),
    'BRCA-UK': Highcharts.Color(this.primarySiteColours.Breast).brighten(0.2).get(),
    'UCEC-US': Highcharts.Color(this.primarySiteColours.Uterus).brighten(0.1).get(),
    'STAD-US': Highcharts.Color(this.primarySiteColours.Stomach).brighten(0.1).get(),
    'GACA-CN': Highcharts.Color(this.primarySiteColours.Stomach).brighten(0.2).get(),
    'SKCM-US': Highcharts.Color(this.primarySiteColours.Skin).brighten(0.1).get(),
    'ESAD-UK': Highcharts.Color(this.primarySiteColours.Esophagus).brighten(-0.1).get(),
    'ESCA-CN': Highcharts.Color(this.primarySiteColours.Esophagus).brighten(-0.2).get(),
    'CESC-US': Highcharts.Color(this.primarySiteColours.Cervix).brighten(-0.1).get(),
    'BOCA-UK': Highcharts.Color(this.primarySiteColours.Bone).brighten(-0.1).get(),
    'BLCA-US': Highcharts.Color(this.primarySiteColours.Bladder).brighten(-0.1).get(),
    'BLCA-CN': Highcharts.Color(this.primarySiteColours.Bladder).brighten(-0.2).get()
  };

  // new
  this.donut = function (params) {
    var innerPie = {}, innerHits = [], outerHits = [];

    // Check for required parameters
    [ 'data', 'type', 'innerFacet', 'outerFacet', 'countBy'].forEach(function (rp) {
      if (!params.hasOwnProperty(rp)) {
        throw new Error('Missing required parameter: ' + rp);
      }
    });
    if (!params.data) {
      return;
    }
    var countBy = params.countBy,
      innerFacet = params.innerFacet,
      outerFacet = params.outerFacet,
      type = params.type,
      data = params.data;

    // Creates outer ring
    function buildOuterRing(hit) {
      var inner = hit[innerFacet],
        name = hit[outerFacet],
        count = hit[countBy] ? hit[countBy] : 0,
        inArray = inner.indexOf(iName) !== -1,
        inValue = inner === iName;

      if (inArray || inValue) {
        outerHits.push({
          name: name,
          y: count,
          type: type,
          facet: outerFacet,
          color: _this.projectColours[name]
        });
      }
    }

    // Gets the total counts for the inner ring
    function sumInnerPie(hit) {
      var name, count;

      name = hit[innerFacet];
      count = hit[countBy];

      if (!name) {
        name = 'No Data';
      }

      if (!innerPie.hasOwnProperty(name)) {
        innerPie[name] = 0;
      }
      innerPie[name] += count;
    }

    data.forEach(sumInnerPie);

    for (var iName in innerPie) {
      if (innerPie.hasOwnProperty(iName)) {
        innerHits.push({
          name: iName,
          y: innerPie[iName],
          type: type,
          facet: innerFacet,
          color: _this.primarySiteColours[iName]
        });
        data.forEach(buildOuterRing);
      }
    }

    return {
      inner: innerHits,
      outer: outerHits
    };
  };

  this.pie = function (params) {
    var filters, r = [], term, terms;

    // Check for required parameters
    [ 'type', 'facet', 'facets'].forEach(function (rp) {
      if (!params.hasOwnProperty(rp)) {
        throw new Error('Missing required parameter: ' + rp);
      }
    });

    filters = LocationService.filters();

    if (params.facets && params.facets[params.facet].hasOwnProperty('terms')) {
      terms = params.facets[params.facet].terms;
    } else {
      return r;
    }

    terms.forEach(function (item) {
      term = {
        name: item.term,
        y: item.count,
        type: params.type,
        facet: params.facet
      };

      if (term.facet === 'primarySite') {
        term.color = _this.primarySiteColours[term.name];
      } else if (term.facet === 'projectId') {
        term.color = _this.projectColours[term.name];
      }

      // Only shows active terms if facet active
      // has filter type - else include
      if (filters.hasOwnProperty(params.type)) {
        // and facet - else include
        if (filters[params.type].hasOwnProperty(params.facet)) {
          // and active - else don't include
          if (filters[params.type][params.facet].is.indexOf(item.term) !== -1) {
            r.push(term);
          }
        } else {
          r.push(term);
        }
      } else {
        r.push(term);
      }
    });

    return r;
  };

  this.bar = function (params) {
    var r, xAxis, data;

    // Check for required parameters
    [ 'hits', 'xAxis', 'yValue'].forEach(function (rp) {
      if (!params.hasOwnProperty(rp)) {
        throw new Error('Missing required parameter: ' + rp);
      }
    });

    if (!params.hits) {
      return {};
    }

    xAxis = [];
    r = [];

    params.hits.forEach(function (hit) {
      data = {};

      xAxis.push(hit[params.xAxis]);

      data.y = hit[params.yValue];

      // Additional options
      if (params.options) {
        if (params.options.linkBase) {
          data.link = params.options.linkBase + hit.id;
        }
      }

      if (data.y > 0) {
        r.push(data);
      }
    });

    return {
      x: xAxis,
      s: r,
      hasData: r.length > 9
    };
  };

  this.stacked = function (params) {
    // Check for required parameters
    [ 'genes'].forEach(function (rp) {
      if (!params.hasOwnProperty(rp)) {
        throw new Error('Missing required parameter: ' + rp);
      }
    });

    if (!params.genes) {
      return {};
    }

    var r, data = [], xAxis = [], genes = params.genes, totalDonors = 0, otherCount = 0;

    function sort(a, b) {
      return a.cout - b.count;
    }

    function sortGenes(a, b) {
      return b.affectedDonorCountFiltered - a.affectedDonorCountFiltered;
    }

    function add(project) {
      if (project.count > (totalDonors * 0.03)) {
        data.push({name: project.term, data: [
          {
            x: i,
            y: project.count,
            color: _this.projectColours[project.id],
            gene_id: gene.id
          }
        ]});
      } else {
        otherCount += project.count? project.count: 0;
      }
    }

    function sum(project) {
      totalDonors += project.count? project.count: 0;
    }

    genes.sort(sortGenes);

    for (var i = 0; i < genes.length; ++i) {
      var gene = genes[i];
      xAxis.push(gene.symbol);
      // gene.projects.sort(sort).forEach(sum);
      gene.uiFIProjects.sort(sort).forEach(sum);

      gene.uiFIProjects.forEach(add);
      if (otherCount) {
        data.unshift({name: 'Others', data: [
          {
            x: i,
            y: otherCount,
            color: '#FFD1DC',
            gene_id: gene.id
          }
        ]});
      }
      otherCount = 0;
      totalDonors = 0;
    }


    r = {
      x: xAxis,
      s: data,
      hasData: data.length
    };

    return r;
  };
});
