$(document).ready(function () {
  $.contextMenu({
    selector: '.context-menu-one',
    trigger: 'left',
    callback: function (key, options) {
      var serverIndex = $(this).closest('div').attr("id").substr(
          "sidebar_".length);

      switch (key) {
        case "start":
          break;
        case "restart":
          break;
        case "stop":
          break;
        case "delete":
          $(this).closest('div').remove();
          $("#content_" + serverIndex).remove();
          break;
        case "logs":
          break;
      }
    },
    items: {
      "start": {name: "Start", icon: "fas fa-play"},
      "restart": {name: "Restart", icon: "fas fa-undo"},
      "stop": {name: "Stop", icon: "fas fa-stop"},
      "delete": {name: "Delete", icon: "fas fa-trash-alt"},
      "logs": {name: "Logs", icon: "fas fa-terminal"},
    }
  });

  $("#sortable").sortable({
    start: function (e, ui) {
      $(this).attr('data-previndex', ui.item.index());
    },
    update: function (e, ui) {
      var newIndex = ui.item.index();
      var oldIndex = $(this).attr('data-previndex');
      $(this).removeAttr('data-previndex');

      var serverId = $(ui.item).attr("id").substr("sidebar_".length);

      if (newIndex > oldIndex) {
        $("#content_" + serverId).insertAfter(
            $(".server-content").children()[newIndex]);
      } else {
        $("#content_" + serverId).insertBefore(
            $(".server-content").children()[newIndex]);
      }
    }
  });

  $('#template').addClass('d-none');
  $("#file").on("change", openYamlFile);

  $(".collapsible").on("click", function (evt) {
    $(this).toggleClass("active");
    $(this.nextElementSibling).toggle();
  });

});


//
// top level menu API methods
//

function openYamlFile() {
  $.ajax({
    url: "/openYamlFile",
    type: "POST",
    data: new FormData($("#openYaml")[0]),
    enctype: 'multipart/form-data',
    processData: false,
    contentType: false,
    cache: false,
    dataType: 'json',
    success: function (res) {
      populateServersFromYaml(res);
    },
    error: function () {
      alert("There was an error")
    }
  });
}

function populateServersFromYaml(testEnvYaml) {
  const serverContent = $('.server-content');
  $('.sidebar').children().remove();
  serverContent.children().remove();

  for (serverKey in testEnvYaml) {
    // create sidebar entry
    $('.container.sidebar.server-container').append(
        '<div id="sidebar_server_' + serverKey + '" class="box text-center">'
        + serverKey
        + '<span class="context-menu-one btn btn-neutral"> <i class="fas fa-ellipsis-v"></i> </span> </div>');

    // create server content form tag
    serverContent.append('<form id="content_server_' + serverKey
        + '" class="col server-formular"></form>')
    // init formular with data
    $('#content_server_' + serverKey).initFormular(serverKey, testEnvYaml[serverKey]);
  }
}




// ----------------------------------------------------------------------------
//   server  formular methods
//

// ONGOING refactor js code
//  TODO: clarify pkiKeys / PkiIdentity struct - can we use simple strings here?
// TODO: EDIT complex list entries
// TODO: readonly management for fieldsets of complex lists
// TODO make serverkey heading editable
// TODO default structure via backend -> Yana
// TODO add server -> Yana
// TODO bootstrap sass -> Yana
// TODO template support
// TODO how to mark settings that are defined in template/overridden
// TODO LOPRIO refactor section attribute to using name???
// section is only needed for special handling of summary text, if general behaviour is ok with summarypattern,
// then no need for section attribute
// and i use it somewhere else too so reinvestigate

const dollarTokens = /\${([\w|.]+)}/g

let editableContentReset;

//
// formular API methods
//

