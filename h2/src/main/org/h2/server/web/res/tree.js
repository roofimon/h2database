/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 *  * Initial Developer: H2 Group
 */

var nodeList = new Array();
var icons = new Array();
var tables = new Array();
var tablesByName = new Object();

function Table(name, columns, i) {
    this.name = name;
    this.columns = columns;
    this.id = i;
}

function addTable(name, columns, i) {
    var t = new Table(name, columns, i);
    tables[tables.length] = t;
    tablesByName[name] = t;
}

function ins(s, isTable) {
    // same-document: defined in app.js
    if (typeof insertText == 'function') {
        insertText(s, isTable);
    }
}

function refreshQueryTables() {
    // same-document: defined in app.js
    if (typeof refreshTables == 'function') {
        refreshTables();
    }
}

function goToTable(s) {
    var t = tablesByName[s];
    if (t) {
        hitOpen(t.id);
        return true;
    }
    return false;
}

function loadIcons() {
    icons[0] = new Image();
    icons[0].src = "tree_minus.svg";
    icons[1] = new Image();
    icons[1].src = "tree_plus.svg";
}

function Node(level, type, icon, text, link) {
    this.level = level;
    this.type = type;
    this.icon = icon;
    this.text = text;
    this.link = link;
}

function setNode(id, level, type, icon, text, link) {
    nodeList[id] = new Node(level, type, icon, text, link);
}

function divStr(i, dist) {
    if (dist>0) {
        return "<div id=\"div"+(i-1)+"\" style=\"display: none;\">";
    }
    var s = "";
    while (dist++<0) {
        s += "</div>";
    }
    return s;
}

// builds the tree markup as a string (no document.write, so it can be
// assigned into the live single-page document after load)
function buildTree() {
    loadIcons();
    var out = "";
    var last=nodeList[0];
    for (var i=0; i<nodeList.length; i++) {
        var node=nodeList[i];
        out += divStr(i, node.level-last.level);
        last=node;
        var j=node.level;
        while (j-->0) {
            out += "<img src=\"tree_empty.svg\"/>";
        }
        if (node.type==1) {
            if (i < nodeList.length-1 && nodeList[i+1].level > node.level) {
                out += "<img onclick=\"hit("+i+");\" id=\"join"+i+"\" src=\"tree_plus.svg\"/>";
            } else {
                out += "<img src=\"tree_empty.svg\"/>";
            }
        }
        out += "<img src=\"tree_"+node.icon+".svg\"/>&nbsp;";
        if (node.link==null) {
            out += node.text;
        } else {
            out += "<a id='"+node.text+"' href=\""+node.link+"\" >"+node.text+"</a>";
        }
        out += "<br />";
    }
    out += divStr(0, -last.type);
    return out;
}

function hit(i) {
    var theDiv = document.getElementById("div"+i);
    var theJoin    = document.getElementById("join"+i);
    if (theDiv.style.display == 'none') {
        theJoin.src = icons[0].src;
        theDiv.style.display = '';
    } else {
        theJoin.src = icons[1].src;
        theDiv.style.display = 'none';
    }
}

function hitOpen(i) {
    var theDiv = document.getElementById("div"+i);
    var theJoin    = document.getElementById("join"+i);
    theJoin.src = icons[0].src;
    theDiv.style.display = '';
}