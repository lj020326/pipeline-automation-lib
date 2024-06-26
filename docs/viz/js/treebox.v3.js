
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
//       _mode           = "line",     /* Choose the values "line" or "diagonal" */
       _mode           = "diagonal",     /* Choose the values "line" or "diagonal" */
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

    _fixedDepth = 80;

    $(id).html("");   // Reset
//    var _width  = $(id).innerWidth()  - _margin.left - _margin.right,
//          _height = $(id).innerHeight() - _margin.top  - _margin.bottom;

    // declares a tree layout and assigns the size
//    var tree = d3.layout.tree().size([height, width]);
    _tree = d3.layout.tree().nodeSize([_rectW + _rectSpacing, _rectH + _rectSpacing]);

    //  assigns the data to a hierarchy using parent-child relationships
//    _root = d3.hierarchy(data);
    _root = data;

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
      // Compute the new tree layout.
      _nodes = _tree.nodes(_root).reverse();
      var links = _tree.links(_nodes);

      // Normalize for fixed-depth.
      _nodes.forEach(function (d) {
         d.y = d.depth * _fixedDepth;
      });

      // Update the nodes
      var node = _svg.selectAll("g.node")
          .data(_nodes, function (d) {
          return d.id || (d.id = ++_counter);
      });

      // Enter any new nodes at the parent's previous position.
      var nodeEnter = node.enter().append("g")
          .attr("class", "node")
          .attr("transform", function (d) {
          return "translate(" + source.x0 + "," + source.y0 + ")";
      })
      .on("click", nodeclick);

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
                         return d.desc;
               });

      // Transition nodes to their new position.
      var nodeUpdate = node.transition()
                           .duration(_duration)
                           .attr("transform", function (d) {
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

      // Update the links
      var link = _svg.selectAll("path.link")
                    .data(links, function (d) {
                          return d.target.id;
                    });

      if (_mode === "line") {
         // Enter any new links at the parent's previous position.
         link.enter().append("path" , "g")
             .attr("class", "link")
             .attr("d", function(d) {
                           var u_line = (function (d) {
                              var u_linedata = [{"x": d.source.x0 + parseInt(_rectW / 2), "y": d.source.y0 + _rectH + 2 },
                                                {"x": d.source.x0 + parseInt(_rectW / 2), "y": d.source.y0 + _rectH + 2 },
                                                {"x": d.source.x0 + parseInt(_rectW / 2), "y": d.source.y0 + _rectH + 2 },
                                                {"x": d.source.x0 + parseInt(_rectW / 2), "y": d.source.y0 + _rectH + 2 }];

                              return u_linedata;
                           })(d);

                           return _lineFunction(u_line);
                        });

         // Transition links to their new position.
         link.transition()
            .duration(_duration)
            .attr("d", function(d) {
                        var u_line = (function (d) {
                           var u_linedata = [{"x": d.source.x + parseInt(_rectW / 2), "y": d.source.y + _rectH },
                                             {"x": d.source.x + parseInt(_rectW / 2), "y": d.target.y - _margin.top / 2 },
                                             {"x": d.target.x + parseInt(_rectW / 2), "y": d.target.y - _margin.top / 2 },
                                             {"x": d.target.x + parseInt(_rectW / 2), "y": d.target.y }];

                           return u_linedata;
                        })(d);

                        return _lineFunction(u_line);
                     });

         // Transition exiting nodes to the parent's new position.
         link.exit().transition()
             .duration(_duration)
             .attr("d", function(d) {
                             /* This is needed to draw the lines right back to the caller */
                             var u_line = (function (d) {
                                var u_linedata = [{"x": _callerNode.x + parseInt(_rectW / 2), "y": _callerNode.y + _rectH + 2 },
                                                  {"x": _callerNode.x + parseInt(_rectW / 2), "y": _callerNode.y + _rectH + 2 },
                                                  {"x": _callerNode.x + parseInt(_rectW / 2), "y": _callerNode.y + _rectH + 2 },
                                                  {"x": _callerNode.x + parseInt(_rectW / 2), "y": _callerNode.y + _rectH + 2 }];

                                return u_linedata;
                             })(d);

                             return _lineFunction(u_line);
                        }).each("end", function() { _callerNode = null; /* After transition clear the caller node variable */ });
      } else if (_mode === "diagonal") {
         // Enter any new links at the parent's previous position.
         link.enter().insert("path" , "g")
             .attr("class", "link")
             .attr("x", _rectW / 2)
             .attr("y", _rectH / 2)
             .attr("d", function (d) {
                var o = {
                   x: source.x0,
                   y: source.y0
                };
                return _diagonal({
                      source: o,
                      target: o
                });
             });

         // Transition links to their new position.
         link.transition()
             .duration(_duration)
             .attr("d", _diagonal);

         // Transition exiting nodes to the parent's new position.
         link.exit().transition()
             .duration(_duration)
             .attr("d", function (d) {
                 var o = {
                     x: source.x,
                     y: source.y
                 };
                 return _diagonal({
                     source: o,
                     target: o
                 });
             })
             .remove();
      }

      // Stash the old positions for transition.
      _nodes.forEach(function (d) {
          d.x0 = d.x;
          d.y0 = d.y;
      });
   }

   // Toggle children on click.
   function nodeclick(d) {
      if (!d.children && !d._children && d.hasChild) {
         // If there are no childs --> Try to load child nodes
         _loadFunction(d, function(childs) {
            var response = {id: d.id,
                            desc: d.desc,
                            children: childs.result};

            response.children.forEach(function(child){
               if (!_tree.nodes(d)[0]._children){
                   _tree.nodes(d)[0]._children = [];
               }

               child.x  = d.x;
               child.y  = d.y;
               child.x0 = d.x0;
               child.y0 = d.y0;
               _tree.nodes(d)[0]._children.push(child);
            });

            if (d.children) {
               _callerNode = d;
               _callerMode = 0;     // Collapse
               d._children = d.children;
               d.children = null;
            } else {
               _callerNode = null;
               _callerMode = 1;     // Expand
               d.children = d._children;
               d._children = null;
            }

            update(d);
         });
      } else {
         if (d.children) {
            _callerNode = d;
             _callerMode = 0;     // Collapse
             d._children = d.children;
             d.children = null;
         } else {
            _callerNode = d;
            _callerMode = 1;     // Expand
             d.children = d._children;
             d._children = null;
         }

         update(d);
      }
   }

   //Redraw for zoom
   function redraw() {
        _svg.attr("transform", "translate(" + d3.event.translate + ")" +
            " scale(" + d3.event.scale.toFixed(1) + ")");
   }

};

