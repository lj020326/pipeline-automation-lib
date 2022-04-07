
// ref: https://gist.github.com/mbostock/1093025
function createCollapsibleIndentedTreeChart(treeData, chartDivName, in_width = 960, in_height = 500) {
    console.log("treeData=" + treeData)

    // ************** Generate the tree diagram	 *****************
//    var margin = {top: 20, right: 120, bottom: 20, left: 120},
//        width = 960 - margin.right - margin.left,
//        height = 500 - margin.top - margin.bottom;

    var margin = {top: 20, right: 120, bottom: 20, left: 120},
        width = in_width - margin.right - margin.left,
        barHeight = 20,
        barWidth = (width - margin.left - margin.right) * 0.8;

    var i = 0,
        duration = 400,
        root;

    var diagonal = d3.linkHorizontal()
        .x(function(d) { return d.y; })
        .y(function(d) { return d.x; });

    var svg = d3.select("body").select(chartDivName).append("svg")
        .attr("width", width + margin.right + margin.left)
        .append("g")
        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

    //root = treeData[0];
    root = d3.hierarchy(treeData);
    root.x0 = 0;
    root.y0 = 0;

    update(root);

//    d3.select(self.frameElement).style("height", "500px");

    function update(source) {

      // Compute the flattened node list.
      var nodes = root.descendants();

      var height = Math.max(500, nodes.length * barHeight + margin.top + margin.bottom);

      d3.select("svg").transition()
          .duration(duration)
          .attr("height", height);

      d3.select(self.frameElement).transition()
          .duration(duration)
          .style("height", height + "px");

      // Compute the "layout". TODO https://github.com/d3/d3-hierarchy/issues/67
      var index = -1;
      root.eachBefore(function(n) {
        n.x = ++index * barHeight;
        n.y = n.depth * 20;
      });

      // Update the nodes…
      var node = svg.selectAll(".node")
        .data(nodes, function(d) { return d.id || (d.id = ++i); });

      var nodeEnter = node.enter().append("g")
          .attr("class", "node")
          .attr("transform", function(d) { return "translate(" + source.y0 + "," + source.x0 + ")"; })
          .style("opacity", 0);

      // Enter any new nodes at the parent's previous position.
      nodeEnter.append("rect")
          .attr("y", -barHeight / 2)
          .attr("height", barHeight)
          .attr("width", barWidth)
          .style("fill", color)
          .on("click", click);

      nodeEnter.append("text")
          .attr("dy", 3.5)
          .attr("dx", 5.5)
          .text(function(d) { return d.data.name; });

      // Transition nodes to their new position.
      nodeEnter.transition()
          .duration(duration)
          .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; })
          .style("opacity", 1);

      node.transition()
          .duration(duration)
          .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; })
          .style("opacity", 1)
        .select("rect")
          .style("fill", color);

      // Transition exiting nodes to the parent's new position.
      node.exit().transition()
          .duration(duration)
          .attr("transform", function(d) { return "translate(" + source.y + "," + source.x + ")"; })
          .style("opacity", 0)
          .remove();

      // Update the links…
      var link = svg.selectAll(".link")
        .data(root.links(), function(d) { return d.target.id; });

      // Enter any new links at the parent's previous position.
      link.enter().insert("path", "g")
          .attr("class", "link")
          .attr("d", function(d) {
            var o = {x: source.x0, y: source.y0};
            return diagonal({source: o, target: o});
          })
        .transition()
          .duration(duration)
          .attr("d", diagonal);

      // Transition links to their new position.
      link.transition()
          .duration(duration)
          .attr("d", diagonal);

      // Transition exiting nodes to the parent's new position.
      link.exit().transition()
          .duration(duration)
          .attr("d", function(d) {
            var o = {x: source.x, y: source.y};
            return diagonal({source: o, target: o});
          })
          .remove();

      // Stash the old positions for transition.
      root.each(function(d) {
        d.x0 = d.x;
        d.y0 = d.y;
      });
    }

    // Toggle children on click.
    function click(d) {
      if (d.children) {
        d._children = d.children;
        d.children = null;
      } else {
        d.children = d._children;
        d._children = null;
      }
      update(d);
    }

    function color(d) {
      return d._children ? "#3182bd" : d.children ? "#c6dbef" : "#fd8d3c";
    }

}

