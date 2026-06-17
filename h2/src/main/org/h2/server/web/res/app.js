/*
 * Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */

/*
 * Single-page console controller. Replaces the former frameset model:
 * one document, panels updated via fetch(). Cross-frame calls
 * (parent.h2query/h2menu/h2result) are now same-document.
 * Globals nodeList/tables/tablesByName/setNode/addTable/buildTree/goToTable/
 * hit/hitOpen/loadIcons come from tree.js; editing helpers from table.js.
 */

var autoComplete = 0; // 0: off, 1: normal, 2: full
var autoSelect = 1;   // 0: off, 1: on
var selectedRow = -1;
var lastList = '';
var lastQuery = null;
var columnsByTable = new Object();
var tableAliases = new Object();
var showAutoCompleteWait = 0;
var autoCompleteManual = false;

// re-render the tree; referenced by the DDL-refresh hook injected into results
window.refreshTree = function() { loadTree(); };

function init() {
    loadTree();
    loadHelp();
}

function el(id) {
    return document.getElementById(id);
}

function getSql() {
    return el('sql');
}

// ------------------------------------------------------------------ loaders

function injectOutput(html) {
    var parsed = new DOMParser().parseFromString(html, 'text/html');
    var src = parsed.getElementById('output');
    var out = el('output');
    out.innerHTML = src ? src.innerHTML : (parsed.body ? parsed.body.innerHTML : html);
    // a fetched fragment may carry its own (duplicate) autocomplete table; drop it
    var dup = out.querySelector('#h2auto');
    if (dup) {
        dup.parentNode.removeChild(dup);
    }
    runScripts(out);
    initSort();
}

// scripts set via innerHTML do not execute; recreate them so they run
function runScripts(container) {
    var scripts = container.getElementsByTagName('script');
    var list = [];
    for (var i = 0; i < scripts.length; i++) {
        list.push(scripts[i]);
    }
    for (var j = 0; j < list.length; j++) {
        var old = list[j];
        var s = document.createElement('script');
        if (old.src) {
            s.src = old.src;
        } else {
            s.text = old.textContent;
        }
        old.parentNode.replaceChild(s, old);
    }
}

function loadTree() {
    fetch('tables.do?jsessionid=' + H2_SID).then(function(r) {
        return r.text();
    }).then(function(html) {
        var parsed = new DOMParser().parseFromString(html, 'text/html');
        var scripts = parsed.getElementsByTagName('script');
        var data = '';
        for (var i = 0; i < scripts.length; i++) {
            if (!scripts[i].src && scripts[i].textContent.indexOf('setNode') >= 0) {
                data = scripts[i].textContent;
            }
        }
        nodeList.length = 0;
        tables.length = 0;
        tablesByName = new Object();
        if (data) {
            (new Function(data))();
        }
        el('treePanel').innerHTML = buildTree();
        refreshTables();
    }).catch(function() {});
}

function loadHelp() {
    fetch('help.jsp?jsessionid=' + H2_SID).then(function(r) {
        return r.text();
    }).then(injectOutput).catch(function() {});
}

function runServerSql(s) {
    fetch('query.do?jsessionid=' + H2_SID + '&sql=' + encodeURIComponent(s)).then(function(r) {
        return r.text();
    }).then(injectOutput).catch(function() {});
}

// generic: serialize a server-generated form and inject the result
// (used by the "Edit Result" button; editFinish in table.js mirrors this)
function submitForm(form) {
    var params = [];
    for (var i = 0; i < form.elements.length; i++) {
        var e = form.elements[i];
        if (e.name && e.type != 'submit' && e.type != 'button') {
            params.push(encodeURIComponent(e.name) + '=' + encodeURIComponent(e.value));
        }
    }
    fetch(form.action, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.join('&')
    }).then(function(r) {
        return r.text();
    }).then(injectOutput).catch(function() {});
    return false;
}

function postQuery(s) {
    fetch('query.do?jsessionid=' + H2_SID, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'sql=' + encodeURIComponent(s)
    }).then(function(r) {
        return r.text();
    }).then(injectOutput).catch(function() {});
}

function logout() {
    window.location = 'logout.do?jsessionid=' + H2_SID;
}

// inserts the SQL of a history row (read from the command cell) into the editor
function insertHistory(a) {
    var tr = a;
    while (tr && tr.tagName != 'TR') {
        tr = tr.parentNode;
    }
    if (!tr || tr.cells.length < 2) {
        return;
    }
    getSql().value = tr.cells[1].textContent;
    getSql().focus();
}

// help sample-SQL links (and standalone help.jsp) call this
function set(s) {
    getSql().value = s;
    getSql().focus();
}

// ------------------------------------------------------------------ editor

function clearQuery() {
    getSql().value = '';
    keyUp();
    getSql().focus();
}

