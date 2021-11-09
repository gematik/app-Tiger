// ----------------------------------------------------------------------------
//
// formular.js PUBLIC
//
// ----------------------------------------------------------------------------

// TODO default structure via backend -> Yana, Julian
// TODO add server -> Yana, wizard or deluxe dropdown based, choose between the types,
//  templates and use cases, each one has a sophisticated explanation
//  visualized besides the entry
// ONGOING bootstrap sass -> Yana

// New tasks -> Yana/Anne
// TODO get all templates via backend
// TODO change sidebar blocks to have a handle for dragndrop and when only clicking
//  on it scroll to server formular
// TODO make source fieldset type specific, add extra template for this
//  fieldset and depending on the type copy it to the server formular

// ONGOING refactor js code
// DONE when updating server list make sure to keep selected items
// DONE make serverkey heading editable
// DONE more clearly mark editableactive content
// TODO: clarify pkiKeys / PkiIdentity struct - can we use simple strings here? YES implement csv based values
// TODO: EDIT complex list entries
// TODO: readonly management for fieldsets of complex lists
// TODO template support (depends on default structure and templates via backend)
// TODO how to mark settings that are defined in template/overridden
// TODO help section probably visualized beneath the side bar showing help text to each hovering input
// or do we do hovering tooltip?

// TODO LOPRIO refactor section attribute to using name???
// section is only needed for special handling of summary text, if general behaviour is ok with summarypattern,
// then no need for section attribute
// and i use it somewhere else too so reinvestigate
// TODO LOPRIO use gutters for label alignment - not sure this really helps in our situation
//  are gutter smore for padding?
// TODO after implementing start/stop of servers color sidebar box elements to show state of server
// TODO click on sidebar box scrolls element into view but heading is hidden by fixed top navbar

const dollarTokens = /\${([\w|.]+)}/g

//
// formular API methods
//

