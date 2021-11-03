const serverData = {
  type: 'Docker',
  hostname: 'dockerhost1',
  template: null,
  startupTimeoutSec: 20,
  source: [
    "source1",
    "source2",
    "source3"
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
      modifications: "TODO List<RbelModificationDescription>"
    }

  },

  environment: [
    "ENV1=test1",
    "ENV2=val2"
  ],
  exports: [
    "sys.prop1=test1",
    "sys.prop2=val2"
  ]
};

// TODO set enable forward proxy flag manually after populating the data
// TODO attach json objects to non directly editable lists and define summary patterns for the items in that list

// TODO refactor section attribute to using name???
// TODO adapt source field according to type (single line for docker, tigerproxy, externalJar, externalUrl, only for docker compose its a list)
// TODO hide/collapse service healthchecks unless its a docekr compose type


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

//
// fieldset collapsible helper methods
//

function getCollapseIcon(visible) {
  return '<i class="text-success collapse-icon bi bi-chevron-' + (visible
      ? 'down' : 'right') + '"></i>';
}

//
// fieldset summary helper methods
//

function getValue(fieldset, name) {
  const elem = fieldset.find("*[name='" + name + "']");
  let str;
  if (elem.prop("tagName").toLowerCase() == 'ul') {
    str = getListValue(fieldset, name);
  } else {
    str = fieldset.find("*[name='" + name + "']").val();
  }
  return $('<span>').text(str).html();
}

function isChecked(fieldSet, name) {
  return fieldSet.find("*[name='" + name + "']").is(":checked");
}

function getListValue(fieldset, name) {
  const lis = fieldset.find("ul[name='" + name + "'] > li");
  let csv = "";
  $.each(lis, function (idx, el) {
    csv += $('<span>').text($(el).text()).html() + ", ";
  });
  return csv.substr(0, csv.length - 2);
}

function generateSummary(fieldSet) {
  const summaryPattern = fieldSet.attr("summaryPattern");
  const regex = /\${([\w|\.]+)}/g
  return '<span class="text summary fs-6">' + summaryPattern.replace(regex,
      (m, g1) => getValue(fieldSet, g1) || "&nbsp;") + '</span>';
}

function setSummaryFor(legend) {
  const fieldSet = legend.parent();
  const fsName = fieldSet.attr("section");
  const summarySpan = fieldSet.find("span.fieldset-summary");
  const collapsed = legend.find('.collapse-icon > i').hasClass(
      "bi-chevron-right");
  if (collapsed) {
    switch (fsName) {
      case "node-settings":
      case ".externalJarOptions.externalSettings":
      case ".dockerOptions.dockerSettings":
      case ".tigerProxyCfg":  
      case ".tigerProxyCfg.proxyCfg.tls":
      case ".tigerProxyCfg.proxyCfg.trafficEndpoints":      
        summarySpan.html(generateSummary(fieldSet));
        break;
      case "source":
        summarySpan.html(generateSummary(fieldSet));
        break;
      case ".externalJarOptions.options":
      case ".externalJarOptions.arguments":
      case "environment":
      case "exports":
      case ".dockerOptions.serviceHealthchecks":
        summarySpan.html(
            '<span class="text summary fs-6">' + getListValue(fieldSet, fsName)
            + '</span>');
        break;
      case ".tigerProxyCfg.proxyCfg.forwardToProxy":
        if (isChecked(fieldSet, "enableForwardProxy")) {
          summarySpan.html(generateSummary(fieldSet));
        } else {
          summarySpan.html('<span class="text summary fs-6">DISABLED</span>');
        }
    }
  } else {
    summarySpan.text("");
  }
}

// populate data from yaml object

function getListItem(text, active) {
  return '<li class="list-group-item ' + (active ? 'active' : '') + '">'
      + '<i class="bi draghandle bi-list"></i><span><span>' + text
      + '</span></span>'
      + '</li>'
}

function populateForm(serverData, path) {
  for (field in serverData) {
    const value = serverData[field];
    if (value != null && typeof value === 'object' && !Array.isArray(value)) {
      populateForm(value, path + "." + field);
      continue;
    }
    const nameStr = path + (path.length == 0 ? "" : ".") + field;
    if (Array.isArray(value)) {
      // TODO deal with objects in arrays!! see TODO on top of file

      const elem = $('.list-group[name="' + nameStr + '"]');
      const fieldSet = elem.parents('fieldset');
      const editable = fieldSet.hasClass('editableList');
      let liststr = '';
      $.each(value, function (idx, item) {
        liststr += getListItem(item, false);
      });
      elem.html(liststr);
      $.each(elem.find(".list-group-item > span"), function (index, item) {
        addClickNKeyCallbacks2ListItem($(item), editable);
      });
      continue;
    }      
    const elem = $('*[name="' + nameStr + '"]');
    if (elem.length === 0) {
      console.error("UNKNOWN ELEM for " + path + " -> " + field);
      continue;
    }
    if (elem.attr("type") === "checkbox") {
      elem.attr("checked", serverData[field]);
    } else if (elem[0].tagName === "SELECT") {
      $.each(elem.find('option'), function (idx, opt) {
        $(opt).attr('selected',
            $(opt).text() === serverData[field]);          
      });
    } else {
        if (elem.attr("type") === "number") {
            elem.val(Number(serverData[field]));
        } else {
            elem.val(serverData[field]);
        }
    }
  }
}

