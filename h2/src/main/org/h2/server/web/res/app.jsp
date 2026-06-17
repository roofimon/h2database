<!DOCTYPE html>
<!--
Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
and the EPL 1.0 (https://h2database.com/html/license.html).
Initial Developer: H2 Group
-->
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
    <title>${text.a.title}</title>
    <link rel="stylesheet" type="text/css" href="stylesheet.css" />
    <script type="text/javascript">var H2_SID = '${sessionId}';</script>
    <script type="text/javascript" src="tree.js"></script>
    <script type="text/javascript" src="table.js"></script>
    <script type="text/javascript" src="app.js"></script>
</head>
<body>
<div class="app-grid">

    <div id="toolbar">
        <form name="header" onsubmit="return false;">
            <table class="toolbar" cellspacing="0" cellpadding="0"><tr class="toolbar">
                <td class="toolbar">
                    <a href="#" onclick="logout();return false;"><img src="icon_disconnect.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.disconnect}" title="${text.toolbar.disconnect}"/></a>
                    <img src="icon_line.svg" class="iconLine" alt=""/>
                    <a href="#" onclick="loadTree();return false;"><img src="icon_refresh.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.refresh}" title="${text.toolbar.refresh}"/></a>
                    <img src="icon_line.svg" class="iconLine" alt=""/>
                </td>
                <td class="toolbar">
                    <input type="checkbox" name="autoCommit" value="autoCommit"
                        onclick="runServerSql('@autocommit_' + (this.checked ? 'true' : 'false') + '.');"/>
                </td>
                <td class="toolbar">${text.toolbar.autoCommit}&nbsp;</td>
                <td class="toolbar">
                    <a href="#" onclick="runServerSql('ROLLBACK');return false;"><img src="icon_rollback.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.rollback}" title="${text.toolbar.rollback}"/></a>
                    <a href="#" onclick="runServerSql('COMMIT');return false;"><img src="icon_commit.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.commit}" title="${text.toolbar.commit}"/></a>
                    <img src="icon_line.svg" class="iconLine" alt=""/>
                </td>
                <td class="toolbar">&nbsp;${text.toolbar.maxRows}:&nbsp;</td>
                <td class="toolbar">
                    <select name="rowcount" size="1"
                        onchange="runServerSql('@maxrows ' + this.value + '.');">
                        <option value="0">${text.toolbar.all}</option>
                        <option value="10000">10000</option>
                        <option selected="selected" value="1000">1000</option>
                        <option value="100">100</option>
                        <option value="10">10</option>
                    </select>&nbsp;
                </td>
                <td class="toolbar">
                    <a href="#" onclick="submitAll();return false;"><img src="icon_run.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.run}" title="${text.toolbar.run}"/></a>
                </td>
                <td class="toolbar">
                    <a href="#" onclick="submitSelected();return false;"><img src="icon_run_selected.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.runSelected}" title="${text.toolbar.runSelected}"/></a>
                </td>
                <td class="toolbar">
                    <a href="#" onclick="runServerSql('@cancel.');return false;"><img src="icon_stop.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.cancelStatement}" title="${text.toolbar.cancelStatement}"/></a>
                    <img src="icon_line.svg" class="iconLine" alt=""/>
                    <a href="#" onclick="runServerSql('@history.');return false;"><img src="icon_history.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.toolbar.history}" title="${text.toolbar.history}"/></a>
                    <img src="icon_line.svg" class="iconLine" alt=""/>
                </td>
                <td class="toolbar">
                    ${text.toolbar.autoComplete}&nbsp;
                    <select name="autoComplete" size="1" onchange="setAutoComplete(this.value)">
                        <option selected="selected" value="0">${text.toolbar.autoComplete.off}</option>
                        <option value="1">${text.toolbar.autoComplete.normal}</option>
                        <option value="2">${text.toolbar.autoComplete.full}</option>
                    </select>&nbsp;
                </td>
                <td class="toolbar">
                    ${text.toolbar.autoSelect}&nbsp;
                    <select name="autoSelect" size="1" onchange="setAutoSelect(this.value)">
                        <option value="0">${text.toolbar.autoSelect.off}</option>
                        <option selected="selected" value="1">${text.toolbar.autoSelect.on}</option>
                    </select>
                </td>
                <td class="toolbar">
                    <a href="#" onclick="loadHelp();return false;"><img src="icon_help.svg"
                        onmouseover="this.className='icon_hover'" onmouseout="this.className='icon'"
                        class="icon" alt="${text.a.help}" title="${text.a.help}"/></a>
                </td>
            </tr></table>
        </form>
    </div>

    <div id="treePanel" class="tree"></div>

    <div id="editorPanel">
        <form name="h2query" onsubmit="return false;">
            <div class="editorButtons">
                <input type="button" class="button" value="${text.toolbar.run}"
                    onclick="submitAll();return true;" />
                <input type="button" class="button" value="${text.toolbar.runSelected}"
                    onclick="submitSelected();return true;" />
                <input type="button" class="button" value="${text.toolbar.autoComplete}"
                    onclick="manualAutoComplete();return true;" />
                <input type="button" class="button" value="${text.toolbar.clear}"
                    onclick="clearQuery();return true;" />
                <span class="editorLabel">${text.toolbar.sqlStatement}:</span>
            </div>
            <textarea id="sql" name="sql" onkeydown="return keyDown(event)" onkeyup="return keyUp(event)"
                onfocus="keyUp()" onchange="return keyUp()"></textarea>
        </form>
    </div>

    <div id="resultRegion">
        <div id="output" class="result"></div>
        <table id="h2auto" class="autoComp"><tbody></tbody></table>
    </div>

</div>

<script type="text/javascript">
//<![CDATA[
    document.header.autoCommit.checked = '${autoCommit}' != '';
    init();
//]]>
</script>
</body>
</html>