// TODO clean this method up
$.fn.initFormular = function (serverKey, serverData) {
  checkTag('initFormular', this, 'FORM');

  // TODO remove the usage of this var
  const serverFormular = this;

  // add copy of template to form and set heading
  $(this).html(
      '<div class="row">' + $('#template-server-formular').html() + '</div>');
  $(this).find('.server-key').text(serverKey);

  // default settings
  if (!serverData.source) {
    serverData.source = [''];
  }
  // if hostname not set default to serverKey
  if (!serverData.hostname) {
    serverFormular.find('*[name="hostname"]').val(serverKey);
  }

  //
  // add collapsible icon with empty summary to legends
  //
  this.find('fieldset > legend').append(
      '&nbsp;<span class="collapse-icon">' + getCollapseIcon(true)
      + '</span>&nbsp;<span class="fieldset-summary fs-6"></span>');

  // collapse all fieldsets that should be collapsed on start
  $.each(this.find('fieldset.start-collapsed > legend'),
      function (idx, legend) {
        const $divs = $(legend).siblings();
        $divs.toggle();
        $(legend).find('span.collapse-icon').html(getCollapseIcon(false));
      }
  );

  //
  // callbacks
  //

  // collapsible fieldsets and formular
  this.find('fieldset > legend').click(function () {
    const $divs = $(this).siblings();
    $divs.toggle();
    $(this).find('span.collapse-icon > i').toggleCollapseIcon();
    $(this).setSummaryFor();
  });
  this.find('.server-formular-collapse-icon').click(function () {
    $(this).parent().siblings().toggle();
    $(this).parent().parent().siblings().toggle();
    $(this).toggleCollapseIcon();
  });


  // sortable lists
  this.find('fieldset .list-group').sortable({
    handle: 'i'
  });

  // editing lists (editable and complex)
  // list group items of editable lists editable on single click
  $.each(this.find('fieldset.editableList .list-group-item > span'),
      function (idx, item) {
        $(item).addClickNKeyCallbacks2ListItem(true);
      });

  // list button callbacks
  this.find('fieldset.editableList .btn-list-add').click(
      function () {
        const fieldSet = $(this).parents('fieldset');
        const listGroup = fieldSet.find(".list-group");
        const activeItem = listGroup.find('.active');

        listGroup.find('.active').removeClass('active');
        if (activeItem.length === 0) {
          listGroup.prepend(getListItem("", true));
        } else {
          $(getListItem("", true)).insertAfter(activeItem);
        }
        $.each(listGroup.find('.list-group-item > span'),
            function (idx, item) {
              $(item).addClickNKeyCallbacks2ListItem(true);
            });
        // start editing
        listGroup.find('.active > span').click();
      });
  this.find('fieldset.complex-list .btn-list-add').click(
      function () {
        const fieldSet = $(this).parents('fieldset');
        const listGroup = fieldSet.find(".list-group");
        const activeItem = listGroup.find('.active');

        listGroup.find('.active').removeClass('active');
        let newItem = $(getListItem("", true));
        if (activeItem.length === 0) {
          listGroup.prepend(newItem);
        } else {
          newItem.insertAfter(activeItem);
        }
        fieldSet.updateDataAndLabelForActiveItem(true);

        // TODO respect default value attributes
        const editFieldSet = fieldSet.find('fieldset');
        editFieldSet.find("*[name][type!='checkbox']").val('');
        editFieldSet.find("*[name][type='checkbox']").prop('checked',
            false);
        // start editing
        editFieldSet.find("*[name]:first").focus();
        fieldSet.find(".btn-list-apply").enabled(true);
        fieldSet.find(".btn-list-delete").enabled(true);
      });
  this.find('fieldset .btn-list-delete').click(function () {
    // TODO select previous list entry Or if no more select next
    $(this).parents('fieldset').find(
        '.list-group .list-group-item.active').remove();
    $(this).enabled(false);
  });
  this.find('fieldset .btn-list-apply').click(function () {
    const fieldSet = $(this).parents('fieldset');
    fieldSet.updateDataAndLabelForActiveItem(false);
    $(this).enabled(false);
  });

  //
  // as for attribute needs unique id and jquery-ui mangles with bootstrap switches
  // we skip the for attribute and add the click via jquery callbacks on the label
  //
  this.find('.form-check-label').click(function (ev) {
    $(this).parent().find('input').click().change();
    ev.preventDefault();
    return false;
  });



  //
  // initial state of buttons
  //
  this.find('.btn-list-delete').enabled(false);
  this.find('.btn-list-apply').enabled(false);
  // disable submit generally and especially on enter key of single input field sections
  this.submit(false);

  this.populateForm(serverData, "");

  // set forwardToProxy flag depending on hostname and port being set
  const forwardToProxySection = '.tigerProxyCfg.proxyCfg.forwardToProxy.';
  this.find('*[name="enableForwardProxy"]').prop(
      'checked',
      this.find(
          '*[name="' + forwardToProxySection + 'hostname"]').val() &&
      this.find(
          '*[name="' + forwardToProxySection + 'port"]').val()
  )

  this.find('fieldset.start-collapsed > legend').setSummaryFor();

  function enabledTab(tabName, flag) {
    serverFormular.find(
        '.nav-tabs .nav-item[tab="' + tabName + '"] .nav-link').enabled(flag);
  }

  //
  // as nav tabs are based on href links we need to work around as we have multiple formulars
  // on the page, so its betetr to do the switching manually by jquery callback on the nav-item
  this.find('.nav-tabs > .nav-item').click(function (ev) {
    if (!$(this).find('.nav-link').attr('disabled')) {
      serverFormular.showTab($(this).attr('tab'));
    }
    ev.preventDefault();
    return false;
  });

  function makeSourceListSingleLineEdit() {
    serverFormular.find('fieldset[section="source"]').find('button').addClass(
        "d-none");
    serverFormular.find('fieldset[section="source"] .list-group').css(
        {minHeight: '2.5rem'})
  }

  //
  // type specific UI adaptations
  //
  this.find(
      'fieldset[section=".dockerOptions.serviceHealthchecks"]').addClass(
      'd-none');
  this.find('*[name="version"]').parent().addClass('d-none');
  this.find('.nav-tabs .nav-link').enabled(false);

  enabledTab('pkiKeys', true);
  enabledTab('environment', true);
  enabledTab('urlMappings', true);

  // adapt source field according to type (single line for docker, tigerproxy, externalJar, externalUrl, only for docker compose its a list)
  // show version only for tigerProxy and docker
  switch (serverData.type) {
    case 'compose':
      const fieldSet =
          this.find(
              'fieldset[section=".dockerOptions.serviceHealthchecks"]');
      fieldSet.removeClass('d-none');
      fieldSet.find('legend').click();
      this.find(
          'fieldset[section=".dockerOptions.dockerSettings"]').addClass(
          'd-none');
      enabledTab('dockerOptions', true);
      this.showTab('dockerOptions');
      break;
    case 'docker':
      this.find('*[name="version"]').parent().removeClass('d-none');
      makeSourceListSingleLineEdit();
      enabledTab('dockerOptions', true);
      this.showTab('dockerOptions');
      break;
    case 'externalJar':
    case 'externalUrl':
      enabledTab('externalJarOptions', true);
      makeSourceListSingleLineEdit();
      this.showTab('externalJarOptions');
      break;
    case 'tigerProxy':
      this.find('*[name="version"]').parent().removeClass('d-none');
      makeSourceListSingleLineEdit();
      enabledTab('externalJarOptions', true);
      enabledTab('tigerProxy', true);
      this.showTab('tigerProxy');
      break;
  }
}