// TODO clean this method up
$.fn.initFormular = function (serverKey, serverData) {
  checkTag('initFormular', this, 'FORM');

  // add copy of template to form and set heading
  $(this).html(
      '<div class="row">' + $('#template-server-formular').html() + '</div>');
  $(this).find('.server-key').text(serverKey);

  // default settings
  if (!serverData.source) { // at least one empty line for source as we have
    // types where no add btn is visible
    serverData.source = [''];
  }
  if (!serverData.hostname) { // if hostname not set default to serverKey
    this.find('*[name="hostname"]').val(serverKey);
  }

  // collapse all fieldsets that should be collapsed on start
  this.find('fieldset.start-collapsed > legend').tgrToggleCollapse();

  this.find('.advanced').hide();

  //
  // callbacks
  //

  // edit heading
  this.find(".server-key").click(function () {
    const editable = $(this).attr("contentEditable");
    if (editable !== 'true') {
      $(this).data("originalContent", $(this).html());
      $(this).attr("contentEditable", true);
      abortOtherEditing();
      $(this).addClass('editing');
      $(this).focus();
      $(this).keydown((ev) => {
        if (ev.keyCode === 13) {
          const newServerKey = $(this).text();
          $(this).html(newServerKey);
          const oldServerKey = $(this).data('originalContent');
          if (newServerKey !== oldServerKey) {
            if (Object.keys(currEnvironment).indexOf(newServerKey) !== -1) {
              bs5Utils.Snack.show('danger',
                  'Server key "' + newServerKey + '" already used!',
                  delay = 10000, dismissible = true);
              ev.keyCode = 27;
            } else {
              const sidebarHandle = $('#sidebar_server_' + oldServerKey);
              sidebarHandle.attr('id', 'sidebar_server_' + newServerKey);
              sidebarHandle.find('.server-label').text(newServerKey);
              const srvContentHandle = $('#content_server_' + oldServerKey);
              srvContentHandle.attr('id', 'content_server_' + newServerKey);
              currEnvironment[newServerKey] = currEnvironment[oldServerKey];
              delete currEnvironment[oldServerKey];
              updateServerLists(Object.keys(currEnvironment), oldServerKey,
                  newServerKey);
            }
          }
        }
        $(this).off('paste');
        return handleEnterEscOnEditableContent($(this), ev);
      });
      $(this).on('paste', function (ev) {
        // TODO check to insert only the text part and NO tags!
      });
    }
  });

  // collapsable fieldsets and formular
  this.find('fieldset > legend').click(function () {
    $(this).tgrToggleCollapse();
  });
  this.find('.server-formular-collapse-icon').click(function () {
    $(this).parent().siblings().toggle();
    $(this).parent().parent().siblings().toggle();
    $(this).toggleCollapseIcon();
  });

  this.find('.btn-advanced.global').click(function () {
    const formular = $(this).parents('.server-formular');
    if ($(this).hasClass('active')) {
      formular.find('.btn-advanced').removeClass('active');
      formular.find('.advanced').fadeOut(600);
      $(this).removeClass('active');
    } else {
      formular.find('.btn-advanced').addClass('active');
      formular.find('.advanced').fadeIn(600);
      $(this).addClass('active');
      $.each(formular.find('fieldset'), function () {
        // TODO if fieldset is collapsed and contains advanced fields uncollapse
        if ($(this).find('i.collapse-icon').isCollapsed() &&
            $(this).find('.advanced').length) {
          $(this).find('legend').tgrToggleCollapse();
        }
      })
    }
  });

  this.find('.btn-advanced:not(.global)').click(function (ev) {
    ev.preventDefault();
    if ($(this).parent().find('.collapse-icon').isCollapsed()) {
      return false;
    }
    $(this).parents('fieldset').find('.advanced').fadeToggle(600);
    $(this).toggleClass('active');
    return false;
  });

  // draggable list items
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
        const listGroup = $(this).parents('fieldset').find(".list-group");
        listGroup.find('.active').removeClass('active');
        const activeItem = listGroup.find('.active');
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
        fieldSet.find(".btn-list-apply").tgrEnabled(true);
        fieldSet.find(".btn-list-delete").tgrEnabled(true);
      });
  this.find('fieldset .btn-list-delete').click(function () {
    // TODO select previous list entry Or if no more select next
    $(this).parents('fieldset').find(
        '.list-group .list-group-item.active').remove();
    $(this).tgrEnabled(false);
  });
  this.find('fieldset .btn-list-apply').click(function () {
    const fieldSet = $(this).parents('fieldset');
    fieldSet.updateDataAndLabelForActiveItem(false);
    $(this).tgrEnabled(false);
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
  this.find('.btn-list-delete').tgrEnabled(false);
  this.find('.btn-list-apply').tgrEnabled(false);
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

  //
  // as nav tabs are based on href links we need to work around as we have multiple formulars
  // on the page, so its betetr to do the switching manually by jquery callback on the nav-item
  this.find('.nav-tabs > .nav-item').click(function (ev) {
    if (!$(this).find('.nav-link').attr('disabled')) {
      $(this).parents('form.server-formular').showTab($(this).attr('tab'));
    }
    ev.preventDefault();
    return false;
  });

  function makeSourceListSingleLineEdit(serverFormular) {
    serverFormular.find('fieldset[section="source"]').find('button').hide()
    serverFormular.find('fieldset[section="source"] .list-group').css(
        {minHeight: '2.5rem'})
  }

  //
  // type specific UI adaptations
  //
  // default hide service healthchecks
  this.find('fieldset[section=".dockerOptions.serviceHealthchecks"]').hide()
  // default hide version
  this.find('*[name="version"]').parent().hide();

  // default hide all but pki, env, urlmappings
  this.find('.nav-tabs .nav-link').hide();
  this.showTabLink('pkiKeys', true);
  this.showTabLink('environment', true);
  this.showTabLink('urlMappings', true);

  this.find('.btn-advanced.global').hide();

  // adapt source field according to type (single line for docker, tigerproxy,
  // externalJar, externalUrl, only for docker compose its a list)
  // show version only for tigerProxy and docker
  switch (serverData.type) {
    case 'compose':
      const fieldSet =
          this.find(
              'fieldset[section=".dockerOptions.serviceHealthchecks"]');
      fieldSet.fadeIn();
      fieldSet.find('legend').click();
      this.find('fieldset[section=".dockerOptions.dockerSettings"]').hide();
      this.showTabLink('dockerOptions', true);
      this.showTab('dockerOptions');
      break;
    case 'docker':
      this.find('*[name="version"]').parent().show();
      makeSourceListSingleLineEdit(this);
      this.showTabLink('dockerOptions', true);
      this.showTab('dockerOptions');
      break;
    case 'externalJar':
    case 'externalUrl':
      this.showTabLink('externalJarOptions', true);
      makeSourceListSingleLineEdit(this);
      this.showTab('externalJarOptions');
      break;
    case 'tigerProxy':
      this.find('*[name="version"]').parent().show();
      makeSourceListSingleLineEdit(this);
      this.showTabLink('externalJarOptions', true);
      this.showTabLink('tigerProxy', true);
      this.showTab('tigerProxy');
      this.find('.btn-advanced.global').show();
      break;
  }
}

// for multiple form.server-formular
$.fn.showTab = function (tabName) {
  checkTag('showTab', this, 'FORM');
  checkClass('showTab', this, 'server-formular');
  this.each(function () {
    $(this).find('.nav-tabs .nav-link').removeClass('active');
    const tabs = $(this).find('.tab-pane');
    tabs.removeClass('active');
    tabs.removeClass('show');
    tabs.hide();
    $(this).find(
        '.nav-tabs .nav-item[tab="' + tabName + '"] > .nav-link').addClass(
        'active');
    const tab = $(this).find('.' + tabName);
    tab.addClass('active');
    tab.show();
    tab.addClass('show');
  });
}

$.fn.showTabLink = function (tabName, flag) {
  checkTag('showTab', this, 'FORM');
  checkClass('showTab', this, 'server-formular');
  this.each(function () {
    if (flag) {
      $(this).find('.nav-tabs .nav-item[tab="' + tabName + '"] .nav-link')
      .show();
    } else {
      $(this).find('.nav-tabs .nav-item[tab="' + tabName + '"] .nav-link')
      .hide();
    }
  });
}

// for form.server-formular
$.fn.updateServerList = function (serverList, replacedSelection,
    optNewSelection) {
  checkTag('updateServerList', this, 'FORM');
  checkClass('updateServerList', this, 'server-formular');
  let html = "";
  $.each(serverList, (idx, server) => {
    html += '<option value="' + server + '">' + server.replace("_", " ")
        + '</option>';
  });
  const select = $(this).find('select[name=".tigerProxyCfg.proxiedServer"]');
  let selected = select.val();
  select.children().remove();
  select.prepend(html);
  if (replacedSelection && selected === replacedSelection) {
    selected = optNewSelection
  }
  select.val(selected);
}

// ----------------------------------------------------------------------------
//
// formular.js INTERNAL
//
// ----------------------------------------------------------------------------

// internal helper methods (Do not use unless discussed with Thomas)

function checkSingle(method, $elem) {
  if ($elem.length > 1) {
    throw new Error("Only single items supported!");
  }
}

function checkTag(method, $elem, tagName) {
  $.each($elem, (idx, item) => {
    if (item.tagName !== tagName) {
      throw new Error(method + ' is only for ' + tagName + ' items! (used on '
          + item.tagName + ')');
    }
  });
}

function checkInputField(method, $elem) {
  $.each($elem, (idx, item) => {
    if (item.tagName !== 'INPUT' && item.tagName !== 'SELECT' &&
        $(item).attr('class').indexOf('list-group') === -1) {
      throw new Error(method + ' is only for named input items! (used on '
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

// any
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

$.fn.extend({
// for single fieldset
  isChecked: function (name) {
    checkTag('isChecked', this, 'FIELDSET');
    const elem = this.find("*[name='" + name + "']");
    checkSingle('isChecked', elem);
    return elem.prop("checked");
  },

  // for multiple legends
  tgrToggleCollapse: function () {
    checkTag('tgrToggleCollapse', this, 'LEGEND');
    return this.each(function () {
      const collIcon = $(this).find('i.collapse-icon');
      const btnAdvanced = $(this).parents('fieldset').find('.btn-advanced');
      if (collIcon.isCollapsed()) {
        $(this).siblings(':not(.advanced)').fadeIn();
        if (!$(this).parents('fieldset').find('.btn-advanced').hasClass(
            "active")) {
          $(this).parent().find('.advanced').hide();
        }
      } else {
        btnAdvanced.removeClass('active');
        $(this).siblings().fadeOut();
        $(this).parent().find('.advanced').fadeOut();
      }
      collIcon.toggleCollapseIcon();
      $(this).setSummaryFor();
    });
  },
  // for multiple any
  tgrEnabled: function (enabled) {
    return this.each(function () {
      $(this).attr('disabled', !enabled);
      if (enabled) {
        $(this).removeClass('disabled');
      } else {
        $(this).addClass('disabled');
      }
    });
  },
  // for single or none i
  isCollapsed: function () {
    if (!this.length) {
      return false;
    }
    checkSingle('isCollapsed', this);
    checkTag('isCollapsed', this, 'I');
    const findState = /(.*)(-down|-right)(.*)/;
    const clz = this.attr('class');
    const result = clz.match(findState);
    return result[2] === '-right';
  },
  // for multiple input or select
  setValue: function (value) {
    if (!this.length) {
      throw new Error('Trying to set value on not found item! (value was '
          + value + ')');
    }
    checkInputField('setValue', this);
    return this.each(function () {
      if ($(this).attr("type") === "checkbox") {
        if (!value) {
          value = false;
        }
        $(this).prop("checked", value);
      } else if (this.tagName === "SELECT") {
        $.each($(this).find('option'), function (idx, opt) {
          $(opt).attr('selected', false);
        });
        if (value) {
          $.each($(this).find('option'), function (idx, opt) {
            $(opt).attr('selected',
                $(opt).text() === value);
          });
        }
      } else {
        if ($(this).attr("type") === "number") {
          if (value) {
            $(this).val(Number(value));
          } else {
            $(this).val('');
          }
        } else {
          if (value) {
            $(this).val(value);
          } else {
            $(this).val('');
          }
        }
      }
    });
  },
  // for multiple legend
  setSummaryFor: function () {
    checkTag('setSummaryFor', this, 'LEGEND');
    return this.each(function () {
      const fieldSet = $(this).parent();
      const fsName = fieldSet.attr("section");
      const summarySpan = fieldSet.find("span.fieldset-summary");
      const collapsed = $(this).find('i.collapse-icon').isCollapsed();
      if (collapsed) {
        switch (fsName) {
          case "source":
            summarySpan.html(fieldSet.generateSummary());
            break;
          case ".tigerProxyCfg.proxyCfg.forwardToProxy":
            if (fieldSet.isChecked("enableForwardProxy")) {
              summarySpan.html(fieldSet.generateSummary());
            } else {
              summarySpan.html(
                  '<span class="text summary fs-6">DISABLED</span>');
            }
            break;
          default:
            summarySpan.html(fieldSet.generateSummary());
            break;
        }
      } else {
        summarySpan.text("");
      }
    });
  },
  // for fieldset
  generateSummary: function () {
    checkTag('generateSummary', this, 'FIELDSET');
    const summaryPattern = this.attr("summaryPattern");
    return '<span class="text summary fs-6">' + summaryPattern.replace(
        dollarTokens,
        (m, g1) => this.getValue(g1) || "&nbsp;") + '</span>';
  },
  // for single fieldset
  getValue: function (name) {
    checkTag('getValue', this, 'FIELDSET');
    checkSingle('getValue', this);
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
  },
  getListValue: function (name) {
    checkTag('getListValue', this, 'FIELDSET');
    checkSingle('getListValue', this);
    const lis = this.find("ul[name='" + name + "'] > li");
    let csv = "";
    $.each(lis, function (idx, el) {
      csv += $('<span>').text($(el).text()).html() + ",<br/>";
    });
    if (csv === "") {
      return "No entries";
    }
    return csv.substr(0, csv.length - ",<br/>".length);
  },
  // for single .list-group
  generateListItemLabel: function (data) {
    checkClass('generateListItemLabel', this, 'list-group');
    checkSingle('generateListItemLabel', this);
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
  },
  // for single fieldset
  setObjectFieldInForm: function (data, field, path) {
    checkTag('setObjectFieldInForm', this, 'FIELDSET')
    checkSingle('setObjectFieldInForm', this);
    if (typeof data[field] === "object" && data[field] !== null) {
      for (const child in data[field]) {
        this.setObjectFieldInForm((data[field]), child, path + "." + field);
      }
    } else {
      const inputField = this.find("*[name='" + path + "." + field + "']");
      checkSingle('setObjectFieldInForm -> input field', inputField);
      inputField.setValue(data[field]);
    }
  },
  // for multiple input fields
  setValueInData: function (section, data, emptyValues) {
    checkInputField('setValueInData', this);
    return this.each(function () {
      let fieldName = $(this).attr('name').substring(section.length + 1);
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
        if ($(this).attr('type') === 'checkbox') {
          pathCursor[fieldName] = false;
        } else {
          pathCursor[fieldName] = null;
        }
      } else {
        if ($(this).attr('type') === 'checkbox') {
          pathCursor[fieldName] = $(this).prop('checked');
        } else {
          pathCursor[fieldName] = $(this).val();
        }
      }

    });
  },
  // for multiple i
  toggleCollapseIcon: function (state) {
    checkTag('toggleCollapseIcon', this, 'I');
    return this.each(function () {
      const findState = /(.*)(-down|-right)(.*)/;
      let clz = $(this).attr('class');
      if (typeof state === 'undefined') {
        state = !$(this).isCollapsed();
      }
      if (state) {
        clz = clz.replace(findState, (m, g1, g2, g3) => g1 + '-right' + g3);
      } else {
        clz = clz.replace(findState, (m, g1, g2, g3) => g1 + '-down' + g3);
      }
      $(this).attr('class', clz);
    });
  }

});

//
// add callback methods for list items (editable and non editable)
//

function abortOtherEditing() {
  if ($('.editing').length) {
    bs5Utils.Snack.show('warning', 'Aborting other editing',
        delay = 5000, dismissible = true);
  }
  $.each($('.editing'), function () {
    handleEnterEscOnEditableContent($(this), {
      keyCode: 27, preventDefault: function () {
      }
    });
  });
}

// for multiple span in .list-group-item
$.fn.addClickNKeyCallbacks2ListItem = function (editable) {
  checkTag('addClickNKeyCallbacks2ListItem', this, 'SPAN')
  this.each(function () {
    if (editable) {
      $(this).off('click');
      $(this).off('keydown');
      $(this).click((ev) => {
        if ($(this).attr('contentEditable') !== 'true') {
          $(this).data('originalContent', $(this).html());
          $(this).attr('contentEditable', 'true');
          abortOtherEditing();
          $(this).addClass('editing');
          $(this).parent().focus();
          $(this).parents('.list-group').find('.active').removeClass('active');
          $(this).parent().addClass('active');
          $(this).focus();
          const btnDel = $(this).parents('fieldset').find('.btn-list-delete');
          btnDel.attr('disabled', false);
          btnDel.removeClass('disabled');
        }
        $(this).keydown((ev) => {
          return handleEnterEscOnEditableContent($(this), ev);
        });
        ev.preventDefault();
        return false;
      });
    }

    const listItem = $(this).parent();
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
        // reset all fields as currently we receive yaml struct without null attributes
        fieldSet.find('input[name][type!="checkbox"]').val('');
        fieldSet.find('input[name][type="checkbox"]').prop('checked', false);
        fieldSet.find('select[name]').val('');
        let data = listItem.data("listdata");
        for (const field in data) {
          fieldSet.setObjectFieldInForm(data, field, section);
        }
      }
    });
  });
}

function handleEnterEscOnEditableContent($elem, ev) {
  if (ev.keyCode === 13 || ev.keyCode === 27) {
    if (ev.keyCode === 27) {
      $elem.html($elem.data('originalContent'));
    }
    $elem.removeClass('editing');
    $elem.attr('contentEditable', 'false');
    $elem.parent().blur();
    $elem.blur();
    $elem.off('keydown');
    ev.preventDefault();
    return false;
  }
  return true;
}

function getListItem(text, active, reallySmall) {
  return '<li class="list-group-item ' + (active ? 'active ' : '') +
      (reallySmall ? 'really-small' : '') + '">'
      + '<i class="fas fa-grip-lines draghandle"></i><span><span>' + text
      + '</span></span>'
      + '</li>'
}

// for single fieldset TODO
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

// for single .server-formular TODO
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
