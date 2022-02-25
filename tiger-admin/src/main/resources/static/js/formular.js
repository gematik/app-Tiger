/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO recheck modification detection on complex lists once default values are implemented
// as of now the yaml from the server does only contain attributes which have a value (null are not added)
// so in routes if id is not set its not forwarded at all in the serverYaml struct
// so the mod detection always barks when "unactivating" it until you applied the settings once

// TODO LOPRIO refactor section attribute to using name???
// section is only needed for special handling of summary text, if general behaviour is ok with summarypattern,
// then no need for section attribute
// and i use it somewhere else too so reinvestigate
// TODO LOPRIO get label size/alignment optimized

//
// formular API methods
//

$.fn.initFormular = function (serverKey, serverData) {
  checkTag('initFormular', this, 'FORM', 'server-formular');

  const $form = this;

  $('.testenv-sidebar-header').fadeIn(500);

  // add copy of template to form and set heading
  $(this).html(`<div>${$('#template-server-formular').html()}</div>`);
  $(this).find('.server-key').text(serverKey);
  $(this).find('.server-icon').addClass(serverIcons[serverData.type]);
  $(this).find('.server-icon').attr('title', serverData.type);
  // add buttons to lists
  const btnsHtml = $('#template-list-all-buttons').html();
  $(btnsHtml).insertBefore($(this).find('fieldset:not(.subset) > .row > .col > .list-group'));
  $(this).find('fieldset.complex-list fieldset.subset .col:last-child').append($('#template-list-apply-button').html());
  // add titles to some elements
  this.find(".server-formular-collapse-icon").attr('title', 'Fold/Unfold');
  this.find(".collapse-icon").attr('title', 'Fold/Unfold');
  this.find(".btn-advanced").attr('title', 'Show advanced settings');

  // deal with source input field special treatment
  // adapt source field according to type (single line for docker, tigerproxy,
  // externalJar, externalUrl, only for docker compose its a list)
  const sourceFieldType = {
    docker: '#template-source-single',
    externalUrl: '#template-source-single',
    externalJar: '#template-source-single'
  }
  switch (serverData.type) {
    case 'compose':
      // empty on purpose as for compose we use the editable list already present
      break;
    case 'localProxy':
    case 'tigerProxy':
      this.showFieldset('source-settings', false);
      break;
    default:
      this.find('fieldset[section="source-settings"]').replaceWith(
          $(sourceFieldType[serverData.type]).prop('outerHTML'));
      this.find('fieldset[section="source-settings"]')[0].removeAttribute('id');
  }

  //
  // callbacks
  //

  // edit heading
  this.find(".server-key").click(initiateEditingServerKeyField);
  // collapsable fieldsets and formular
  this.find('fieldset > legend').click(function () {
    $(this).toggleLegendCollapse();
  });
  this.find('.server-formular-collapse-icon').click(handleNodeCollapse);
  // advanced fields and fieldsets
  this.find('.btn-advanced.global').click(handleGlobalAdvancedButtonClick);
  this.find('.btn-advanced:not(.global)').click(handleSectionAdvancedButtonClick);
  // draggable list items
  this.find('fieldset .list-group').sortable({handle: 'i.draghandle'});
  // list group items of editable lists editable on single click
  this.find('fieldset.editableList .list-group-item > span').each(function () {
    $(this).addClickNKeyCallbacks2ListItem(true);
  });
  // list button callbacks
  this.find('fieldset.editableList .btn-list-add').click(handleAddButtonOnSimpleList);
  this.find('fieldset.complex-list .btn-list-add').click(handleAddButtonOnComplexList);
  this.find('fieldset .btn-list-delete').click(handleDeleteButtonOnList);
  this.find('fieldset .btn-list-apply').click(handleApplyButtonOnList);
  // as for attribute needs unique id and jquery-ui mangles with bootstrap switches
  // we skip the for attribute and add the click via jquery callbacks on the label
  this.find('.form-check-label').click(function (ev) {
    $(this).parent().find('input').click().change();
    ev.preventDefault();
    return false;
  });
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
  // fill in data into form
  //

  //
  // type specific data adaptations pre
  //
  const fileLoadingInformationFields = [
    'serverRootCa',
    'forwardMutualTlsIdentity',
    'serverIdentity'];
  if (serverData.tigerProxyCfg && serverData.tigerProxyCfg.proxyCfg && serverData.tigerProxyCfg.proxyCfg.tls) {
    fileLoadingInformationFields.forEach(field => {
          if (serverData.tigerProxyCfg.proxyCfg.tls[field]) {
            serverData.tigerProxyCfg.proxyCfg.tls[field] = serverData.tigerProxyCfg.proxyCfg.tls[field].fileLoadingInformation;
          }
        }
    );
  }

  const skipDefaultValuesFor = [
    'type', 'key', 'hostname', 'template', 'dependsUpon', 'version',
    'entryPoint', 'workingDir', '.tigerProxyCfg.proxyCfg.port', 'enableForwardProxy',
    '.tigerProxyCfg.proxyCfg.tls.serverRootCa',
    '.tigerProxyCfg.proxyCfg.tls.forwardMutualTlsIdentity',
    '.tigerProxyCfg.proxyCfg.tls.serverIdentity'];
  // preset default values for all fields (except subset fields) in formular
  $(this).find('*[name]').each(function () {
    const fieldName = $(this).attr('name');
    if (!$(this).parents('fieldset.subset').length && this.tagName !== 'UL' &&
        skipDefaultValuesFor.indexOf(fieldName) === -1) {
      const defValue = getDefaultValueFor(fieldName);
      if (defValue !== null) {
        $(this).setValue(defValue);
      }
    }
  });

  this.populateForm(serverData, "", false);
  // default settings
  if (!serverData.hostname) { // if hostname not set default to serverKey
    this.find('*[name="hostname"]').val(serverKey);
  }
  this.populateTemplateList();
  this.find('select[name="template"]').val(serverData.template);

  //
  // type specific data adaptations post
  //
  // set forwardToProxy flag depending on hostname and port being set
  const forwardToProxySection = '.tigerProxyCfg.proxyCfg.forwardToProxy.';
  const proxyHostname = this.find('*[name="' + forwardToProxySection + 'hostname"]').val();
  this.find('*[name="enableForwardProxy"]').prop('checked',
      (proxyHostname && this.find(`*[name="${forwardToProxySection}port"]`).val()) ||
      (proxyHostname === '$SYSTEM' && serverData.type === 'localProxy')
  )

  //
  // show hide Tabs depending on type
  //
  // default hide all to propagate hidden to input fields
  this.find('.nav-tabs .nav-link').each(function (idx, link) {
    $form.showTabLink($(link).parent().attr("tab"), false);
  });

  // default tabs shown for most node types
  this.showTabLink('general', true);
  this.showTabLink('pkiKeys', serverData.type !== 'localProxy');
  this.showTabLink('environment', serverData.type !== 'localProxy');
  this.showTabLink('urlMappings', serverData.type !== 'localProxy');
  // show default tab for each node type
  const defaultTabs = {
    docker: 'dockerOptions',
    compose: 'dockerOptions',
    externalUrl: 'externalJarOptions',
    externalJar: 'externalJarOptions',
    tigerProxy: 'tigerProxy',
    localProxy: 'general'
  }
  this.showTab(defaultTabs[serverData.type]);
  this.showTabLink(defaultTabs[serverData.type], true);
  this.showTabLink('externalJarOptions', defaultTabs[serverData.type] === 'externalJarOptions');
  this.showTabLink('tigerProxy', serverData.type === 'localProxy' || serverData.type === 'tigerProxy');

  //
  // show fieldsets and input groups specific to types
  //
  // show advanced global button for some
  this.find('.btn-advanced.global').toggle(['docker', 'tigerProxy', 'localProxy'].includes(serverData.type));
  // default hide service healthchecks
  this.showFieldset('.dockerOptions.serviceHealthchecks', serverData.type === 'compose');
  // show template only if set
  this.showInputGroup('template', serverData.template);
  // show version only for docker
  this.showInputGroup('version', ['docker'].includes(serverData.type));
  // initialize multiselects
  this.find('select[name="dependsUpon"]').bsMultiSelect();

  switch (serverData.type) {
    case 'compose':
      this.showFieldset('.dockerOptions.dockerSettings', false);
      this.showInputGroup('hostname', false);
      break;
    case 'externalUrl':
      this.showFieldset('.externalJarOptions.options', false);
      this.showFieldset('.externalJarOptions.arguments', false);
      this.showFieldset('environment', false);
      this.showInputGroup('.externalJarOptions.workingDir', false);
      break;
    case 'localProxy':
      this.showFieldset('node-settings', false);
      this.showFieldset('source-settings', false);
      this.showInputGroup('.tigerProxyCfg.serverPort', false);
      this.showInputGroup('.tigerProxyCfg.proxiedServer', false);
      this.find('div.local_proxy_info').removeClass('hidden');
      break;
  }
  this.find('div.local_proxy_info *[name="localProxyActive"]').toggleClass('hidden', serverData.type !== 'localProxy');

  //
  // initial state of buttons / fieldsets / advanced buttons
  //
  this.find('fieldset .btn-list-delete').click(handleDeleteButtonOnList);
  this.find('.btn-list-apply').hide();
  // disable submit generally and especially on enter key of single input field sections
  this.submit(false);
  this.find('fieldset.start-collapsed > legend').toggleLegendCollapse();
  this.find('.advanced').hide();
}

