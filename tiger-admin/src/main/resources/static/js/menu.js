/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

let currEnvironment = {};
/** @namespace currEnvironment.tigerProxyCfg.proxiedServer */
/** @namespace currEnvironment.tigerProxyCfg.proxyCfg.tls */
/** @namespace currEnvironment.tigerProxyCfg.proxyCfg.tls.serverRootCa.fileLoadingInformation */
/** @namespace currEnvironment.tigerProxyCfg.proxyCfg.tls.forwardMutualTlsIdentity.fileLoadingInformation */
/** @namespace currEnvironment.tigerProxyCfg.proxyCfg.tls.serverIdentity.fileLoadingInformation */

let currTemplates = [];
/** @namespace currTemplates.templates */
/** @namespace currTemplates.templates.templateName */

let configScheme = {};

let currFolder = '.';
let fsSeparator = '';
let currFile = '';
let unsavedModifications = false;

const serverIcons = {
  docker: "fab fa-docker",
  compose: "fas fa-cubes",
  tigerProxy: "fas fa-project-diagram",
  localProxy: "fas fa-project-diagram",
  externalJar: "fas fa-rocket",
  externalUrl: "fas fa-external-link-alt"
}

const serverTypeNames = {
  docker: "Docker Container",
  compose: "Docker Compose",
  tigerProxy: "Tiger Proxy",
  externalJar: "External Jar",
  externalUrl: "External URL"

}

// =============================================================================
//
// menu.js
//
//
// =============================================================================
function openYamlFile(path, separator, cfgfile) {
  $.ajax({
    url: "/openYamlFile",
    contentType: "application/json",
    data: {cfgfile: path + separator + cfgfile},
    type: "GET",
    dataType: 'json',
    success: function (res) {
      /** @namespace res.servers */
      currEnvironment = {};
      currEnvironment['local_proxy'] = {
        localProxyActive: res.localProxyActive,
        type: 'localProxy',
        tigerProxyCfg: {proxyCfg: res.tigerProxy}
      };
      for (const key in res.servers) {
        currEnvironment[key] = res.servers[key];
      }
      discardChanges();
      populateServersFromYaml(currEnvironment);
      setYamlFileName(cfgfile, path);
      removeWelcomeCardAndShowSidebar();

      $('.btn-new-testenv').setEnabled(true);
      $('.btn-save-as-testenv').setEnabled(true);
      $('.btn-save-testenv').setEnabled(true);
    },
    error: function (xhr) {
      /** @namespace xhr.responseJSON */
      showError(
          `We are sorry, but we were unable to load your configuration file '${cfgfile}'!`,
          xhr.responseJSON);
    }
  });
}

function setYamlFileName(cfgfile, path) {
  currFile = cfgfile;
  if (!cfgfile) {
    $('.cfg-file-label').text("Unsaved test environment");
    document.title = "Tiger Admin";
  }
  else if (!currFile.endsWith(".yaml") || currFile.length <= ".yaml".length) {
    $('.cfg-file-label').text("Unsaved test environment");
    document.title = "Tiger Admin";
    showError("Please specify a filename with a yaml extension")
  }
  else {
    $('.cfg-file-label').text(cfgfile);
    document.title = `Tiger Admin - ${path}${fsSeparator}${cfgfile}`;
    currFolder = path;
  }
}