// for form.server-formular
$.fn.showTab = function (tabName) {
  checkTag('showTab', this, 'FORM');
  checkClass('showTab', this, 'server-formular');
  this.find('.nav-tabs .nav-link').removeClass('active');
  const tabs = this.find('.tab-pane');
  tabs.removeClass('active');
  tabs.removeClass('show');
  tabs.hide();
  this.find(
      '.nav-tabs .nav-item[tab="' + tabName + '"] > .nav-link').addClass(
      'active');
  const tab = this.find('.' + tabName);
  tab.addClass('active');
  tab.show();
  tab.addClass('show');
}

// for form.server-formular
$.fn.updateServerList = function (serverList) {
  checkTag('updateServerList', this, 'FORM');
  checkClass('updateServerList', this, 'server-formular');
  let html = "";
  $.each(serverList, (idx, server) => {
    html += '<option value="' + server + '">' + server.replace("_", " ")
        + '</option>';
  });
  const select = $(this).find('select[name=".tigerProxyCfg.proxiedServer"]');
  select.children().remove();
  select.prepend(html);
}

updateServerLists = function (serverList) {
  $('form.server-formular').updateServerList(serverList);
}

//
// internal helper methods (Do not use unless discussed with Thomas)
//

function checkTag(method, $elem, tagName) {
  $.each($elem, (idx, item) => {
    if (item.tagName !== tagName) {
      throw new Error(method + ' is only for ' + tagName + ' items! (used on '
          + item.tagName + ')');
    }
  });
}