// for multiple form.server-formular
$.fn.showTab = function (tabName) {
  checkTagNClass('showTab', this, 'FORM', 'server-formular');
  this.each(function () {
    $(this).find('.nav-tabs .nav-link').removeClass('active');
    const tabs = $(this).find('.tab-pane');
    tabs.removeClass('active show');
    tabs.hide();
    $(this).find(`.nav-tabs .nav-item[tab="${tabName}"] > .nav-link`)
    .addClass('active');
    const tab = $(this).find('.' + tabName);
    tab.addClass('active show');
    tab.show();
  });
}

$.fn.showTabLink = function (tabName, flag) {
  checkTagNClass('showTabLink', this, 'FORM', 'server-formular');
  this.each(function () {
    const tab = $(this).find(`.nav-tabs .nav-item[tab="${tabName}"] .nav-link`);
    tab.toggle(flag);
    // make all *[name] nodes hidden to ease save job
    $(this).find('.tab-pane.' + tabName + ' *[name]').toggleClass('hidden', !flag);
  });
}

$.fn.showFieldset = function (section, flag) {
  checkTag('showhideFieldsetTab', this, 'FORM');
  checkClass('hideFieldset', this, 'server-formular');
  $(this).find(`fieldset[section="${section}"]`).toggleClass('hidden', !flag);
  $(this).find(`fieldset[section="${section}"] *[name]`).toggleClass('hidden', !flag);
}