function saveYamlFile() {
  const data = {servers: {}};

  $(".server-content.server-container .server-formular").each(
      function (idx, node) {
        const serverKey = $(node).attr("id").substring(
            "content_server_".length);
        data.servers[serverKey] = {};
        $(node).find("*[name]").each(function (idx, field) {
          const $field = $(field);
          if (!$field.hasClass("hidden") && !$field.hasClass("disabled")) {
            $field.saveInputValueInData(null, data.servers[serverKey], false,
                true);
          }
        });

        // remove null properties and properties only containing [] or {}
        removeNullPropertiesAndEmptyArraysOrObjects(data);

        // special handling of some fields
        if (data.servers[serverKey].dependsUpon) {
          if (data.servers[serverKey].dependsUpon.length) {
            data.servers[serverKey].dependsUpon = data.servers[serverKey].dependsUpon.toString();
          } else {
            delete data.servers[serverKey].dependsUpon;
          }
        }
        if (data.servers[serverKey].source && !Array.isArray(
            data.servers[serverKey].source)) {
          data.servers[serverKey].source = [data.servers[serverKey].source];
        }

        delete data.servers[serverKey].enableForwardProxy;
      });

  // special handling for local proxy
  if (data.servers["local_proxy"]
      && data.servers["local_proxy"].tigerProxyCfg) {
    data.tigerProxy = data.servers["local_proxy"].tigerProxyCfg.proxyCfg;
  }
  if (data.servers["local_proxy"]) {
    data.localProxyActive = data.servers["local_proxy"].localProxyActive;
  }
  delete data.servers["local_proxy"];
  if (data.tigerProxy) {
    delete data.tigerProxy.localProxyActive;
  }

    $.ajax({
    url: "/saveYamlFile",
    contentType: "application/json",
    type: "POST",
    processData: false,
    data: JSON.stringify({folder: currFolder, file: currFile, config: data}),
    dataType: 'json',
    success: function () {
        notifyChangesToTestenvData(false);
        let fileSeparator;
        navigator.platform.includes("Win") ? fileSeparator = '\\'
            : fileSeparator = '/';
        snack(`Saved configuration to ${currFolder}${fileSeparator}${currFile}`,
            'info', 3000, true);
    },
    error: function (xhr) {
      /** @namespace xhr.responseJSON */
      showError(
          `We are sorry, but we were unable to save your configuration to file '${currFile}'!`,
          xhr.responseJSON);
    }
  });
}


function isObject(objValue) {
  return objValue && typeof objValue === 'object' && objValue.constructor === Object;
}

function removeNullPropertiesAndEmptyArraysOrObjects(data) {
  Object.keys(data).forEach(function (key) {
    if (Array.isArray(data[key])) {
      if (data[key].length === 0) {
        delete data[key];
      }
    } else if (isObject(data[key])) {
      if (Object.keys(data[key]).length === 0) {
        delete data[key];
      } else {
        removeNullPropertiesAndEmptyArraysOrObjects(data[key]);
        if (Object.keys(data[key]).length === 0) {
          delete data[key];
        }
      }
    } else if (data[key] === null) {
      delete data[key];
    }
  });
}

function populateServersFromYaml(testEnvYaml) {
  for (const serverKey in testEnvYaml) {
    addServer(serverKey, testEnvYaml[serverKey]);
  }
  const serverList = Object.keys(testEnvYaml).sort();
  // update server list fields in all formulars, setting the value from testEnvYaml
  for (const serverKey in testEnvYaml) {
    updateServerListFields(serverList, serverKey, testEnvYaml[serverKey]);
  }
  notifyChangesToTestenvData(false);
  snack(`Loaded yaml file`, 'success');
}

function discardChanges() {
  const serverContent = $('.server-content');
  $('.sidebar').children().remove();
  serverContent.children().remove();

  if (!serverContent.children().length) {
    showWelcomeCard();
  }
  $('.btn-save-as-testenv').setEnabled(false);
}