function checkClass(method, $elem, className) {
  $.each($elem, (idx, item) => {
    if (!$(item).hasClass(className)) {
      throw new Error(
          method + ' is only for items of class ' + className + '! (used on '
          + item.tagName + ')');
    }
  });
}

$.fn.selectText = function () {
  const doc = document;
  const element = this[0];
  if (doc.body.createTextRange) {
    const range = document.body.createTextRange();
    range.moveToElementText(element);
    range.select();
  } else if (window.getSelection) {
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(element);
    selection.removeAllRanges();
    selection.addRange(range);
  }
};

$.fn.enabled = function (enabled) {
  this.attr('disabled', !enabled);
  if (enabled) {
    this.removeClass('disabled');
  } else {
    this.addClass('disabled');
  }
}

$.fn.setValue = function (value) {
  if (!this.length) {
    throw new Error('Trying to set value on not found item! (value was '
        + value + ')');
  }
  // TODO check if when multiple elems (select and non select that all get set the value
  // the this[0] looks suspicious to me :(
  if (this.attr("type") === "checkbox") {
    this.prop("checked", value);
  } else if (this[0].tagName === "SELECT") {
    $.each(this.find('option'), function (idx, opt) {
      $(opt).attr('selected',
          $(opt).text() === value);
    });
  } else {
    if (this.attr("type") === "number") {
      this.val(Number(value));
    } else {
      this.val(value);
    }
  }
}

$.fn.setValueInData = function (section, data, emptyValues) {
  $.each(this, (idx, item) => {
    let fieldName = $(item).attr('name').substring(section.length + 1);
    let pathCursor = data;
    if (fieldName.indexOf(".") !== -1) {
      // auto create all struct nodes as empty objects
      const path = fieldName.split('.');
      fieldName = path.pop();
      $.each(path, function (idx, node) {
        if (!pathCursor[node]) {
          pathCursor[node] = {};
        }
        pathCursor = pathCursor[node];
      });
    }
    if (emptyValues) {
      if ($(item).attr('type') === 'checkbox') {
        pathCursor[fieldName] = false;
      } else {
        pathCursor[fieldName] = null;
      }
    } else {
      if ($(item).attr('type') === 'checkbox') {
        pathCursor[fieldName] = $(item).prop('checked');
      } else {
        pathCursor[fieldName] = $(item).val();
      }
    }

  });
}

// for fieldset
$.fn.isChecked = function (name) {
  checkTag('isChecked', this, 'FIELDSET');
  return this.find("*[name='" + name + "']").prop("checked");
}

// for legend
$.fn.setSummaryFor = function () {
  checkTag('setSummaryFor', this, 'LEGEND');
  const fieldSet = this.parent();
  const fsName = fieldSet.attr("section");
  const summarySpan = fieldSet.find("span.fieldset-summary");
  const collapsed = this.find('.collapse-icon > i').isCollapsed();
  if (collapsed) {
    switch (fsName) {
      case "source":
        summarySpan.html(fieldSet.generateSummary());
        break;
      case ".tigerProxyCfg.proxyCfg.forwardToProxy":
        if (fieldSet.isChecked("enableForwardProxy")) {
          summarySpan.html(fieldSet.generateSummary());
        } else {
          summarySpan.html('<span class="text summary fs-6">DISABLED</span>');
        }
        break;
      default:
        summarySpan.html(fieldSet.generateSummary());
        break;
    }
  } else {
    summarySpan.text("");
  }
}