// ref: https://gist.github.com/d3noob/8323795
// ref: https://bl.ocks.org/d3noob/43a860bc0024792f8803bba8ca0d5ecd
function createTreeChart(treeData, chartDivName, in_width = 960, in_height = 500) {
    console.log("treeData=" + treeData)

    // ************** Generate the tree diagram	 *****************
//    var margin = {top: 20, right: 120, bottom: 20, left: 120},
//        width = 960 - margin.right - margin.left,
//        height = 500 - margin.top - margin.bottom;

    var margin = {top: 20, right: 120, bottom: 20, left: 120},
        width = in_width - margin.right - margin.left,
        height = in_height - margin.top - margin.bottom;

    var i = 0,
        duration = 750,
        root;

    // declares a tree layout and assigns the size
//    var tree = d3.layout.tree().size([height, width]);
    var tree = d3.tree().size([height, width]);

//    var diagonal = d3.svg.diagonal()
//        .projection(function(d) { return [d.y, d.x]; });

    //var svg = d3.select("body").append("svg")
    var svg = d3.select("body").select(chartDivName).append("svg")
        .attr("width", width + margin.right + margin.left)
        .attr("height", height + margin.top + margin.bottom)
        .append("g")
        .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

    //root = treeData[0];
//    root = treeData;
    root = d3.hierarchy(treeData);
//    root = d3.hierarchy(treeData, function(d) { return d.children; });

    root.x0 = height / 2;
    root.y0 = 0;

    update(root);

//    d3.select(self.frameElement).style("height", "500px");

    function update(source) {

      // Assigns the x and y position for the nodes
      var treeData = tree(root);

      // Compute the new tree layout.
      var nodes = treeData.descendants(),
          links = treeData.descendants().slice(1);

      // Normalize for fixed-depth.
      nodes.forEach(function(d){ d.y = d.depth * 180});

      // ****************** Nodes section ***************************

      // Update the nodes...
      var node = svg.selectAll('g.node')
          .data(nodes, function(d) {return d.id || (d.id = ++i); });

      // Enter any new modes at the parent's previous position.
      var nodeEnter = node.enter().append('g')
          .attr('class', 'node')
          .attr("transform", function(d) {
            return "translate(" + source.y0 + "," + source.x0 + ")";
        })
        .on('click', click);

      // Add Circle for the nodes
      nodeEnter.append('circle')
          .attr('class', 'node')
          .attr('r', 1e-6)
          .style("fill", function(d) {
              return d._children ? "lightsteelblue" : "#fff";
          });

      // Add labels for the nodes
      nodeEnter.append('text')
          .attr("dy", ".35em")
          .attr("x", function(d) {
              return d.children || d._children ? -13 : 13;
          })
          .attr("text-anchor", function(d) {
              return d.children || d._children ? "end" : "start";
          })
          .text(function(d) { return d.data.name; });

      // UPDATE
      var nodeUpdate = nodeEnter.merge(node);

      // Transition to the proper position for the node
      nodeUpdate.transition()
        .duration(duration)
        .attr("transform", function(d) {
            return "translate(" + d.y + "," + d.x + ")";
         });

      // Update the node attributes and style
      nodeUpdate.select('circle.node')
        .attr('r', 10)
        .style("fill", function(d) {
            return d._children ? "lightsteelblue" : "#fff";
        })
        .attr('cursor', 'pointer');


      // Remove any exiting nodes
      var nodeExit = node.exit().transition()
          .duration(duration)
          .attr("transform", function(d) {
              return "translate(" + source.y + "," + source.x + ")";
          })
          .remove();

      // On exit reduce the node circles size to 0
      nodeExit.select('circle')
        .attr('r', 1e-6);

      // On exit reduce the opacity of text labels
      nodeExit.select('text')
        .style('fill-opacity', 1e-6);

      // ****************** links section ***************************

      // Update the links...
      var link = svg.selectAll('path.link')
          .data(links, function(d) { return d.id; });

      // Enter any new links at the parent's previous position.
      var linkEnter = link.enter().insert('path', "g")
          .attr("class", "link")
          .attr('d', function(d){
            var o = {x: source.x0, y: source.y0}
            return diagonal(o, o)
          });

      // UPDATE
      var linkUpdate = linkEnter.merge(link);

      // Transition back to the parent element position
      linkUpdate.transition()
          .duration(duration)
          .attr('d', function(d){ return diagonal(d, d.parent) });

      // Remove any exiting links
      var linkExit = link.exit().transition()
          .duration(duration)
          .attr('d', function(d) {
            var o = {x: source.x, y: source.y}
            return diagonal(o, o)
          })
          .remove();

      // Store the old positions for transition.
      nodes.forEach(function(d){
        d.x0 = d.x;
        d.y0 = d.y;
      });

      // Creates a curved (diagonal) path from parent to the child nodes
      function diagonal(s, d) {

        path = `M ${s.y} ${s.x}
                C ${(s.y + d.y) / 2} ${s.x},
                  ${(s.y + d.y) / 2} ${d.x},
                  ${d.y} ${d.x}`

        return path
      }

      // Toggle children on click.
      function click(d) {
        if (d.children) {
            d._children = d.children;
            d.children = null;
          } else {
            d.children = d._children;
            d._children = null;
          }
        update(d);
      }
    }

};