function addServer(serverKey, serverData) {
  const serverContent = $('.server-content');

  // create server content form tag
  serverContent.append('<form id="content_server_' + serverKey
      + '" class="col server-formular"></form>')
  // init formular with data
  const formular = $(`#content_server_${serverKey}`);

  formular.initFormular(serverKey, serverData);

  formular.find('*[name]').change(function () {
    const required = $(this).attr('required');
    const validation = $(this).attr('validation');
    const value = $(this).val();
    if (required && !value) {
      $(this).addClass('is-invalid');
      return;
    }
    if (validation && !eval(validation)) {
      $(this).addClass('is-invalid');
    } else {
      $(this).removeClass('is-invalid');
      $(this).addClass('is-valid');
    }
    notifyChangesToTestenvData(true);
  });

  // create sidebar entry
  if (serverKey !== 'local_proxy') {
    $('.container.sidebar.server-container').append(
        `<div id="sidebar_server_${serverKey}" class="box sidebar-item row">`
        + '<div class="col-1">'
        + '  <i class="fas fa-grip-lines draghandle"></i>'
        + '</div>'
        + '<div class="col-10">'
        + `  <i title="${serverData.type}" class="server-icon ${serverIcons[serverData.type]}"></i>`
        + `  <span class="server-label">${serverKey}</span>`
        + '</div>'
        + '<div class="col-1 context-menu-one">'
        + '  <i class="fas fa-ellipsis-v"></i>'
        + '</div></div>');
    $(`#sidebar_server_${serverKey} .server-label`).parent().parent().click(
        function (ev) {
          const target = $(ev.target);
          if (!target.hasClass("fa-ellipsis-v") && !target.hasClass(
              "context-menu-one")) {
            window.scrollTo(0,
                formular.position().top - $('.navbar').outerHeight() - 10);
          }
        });
  } else {
    $('#sidebar_server_local_proxy .server-label').parent().click(function () {
      window.scrollTo(0, 0);
    });

  }
}

function updateServerListFields(serverList, serverKey, serverData) {
  const form = $('#content_server_' + serverKey);
  const serverList2 = [...serverList].filter(e => e !== serverKey);
  if (serverData.type === 'tigerProxy') {
    form.updateServerList(serverList2, null,
        serverData.tigerProxyCfg.proxiedServer);
  }
  form.updateDependsUponList(serverList2, null, "");
  if (serverData.dependsUpon) {
    form.find('select[name="dependsUpon"]').val(
        serverData.dependsUpon.split(','));
    form.find('select[name="dependsUpon"]').bsMultiSelect("Update");
  }
}

function addSelectedServer() {
  const addServerModal = $('#add-server-modal');
  const type = addServerModal.find('.list-server-types .active').attr(
      'data-value');
  // hide welcome card
  if (!Object.keys(currEnvironment).length) {
    currEnvironment['local_proxy'] = {
      type: 'localProxy'
    };
    addServer('local_proxy', currEnvironment.local_proxy);
  }

  let index = 1;
  const serverKeys = Object.keys(currEnvironment);
  while (serverKeys.indexOf(type + "_" + String(index).padStart(3, '0'))
  !== -1) {
    index++;
  }
  const newKey = type + "_" + String(index).padStart(3, '0');
  if (serverIcons[type]) {
    currEnvironment[newKey] = {type: type};
  } else {
    let templateData = {};
    currTemplates.templates.forEach(function (tmpl) {
      if (tmpl.templateName === type) {
        templateData = tmpl;
      }
    });
    currEnvironment[newKey] = {...templateData};
    currEnvironment[newKey].hostname = newKey;
    currEnvironment[newKey].template = templateData.templateName;
    delete currEnvironment[newKey].templateName;
  }
  addServer(newKey, {...currEnvironment[newKey]});
  removeWelcomeCardAndShowSidebar();
  $('.btn-new-testenv').setEnabled(true);
  $('.btn-save-as-testenv').setEnabled(true);
  notifyChangesToTestenvData(true);

  // update server list fields, as we add a new server no selection replacement needed, thus no add. params
  const serverList = Object.keys(currEnvironment).sort();
  $.each(serverList, function () {
    const form = $("#content_server_" + this);
    const serverList2 = [...serverList].filter(e => e != this);
    if (currEnvironment[this].type === 'tigerProxy') {
      form.updateServerList(serverList2);
    }
    form.updateDependsUponList(serverList2);
  });
  addServerModal.modal('hide');
  snack(`Added node ${newKey}`, 'success', 5000);
}

