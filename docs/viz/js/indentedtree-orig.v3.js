

// ref: http://bl.ocks.org/thehogfather/0e48ec486abbd5be17d7
function createIndentedCollapsibleTree(data, divName, in_width = 960, in_height = 500) {

    var id = 0;

    var tree = d3.layout.treelist()
        .childIndent(10)
        .nodeHeight(30);

//    var ul = d3.select("body").append("ul").classed("treelist", "true");
    var ul = d3.select("body").select(divName).append("ul").classed("treelist", "true");

    function update(data, parent) {
        var nodes = tree.nodes(data),
            duration = 250;
        function toggleChildren(d) {
            if (d.children) {
                d._children = d.children;
                d.children = null;
            } else if (d._children) {
                d.children = d._children;
                d._children = null;
            }
        }

        var nodeEls = ul.selectAll("li.node").data(nodes, function (d) {
            d.id = d.id || ++id;
            return d.id;
        });
        //entered nodes
        var entered = nodeEls.enter().append("li").classed("node", true)
            .style("top", parent.y +"px")
            .style("opacity", 0)
            .style("height", tree.nodeHeight() + "px")
            .on("click", function (d) {
                toggleChildren(d);
                update(data, d);
            })
            .on("mouseover", function (d) {
                d3.select(this).classed("selected", true);
            })
            .on("mouseout", function (d) {
                d3.selectAll(".selected").classed("selected", false);
            });
        //add arrows if it is a folder
        entered.append("span").attr("class", function (d) {
            var icon = d.children ? " glyphicon-chevron-down"
                : d._children ? "glyphicon-chevron-right" : "";
            return "caret glyphicon " + icon;
        });
        //add icons for folder for file
        entered.append("span").attr("class", function (d) {
            var icon = d.children || d._children ? "glyphicon-folder-close"
                : "glyphicon-file";
            return "glyphicon " + icon;
        });
        //add text
        entered.append("span").attr("class", "filename")
            .html(function (d) { return d.name; });
        //update caret direction
        nodeEls.select("span.caret").attr("class", function (d) {
            var icon = d.children ? " glyphicon-chevron-down"
                : d._children ? "glyphicon-chevron-right" : "";
            return "caret glyphicon " + icon;
        });
        //update position with transition
        nodeEls.transition().duration(duration)
            .style("top", function (d) { return (d.y - tree.nodeHeight()) + "px";})
            .style("left", function (d) { return d.x + "px"; })
            .style("opacity", 1);
        nodeEls.exit().remove();
    }

    update(data, data);

};


