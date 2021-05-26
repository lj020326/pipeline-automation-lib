
// ref: https://bl.ocks.org/d3noob/b024fcce8b4b9264011a1c3e7c7d70dc
function createTreeBox(data, id, in_width = 1400, in_height = 500) {

    // set the dimensions and margins of the diagram
//    var margin = {top: 20, right: 120, bottom: 20, left: 120},
    var _margin = {top: 20, right: 20, bottom: 20, left: 20},
        _width = in_width - _margin.left - _margin.right,
        _height = in_height - _margin.top - _margin.bottom,
       _root           = {},
       _nodes          = [],
       _counter        = 0,
       _svgroot        = null,
       _svg            = null,
       _tree           = null,
       _diagonal       = null,
       _lineFunction   = null,
       _loadFunction   = null,
       /* Configuration */
       _duration       = 750,        /* Duration of the animations */
       _rectW          = 150,        /* Width of the rectangle */
       _rectH          = 50,         /* Height of the rectangle */
       _rectSpacing    = 20          /* Spacing between the rectangles */
       _fixedDepth     = 80,         /* Height of the line for child nodes */
       _mode           = "line",     /* Choose the values "line" or "diagonal" */
       _callerNode     = null,
       _callerMode     = 0,
   defLinearGradient = function(id, x1, y1, x2, y2, stopsdata) {
      var gradient = _svgroot.append("svg:defs")
                     .append("svg:linearGradient")
                       .attr("id", id)
                       .attr("x1", x1)
                       .attr("y1", y1)
                       .attr("x2", x2)
                       .attr("y2", y2)
                       .attr("spreadMethod", "pad");

      $.each(stopsdata, function(index, value) {
         gradient.append("svg:stop")
                 .attr("offset", value.offset)
                 .attr("stop-color", value.color)
                 .attr("stop-opacity", value.opacity);
      });

   },
   defBoxShadow = function(id) {
      var filter = _svgroot.append("svg:defs")
                      .append("svg:filter")
                      .attr("id", id).attr("height", "150%").attr("width", "150%");

      filter.append("svg:feOffset")
            .attr("dx", "2").attr("dy", "2").attr("result", "offOut");  // how much to offset
      filter.append("svg:feGaussianBlur")
            .attr("in", "offOut").attr("result", "blurOut").attr("stdDeviation", "2");     // stdDeviation is how much to blur
      filter.append("svg:feBlend")
            .attr("in", "SourceGraphic").attr("in2", "blurOut").attr("mode", "normal");
   },
   collapse = function(d) {
       if (d.children) {
           d._children = d.children;
           d._children.forEach(collapse);
           d.children = null;
       }
   };

    var i = 0,
        duration = 750;

    _fixedDepth = 80;

    $(id).html("");   // Reset
//    var _width  = $(id).innerWidth()  - _margin.left - _margin.right,
//          _height = $(id).innerHeight() - _margin.top  - _margin.bottom;

    // declares a tree layout and assigns the size
//    var tree = d3.layout.tree().size([height, width]);
//    var tree = d3.tree().size([height, width]);
    _tree = d3.tree().nodeSize([_rectW + _rectSpacing, _rectH + _rectSpacing]);

    //  assigns the data to a hierarchy using parent-child relationships
    _root = d3.hierarchy(data);

//    /* Basic Setup for the diagonal function. _mode = "diagonal" */
//    _diagonal = d3.linkRadial()
//      .projection(function (d) {
//      return [d.x + _rectW / 2, d.y + _rectH / 2];
//    });

//    /* Basic setup for the line function. _mode = "line" */
//    _lineFunction = d3.linkVertical()
//                       .x(function(d) { return d.x; })
//                       .y(function(d) { return d.y; })
//                       .interpolate("linear");

    var u_childwidth = parseInt((_root.children.length * _rectW) / 2);

    // append the svg obgect to the body of the page
    // appends a 'group' element to 'svg'
    // moves the 'group' element to the top left margin
    _svgroot = d3.select(id).append("svg")
          .attr("width", _width + _margin.left + _margin.right)
          .attr("height", _height + _margin.top + _margin.bottom)
          .append("g")
          .attr("transform","translate(" + _margin.left + "," + _margin.top + ")");

//    _svgroot = d3.select(id).append("svg").attr("width", _width).attr("height", _height)
//               .call(zm = d3.behavior.zoom().scaleExtent([0.15,3]).on("zoom", redraw));

    _svg = _svgroot.append("g")
                 .attr("transform", "translate(" + parseInt(u_childwidth + ((_width - u_childwidth * 2) / 2) - _margin.left / 2) + "," + 20 + ")");

    var u_stops = [{offset: "0%", color: "#03A9F4", opacity: 1}, {offset: "100%", color: "#0288D1", opacity: 1}];
    defLinearGradient("gradientnochilds", "0%", "0%", "0%" ,"100%", u_stops);
    var u_stops = [{offset: "0%", color: "#8BC34A", opacity: 1}, {offset: "100%", color: "#689F38", opacity: 1}];
    defLinearGradient("gradientchilds", "0%", "0%", "0%" ,"100%", u_stops);

    defBoxShadow("boxShadow");

    //necessary so that zoom knows where to zoom and unzoom from
//    zm.translate([parseInt(u_childwidth + ((width - u_childwidth * 2) / 2) - _margin.left / 2), 20]);

    _root.x0 = 0;
    _root.y0 = _height / 2;

    update(_root);

//    d3.select(self.frameElement).style("height", "500px");
    d3.select(id).style("height", _height + _margin.top + _margin.bottom);

    function update(source) {

        // maps the node data to the tree layout
        var treeData = _tree(_root);

        // Compute the new tree layout.
        var nodes = treeData.descendants(),
          links = treeData.descendants().slice(1);

        // Normalize for fixed-depth.
        nodes.forEach(function(d){ d.y = d.depth * 100});

        // ****************** Nodes section ***************************

        // Update the nodes...
        var node = _svg.selectAll('g.node')
          .data(nodes, function(d) {return d.id || (d.id = ++i); });

        // Enter any new modes at the parent's previous position.
        var nodeEnter = node.enter().append('g')
              .attr('class', 'node')
              .attr("transform", function(d) {
                return "translate(" + source.x0 + "," + source.y0 + ")";
            })
            .on('click', click);

//        // Add Circle for the nodes
//        nodeEnter.append('circle')
//          .attr('class', 'node')
//          .attr('r', 1e-6)
//          .style("fill", function(d) {
//              return d._children ? "lightsteelblue" : "#fff";
//          });
//
//        // Add labels for the nodes
//        nodeEnter.append('text')
//          .attr("dy", ".35em")
//          .attr("y", function(d) {
//              return d.children || d._children ? -13 : 13;
//          })
//          .attr("text-anchor", function(d) {
//              return d.children || d._children ? "end" : "start";
//          })
//          .text(function(d) { return d.data.name; });

        nodeEnter.append("rect")
               .attr("width", _rectW)
               .attr("height", _rectH)
               .attr("fill", "#898989")
               .attr("filter", "url(#boxShadow)");

        nodeEnter.append("rect")
               .attr("width", _rectW)
               .attr("height", _rectH)
               .attr("id", function(d) {
                   return d.id;
               })
               .attr("fill", function (d) { return (d.children || d._children || d.hasChild) ? "url(#gradientchilds)" : "url(#gradientnochilds)"; })
               .style("cursor", function (d) { return (d.children || d._children || d.hasChild) ? "pointer" : "default"; })
               .attr("class", "box");

        nodeEnter.append("text")
               .attr("x", _rectW / 2)
               .attr("y", _rectH / 2)
               .attr("dy", ".35em")
               .attr("text-anchor", "middle")
               .style("cursor", function (d) { return (d.children || d._children || d.hasChild) ? "pointer" : "default"; })
               .text(function (d) {
                         return d.data.name;
               });

        // UPDATE
        var nodeUpdate = nodeEnter.merge(node);

        // Transition to the proper position for the node
        nodeUpdate.transition()
        .duration(duration)
        .attr("transform", function(d) {
            return "translate(" + d.x + "," + d.y + ")";
         });

        nodeUpdate.select("rect.box")
                .attr("fill", function (d) {
                    return (d.children || d._children || d.hasChild) ? "url(#gradientchilds)" : "url(#gradientnochilds)";
                });

        // Transition exiting nodes to the parent's new position.
        var nodeExit = node.exit().transition()
                         .duration(_duration)
                         .attr("transform", function (d) {
                             return "translate(" + source.x + "," + source.y + ")";
                         })
                         .remove();

        // ****************** links section ***************************

        // Update the links...
        var link = _svg.selectAll('path.link')
          .data(links, function(d) { return d.id; });

        // Enter any new links at the parent's previous position.
        var linkEnter = link.enter().insert('path', "g")
            .attr("class", "link")
            .attr("x", _rectW / 2)
            .attr("y", _rectH / 2)
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
            path = `M ${s.x + _rectW / 2 } ${s.y + _rectH / 2 }
                    C ${s.x + _rectW / 2 } ${(s.y + d.y + _rectH ) / 2},
                      ${d.x + _rectW / 2 } ${(s.y + d.y + _rectH ) / 2},
                      ${d.x + _rectW / 2 } ${d.y + _rectH / 2 }`

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