function notifyChangesToTestenvData(bModifications) {
  unsavedModifications = bModifications;
  $('.btn-save-testenv').toggleClass('disabled', !bModifications);
  if (bModifications) {
    $('.btn.btn-save-testenv').fadeIn();
  } else {
    $('.btn.btn-save-testenv').fadeOut();
  }
}

function handleOpenTestEnvironmentClick() {
  confirmNoDefault(unsavedModifications, 'Unsaved Modifications',
      'Do you really want to discard current changes?',
      function () {
        openFileOpenDialog(openYamlFile);
      });
}

function showWelcomeCard() {
  $('.btn-new-testenv').setEnabled(false);
  $('.sidebar-col').hide();
  $('.server-content').html($('#template-welcome-card').html());
  $('.server-content .btn-open-testenv').click(handleOpenTestEnvironmentClick);
  $('.server-content .btn-add-server').click(openAddServerModal);
}

function removeWelcomeCardAndShowSidebar() {
  $('.sidebar-col').show();
  $('.sidebar-bottom-toolbar').toggleClass('hidden', false);
  $('.server-content > .card-body').remove();
}

function confirmNoDefault(flag, title, content, yesfunc) {
  if (flag) {
    bs5Utils.Modal.show({
      title: title,
      content:
          '<div>' + content + '</div>',
      buttons: [
        {
          text: 'Yes', class: 'btn btn-outline-primary btn-lg btn-yes',
          handler: (ev) => {
            $(ev.target).parents('.modal.show').modal('hide')
            yesfunc(ev);
          }
        },
        {text: 'No', class: 'btn btn-primary btn-lg btn-no', type: 'dismiss'},
      ],
      centered: true, dismissible: true, backdrop: 'static', keyboard: true,
      focus: false, type: 'white'
    });
  } else {
    yesfunc();
  }
}

function showError(errMessage, errorCauses) {
  /** @namespace errorCauses.mainCause */
  /** @namespace errorCauses.causes */
  let details = null;
  if (errorCauses) {
    details = '<b>' + errorCauses.mainCause + '</b><br/>';
    errorCauses.causes.forEach(function (cause) {
      details += '\n<br/>Caused by: ' + cause;
    });
  }

  bs5Utils.Modal.show({
    title: `Error`,
    content:
        '<div>' + errMessage + '</div>'
        + '<hr class="dropdown-divider" style="display: none;">'
        + '<small><div class="modal-error detailedMessage hidden"></div></small>',
    buttons: [
      {
        text: 'Advanced details',
        class: 'btn btn-info btn-lg' + (details === null ? ' hidden' : ''),
        handler: (ev) => {
          const detMsg = $(ev.target).parents('.modal-dialog').find(
              '.detailedMessage');
          const divider = $(ev.target).parents('.modal-dialog').find(
              '.dropdown-divider');
          detMsg.html(details);
          const btn = $('.btn.btn-sm.btn-info');
          const advDetailsHidden = btn.text() === 'Advanced Details';
          divider.toggle(!advDetailsHidden)
          detMsg.toggle(!advDetailsHidden);
          btn.text((advDetailsHidden ? 'Hide' : 'Show') + ' Details');
        }
      },
      {text: 'Close', class: 'btn btn-primary', type: 'dismiss'}
    ],
    centered: true, dismissible: true, backdrop: 'static', keyboard: true,
    focus: false, type: 'danger'
  });
}

function warn(text, delay, dismissible) {
  snack(text, 'warning', delay, dismissible);
}

function danger(text, delay, dismissible) {
  snack(text, 'danger', delay, dismissible);
}

function snack(text, type, delay, dismissible) {
  if (!type) {
    type = 'warning'
  }
  if (!delay) {
    delay = bs5UtilsDelay5Sec;
  }
  if (!dismissible && dismissible !== false) {
    dismissible = bs5UtilsDismissible;
  }
  bs5Utils.Snack.show(type, text, delay, dismissible);
}

