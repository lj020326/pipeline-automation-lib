
///*jshint esversion: 6 */
//(function () {
//  'use strict';
//}());


// ref: https://codepen.io/brendandougan/pen/PpEzRp
function createIndentedCollapsibleTree(data, divName, in_width = 960, in_height = 500) {

    var tree = d3.layout.tree;
//    var hierarchy = d3.hierarchy;
//    var hierarchy = d3-hierarchy;
    var select = d3.select;

    margin = {top: 20, right: 10, bottom: 20, left: 10};
    width = 1400 - margin.right - margin.left;
    height = 800 - margin.top - margin.bottom;
    barHeight = 20;
    barWidth = width *.8;
    i = 0;
    duration = 750;
//    tree = tree().size([width, height]);
    tree = tree().nodeSize([0, 30]);

//    var root = tree(hierarchy(data));
//    root.each((d)=> {
//      d.name = d.id; //transferring name to a name variable
//      d.id = i; //Assigning numerical Ids
//      i++;
//    });

//    var root = tree(data)
//    root.forEach((d)=> {
//      d.name = d.id; //transferring name to a name variable
//      d.id = i; //Assigning numerical Ids
//      i++;
//    });

    var root = data;

    root.x0 = root.x;
    root.y0 = root.y
//    svg = d3.select("body").select(chartDivName).append("svg")
//    svg = select('.hierarchy-container').append('svg')
    svg = select(divName).append('svg')
      .attr('width', width + margin.right + margin.left)
      .attr('height', height + margin.top + margin.bottom)
      .append('g')
      .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

    // root.children.forEach(collapse);
    update(root);

//  update = (source) => {
  function update(source) {

    width=800;

    // Compute the new tree layout.
    let nodes = tree(root)
//    let nodes = source

    nodes.forEach((d)=> {
      d.name = d.id; //transferring name to a name variable
      d.id = i; //Assigning numerical Ids
      i++;
    });

    let nodesSort = [];
//    nodes.eachBefore(function (n) {
//      nodesSort.push(n);
//    });
    nodes.forEach(function (n) {
      nodesSort.push(n);
    });
    height = Math.max(500, nodesSort.length * barHeight + margin.top + margin.bottom);
    let links = nodesSort.slice(1);
    // Compute the "layout".
    nodesSort.forEach ((n,i)=> {
      n.x = i *barHeight;
    });

    d3.select('svg').transition()
      .duration(duration)
      .attr("height", height);

    // Update the nodes…
    let node = svg.selectAll('g.node')
    .data(nodesSort, function (d) {
      return d.id || (d.id = ++i);
    });

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

    nodeEnter.append('circle')
      .attr('r', 1e-6)
      .style('fill', function(d) {
      return d._children ? 'lightsteelblue' : '#fff';
    });

    nodeEnter.append('text')
      .attr('x', function (d) {
      return d.children || d._children ? 10 : 10;
    })
      .attr('dy', '.35em')
      .attr('text-anchor', function(d) {
      return d.children || d._children ? 'start' : 'start';
    })
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

    nodeUpdate
        .attr('transform', function (d) {
        return 'translate(' + d.y + ',' + d.x + ')';
      });

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

    nodeExit
    .attr('transform', function (d) {
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
    }
         );

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

//  connector = function(d:any) {
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

//  collapse = (d) => {
    function collapse(d) {
        if (d.children) {
          d._children = d.children;
          d._children.forEach(collapse);
          d.children = null;
        }
    };

//  click = (d) => {
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