// ref: https://codepen.io/brendandougan/pen/PpEzRp
function createIndentedCollapsibleTree(data, divName, in_width = 960, in_height = 500) {

    var margin = {top: 20, right: 120, bottom: 20, left: 120},
        width = in_width - margin.right - margin.left,
        height = in_height - margin.top - margin.bottom,
        barHeight = 20,
        barWidth = (width - margin.left - margin.right) * 0.8;

    var i = 0,
        duration = 750;

//    tree = d3.tree().size([width, height]);
    var tree = d3.tree().nodeSize([0, 30]);

    var root = tree(d3.hierarchy(data));
    root.each((d)=> {
      d.name = d.id; //transferring name to a name variable
      d.id = i; //Assigning numerical Ids
      i++;
    });
    root.x0 = root.x;
    root.y0 = root.y;

    var svg = d3.select(divName).append("svg")
      .attr('width', width + margin.right + margin.left)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

    update(root);

    function update(source) {

        // Compute the new tree layout.
        let nodes = tree(root)
        let nodesSort = [];
        nodes.eachBefore(function (n) {
          nodesSort.push(n);
        });

        var height = Math.max(500, nodesSort.length * barHeight + margin.top + margin.bottom);
        let links = nodesSort.slice(1);
        // Compute the "layout".
        nodesSort.forEach ((n,i)=> {
          n.x = i *barHeight;
        });

        d3.select("svg").transition()
          .duration(duration)
          .attr("height", height);

        // Update the nodes…
        var node = svg.selectAll("g.node")
              .data(nodesSort, function(d) { return d.id || (d.id = ++i); });
//        let node = svg.selectAll('g.node')
//            .data(nodesSort, function (d) {
//              return d.id || (d.id = ++i);
//            });

        // Enter any new nodes at the parent's previous position.
        var nodeEnter = node.enter().append('g')
            .attr('class', 'node')
              .attr("y", -barHeight / 2)
              .attr("height", barHeight)
              .attr("width", barWidth)
            .attr('transform', function(d) {
              return 'translate(' + source.y0 + ',' + source.x0 + ')';
            })
            .on('click', click);

        nodeEnter.append("circle")
          .attr("r", 1e-6)
          .style("fill", function(d) { return d._children ? "lightsteelblue" : "#fff"; });

        nodeEnter.append("text")
          .attr('x', function (d) { return d.children || d._children ? 10 : 10; })
          .attr('dy', '.35em')
          .attr('text-anchor', function(d) { return d.children || d._children ? 'start' : 'start'; })
          .text(function(d) {
          if (d.data.name.length > 50) {
            return d.data.name.substring(0, 20) + '...';
          } else {
            return d.data.name;
          }
        })
          .style('fill-opacity', 1e-6);

        nodeEnter.append('svg:title').text(function (d) {
          return d.data.name;
        });

        // Transition nodes to their new position.
        let nodeUpdate = node.merge(nodeEnter)
          .transition()
          .duration(duration);

        nodeUpdate.attr('transform', function (d) { return 'translate(' + d.y + ',' + d.x + ')'; });

        nodeUpdate.select('circle')
          .attr('r', 4.5)
          .style('fill', function (d) {
          return d._children ? 'lightsteelblue' : '#fff';
        });

        nodeUpdate.select('text')
          .style('fill-opacity', 1);

        // Transition exiting nodes to the parent's new position (and remove the nodes)
        var nodeExit = node.exit().transition()
            .duration(duration);

        nodeExit.attr('transform', function (d) {
              return 'translate(' + source.y + ',' + source.x + ')';
            })
            .remove();

        nodeExit.select('circle')
          .attr('r', 1e-6);

        nodeExit.select('text')
          .style('fill-opacity', 1e-6);

        // Update the links…
        var link = svg.selectAll('path.link')
            .data(links, function (d) {
              // return d.target.id;
              var id = d.id + '->' + d.parent.id;
              return id;
            });

        // Enter any new links at the parent's previous position.
        let linkEnter = link.enter().insert('path', 'g')
            .attr('class', 'link')
            .attr('d', (d) => {
              var o = {x: source.x0, y: source.y0, parent: {x: source.x0, y: source.y0}};
              return connector(o);
            });

        // Transition links to their new position.
        link.merge(linkEnter).transition()
          .duration(duration)
          .attr('d', connector);


        // // Transition exiting nodes to the parent's new position.
        link.exit().transition()
          .duration(duration)
          .attr('d', (d) => {
          var o = {x: source.x, y: source.y, parent: {x: source.x, y: source.y}};
          return connector(o);
        })
          .remove();

        // Stash the old positions for transition.
        nodesSort.forEach(function (d) {
          d.x0 = d.x;
          d.y0 = d.y;
        });

        function connector(d) {
            //curved
            /*return "M" + d.y + "," + d.x +
              "C" + (d.y + d.parent.y) / 2 + "," + d.x +
              " " + (d.y + d.parent.y) / 2 + "," + d.parent.x +
              " " + d.parent.y + "," + d.parent.x;*/
            //straight
            return "M" + d.parent.y + "," + d.parent.x
              + "V" + d.x + "H" + d.y;
        }

        function collapse(d) {
            if (d.children) {
              d._children = d.children;
              d._children.forEach(collapse);
              d.children = null;
            }
        };

        function click(d) {
            if (d.children) {
              d._children = d.children;
              d.children = null;
            } else {
              d.children = d._children;
              d._children = null;
            }
            //    update(d);
            update(d);
        };

    }
};