// for fieldset
$.fn.generateSummary = function () {
  checkTag('generateSummary', this, 'FIELDSET');
  const summaryPattern = this.attr("summaryPattern");
  return '<span class="text summary fs-6">' + summaryPattern.replace(
      dollarTokens,
      (m, g1) => this.getValue(g1) || "&nbsp;") + '</span>';
}

// for fieldset
$.fn.getValue = function (name) {
  checkTag('getValue', this, 'FIELDSET');
  const elem = this.find("*[name='" + name + "']");
  let str;
  if (elem.prop("tagName") === 'UL') {
    str = this.getListValue(name);
    return str;
  } else if (elem.attr("type") === 'checkbox') {
    return elem.prop('checked') ? 'ON' : 'OFF';
  } else {
    str = this.find("*[name='" + name + "']").val();
    return $('<span>').text(str).html();
  }
}

$.fn.getListValue = function (name) {
  checkTag('getListValue', this, 'FIELDSET');
  const lis = this.find("ul[name='" + name + "'] > li");
  let csv = "";
  $.each(lis, function (idx, el) {
    csv += $('<span>').text($(el).text()).html() + ",<br/>";
  });
  if (csv === "") {
    return "No entries";
  }
  return csv.substr(0, csv.length - ",<br/>".length);
}

$.fn.generateListItemLabel = function (data) {
  checkClass('generateListItemLabel', this, 'list-group');
  const summaryPattern = this.attr("summaryPattern");
  return summaryPattern.replace(dollarTokens, (m, g1) => {
    if (g1.indexOf('.') !== -1) {
      const path = g1.split('.');
      let pathCursor = data;
      $.each(path, function (idx, node) {
        if (!pathCursor) {
          return false;
        }
        pathCursor = pathCursor[node];
      });
      return pathCursor || "&nbsp;";
    } else {
      return data[g1] || "&nbsp;";
    }
  });
}

$.fn.setObjectFieldInForm = function (data, field, path) {
  checkTag('setObjectFieldInForm', this, 'FIELDSET')
  if (typeof data[field] === "object" && data[field] !== null) {
    for (const child in data[field]) {
      this.setObjectFieldInForm((data[field]), child, path + "." + field);
    }
  } else {
    const inputField = this.find("*[name='" + path + "." + field + "']");
    inputField.setValue(data[field]);
  }
}

//
// add callback methods for list items (editable and non editable)
//

$.fn.addClickNKeyCallbacks2ListItem = function (editable) {
  checkTag('addClickNKeyCallbacks2ListItem', this, 'SPAN')
  if (editable) {
    this.off("click");
    this.off("keydown");
    this.click((ev) => {
      $(this).attr("contentEditable", "true");
      $(this).parent().focus();
      $(this).parents('.list-group').find('.active').removeClass('active');
      $(this).parent().addClass('active');
      $(this).selectText();
      editableContentReset = $(this).html();
      $(this).focus();
      const btnDel = $(this).parents('fieldset').find('.btn-list-delete');
      btnDel.attr("disabled", false);
      btnDel.removeClass("disabled");
      ev.preventDefault();
      return false;
    });
    this.keydown((ev) => {
      if (ev.keyCode === 13) {
        $(this).attr("contentEditable", "false");
        $(this).parent().blur();
        $(this).blur();
        ev.preventDefault();
        return false;
      } else if (ev.keyCode === 27) {
        $(this).html(editableContentReset);
        $(this).attr("contentEditable", "false");
        $(this).parent().blur();
        $(this).blur();
        ev.preventDefault();
        return false;
      }
    });
  }

  const listItem = this.parent();
  listItem.click(() => {
    const fieldSet = listItem.parents('fieldset');
    const curActive = listItem.parents('.list-group').find('.active');
    curActive.removeClass('active');
    listItem.addClass('active');
    const section = fieldSet.attr("section");
    const btnDel = fieldSet.find('.btn-list-delete');
    btnDel.attr("disabled", false);
    btnDel.removeClass("disabled");
    // for complex lists also populate the edit fieldset
    if (!editable) {
      let data = listItem.data("listdata");
      for (const field in data) {
        fieldSet.setObjectFieldInForm(data, field, section);
      }
    }
  });
}

