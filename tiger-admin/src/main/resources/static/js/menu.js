let currEnvironment = {};
let currTemplates = [];
/** @namespace currTemplates.templates */
/** @namespace currTemplates.templates.templateName */

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
      populateServersFromYaml(currEnvironment);
      setYamlFileName(cfgfile, path);
      $('.btn-save-as-testenv').setEnabled(true);
      $('.btn-save-testenv').setEnabled(true);
    },
    error: function (xhr) {
      /** @namespace xhr.responseJSON */
      showError(`We are sorry, but we were unable to load your configuration file '${cfgfile}'!`,
          xhr.responseJSON);
    }
  });
}

function setYamlFileName(cfgfile, path) {
  currFile = cfgfile;
  if (!cfgfile) {
    $('.cfg-file-label').text("Unsaved test environment");
    document.title = "Tiger Admin";
  } else {
    $('.cfg-file-label').text(cfgfile);
    document.title = `Tiger Admin - ${path}${fsSeparator}${cfgfile}`;
    currFolder = path;
  }
}

function saveYamlFile() {
  // TODO get values from formulars into json struct and send to server together with file/path info
  showError("Unable to save file - NOT implemented!");
}

function populateServersFromYaml(testEnvYaml) {
  const serverContent = $('.server-content');
  $('.sidebar').children().remove();
  serverContent.children().remove();

  for (const serverKey in testEnvYaml) {
    addServer(serverKey, testEnvYaml[serverKey]);
  }

  // update proxied select field in all formulars
  updateServerLists(Object.keys(testEnvYaml));

  if (!serverContent.children().length) {
    showWelcomeCard();
  }
  notifyChangesToTestenvData(false);
  snack(`Loaded yaml file`, 'success');

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
        + '<div class="col-9">'
        + `  <i title="${serverData.type}" class="server-icon ${serverIcons[serverData.type]}"></i>`
        + `  <span class="server-label">${serverKey}</span>`
        + '</div>'
        + '<div class="col-1 context-menu-one">'
        + '  <i class="fas fa-ellipsis-v"></i>'
        + '</div></div>');
    $(`#sidebar_server_${serverKey} .server-label`).parent().parent().click(
        function () {
          window.scrollTo(0, formular.position().top);
        });
  } else {
    $('#sidebar_server_local_proxy .server-label').parent().click(function () {
      window.scrollTo(0, 0);
    });

  }
}

