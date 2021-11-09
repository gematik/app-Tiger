const dummyServerData = {
  type: 'docker',
  hostname: 'dockerhost1',
  template: null,
  startupTimeoutSec: 20,
  source: [
    "source1",
  ],
  version: "0.0.2",
  dockerOptions: {
    entryPoint: "cmd to delete all",
    proxied: true,
    oneShot: false,
    serviceHealthchecks: [
      "check1",
      "check2"
    ]
  },
  externalJarOptions: {
    workingDir: "workdir",
    healthcheck: "http://127.0.0.1:12345/status.php",
    options: [
      "--standalone",
      "--nogui",
      "--help"
    ],
    arguments: [
      "--arg1",
      "val2"
    ]
  },
  tigerProxyCfg: {
    serverPort: "4455",
    proxiedServer: "IDP RISE in RU",
    proxyPort: "6666",
    proxyProtocol: "HTTPS",
    proxyCfg: {
      proxyRoutes: [
        {
          id: "id1",
          from: "http://orf.at:8008",
          to: "https://ard.de:443",
          internalRoute: true,
          disableRbelLogging: true,
          basicAuth: {
            username: "test1",
            password: "pwd2"
          }
        },
        {
          id: "id2",
          from: "https://zdf.de:8008",
          to: "https://heise.de:443",
          internalRoute: false,
          disableRbelLogging: true,
          basicAuth: {
            username: "test3",
            password: "pwd4"
          }
        },
        {
          id: "id2",
          from: "https://zdf.de:8008",
          to: "https://heise.de:443",
          internalRoute: false,
          disableRbelLogging: false,
        }
      ],
      proxyLogLevel: "TRACE",
      forwardToProxy: {
        hostname: "company proxy",
        port: "3128",
        type: "HTTP"
      },
      tls: {
        serverRootCa: "TODO can we stringify PKIIdentity?",
        forwardMutualTlsIdentity: "TODO",
        serverIdentity: "TODO",
        domainName: "mydomain",
        alternativeNames: [
          "128.0.0.1",
          "localhost2"
        ]
      },
      keyFolders: [
        "folder1",
        "folder2"
      ],
      activateRbelEndpoint: false,
      activateAsn1Parsing: true,
      activateForwardAllLogging: true,
      fileSaveInfo: "TODO",
      port: "TO BE IGNORED TODO",
      skipTrafficEndpointsSubscription: false,
      trafficEndpoints: [
        "endppint1",
        "endppint2"
      ],
      connectionTimeoutInSeconds: 10,
      stompClientBufferSizeInMb: 1,
      perMessageBufferSizeInMb: 100,
      rbelBufferSizeInMb: 1024,
      disableRbelParsing: false,
      modifications: [
        {
          name: "mod1",
          condition: "is_response()",
          targetElement: "$.body.Message...Result.text",
          replaceWith: "OK",
          regexFilter: ".*FAILED.*"
        }
      ]
    }

  },

  environment: [
    "ENV1=test1",
    "ENV2=val2"
  ],
  exports: [
    "sys.prop1=test1",
    "sys.prop2=val2"
  ],
  urlMappings: [
    "http://host1 -> http://host2",
    "https://host3 -> http://host4"
  ]
};

// ONGOING refactor js code

//  TODO: clarify pkiKeys / PkiIdentity struct - can we use simple strings here?

// TODO: EDIT complex list entries

// TODO: readonly management for fieldsets of complex lists

// TODO make serverkey heading editable

// TODO make formular collapsible by clicking on key heading

// TODO LOPRIO refactor section attribute to using name???
// section is only needed for special handling of summary text, if general behaviour is ok with summarypattern,
// then no need for section attribute
// and i use it somewhere else too so reinvestigate

const dollarTokens = /\${([\w|.]+)}/g

let editableContentReset;

//
// jquery helper methods
//
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
  const collapsed = this.find('.collapse-icon > i').hasClass(
      "bi-chevron-right");
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
      + '<i class="bi draghandle bi-list"></i><span><span>' + text
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

