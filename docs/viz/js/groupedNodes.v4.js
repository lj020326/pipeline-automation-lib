
// ref: https://ialab.it.monash.edu/webcola/examples/gridifiedSmallGroups.html
function createGridifiedGroupedGraph(cola, data, divName, width = 960, height = 500) {

    var color = d3.scaleOrdinal(d3.schemeCategory20);

    var cola = cola.d3adaptor(d3)
        .linkDistance(80)
        .avoidOverlaps(true)
        .handleDisconnected(false)
        .size([width, height]);

    var svg = d3.select(divName).append("svg")
        .attr("width", width)
        .attr("height", height);

    data.nodes.forEach(function (v) {
        v.width = v.height = 95;
    })
    data.groups.forEach(function (g) { g.padding = 0.01; });
    cola
        .nodes(data.nodes)
        .links(data.links)
        .groups(data.groups)
        .start(100, 0, 50, 50);

    var group = svg.selectAll(".group")
        .data(data.groups)
      .enter().append("rect")
        .attr("rx", 8).attr("ry", 8)
        .attr("class", "group")
        .style("fill", function (d, i) { return color(i); });

    var link = svg.selectAll(".link")
        .data(data.links)
      .enter().append("line")
        .attr("class", "link");

    var pad = 20;
    var node = svg.selectAll(".node")
        .data(data.nodes)
      .enter().append("rect")
        .attr("class", "node")
        .attr("width", function (d) { return d.width - 2 * pad; })
        .attr("height", function (d) { return d.height - 2 * pad; })
        .attr("rx", 5).attr("ry", 5)
        .style("fill", function (d) { return color(data.groups.length); })
        .call(cola.drag)
        .on('mouseup', function (d) {
            d.fixed = 0;
            cola.alpha(1); // fire it off again to satify gridify
        });

    var label = svg.selectAll(".label")
        .data(data.nodes)
       .enter().append("text")
        .attr("class", "label")
        .text(function (d) { return d.name; })
        .call(cola.drag);

    node.append("title")
        .text(function (d) { return d.name; });

    cola.on("tick", function () {
        link.attr("x1", function (d) { return d.source.x; })
            .attr("y1", function (d) { return d.source.y; })
            .attr("x2", function (d) { return d.target.x; })
            .attr("y2", function (d) { return d.target.y; });

        node.attr("x", function (d) { return d.x - d.width / 2 + pad; })
            .attr("y", function (d) { return d.y - d.height / 2 + pad; });

        group.attr("x", function (d) { return d.bounds.x; })
             .attr("y", function (d) { return d.bounds.y; })
            .attr("width", function (d) { return d.bounds.width(); })
            .attr("height", function (d) { return d.bounds.height(); });

        label.attr("x", function (d) { return d.x; })
             .attr("y", function (d) {
                 var h = this.getBBox().height;
                 return d.y + h/4;
             });
    });
};


// ref: view-source:https://ialab.it.monash.edu/webcola/examples/smallgroups.html
function createGroupedGraph(cola, data, divName, width = 960, height = 500) {

//    data.nodes.forEach(function (v) {
//        v.width = v.height = 95;
//    })
//    data.groups.forEach(function (g) { g.padding = 0.01; });

    var color = d3.scaleOrdinal(d3.schemeCategory20);

    var cola = cola.d3adaptor(d3)
        .linkDistance(100)
        .avoidOverlaps(true)
        .handleDisconnected(false)
        .size([width, height]);

    var svg = d3.select(divName).append("svg")
        .attr("width", width)
        .attr("height", height);

    cola
        .nodes(data.nodes)
        .links(data.links)
        .groups(data.groups)
        .start();

    var group = svg.selectAll(".group")
        .data(data.groups)
      .enter().append("rect")
        .attr("rx", 8).attr("ry", 8)
        .attr("class", "group")
        .style("fill", function (d, i) { return color(i); })
        .call(cola.drag);

    var link = svg.selectAll(".link")
        .data(data.links)
      .enter().append("line")
        .attr("class", "link");

    var pad = 3;
    var node = svg.selectAll(".node")
        .data(data.nodes)
      .enter().append("rect")
        .attr("class", "node")
        .attr("width", function (d) { return d.width - 2 * pad; })
        .attr("height", function (d) { return d.height - 2 * pad; })
        .attr("rx", 5).attr("ry", 5)
        .style("fill", function (d) { return color(data.groups.length); })
        .call(cola.drag);

    var label = svg.selectAll(".label")
        .data(data.nodes)
       .enter().append("text")
        .attr("class", "label")
        .text(function (d) { return d.name; })
        .call(cola.drag);

    node.append("title")
        .text(function (d) { return d.name; });

    cola.on("tick", function () {
        link.attr("x1", function (d) { return d.source.x; })
            .attr("y1", function (d) { return d.source.y; })
            .attr("x2", function (d) { return d.target.x; })
            .attr("y2", function (d) { return d.target.y; });

        node.attr("x", function (d) { return d.x - d.width / 2 + pad; })
            .attr("y", function (d) { return d.y - d.height / 2 + pad; });

        group.attr("x", function (d) { return d.bounds.x; })
             .attr("y", function (d) { return d.bounds.y; })
            .attr("width", function (d) { return d.bounds.width(); })
            .attr("height", function (d) { return d.bounds.height(); });

        label.attr("x", function (d) { return d.x; })
             .attr("y", function (d) {
                 var h = this.getBBox().height;
                 return d.y + h/4;
             });
    });

};