function openFileOpenDialog(okfunc) {
  const filedlg = $('#file-navigation-modal');
  filedlg.find('.modal-title').text("Open file ...");
  filedlg.find(".btn-filenav-ok").hide();
  filedlg.find('.file-navigation-save').hide();
  filedlg.find('.btn-filenav-cancel').click(function () {
    filedlg.modal('hide');
  });
  navigateIntoFolder(currFolder, okfunc, true, 'open');
}

function pressEnterToConfirm(modal, okButton) {
  modal.keydown(function (event) {
    if (event.keyCode === 13) {
      $(okButton).click();
    }
  });
}

function openFileSaveAsDialog(okfunc) {
  const filedlg = $('#file-navigation-modal');
  filedlg.find('.modal-title').text("Save file as ...");
  filedlg.find(".btn-filenav-ok").text("Save as");
  filedlg.find(".btn-filenav-ok").show();
  filedlg.find('.file-navigation-save').show();
  filedlg.find('.file-navigation-save input').val(currFile);
  filedlg.find('.btn-filenav-cancel').off('click');
  filedlg.find('.btn-filenav-cancel').click(function () {
    filedlg.modal('hide');
  });
  navigateIntoFolder(currFolder, okfunc, true, 'save');
  filedlg.find('.btn-filenav-ok').off('click');
  pressEnterToConfirm(filedlg, ".btn-filenav-ok");
  filedlg.find('.btn-filenav-ok').click(function () {
    // get cfgfile from input field together with currentFolder and separator
    console.log(`Saving to file '${
        currFolder}${fsSeparator}${filedlg.find(
        '.file-navigation-save input').val()
    }`);
    notifyChangesToTestenvData(false);
    setYamlFileName(filedlg.find('.file-navigation-save input').val(), currFolder);
    if(currFile.length > ".yaml".length && currFile.endsWith(".yaml")) {
      saveYamlFile();
      filedlg.modal('hide');
    }
  });
}

function navigateIntoFolder(folder, okfunc, addroots, mode) {
  const filedlg = $('#file-navigation-modal');
  $.ajax({
    url: "/navigator/folder",
    contentType: "application/json",
    data: {current: folder},
    type: "GET",
    dataType: 'json',
    success: function (res) {
      /** @namespace res.current */
      /** @namespace res.folders */
      /** @namespace res.cfgfiles */
      /** @namespace res.roots */
      /** @namespace res.separator */
      fsSeparator = res.separator;
      let htmlstr = '';
      res.folders.forEach(function (folder) {
        htmlstr += `<div class="folder text-primary"><i class="far fa-folder icon-right"></i>${folder}</div>`;
      });
      res.cfgfiles.forEach(function (cfgfile) {
        htmlstr += `<div class="cfgfile text-success"><i class="far fa-file-alt icon-right"></i>${cfgfile}</div>`;
      });
      const filepathInput = filedlg.find('.filepath input');
      filepathInput.val(res.current);
      if (res.current.length < 4) {
        filepathInput.css({direction: 'ltr'})
      } else {
        filepathInput.css({direction: 'rtl'})
      }
      currFolder = res.current;
      filedlg.find('.modal-body').html(htmlstr);
      filedlg.find('.folder').click(function () {
        navigateIntoFolder(currFolder + res.separator + $(this).text(), okfunc,
            false, mode);
      });
      filedlg.find('.cfgfile').click(function () {
        if (mode === 'open') {
          okfunc(currFolder, res.separator, $(this).text());
          filedlg.modal('hide');
        } else {
          $('.file-navigation-save input').val($(this).text());
        }
      });
      if (addroots) {
        if (res.roots.length === 1 && !res.roots[0]) {
          console.log("info no filesystem roots, disabling drive button...");
          $('#dropdown-rootfs').parent().hide();
        } else {
          htmlstr = '';
          res.roots.forEach(function (rootfs) {
            htmlstr += `<li><a class="dropdown-item rootfs-item" href="#">${rootfs}</a></li>
`;
          });
          const fsListRootsList = $('.fs-list-roots-list')
          fsListRootsList.children().remove();
          fsListRootsList.prepend(htmlstr);
          fsListRootsList.find('li a').on('click', function () {
            navigateIntoFolder($(this).text(), okfunc, false, mode);
          });
        }
      }
      if (!filedlg.is(':visible')) {
        filedlg.modal('show');
      }
    },
    error: function (xhr) {
      showError('Unable to access Folder/File!', xhr.responseJSON);
    }
  });
}