$.fn.showInputGroup = function (name, flag) {
  checkTagNClass('hideInputGroup', this, 'FORM', 'server-formular');
  this.each(function () {
    $(this).find(`*[name="${name}"]`).parent().toggleClass('hidden', !flag);
    $(this).find(`*[name="${name}"]`).toggleClass('hidden', !flag);
  });
}

// for form.server-formular
$.fn.updateServerList = function (serverList, optOldSelection, optNewSelection) {
  checkTagNClass('updateServerList', this, 'FORM', 'server-formular');
  let html = '<option value=""></option>\n';
  serverList.filter(key => key !== 'local_proxy').forEach(key => {
    html += `<option value="${key}">${key}</option>\n`;
  });
  replaceSelectOptions($(this).find('select[name=".tigerProxyCfg.proxiedServer"]'), html, optOldSelection,
      optNewSelection);
}

$.fn.updateDependsUponList = function (serverList, optOldSelection, optNewSelection) {
  checkTagNClass('updateDependsUponList', this, 'FORM', 'server-formular');
  let html = "";
  serverList.filter(key => key !== 'local_proxy').forEach(key => {
    html += `<option value="${key}">${key}</option>`;
  });
  replaceSelectOptions($(this).find('select[name="dependsUpon"]'), html, optOldSelection, optNewSelection);
  $(this).find('select[name="dependsUpon"]').bsMultiSelect("Update");
}

