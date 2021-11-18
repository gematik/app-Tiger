// ----------------------------------------------------------------------------
//
// formular.js PUBLIC
//
// ----------------------------------------------------------------------------

/* DONE
welcome page, new testenv, type icons
new testenv fixed
introduced unsavedmodification flag
handle save icon on toolbar accordingly
make sure callbacks are registered for welcome section buttons
on rename of serverkey type icon gets removed!
raise flag only if list edit really changes value
on add new testenv ask before deleting existing env if any unsaved
before opening ask to save existing env if any unsaved
moved exception parsing to browser and introduced general error modal
*/

/* before commit
*/

// TODO support textarea for larger input data (PKI I here ya!)
// TODO recheck modification detection on complex lists once default values are implemented
// as of now the yaml from the server does only ocntain attributes which have a value (null are not added)
// so in routes if id is not set its not forwarded at all in the serverYaml struct
// so the mod detection always barks when unactivating it until you applied the settings once

// TODO fix all test data configs (demis no type et all....)

// TODO how to mark settings that are defined in template/overridden

// TODO save to file

// TODO regex based validation pattern TGR-204

// TODO make source fieldset type specific, add extra template for this
//  fieldset and depending on the type copy it to the server formular

// TODO help section probably visualized beneath the side bar showing help text to each hovering input
// or do we do hovering tooltip?

// TODO LOPRIO refactor section attribute to using name???
// section is only needed for special handling of summary text, if general behaviour is ok with summarypattern,
// then no need for section attribute
// and i use it somewhere else too so reinvestigate
// TODO LOPRIO use gutters for label alignment - not sure this really helps in our situation
//  are gutter smore for padding?

const dollarTokens = /\${([\w|.]+)}/g

//
// formular API methods
//