function openAddServerModal() {
  const addServerModal = $('#add-server-modal');
  addServerModal.find('button.dropdown-toggle').text("Bitte wählen");
  addServerModal.find('.list-server-types .active').removeClass("active");
  addServerModal.find('.info-block').html('');
  addServerModal.find('.info-block').hide();
  addServerModal.find('.btn-add-server-ok').setEnabled(false);
  addServerModal.modal('show');
}

function loadMetaDataFromServer() {
  $.ajax({
    url: "/getTemplates",
    type: "GET",
    dataType: 'json',
    success: function (res) {
      currTemplates = res;
      const addServerModal = $('#add-server-modal');
      addServerModal.find('.btn-add-server-ok').click(addSelectedServer);
      addServerModal.find('.btn-add-server-cancel').click(function () {
        addServerModal.modal('hide');
      });

      const dropdown = addServerModal.find('.list-server-types .dropdown-menu');
      dropdown.children().remove();
      let html = '<li class="p-2 text-center text-success">Servertypen</li>'
      for (let icon in serverIcons) {
        if (icon !== 'localProxy') {
          html += '<li class="dropdown-item p-2 text-success" data-value="'
              + icon + '">'
              + '<i class="server-icon ' + serverIcons[icon] + '"></i>'
              + serverTypeNames[icon] + '</li>';
        }
      }
      html += '<li class="p-2 text-center text-secondary">Testvorlagen</li>';
      currTemplates.templates.forEach(function (template) {
        html += '<li class="dropdown-item p-2 text-secondary" data-value="'
            + template.templateName + '">'
            + '<i class="server-icon ' + serverIcons[template.type] + '"></i>'
            + template.templateName[0].toUpperCase()
            + template.templateName.substr(1) + '</li>';

      })
      dropdown.prepend($(html));
      // single click displays info
      dropdown.find('li.dropdown-item').click(function () {
        $(this).parent().find('.active').removeClass('active');
        $(this).addClass('active');
        addServerModal.find('.info-block').html(
            $('#add-server-modal .info-' + $(this).attr('data-value')).html());
        addServerModal.find('.info-block').show();
        addServerModal.find('.btn-add-server-ok').setEnabled(true);
        addServerModal.find('button.dropdown-toggle').html($(this).html());
        addServerModal.find('.btn-add-server-ok').focus();
      });
      snack('Templates loaded', 'success', 2000);
    },
    error: function (xhr) {
      $('body *').setEnabled(false);
      showError('We are sorry, but we were unable to load the server templates!'
          + '<p>The admin UI is NOT usable!</p><p><b>Please reload the page</b></p>',
          xhr.responseJSON);
    }
  });
  $.ajax({
    url: "/getConfigScheme",
    type: "GET",
    dataType: 'json',
    success: function (res) {
      configScheme = res;
      snack('ConfigScheme loaded', 'success', 2000);
    },
    error: function (xhr) {
      $('body *').setEnabled(false);
      showError('We are sorry, but we were unable to load the config scheme!'
          + '<p>The admin UI is NOT usable!</p><p><b>Please reload the page</b></p>',
          xhr.responseJSON);
    }
  });
}