function addSelectedServer() {
  const addServerModal = $('#add-server-modal');
  const type = addServerModal.find('.list-server-types .active').text();
  // hide welcome card
  if (!Object.keys(currEnvironment).length) {
    currEnvironment['local_proxy'] = {
      type: 'localProxy'
    };
    addServer('local_proxy', currEnvironment.local_proxy);
    $('.server-content > .card-body').remove();
  }

  let index = 1;
  const serverKeys = Object.keys(currEnvironment);
  while (serverKeys.indexOf(type + String(index).padStart(3, '0'))
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
  notifyChangesToTestenvData(true);
  updateServerLists(Object.keys(currEnvironment));
  addServerModal.modal('hide');
  snack(`Added node ${newKey}`, 'success', 3000);
}

function notifyChangesToTestenvData(flag) {
  unsavedModifications = flag;
  if (flag) {
    $('.btn.btn-save-testenv').fadeIn();
    $('.btn-save-testenv').removeClass('disabled');
  } else {
    $('.btn.btn-save-testenv').fadeOut();
    $('.btn-save-testenv').addClass('disabled');
  }
}

function handleOpenTestEnvironmentClick() {
  confirmNoDefault(unsavedModifications, 'Unsaved Modifications', 'Do you really want to discard current changes?',
      function () {
        openFileOpenDialog(openYamlFile);
      });
}

function showWelcomeCard() {
  $('.server-content').html($('#template-welcome-card').html());
  $('.server-content .btn-open-testenv').click(handleOpenTestEnvironmentClick);
  $('.server-content .btn-add-server').click(openAddServerModal);
}

function updateServerLists(serverList, replacedSelection, optNewSelection) {
  $('form.server-formular').updateServerList(serverList, replacedSelection,
      optNewSelection);
}

function confirmNoDefault(flag, title, content, yesfunc) {
  if (flag) {
    bs5Utils.Modal.show({
      title: title,
      content:
          '<div>' + content + '</div>',
      buttons: [
        {
          text: 'Yes', class: 'btn btn-danger btn-lg',
          handler: (ev) => {
            $(ev.target).parents('.modal.show').modal('hide')
            yesfunc(ev);
          }
        },
        {text: 'No', class: 'btn btn-primary btn-lg', type: 'dismiss'},
      ],
      centered: true, dismissible: true, backdrop: 'static', keyboard: true,
      focus: false, type: 'danger'
    });
  } else {
    yesfunc();
  }
}

function showError(errMessage, errorCauses) {
  /** @namespace errorCauses.mainCause */
  /** @namespace errorCauses.causes */
  let details = '<b>' + errorCauses.mainCause + '</b><br/>';
  errorCauses.causes.forEach(function (cause) {
    details += '\n<br/>Caused by: ' + cause;
  });
  bs5Utils.Modal.show({
    title: `Error`,
    content:
        '<div>' + errMessage + '</div>'
        + '<hr class="dropdown-divider" style="display: none;">'
        + '<small><div class="modal-error detailedMessage hidden"></div></small>',
    buttons: [
      {
        text: 'Advanced details',
        class: 'btn btn-info btn-lg',
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
  filedlg.modal('show');
  navigateIntoFolder(currFolder, okfunc, true, 'open');
}

function openFileSaveAsDialog(okfunc) {
  const filedlg = $('#file-navigation-modal');
  filedlg.find('.modal-title').text("Save file as ...");
  filedlg.find(".btn-filenav-ok").text("Save as");
  filedlg.find(".btn-filenav-ok").show();
  filedlg.find('.file-navigation-save').show();
  filedlg.find('.file-navigation-save input').val(currFile);
  filedlg.find('.btn-filenav-cancel').click(function () {
    filedlg.modal('hide');
  });
  filedlg.modal('show');
  navigateIntoFolder(currFolder, okfunc, true, 'save');
  filedlg.find('.btn-filenav-ok').click(function () {
    // get cfgfile from input field together with currentFolder and separator
    console.log(`Saving to file '${
        currFolder}${fsSeparator}${filedlg.find(
        '.file-navigation-save input').val()
    }`);
    notifyChangesToTestenvData(false);
    setYamlFileName(filedlg.find('.file-navigation-save input').val(),
        currFolder);
    filedlg.modal('hide');
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
    },
    error: function (xhr) {
      showError('Unable to access Folder/File!', xhr.responseJSON);
    }
  });
}

function openAddServerModal() {
  const addServerModal = $('#add-server-modal');
  addServerModal.find('.info-block').html(
      addServerModal.find('.info-intro-text').html());
  addServerModal.find('.list-server-types .active').removeClass("active");
  addServerModal.find('.btn-add-server-ok').setEnabled(false);
  addServerModal.modal('show');
}

function loadTemplatesFromServer() {
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

      const list = addServerModal.find('.list-server-types');
      list.children().remove();
      let html = '<li class="list-group-item p-2 bg-success text-center text-white">Basic Types</li>';
      for (let icon in serverIcons) {
        if (icon !== 'localProxy') {
          html += '<li class="list-group-item p-2 text-success">'
              + '<i class="server-icon ' + serverIcons[icon] + '"></i>'
              + icon + '</li>';
        }
      }
      html += '<li class="list-group-item p-2 bg-secondary text-center text-white">Templates</li>';
      currTemplates.templates.forEach(function (template) {
        html += '<li class="list-group-item p-2 text-secondary">'
            + '<i class="server-icon ' + serverIcons[template.type] + '"></i>'
            + template.templateName + '</li>';

      })
      list.prepend($(html));
      list.find('.list-group-item:not(.text-white)').click(function () {
        list.find('.active').removeClass("active");
        $('#add-server-modal .info-block').html(
            $('#add-server-modal .info-' + $(this).text()).html());
        $(this).addClass("active");
        addServerModal.find('.btn-add-server-ok').setEnabled(true);
      });
      list.find('.list-group-item:not(.text-white)').dblclick(function (ev) {
        $(this).click();
        addServerModal.find('.btn-add-server-ok').click();
        ev.preventDefault();
        return false;
      });

      snack('Templates loaded', 'success', 1000);
    },
    error: function (xhr) {
      $('body *').setEnabled(false);
      showError('We are sorry, but we were unable to load the server templates!'
          + '<p>The admin UI is NOT usable!</p><p><b>Please reload the page</b></p>',
          xhr.responseJSON);
    }
  });
}