// TODO clean this method up
$.fn.initFormular = function (serverKey, serverData) {
  checkTag('initFormular', this, 'FORM');

  $('.testenv-sidebar-header').fadeIn(500);

  // add copy of template to form and set heading
  $(this).html(
      '<div class="row">' + $('#template-server-formular').html() + '</div>');
  $(this).find('.server-key').text(serverKey);
  $(this).find('.server-icon').addClass(serverIcons[serverData.type]);
  $(this).find('.server-icon').attr('title', serverData.type);

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

  this.find(".server-formular-collapse-icon").attr('title', 'Fold/Unfold');
  this.find(".collapse-icon").attr('title', 'Fold/Unfold');

  this.populateTemplateList();
  this.find('select[name="template"]').val(serverData.template);

  this.find('.advanced').hide();
  this.find(".btn-advanced").attr('title', 'Show advanced settings');

  //
  // callbacks
  //

  // edit heading
  this.find(".server-key").click(function (ev) {
    if ($(this).text() === 'local_proxy') {
      ev.preventDefault();
      return false;
    }
    const editable = $(this).attr("contentEditable");
    if (editable !== 'true') {
      $(this).data("originalContent", $(this).html());
      $(this).attr("contentEditable", true);
      abortOtherEditing();
      $(this).addClass('editing');
      $(this).focus();
      $(this).keydown((ev) => {
        if (ev.keyCode === 13) {
          const text = $(this).text();
          if (text.indexOf(' ') !== -1) {
            snack(
                'No SPACES allowed in server key!<br/>Replacing spaces with underscores!',
                'warning');
            $(this).text(text.replace(' ', '_'));
          } else if (text === 'local_proxy') {
            snack(
                '<p>Sorry \'local_proxy\' is reserved for the test suite\'s local tiger proxy!</p>'
                +
                '<p>Please choose another name!</p>',
                'warning');
            return false;
          }
          const newServerKey = $(this).text();
          $(this).html(newServerKey);
          const oldServerKey = $(this).data('originalContent');
          if (newServerKey !== oldServerKey) {
            if (Object.keys(currEnvironment).indexOf(newServerKey) !== -1) {
              danger('Server key "' + newServerKey + '" already used!');
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
        return $(this).handleEnterEscOnEditableContent(ev);
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
    $(this).parent().siblings(':not(.hidden)').toggle();
    $(this).parent().parent().siblings(':not(.hidden)').toggle();
    $(this).toggleCollapseIcon();
  });

  // advanced fields and fieldsets
  this.find('.btn-advanced.global').click(function () {
    const formular = $(this).parents('.server-formular');
    if ($(this).hasClass('active')) {
      formular.find('.btn-advanced').removeClass('active');
      formular.find('.advanced').fadeOut(600);
      $(this).removeClass('active');
    } else {
      formular.find('.btn-advanced').addClass('active');
      formular.find('.advanced:not(.hidden)').fadeIn(600);
      $(this).addClass('active');
      $.each(formular.find('fieldset'), function () {
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
    $(this).parents('fieldset').find('.advanced:not(.hidden)').fadeToggle(600);
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

  // add buttons to lists
  $(this).find('fieldset.complex-list > .row > .col-list-btns').html(
      $('#template-list-all-buttons').html());
  $(this).find('fieldset.editableList > .row > .col-list-btns').html(
      $('#template-list-add-delete-buttons').html());

  // list button callbacks
  this.find('fieldset.editableList .btn-list-add').click(
      function () {
        const listGroup = $(this).parents('fieldset').find(".list-group");
        listGroup.find('.active').removeClass('active');
        const activeItem = listGroup.find('.active');
        if (activeItem.length === 0) {
          listGroup.prepend(getListItem("", true, false));
        } else {
          $(getListItem("", true, false)).insertAfter(activeItem);
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
        fieldSet.enableSubSetFields(true);
        const listGroup = fieldSet.find(".list-group");

        const activeItem = listGroup.find('.active');
        if (activeItem.length) {
          const origData = activeItem.data("listdata");
          const newData = fieldSet.getNewDataFromSubsetFieldset(false);
          if (!objectDeepEquals(origData, newData)) {
            warn('Aborting other editing');
          }
        }
        listGroup.find('.active').removeClass('active');
        let newItem = $(getListItem("", true, false));

        if (activeItem.length === 0) {
          listGroup.prepend(newItem);
        } else {
          newItem.insertAfter(activeItem);
        }

        const editFieldSet = fieldSet.find('fieldset');
        if (activeItem.length) {
          // if no active item dont skip entered data as its not very user friendly
          fieldSet.updateDataAndLabelForActiveItem(true);
          // TODO respect default value attributes
          editFieldSet.find("*[name][type!='checkbox']").val('');
          editFieldSet.find("*[name][type='checkbox']").prop('checked',
              false);
        }

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
  const proxyHostname = this.find(
      '*[name="' + forwardToProxySection + 'hostname"]').val();
  this.find('*[name="enableForwardProxy"]').prop('checked',
      (proxyHostname && this.find(
          '*[name="' + forwardToProxySection + 'port"]').val()) ||
      (proxyHostname === '$SYSTEM' && serverData.type === 'localProxy')
  )

  // set summary for initially collapsed fieldsets
  this.find('fieldset.start-collapsed > legend').setSummaryFor();

  //
  // as nav tabs are based on href links we need to work around as we have multiple formulars
  // on the page, so its better to do the switching manually by jquery callback on the nav-item
  this.find('.nav-tabs > .nav-item').click(function (ev) {
    if (!$(this).find('.nav-link').attr('disabled')) {
      $(this).parents('form.server-formular').showTab($(this).attr('tab'));
    }
    ev.preventDefault();
    return false;
  });

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

  const source = this.find('fieldset[section="source"]').getValue("source");
  const version = this.find('fieldset[section="source"]').getValue("version");

  // adapt source field according to type (single line for docker, tigerproxy,
  // externalJar, externalUrl, only for docker compose its a list)
  // show version only for tigerProxy and docker
  // noinspection FallThroughInSwitchStatementJS
  switch (serverData.type) {
    case 'compose':
      const fieldSet = this.find(
          'fieldset[section=".dockerOptions.serviceHealthchecks"]');
      fieldSet.fadeIn();
      fieldSet.find('legend').click();
      this.find('fieldset[section=".dockerOptions.dockerSettings"]').addClass(
          'hidden');
      this.showTabLink('dockerOptions', true);
      this.showTab('dockerOptions');
      break;
    case 'docker':
      this.showTabLink('dockerOptions', true);
      this.showTab('dockerOptions');
      this.find('.btn-advanced.global').show();

      this.find('fieldset[section="source"]').replaceWith($("#template-source-single").prop('outerHTML'));
      this.find('*[name="source"]').setValue(source);
      break;
    case 'externalUrl':
      this.find('fieldset[section=".externalJarOptions.options"]').addClass(
          'hidden');
      this.find('fieldset[section=".externalJarOptions.arguments"]').addClass(
          'hidden');
      this.find(
          'input[name=".externalJarOptions.workingDir"]').parent().addClass(
          'hidden');
      this.find('fieldset[section="environment"]').addClass('hidden');

    case 'externalJar':
      this.showTabLink('externalJarOptions', true);
      this.showTab('externalJarOptions');

      this.find('fieldset[section="source"]').replaceWith($("#template-source-single").prop('outerHTML'));
      this.find('*[name="source"]').setValue(source);
      this.find('*[name="version"]').parent().hide();
      break;

      break;
    case 'tigerProxy':
      this.find('*[name="version"]').parent().show();
      this.showTabLink('externalJarOptions', true);
      this.showTabLink('tigerProxy', true);
      this.showTab('tigerProxy');
      this.find('.btn-advanced.global').show();
      this.find('fieldset[section="source"]').replaceWith($("#template-source-select").prop('outerHTML'));
      this.find('*[name="source"]').setValue(source);
      break;
    case 'localProxy':
      this.showTabLink('tigerProxy', true);
      this.showTab('tigerProxy');
      this.find('.btn-advanced.global').show();
      this.showTabLink('pkiKeys', false);
      this.showTabLink('environment', false);
      this.showTabLink('urlMappings', false);
      this.find('input[name=".tigerProxyCfg.serverPort"]').parent().addClass(
          'hidden');
      this.find(
          'select[name=".tigerProxyCfg.proxiedServer"]').parent().addClass(
          'hidden');
      this.find('input[name="version"]').parent().addClass('hidden');
      this.find('fieldset[section="node-settings"]').addClass('hidden');
      this.find('fieldset[section="source"]').addClass('hidden');
      this.find('div.local_proxy_info').removeClass('hidden');
      break;

  }
  this.find('*[name="version"]').setValue(version);

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

  // for single .server-formular
  populateForm: function (serverData, path) {
    checkTag('populateForm', this, 'FORM');
    checkClass('populateForm', this, 'server-formular');
    checkSingle('populateForm', this);

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
            listHtml += getListItem(
                $('<div/>').text(elem.generateListItemLabel(item)).html(),
                false, false);
          });
          elem.html(listHtml);
          $.each(value, function (idx, itemData) {
            $(elem.children()[idx]).data("listdata", itemData);
          });
        } else {
          const fieldSet = elem.parents('fieldset');
          editable = fieldSet.hasClass('editableList');
          $.each(value, function (idx, item) {
            listHtml += getListItem(item, false, false);
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

    this.find('fieldset:not(.subset):not(subset-below)').enableSubSetFields(
        false);

  },
  // for single form.server-formular
  populateTemplateList: function () {
    checkTag('populateTemplateList', this, 'FORM');
    checkClass('populateTemplateList', this, 'server-formular');
    let html = "";
    $.each(currTemplates.templates, (idx, template) => {
      html += '<option value="' + template.templateName + '">'
          + template.templateName.replace("_", " ")
          + '</option>';
    });
    const select = $(this).find('select[name="template"]');
    let selected = select.val();
    select.children().remove();
    select.prepend(html);
    select.val(selected);
  },

  // for single fieldset
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
  // for single fieldset
  isChecked: function (name) {
    checkTag('isChecked', this, 'FIELDSET');
    const elem = this.find("*[name='" + name + "']");
    checkSingle('isChecked', elem);
    return elem.prop("checked");
  },
  // for single fieldset
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
  // for single fieldset
  setObjectFieldInForm: function (data, field, path) {
    checkTag('setObjectFieldInForm', this, 'FIELDSET')
    checkSingle('setObjectFieldInForm', this);
    if (typeof data[field] === "object" && data[field] !== null) {
      for (const child in data[field]) {
        this.setObjectFieldInForm(data[field], child, path + "." + field);
      }
    } else {
      const inputField = this.find("*[name='" + path + "." + field + "']");
      checkSingle('setObjectFieldInForm -> input field', inputField);
      inputField.setValue(data[field]);
    }
  },
  // for single fieldset
  enableSubSetFields: function (state) {
    checkTag('enableSubSetFields', this, 'FIELDSET');
    this.find('fieldset.subset input').tgrEnabled(state);
    this.find('fieldset.subset select').tgrEnabled(state);
    this.find('fieldset.subset .form-switch').tgrEnabled(state);
    this.find('fieldset.subset-below input').tgrEnabled(state);
    this.find('fieldset.subset-below select').tgrEnabled(state);
    this.find('fieldset.subset-below .form-switch').tgrEnabled(state);
  },
  // for single fieldset
  updateDataAndLabelForActiveItem: function (emptyValues) {
    checkTag('updateDataAndLabelForActiveItem', this, 'FIELDSET');
    checkSingle('updateDataAndLabelForActiveItem', this);
    const listGroup = this.find('.list-group');
    let elem = listGroup.find(".list-group-item.active");
    const fieldSet = this.find('fieldset.subset');
    const data = this.getNewDataFromSubsetFieldset(emptyValues)
    if (emptyValues) {
      let notEmpty = false;
      $.each(fieldSet.find("*[name]"), function () {
        const value = fieldSet.getValue($(this).attr('name'));
        if (value) {
          notEmpty = true;
        }
      });
      if (notEmpty) {
        warn('Aborting other editing');
      }
    }
    elem.replaceWith(
        getListItem(
            $('<div/>').text(listGroup.generateListItemLabel(data)).html(),
            true, false
        )
    );
    elem = listGroup.find(".list-group-item.active");
    elem.data("listdata", data);
    elem.find('span:first').addClickNKeyCallbacks2ListItem(false);
  },
  // for single fieldset
  getNewDataFromSubsetFieldset: function (emptyValues) {
    checkTag('getNewDataFromSubsetFieldset', this, 'FIELDSET');
    checkSingle('getNewDataFromSubsetFieldset', this);
    const section = this.attr("section");
    const listGroup = this.find('.list-group');
    let elem = listGroup.find(".list-group-item.active");
    let data = elem.data("listdata");
    // read input from fields into data() struct
    let clonedData;
    if (!data) {
      clonedData = {};
    } else {
      clonedData = {...data};
    }
    this.find('fieldset').find("*[name]").setValueInData(section, clonedData,
        emptyValues);
    return clonedData;
  },

  // for multiple legends
  tgrToggleCollapse: function () {
    checkTag('tgrToggleCollapse', this, 'LEGEND');
    return this.each(function () {
      const collIcon = $(this).find('i.collapse-icon');
      const btnAdvanced = $(this).parents('fieldset').find('.btn-advanced');
      if (collIcon.isCollapsed()) {
        $(this).siblings(':not(.advanced):not(.hidden)').fadeIn();
        if (!$(this).parents('fieldset').find('.btn-advanced').hasClass(
            "active")) {
          $(this).parent().find('.advanced').hide();
        }
      } else {
        btnAdvanced.removeClass('active');
        $(this).siblings(':not(.hidden)').fadeOut();
        $(this).parent().find('.advanced').fadeOut();
      }
      collIcon.toggleCollapseIcon();
      $(this).setSummaryFor();
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
        return pathCursor || " ";
      } else {
        return data[g1] || " ";
      }
    });
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

  // for single editing elem
  handleEnterEscOnEditableContent: function (ev) {
    checkSingle('handleEnterEscOnEditableContent', this)
    if (!$(this).hasClass('editing')) {
      console.log(
          "WARN Used handleEnterEscOnEditableContent on non .editing element "
          + $(this).attr('class'));
      return;
    }
    if (ev.keyCode === 13 || ev.keyCode === 27) {
      if (ev.keyCode === 27) {
        this.html(this.data('originalContent'));
      } else {
        notifyChangesToTestenvData(
            this.data('originalContent') !== this.html());
      }
      this.removeClass('editing');
      this.attr('contentEditable', 'false');
      this.parent().blur();
      this.blur();
      this.off('keydown');
      ev.preventDefault();
      return false;
    }
    return true;
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
});

//
// add callback methods for list items (editable and non editable)
//

function abortOtherEditing() {
  const editing = $('.editing');
  if (editing.length) {
    warn('Aborting other editing');
  }
  $.each(editing, function () {
    $(this).handleEnterEscOnEditableContent({
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
      $(this).click((ev) => {
        $(this).off('keydown');
        if ($(this).attr('contentEditable') !== 'true') {
          $(this).data('originalContent', $(this).html());
          $(this).attr('contentEditable', 'true');
          abortOtherEditing();
          $(this).addClass('editing');
          $(this).parent().focus();
          $(this).parents('.list-group').find('.active').removeClass('active');
          $(this).parent().addClass('active');
          $(this).focus();
          $(this).parents('fieldset').find('.btn-list-delete').tgrEnabled(true);
        }
        $(this).keydown((ev) => {
          return $(this).handleEnterEscOnEditableContent(ev);
        });
        ev.preventDefault();
        return false;
      });
    }

    const listItem = $(this).parent();
    listItem.click(() => {
      if (listItem.hasClass('active')) {
        return true;
      }

      const fieldSet = listItem.parents('fieldset');
      const curActive = listItem.parents('.list-group').find('.active');

      fieldSet.enableSubSetFields(true);

      if (!editable) {
        const origData = curActive.data("listdata");
        if (origData) {
          const newData = fieldSet.getNewDataFromSubsetFieldset(false);
          if (!objectDeepEquals(origData, newData)) {
            warn('Aborting other editing');
          }
        }
      }

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
        $(this).parents('fieldset').find('.btn-list-apply').tgrEnabled(true);
      }
    });
  });
}

function getListItem(text, active, reallySmall) {
  return '<li class="list-group-item ' + (active ? 'active ' : '') +
      (reallySmall ? 'really-small' : '') + '">'
      // TODO why <span><span> ??
      + '<i title="Click to rearrange item in list" class="fas fa-grip-lines draghandle"></i><span><span>'
      + text
      + '</span></span>'
      + '</li>'
}

function objectDeepEquals(obj1, obj2) {
  let props1 = Object.getOwnPropertyNames(obj1);
  let props2 = Object.getOwnPropertyNames(obj2);
  if (props1.length !== props2.length) {
    return false;
  }
  for (let i = 0; i < props1.length; i++) {
    let prop = props1[i];
    let bothAreObjects = typeof (obj1[prop]) === 'object'
        && typeof (obj2[prop]) === 'object';
    if ((!bothAreObjects && (obj1[prop] !== obj2[prop]))
        || (bothAreObjects && !objectDeepEquals(obj1[prop], obj2[prop]))) {
      return false;
    }
  }
  return true;
}