//
// add callback methods for list items (editable and non editable)
//

let editableContentReset;

function addClickNKeyCallbacks2ListItem(item, editable) {
  if (editable) {
    item.off("click");
    item.off("keydown");
    item.click(function (ev) {
      $(this).attr("contentEditable", "true");
      $(this).parent().focus();
      $(this).parents('.list-group').find('.active').removeClass('active');
      $(this).parent().addClass('active');
      $(this).selectText();
      editableContentReset = $(this).html();
      $(this).focus();
      var btnDel = $(this).parents('fieldset').find('.btn-list-delete');
      btnDel.attr("disabled", false);
      btnDel.removeClass("disabled");

      ev.preventDefault();
      return false;
    });
    item.keydown(function (ev) {
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

  item.parent().click(function (ev) {
    $(this).parent().find('.active').removeClass('active');
    $(this).addClass('active');
    var btnDel = $(this).parents('fieldset').find('.btn-list-delete');
    btnDel.attr("disabled", false);
    btnDel.removeClass("disabled");
  });
}

//
// initialization
//
$(function () {

  //
  // add collapsible icon with empty summary to legends
  //

  $.each($('fieldset > legend'), function (idx, elem) {
    const $divs = $(elem).siblings();
    $(elem).append('&nbsp;<span class="collapse-icon">' + getCollapseIcon(true)
        + '</span>&nbsp;<span class="fieldset-summary fs-6"></span>');
  });

  //
  // add list groups draggable
  //

  // TODO remove once data is field in via init method
  $.each($('fieldset .list-group .list-group-item'), function (idx, elem) {
    $(elem).html('<i class="bi draghandle bi-list"></i><span>' + $(elem).html()
        + "</span>");
  });

  //
  // initial state of buttons
  //

  const delBtns = $('.btn-list-delete');
  delBtns.enabled(false);
  const notEditableFieldSetLists = $('fieldset:not(.editableList) button');
  notEditableFieldSetLists.enabled(false);

  //
  // callbacks
  //

  // collapsible fieldsets

  $('fieldset > legend').click(function () {
    const $divs = $(this).siblings();
    $divs.toggle();
    $(this).find('.collapse-icon > i').hasClass("bi-chevron-right");

    $(this).find('span.collapse-icon').html(getCollapseIcon(
        $(this).find('.collapse-icon > i').hasClass("bi-chevron-right")));
    setSummaryFor($(this));
  });

  // sortable lists

  $('fieldset .list-group').sortable({
    handle: 'i'
  });

  // list group items editable

  $.each($('fieldset.editableList .list-group-item > span'),
      function (idx, item) {
        addClickNKeyCallbacks2ListItem($(item), true);
      });

  // disable submit generally and especially on enter key of single input field sections

  $('form').submit(false);

  // add entry to list for editable lists only

  $('fieldset.editableList .btn-list-add').click(function (ev) {
    const fieldSet = $(this).parents('fieldset');
    const listGroup = fieldSet.find(".list-group");
    const activeItem = listGroup.find('.active');

    listGroup.find('.active').removeClass('active');
    if (activeItem.length === 0) {
      listGroup.prepend(getListItem("", true));
    } else {
      $(getListItem("", true)).insertAfter(activeItem);
    }
    $.each(listGroup.find('.list-group-item > span'), function (idx, item) {
      addClickNKeyCallbacks2ListItem($(item), true);
    });
    // start editing
    listGroup.find('.active > span').click();
  });

  // remove entry from list for editable and not editable

  $('fieldset .btn-list-delete').click(function (ev) {
    // TODO select previous list entry Or if no more select next
    $(this).parents('fieldset').find(
        '.list-group .list-group-item.active').remove();
    $(this).enabled(false);
  });

  $('.tabbedpanel a.nav-link').click(function (ev) {
    $(this).find('fieldset > legend > .collapse-icon').html(
        getCollapseIcon(true));
  });

  $.each($('fieldset.start-collapsed > legend'), function (idx, legend) {
    const $divs = $(legend).siblings();
    $divs.toggle();
    $(legend).find('span.collapse-icon').html(getCollapseIcon(false));
  });

  populateForm(serverData, "");

  $.each($('fieldset.start-collapsed > legend'), function (idx, legend) {
    setSummaryFor($(legend));
  });

    

});
