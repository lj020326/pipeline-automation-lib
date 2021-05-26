
// ref: https://bl.ocks.org/d3noob/b024fcce8b4b9264011a1c3e7c7d70dc

function createTreeBox(data, divName, in_width = 660, in_height = 500) {

    // set the dimensions and margins of the diagram
    var margin = {top: 40, right: 90, bottom: 50, left: 90},
        width = in_width - margin.left - margin.right,
        height = in_height - margin.top - margin.bottom;

    var i = 0,
        duration = 750;

    // declares a tree layout and assigns the size
//    var tree = d3.layout.tree().size([height, width]);
    var tree = d3.tree().size([height, width]);

    // append the svg obgect to the body of the page
    // appends a 'group' element to 'svg'
    // moves the 'group' element to the top left margin
//    var svg = d3.select("body").append("svg")
    var svg = d3.select(divName).append("svg")
          .attr("width", width + margin.left + margin.right)
          .attr("height", height + margin.top + margin.bottom),
        g = svg.append("g")
          .attr("transform","translate(" + margin.left + "," + margin.top + ")");

    //  assigns the data to a hierarchy using parent-child relationships
    var root = d3.hierarchy(data);

//    root.x0 = height / 2;
//    root.y0 = 0;

    update(root);

//    d3.select(self.frameElement).style("height", "500px");

    function update(source) {

        // maps the node data to the tree layout
        var treeData = tree(root);

        // Compute the new tree layout.
        var nodes = treeData.descendants(),
          links = treeData.descendants().slice(1);

        // adds each node as a group
        var node = g.selectAll(".node")
            .data(nodes)
          .enter().append("g")
            .attr("class", function(d) {
              return "node" +
                (d.children ? " node--internal" : " node--leaf"); })
            .attr("transform", function(d) {
              return "translate(" + d.x + "," + d.y + ")"; });

        // adds the circle to the node
        node.append("circle")
          .attr("r", 10);

        // adds the text to the node
        node.append("text")
          .attr("dy", ".35em")
          .attr("y", function(d) { return d.children ? -20 : 20; })
          .style("text-anchor", "middle")
          .text(function(d) { return d.data.name; });

        // adds the links between the nodes
        var link = g.selectAll(".link")
            .data( nodes.slice(1))
          .enter().append("path")
            .attr("class", "link")
            .attr("d", function(d) {
               return "M" + d.x + "," + d.y
                 + "C" + d.x + "," + (d.y + d.parent.y) / 2
                 + " " + d.parent.x + "," +  (d.y + d.parent.y) / 2
                 + " " + d.parent.x + "," + d.parent.y;
               });


    }

};