function getListItem(text, active, reallySmall) {
  return '<li class="list-group-item ' + (active ? 'active ' : '') +
      (reallySmall ? 'really-small' : '') + '">'
      + '<i class="fas fa-grip-lines draghandle"></i><span><span>' + text
      + '</span></span>'
      + '</li>'
}

$.fn.updateDataAndLabelForActiveItem = function (emptyValues) {
  checkTag('updateDataAndLabelForActiveItem', this, 'FIELDSET');
  const section = this.attr("section");
  const listGroup = this.find('.list-group');
  let elem = listGroup.find(".list-group-item.active");
  let data = elem.data("listdata");

  // read input from fields into data() struct and update list item value
  if (!data) {
    data = {};
  }
  this.find('fieldset').find("*[name]").setValueInData(section, data,
      emptyValues);
  elem.replaceWith(
      getListItem(listGroup.generateListItemLabel(data), true, true));

  elem = listGroup.find(".list-group-item.active");
  elem.data("listdata", data);
  elem.find('span:first').addClickNKeyCallbacks2ListItem(false);
}

// from.server-formular
$.fn.populateForm = function (serverData, path) {
  checkTag('populateForm', this, 'FORM');
  checkClass('populateForm', this, 'server-formular');

  for (const field in serverData) {
    const value = serverData[field];
    if (value != null && typeof value === 'object' && !Array.isArray(
        value)) {
      this.populateForm(value, path + "." + field);
      continue;
    }
    const nameStr = path + (path.length === 0 ? "" : ".") + field;
    let listHtml = '';
    if (Array.isArray(value)) {
      const elem = this.find('.list-group[name="' + nameStr + '"]');
      let editable = false;
      if (typeof value[0] === 'object') {
        $.each(value, function (idx, item) {
          listHtml += getListItem(elem.generateListItemLabel(item), false,
              true);
        });
        elem.html(listHtml);
        $.each(value, function (idx, itemData) {
          $(elem.children()[idx]).data("listdata", itemData);
        });
      } else {
        const fieldSet = elem.parents('fieldset');
        editable = fieldSet.hasClass('editableList');
        $.each(value, function (idx, item) {
          listHtml += getListItem(item, false);
        });
        elem.html(listHtml);
      }
      elem.find(".list-group-item > span").addClickNKeyCallbacks2ListItem(
          editable);
      continue;
    }
    const elem = this.find('*[name="' + nameStr + '"]');
    if (elem.length === 0) {
      console.error("UNKNOWN ELEM for " + path + " -> " + field);
      continue;
    }
    elem.setValue(serverData[field]);
  }
}

$.fn.toggleCollapseIcon = function (state) {
  checkTag('toggleCollapseIcon', this, 'I');
  const findState = /(.*)(-down|-right)(.*)/;
  const clz = this.attr('class');
  if (typeof state === 'undefined') {
    state = !this.isCollapsed();
  }
  if (state) {
    this.attr('class',
        clz.replace(findState, (m, g1, g2, g3) => g1 + '-right' + g3));
  } else {
    this.attr('class',
        clz.replace(findState, (m, g1, g2, g3) => g1 + '-down' + g3));
  }
}

$.fn.isCollapsed = function () {
  checkTag('isCollapsed', this, 'I');
  const findState = /(.*)(-down|-right)(.*)/;
  const clz = this.attr('class');
  const result = clz.match(findState);
  return result[2] === '-right';
}

function getCollapseIcon(visible) {
  return '<i class="collapse-icon fas fa-angle-' +
      (visible ? 'down' : 'right') + '"></i>';
}