function refreshTables() {
    columnsByTable = new Object();
    for (var i = 0; i < tables.length; i++) {
        columnsByTable[tables[i].name] = tables[i].columns;
    }
}

function buildTableAliases(input) {
    tableAliases = new Object();
    var list = splitSQL(input);
    var last = '';
    for (var i = 0; i < list.length; i++) {
        var word = list[i].toUpperCase();
        if (word != 'AS') {
            if (columnsByTable[last]) {
                tableAliases[word] = last;
            }
            last = word;
        }
    }
}

function splitSQL(s) {
    var list = new Array();
    s = s.toUpperCase() + ' ';
    var e = s.length;
    for (var i = 0; i < e; i++) {
        var ch = s.charAt(i);
        if (ch == '_' || (ch >= 'A' && ch <= 'Z')) {
            var start = i;
            do {
                ch = s.charAt(++i);
            } while (ch == '_' || (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z'));
            list[list.length] = s.substring(start, i);
        }
    }
    return list;
}

// dot-trigger: reveal the table in the tree if recognized
function help() {
    var input = getSql();
    var pos = input.selectionStart;
    if (pos > 0) {
        var s = input.value.substring(0, pos).toUpperCase();
        var e = pos - 1;
        for (; e >= 0; e -= 1) {
            var ch = s.charAt(e);
            if (ch != '_' && (ch < '0' || ch > '9') && (ch < 'A' || ch > 'Z')) {
                break;
            }
        }
        s = s.substring(e + 1, s.length);
        buildTableAliases(input.value);
        if (!columnsByTable[s]) {
            s = tableAliases[s];
        }
        if (columnsByTable[s]) {
            if (goToTable(s)) {
                input.focus();
            }
        }
    }
}

function trim(s) {
    while (s.charAt(0) == ' ' && s.length > 0) {
        s = s.substring(1);
    }
    while (s.charAt(s.length - 1) == ' ' && s.length > 0) {
        s = s.substring(0, s.length - 1);
    }
    return s;
}

// called by tree node links (tree.js ins() -> insertText)
function insertText(s, isTable) {
    s = decodeURIComponent(s);
    var field = getSql();
    var last = s.substring(s.length - 1);
    if (last != '.' && last != '\'' && last != '"' && last > ' ') {
        s += ' ';
    }
    if (isTable && trim(field.value) == '') {
        field.value = 'SELECT * FROM ' + s;
    } else if (field.selectionStart != null) {
        var startPos = field.selectionStart;
        var endPos = field.selectionEnd;
        field.value = field.value.substring(0, startPos) + s + field.value.substring(endPos);
        var pos = startPos + s.length;
        field.selectionStart = pos;
        field.selectionEnd = pos;
    } else {
        field.value += s;
    }
    field.focus();
}

// ------------------------------------------------------------- autocomplete

function showAutoComplete() {
    if (showAutoCompleteWait == 0) {
        showAutoCompleteWait = 5;
        setTimeout(showAutoCompleteNow, 100);
    } else {
        showAutoCompleteWait -= 1;
    }
}

function showAutoCompleteNow() {
    var input = getSql();
    var pos = input.selectionStart;
    var s = input.value.substring(0, pos);
    if (s != lastQuery) {
        lastQuery = s;
        retrieveList(s);
    }
    showAutoCompleteWait = 0;
}

function keyDown(event) {
    var key = event.keyCode ? event.keyCode : event.charCode;
    if (key == null) {
        return false;
    }
    if (key == 13 && (event.ctrlKey || event.metaKey)) {
        // ctrl + return, cmd + return
        submitAll();
        return false;
    } else if (key == 13 && event.shiftKey) {
        // shift + return
        submitSelected();
        return false;
    } else if (key == 32 && (event.ctrlKey || event.altKey)) {
        // ctrl + space
        manualAutoComplete();
        return false;
    } else if (key == 190 && autoComplete == 0) {
        // dot
        help();
        return true;
    }
    var table = getAutoCompleteTable();
    if (table.rows.length > 0) {
        if (key == 27) {
            // escape
            removeAutoComplete();
            return false;
        } else if ((key == 9 && !event.shiftKey) || (key == 13 && !event.shiftKey && !event.ctrlKey && !event.altKey)) {
            // tab / enter
            if (table.rows.length > selectedRow) {
                var row = table.rows[selectedRow];
                if (row.cells.length > 1) {
                    insertText(row.cells[1].innerHTML);
                }
                removeAutoComplete();
                return false;
            }
        } else if (key == 38 && !event.shiftKey) {
            // up
            if (table.rows.length > selectedRow) {
                selectedRow = selectedRow <= 0 ? table.rows.length - 1 : selectedRow - 1;
                highlightRow(selectedRow);
                return false;
            }
        } else if (key == 40 && !event.shiftKey) {
            // down
            if (table.rows.length > selectedRow) {
                selectedRow = selectedRow >= table.rows.length - 1 ? 0 : selectedRow + 1;
                highlightRow(selectedRow);
                return false;
            }
        }
        if (autoComplete == 0) {
            removeAutoComplete();
        }
    }
    return true;
}

function keyUp(event) {
    var key = event == null ? 0 : (event.keyCode ? event.keyCode : event.charCode);
    if (autoComplete != 0) {
        if (key != 37 && key != 38 && key != 39 && key != 40) {
            // not an arrow key: refresh the list
            showAutoComplete();
        }
    }
    if (key == 13 && event && event.shiftKey) {
        return false;
    }
    return true;
}

function setAutoComplete(value) {
    autoComplete = value;
    if (value == 0) {
        removeAutoComplete();
    } else {
        var s = lastList;
        lastList = '';
        showList(s);
    }
}

function setAutoSelect(value) {
    autoSelect = value;
}

function manualAutoComplete() {
    autoCompleteManual = true;
    lastQuery = null;
    lastList = '';
    showAutoCompleteNow();
    getSql().focus();
}

function removeAutoComplete() {
    var table = getAutoCompleteTable();
    while (table.rows.length > 0) {
        table.deleteRow(0);
    }
}

function highlightRow(row) {
    if (row != null) {
        selectedRow = row;
    }
    var table = getAutoCompleteTable();
    highlightThisRow(table.rows[selectedRow]);
}

function highlightThisRow(row) {
    var table = getAutoCompleteTable();
    for (var i = 0; i < table.rows.length; i++) {
        var r = table.rows[i];
        var col = (r == row) ? '#cce0ff' : '';
        var cells = r.cells;
        if (cells.length > 0) {
            cells[0].style.backgroundColor = col;
        }
    }
}

function getAutoCompleteTable() {
    return el('h2auto');
}

function showList(s) {
    if (lastList == s) {
        return;
    }
    lastList = s;
    var list = s.length == 0 ? null : s.split('|');
    var table = getAutoCompleteTable();
    if (table == null) {
        return;
    }
    while (table.rows.length > 0) {
        table.deleteRow(0);
    }
    selectedRow = 0;
    var count = 0;
    var tbody = table.tBodies[0];
    for (var i = 0; list != null && i < list.length; i++) {
        var kv = list[i].split('#');
        var type = kv[0];
        if (type > 0 && autoComplete != 2 && !autoCompleteManual) {
            continue;
        }
        var row = document.createElement('tr');
        tbody.appendChild(row);
        var cell = document.createElement('td');
        var key = kv[1];
        var value = kv[2];
        if (!key || !value) {
            break;
        }
        count++;
        cell.className = 'autoComp' + type;
        key = decodeURIComponent(key);
        row.onmouseover = function() { highlightThisRow(this); };
        row.onclick = function() { insertText(this.cells[1].innerHTML); keyUp(); };
        var text = document.createTextNode(key);
        cell.appendChild(text);
        row.appendChild(cell);
        cell = document.createElement('td');
        cell.style.display = 'none';
        text = document.createTextNode(value);
        cell.appendChild(text);
        row.appendChild(cell);
    }
    if (count > 0) {
        highlightRow();
    }
    el('output').scrollTop = 0;
    autoCompleteManual = false;
}

function retrieveList(s) {
    if (s.length > 2000) {
        s = s.substring(s.length - 2000);
    }
    fetch('autoCompleteList.do?jsessionid=' + H2_SID + '&query=' + encodeURIComponent(s)).then(function(r) {
        return r.text();
    }).then(function(t) {
        showList(t);
    }).catch(function() {});
}

// ------------------------------------------------------------------- submit

function submitAll() {
    postQuery(getSql().value);
    getSql().focus();
}

function submitSelected() {
    var field = getSql();
    if (field.selectionStart == field.selectionEnd) {
        if (autoSelect == 0) {
            return;
        }
        doAutoSelect();
        if (field.selectionStart == field.selectionEnd) {
            return;
        }
    }
    var startPos = field.selectionStart;
    var endPos = field.selectionEnd;
    postQuery(field.value.substring(startPos, endPos));
}

function doAutoSelect() {
    var field = getSql();
    var position = field.selectionStart;
    try {
        var prevDoubleLine = field.value.lastIndexOf('\n\n', position - 1) + 2;
        if (prevDoubleLine == 1) {
            prevDoubleLine = 0;
        }
        var nextDoubleLine = field.value.indexOf('\n\n', position);
        if (nextDoubleLine == -1) {
            nextDoubleLine = field.value.length;
        }
        field.setSelectionRange(prevDoubleLine, nextDoubleLine);
    } catch (e) {
        field.selectionStart = field.selectionEnd = position;
    }
}
