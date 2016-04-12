window.oncogrid = function(donors, genes, observations, element) {
  this.donors = donors;
  this.genes = genes;
  this.observations = observations;
  this.element = element;

  this.heatMap = false; // Heatmap view turned off by default.

  this.colorMap = {
    1: '#ff825a',
    2: '#57dba4',
    3: '#af57db',
  }

  /**
   * Returns 1 if at least one mutation, 0 otherwise. 
   */
  this.mutationScore = function(donor, gene) {
    var _self = this;

    for (var i in _self.observations) {
      var obs = _self.observations[i];
      if (obs.donorId === donor && obs.gene === gene) {
        return 1;
      }
    }

    return 0;
  };
  
  /**
   * Returns 1 if at least one mutation, 0 otherwise. 
   */
  this.altMutationScore = function(donor, gene) {
    var _self = this;
    
    var retVal = 0;

    for (var i in _self.observations) {
      var obs = _self.observations[i];
      if (obs.donorId === donor && obs.gene === gene) {
        retVal++;
      }
    }

    return retVal;
  };

  /**
   * Computes scores for donor sorting. 
   */
  this.computeScores = function() {
    var _self = this;

    for (var i in _self.donors) {
      var donor = _self.donors[i];
      donor.score = 0;
      for (var j in _self.genes) {
        var gene = _self.genes[j];
        donor.score += (mutationScore(donor.donorId, gene) * Math.pow(2, _self.genes.length + 1 - j));
      }
    }

  };

  /**
   * Comparator for scores
   */
  this.sortScore = function(a, b) {
    if (a.score < b.score) {
      return 1;
    } else if (a.score > b.score) {
      return -1;
    } else {
      return 0;
    }
  };

  /**
   * Sorts donors by score
   */
  this.sortByScores = function() {
    var _self = this;
    _self.donors.sort(sortScore);
  }

  /**
   * Helper for getting donor index position
   */
  this.getDonorIndex = function(donors, donorId) {
    for (var i in donors) {
      donor = donors[i];
      if (donor.donorId === donorId) {
        return i;
      }
    }

    return -1;
  }

  /**
   * Initializes and creates the main SVG with rows and columns. Does prelim sort on data
   */
  this.init = function() {
    var _self = this;

    _self.div = d3.select("body").append("div")
      .attr("class", "tooltip")
      .style("opacity", 0);

    _self.margin = { top: 50, right: 15, bottom: 15, left: 80 };
    _self.width = 720;
    _self.height = 300;


    _self.numDonors = _self.donors.length;
    _self.numGenes = _self.genes.length;

    _self.zero = d3.range(numDonors).map(function() { return 0; });
    _self.matrix = zero.map(function() { return d3.range(_self.numGenes).map(function() { return 0; }); });

    _self.cellWidth = width / _self.donors.length;
    _self.cellHeight = height / _self.genes.length;

    _self.x = d3.scale.ordinal()
      .domain(d3.range(_self.numDonors))
      .rangeBands([0, _self.width]);

    _self.y = d3.scale.ordinal()
      .domain(d3.range(_self.numGenes))
      .rangeBands([0, _self.height]);

    _self.svg = d3.select(_self.element).append("svg")
      .attr("width", width + margin.left + margin.right)
      .attr("height", height + margin.top + margin.bottom)
      .style("margin-left", margin.left + "px")
      .append("g")
      .attr("transform", "translate(" + _self.margin.left + "," + _self.margin.top + ")");

    svg.append("rect")
      .attr("class", "background")
      .attr("width", width)
      .attr("height", height);

    _self.row = svg.selectAll(".row")
      .data(_self.genes)
      .enter().append("g")
      .attr("class", "row")
      .attr("transform", function(d, i) { return "translate(0," + _self.y(i) + ")"; });

    row.append("line")
      .attr("x2", _self.width);


    _self.column = svg.selectAll(".column")
      .data(_self.donors)
      .enter().append("g")
      .attr("class", "column")
      .attr("donor", function(d, i) { return d.donorId; })
      .attr("transform", function(d, i) { return "translate(" + _self.x(i) + ")rotate(-90)"; });

    _self.column.append("line")
      .attr("x1", -width);

    _self.computeScores();
    _self.sortByScores();
  };

  /**
   * Only to be called the first time the OncoGrid is rendered. It creates the rects representing the
   * mutation occurrences. 
   */
  this.renderFirst = function() {
    var _self = this;

    _self.row.append("text")
      .attr("class", "gene-label label-text-font")
      .transition()
      .attr("x", -6)
      .attr("y", _self.cellHeight / 2)
      .attr("dy", ".32em")
      .attr("text-anchor", "end")
      .text(function(d, i) { 
        return _self.genes[i].symbol; 
      });

    _self.defineRowDragBehaviour();

    svg.selectAll("svg")
      .data(_self.observations).enter()
      .append("rect")
      .on('mouseover', function(d) {
        _self.div.transition()
          .duration(200)
          .style("opacity", .9);
        _self.div.html(d.donorId + "<br/>" + d.gene + "<br/>" + d.consequence)
          .style("left", (d3.event.pageX + 10) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      })
      .on('mouseout', function(d) {
        _self.div.transition()
          .duration(500)
          .style("opacity", 0);
      })
      .transition()
      .attr('class', function(d, i) { return 'sortable-rect ' +  d.donorId + '-cell ' + d.gene + '-cell'; })
      .attr('cons', function(d, i) { return d.consequence; })
      .attr('x', function(d, i) { return _self.x(_self.getDonorIndex(_self.donors, d.donorId)); })
      .attr('y', function(d) { return _self.getY(d); })
      .attr('width', _self.cellWidth)
      .attr('height', function(d) { return getHeight(d); })
      .attr('fill', function(d) { return _self.getColor(d); })
      .attr('opacity', function(d) { return _self.getOpacity(d); })
      .attr('stroke-width', 2);
  };


  /**
   * Defines the row drag behaviour for moving genes and binds it to the row elements. 
   */
  this.defineRowDragBehaviour = function() {
    var _self = this;

    var drag = d3.behavior.drag();
    drag.on("dragstart", function() {
      d3.event.sourceEvent.stopPropagation(); // silence other listeners
    });
    drag.on("drag", function(d, i) {
      var trans = d3.event.dy
      var dragged = genes.indexOf(d);
      var selection = d3.select(this);

      selection.attr('transform', function(d, k) {
        transform = d3.transform(d3.select(this).attr("transform"));
        return 'translate( 0, ' + (parseInt(transform.translate[1]) + trans) + ')';
      });

      var newY = d3.transform(d3.select(this).attr("transform")).translate[1];

      d3.selectAll('.row').each(function(f, j) {
        var curGeneIndex = genes.indexOf(f);
        if (trans > 0 && curGeneIndex > dragged) {
          var yCoord = d3.transform(d3.select(this).attr("transform")).translate[1];
          if (newY > yCoord) {
            curGene = genes[dragged];
            genes[dragged] = genes[curGeneIndex];
            genes[curGeneIndex] = curGene;
          }
        } else if (trans < 0 && curGeneIndex < dragged) {
          var yCoord = d3.transform(d3.select(this).attr("transform")).translate[1];
          if (newY < yCoord) {
            curGene = genes[dragged];
            genes[dragged] = genes[curGeneIndex];
            genes[curGeneIndex] = curGene;
          }
        }
      });

    });

    drag.on("dragend", function(d, i) {
      _self.computeScores();
      _self.sortByScores();
      _self.render();
    });

    var dragSelection = d3.selectAll(".row").call(drag);
    dragSelection.on("click", function() {
      if (d3.event.defaultPrevented) return;
    });

  };

  /**
   * Render function ensures presentation matches the data. Called after modifying data. 
   */
  this.render = function() {
    var _self = this;

    d3.selectAll(".row")
      .transition()
      .attr("transform", function(d, i) {
        return "translate( 0, " + _self.y(_self.genes.indexOf(d)) + ")";
      });

    d3.selectAll(".sortable-rect")
      .transition()
      .attr('y', function(d, i) {
        return _self.getY(d)
      })
      .attr('x', function(d, i) { return _self.x(_self.getDonorIndex(_self.donors, d.donorId)); });
  }

  /**
   * Function that determines the y position of a mutation within a cell
   */
  this.getY = function(d) {
    var _self = this;

    if (_self.heatMap === true) {
      return _self.y(_self.genes.indexOf(d.gene));
    }
    if (d.consequence == 1) {
      return _self.y(_self.genes.indexOf(d.gene));
    } else if (d.consequence == 2) {
      return _self.y(_self.genes.indexOf(d.gene)) + _self.cellHeight / 3;
    } else if (d.consequence == 3) {
      return _self.y(_self.genes.indexOf(d.gene)) + (_self.cellHeight / 3) * 2;
    } else {
      return _self.y(_self.genes.indexOf(d.gene));
    }
  }

  this.getColor = function(d) {
    var _self = this;

    if (_self.heatMap === true) {
      return '#f00';
    } else {
      return _self.colorMap[d.consequence];
    }
  };

  this.getOpacity = function(d) {
    var _self = this;

    if (_self.heatMap === true) {
      return 0.3;
    } else {
      return 1;
    }
  };

  this.getHeight = function(d) {
    var _self = this;

    if (_self.heatMap === true) {
      return cellHeight;
    } else {
      return cellHeight / 3;
    }
  };

  this.toggleHeatmap = function() {
    var _self = this;
    if (_self.heatMap === true) {
      _self.heatMap = false;
    } else {
      _self.heatMap = true;
    }

    d3.selectAll(".sortable-rect")
      .transition()
      .attr('y', function(d, i) {
        return getY(d);
      })
      .attr('height', function(d) { return getHeight(d); })
      .attr('fill', function(d) { return getColor(d); })
      .attr('opacity', function(d) { return getOpacity(d); })
  };

  this.removeDonors = function(func) {
    var _self = this;
    
    var removedList = [];
    
    // Remove donors from data
    for (var i = 0; i < _self.donors.length; i++) {
      var donor = _self.donors[i];
      if (func(donor)) {
        removedList.push(donor.donorId);
        d3.selectAll("." + donor.donorId+"-cell").remove();
        _self.donors.splice(i, 1);
        i--;
      }
    }
    
    for (var i = 0; i < _self.observations.length; i++) {
      var obs = _self.observations[i];
      if (_self.donors.indexOf(obs.donorId) >= 0) {
        _self.observations.splice(i,1);
        i--;
      }
    }

    _self.x = d3.scale.ordinal()
      .domain(d3.range(_self.donors.length))
      .rangeBands([0, _self.width]);
    _self.cellWidth = _self.width / _self.donors.length;

   _self.column.remove();
    
    _self.column = svg.selectAll(".column")
      .data(_self.donors)
      .enter().append("g")
      .attr("class", "column")
      .attr("donor", function(d, i) { return d.donorId; })
      .attr("transform", function(d, i) { return "translate(" + x(i) + ")rotate(-90)"; });
      
   _self.column.append("line")
      .attr("x1", -width)

    d3.selectAll(".sortable-rect")
      .transition()
      .attr('width', _self.cellWidth)
      .attr('y', function(d, i) {
        return _self.getY(d);
      })
      .attr('x', function(d, i) { return _self.x(_self.getDonorIndex(_self.donors, d.donorId)); });
  }
  
  this.sortDonors = function(func) {
    var _self = this;
    _self.donors.sort(func);
    _self.render();
  }

  return this;
};