function replaceSelectOptions(select, html, optOldSelection, optNewSelection) {
  let selected = select.val();
  select.children().remove();
  select.prepend(html);
  if (Array.isArray(selected)) {
    selected = selected.map(
        entry => ((optOldSelection && entry === optOldSelection) || optOldSelection === null) ?
            optNewSelection : entry);
  } else {
    if ((optOldSelection && selected === optOldSelection) || optOldSelection === null) {
      selected = optNewSelection
    }
  }
  select.val(selected);
  if (selected && select.val() !== selected) {
    console.error(`ERR Unable to select ${optNewSelection} in ${select.attr('name')}`);
  }
}

// ----------------------------------------------------------------------------
//
// formular.js INTERNAL
//
// ----------------------------------------------------------------------------

// for single .server-formular
$.fn.populateForm = function (serverData, path) {
  checkTagNClass('populateForm', this, 'FORM', 'server-formular');
  checkSingle('populateForm', this);

  for (const field in serverData) {
    const value = serverData[field];
    if (value != null && typeof value === 'object' && !Array.isArray(
        value)) {
      this.populateForm(value, `${path}.${field}`);
      continue;
    }
    const nameStr = path + (path.length === 0 ? "" : ".") + field;
    let listHtml = '';
    // for all arrays and for source array onl yif its compose type
    if (Array.isArray(value) &&
        (field !== 'source' || serverData.type === 'compose')) {
      const elem = this.find(`.list-group[name="${nameStr}"]`);
      let editable = false;
      if (typeof value[0] === 'object') {
        $.each(value, function () {
          listHtml += getListItem($('<div/>').text(elem.generateListItemLabel(this)).html(), false);
        });
        elem.html(listHtml);
        $.each(value, function (idx, itemData) {
          $(elem.children()[idx]).data("listdata", itemData);
        });
      } else {
        const fieldSet = elem.closest('fieldset');
        editable = fieldSet.hasClass('editableList');
        $.each(value, function () {
          listHtml += getListItem(this, false);
        });
        elem.html(listHtml);
      }
      elem.find(".list-group-item > span").addClickNKeyCallbacks2ListItem(editable);
    } else {
      const elem = this.find(`*[name="${nameStr}"]`);
      if (elem.length === 0) {
        console.error(`UNKNOWN ELEM for ${path} -> ${field}`);
        continue;
      }
      if (field === 'source') {
        elem.setValue(serverData[field][0]);
      } else {
        elem.setValue(serverData[field]);
      }
    }
  }
  this.find('fieldset:not(.subset)').enableSubSetFields(false);
}

// for single form.server-formular
$.fn.populateTemplateList = function () {
  checkTagNClass('populateTemplateList', this, 'FORM', 'server-formular');
  let html = "";
  $.each(currTemplates.templates, function () {
    html += `<option value="${this.templateName}">${this.templateName.replace("_", " ")}</option>`;
  });
  const select = $(this).find('select[name="template"]');
  let selected = select.val();
  select.children().remove();
  select.prepend(html);
  select.val(selected);
}