//
// formular API methods
//
// TODO clean this method up
$.fn.initFormular = function (serverKey, serverData) {

  //
// fieldset collapsible helper methods
//

  function getCollapseIcon(visible) {
    return '<i class="collapse-icon bi bi-chevron-' +
        (visible ? 'down' : 'right') + '"></i>';
  }

  function populateForm(serverData, path) {
    for (const field in serverData) {
      const value = serverData[field];
      if (value != null && typeof value === 'object' && !Array.isArray(
          value)) {
        populateForm(value, path + "." + field);
        continue;
      }
      const nameStr = path + (path.length === 0 ? "" : ".") + field;
      if (Array.isArray(value)) {
        const elem = serverFormular.find(
            '.list-group[name="' + nameStr + '"]');
        let editable = false;
        let liststr = '';
        if (typeof value[0] === 'object') {
          $.each(value, function (idx, item) {
            liststr += getListItem(elem.generateListItemLabel(item), false,
                true);
          });
          elem.html(liststr);
          $.each(value, function (idx, itemData) {
            $(elem.children()[idx]).data("listdata", itemData);
          });
        } else {
          const fieldSet = elem.parents('fieldset');
          editable = fieldSet.hasClass('editableList');
          $.each(value, function (idx, item) {
            liststr += getListItem(item, false);
          });
          elem.html(liststr);
        }
        elem.find(".list-group-item > span").addClickNKeyCallbacks2ListItem(
            editable);
        continue;
      }
      const elem = serverFormular.find('*[name="' + nameStr + '"]');
      if (elem.length === 0) {
        console.error("UNKNOWN ELEM for " + path + " -> " + field);
        continue;
      }
      elem.setValue(serverData[field]);
    }
  }

  checkTag('initFormular', this, 'FORM');

  const serverFormular = this;

  //
  $(this).html('<div class="row">' + $('#template').html() + '</div>');
  $(this).find('.serverKey').text(serverKey);

  if (!serverData.source) {
    serverData.source = [''];
  }

  //
  // add collapsible icon with empty summary to legends
  //
  serverFormular.find('fieldset > legend').append(
      '&nbsp;<span class="collapse-icon">' + getCollapseIcon(true)
      + '</span>&nbsp;<span class="fieldset-summary fs-6"></span>');

  //
  // initial state of buttons
  //
  serverFormular.find('.btn-list-delete').enabled(false);
  serverFormular.find('.btn-list-apply').enabled(false);

  // disable submit generally and especially on enter key of single input field sections

  serverFormular.submit(false);

  //
  // callbacks
  //

  // collapsible fieldsets

  serverFormular.find('fieldset > legend').click(function () {
    const $divs = $(this).siblings();
    $divs.toggle();
    $(this).find('span.collapse-icon').html(getCollapseIcon(
        $(this).find('.collapse-icon > i').hasClass("bi-chevron-right")));

    $(this).setSummaryFor();
  });

  // sortable lists

  serverFormular.find('fieldset .list-group').sortable({
    handle: 'i'
  });

  // list group items of editable lists editable on single click

  $.each(serverFormular.find('fieldset.editableList .list-group-item > span'),
      function (idx, item) {
        $(item).addClickNKeyCallbacks2ListItem(true);
      });

  // add entry to list for editable lists only

  serverFormular.find('fieldset.editableList .btn-list-add').click(
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

  serverFormular.find('fieldset.complex-list .btn-list-add').click(
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

  // remove entry from list for editable and not editable

  serverFormular.find('fieldset .btn-list-delete').click(function () {
    // TODO select previous list entry Or if no more select next
    $(this).parents('fieldset').find(
        '.list-group .list-group-item.active').remove();
    $(this).enabled(false);
  });

  serverFormular.find('fieldset .btn-list-apply').click(function () {
    const fieldSet = $(this).parents('fieldset');
    fieldSet.updateDataAndLabelForActiveItem(false);
    $(this).enabled(false);
  });

  $.each(serverFormular.find('fieldset.start-collapsed > legend'),
      function (idx, legend) {
        const $divs = $(legend).siblings();
        $divs.toggle();
        $(legend).find('span.collapse-icon').html(getCollapseIcon(false));
      }
  );

  populateForm(serverData, "");

  // set forwardToProxy flag depending on hostname and port being set
  const forwardToProxySection = '.tigerProxyCfg.proxyCfg.forwardToProxy.';
  serverFormular.find('*[name="enableForwardProxy"]').prop('checked',
      serverFormular.find('*[name="' + forwardToProxySection +
          'hostname"]').val() &&
      serverFormular.find('*[name="' + forwardToProxySection + 'port"]').val()
  )

  serverFormular.find('fieldset.start-collapsed > legend').setSummaryFor();

  // if hostname not set default to serverKey
  if (!serverData.hostname) {
    serverFormular.find('*[name="hostname"]').val(serverKey);
  }

  //
  // as for attribute needs unique id and jquery-ui mangles with bootstrap switches
  // we skip the for attribute and add the click via jquery callbacks on the label
  //
  serverFormular.find('.form-check-label').click(function (ev) {
    $(this).parent().find('input').click().change();
    ev.preventDefault();
    return false;
  });

  function enabledTab(tabName, flag) {
    serverFormular.find(
        '.nav-tabs .nav-item[tab="' + tabName + '"] .nav-link').enabled(flag);
  }

  //
  // as nav tabs are based on href links we need to work around as we have multiple formulars
  // on the page, so its betetr to do the switching manually by jquery callback on the nav-item
  serverFormular.find('.nav-tabs > .nav-item').click(function (ev) {
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
  serverFormular.find(
      'fieldset[section=".dockerOptions.serviceHealthchecks"]').addClass(
      'd-none');
  serverFormular.find('*[name="version"]').parent().addClass('d-none');
  serverFormular.find('.nav-tabs .nav-link').enabled(false);

  enabledTab('pkiKeys', true);
  enabledTab('environment', true);
  enabledTab('urlMappings', true);

  // adapt source field according to type (single line for docker, tigerproxy, externalJar, externalUrl, only for docker compose its a list)
  // show version only for tigerProxy and docker
  switch (serverData.type) {
    case 'compose':
      const fieldSet =
          serverFormular.find(
              'fieldset[section=".dockerOptions.serviceHealthchecks"]');
      fieldSet.removeClass('d-none');
      fieldSet.find('legend').click();
      serverFormular.find(
          'fieldset[section=".dockerOptions.dockerSettings"]').addClass(
          'd-none');
      enabledTab('dockerOptions', true);
      this.showTab('dockerOptions');
      break;
    case 'docker':
      serverFormular.find('*[name="version"]').parent().removeClass('d-none');
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
      serverFormular.find('*[name="version"]').parent().removeClass('d-none');
      makeSourceListSingleLineEdit();
      enabledTab('externalJarOptions', true);
      enabledTab('tigerProxy', true);
      this.showTab('tigerProxy');
      break;
  }
}

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

// TODO
// store server formular content to data object

//
// initialization
//
$(function () {
  $('#template').addClass('d-none');

  for (let i = 0; i < 10; i++) {
    $('body .container').append(
        '<form id="formular' + i + '" class="col server-formular"></form>');
  }
  $('#formular0').initFormular("docker", dummyServerData);
  //$('#newform2').showTab("dockerOptions");
  $('#formular1').initFormular("tigerProxy", {type: 'tigerProxy'});
  //$('#newform').showTab("environment");
  $('#formular2').initFormular("externalJar", {type: 'externalJar'});
  $('#formular3').initFormular("externalUrl", {type: 'externalUrl'});
  $('#formular4').initFormular("compose", {type: 'compose'});

  $('#formular5').initFormular("tigerProxy 2", {type: 'tigerProxy'});
  updateServerLists(['server1', 'server_2', "RISE_IDP_in_RU"]);
  updateServerLists(
      ['server1', 'server_2', "RISE_IDP_in_RU", "eRP_IBM_in_PU"]);
});

