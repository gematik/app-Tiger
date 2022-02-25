/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

// =============================================================================
//
// initialization.js
//
// =============================================================================
let bs5Utils;

const bs5UtilsDismissible = true;
const bs5UtilsDelay5Sec = 5000;

$(document).ready(function () {
  Bs5Utils.defaults.toasts.position = 'bottom-right';
  Bs5Utils.defaults.toasts.container = 'toast-container';
  Bs5Utils.defaults.toasts.stacking = true;
  /** @namespace Bs5Utils.defaults */
  bs5Utils = new Bs5Utils();

  loadMetaDataFromServer();

  // sidebar

  $.contextMenu({
    selector: '.context-menu-one',
    trigger: 'left',
    callback: function (key/*, options*/) {
      const sidebarItem = $(this).parents('.sidebar-item');
      const serverIndex = sidebarItem.attr("id").substr("sidebar_server_".length);

      switch (key) {
        case "start":
        case "restart":
        case "stop":
          danger('TODO feature ' + key + ' NOT implemented so far!');
          break;
        case "duplicate":
          // get name for new node
          // check if at least one digit is last char(s)
          // parse to number and increase by one, check if exists repeat until non existing name found
          let namePrefix = serverIndex + "_";
          let ctr = 0;
          let dash = serverIndex.lastIndexOf("_");
          if (dash !== -1) {
            ctr = Number(serverIndex.substr(dash+1));
            namePrefix = serverIndex.substr(0, dash + 1);
          }
          const serverList = Object.keys(currEnvironment).sort();
          ctr++;
          while (serverList.includes(namePrefix + String(ctr).padStart(3, '0'))) {
            ctr++;
          }
          // deep copy node
          // check how to best do deep copy, maybe JSON.stringify? or lodash?
          let newNode = JSON.parse(JSON.stringify(currEnvironment[serverIndex]));
          const serverKey = namePrefix + String(ctr).padStart(3, '0');
          newNode.hostname = serverKey;
          currEnvironment[serverKey] = newNode;
          // add new node
          addServer(serverKey, newNode);
          serverList.push(serverKey)
          updateServerListFields(serverList, serverKey, newNode);
          break;
        case "delete":
          confirmNoDefault(true, 'Delete node ' + serverIndex, 'Do you really want to delete this node?', function() {
            sidebarItem.remove();
            $("#content_server_" + serverIndex).remove();
            delete currEnvironment[serverIndex];
            warn('Server "' + serverIndex + '" removed!');

            // update server lists removing any selection of the currently deleted node
            const serverList = Object.keys(currEnvironment).sort();
            $.each(serverList, function () {
              const form = $("#content_server_" + this);
              const serverList2 = [...serverList].filter(e => e != this);
              if (currEnvironment[this].type === 'tigerProxy') {
                form.updateServerList(serverList2, serverIndex, null);
              }
              form.updateDependsUponList(serverList2, serverIndex, null);
            });
          });
          break;
          /*
          case "logs":
            break;
           */
      }
    },
    items: {
      "duplicate": {name: "Duplicate", icon: "far text-success fa-clone ctxt-duplicate"},
      "delete": {name: "Delete", icon: "fas text-danger fa-trash-alt ctxt-delete"}
    }
  });

  // noinspection JSUnusedGlobalSymbols
  $(".sidebar.server-container").sortable({
    start: function (e, ui) {
      $(this).attr('data-previndex', ui.item.index());
    },
    handle: 'i',
    update: function (e, ui) {
      const newIndex = ui.item.index();
      const oldIndex = $(this).attr('data-previndex');
      const serverId = $(ui.item).attr("id").substr("sidebar_".length);
      const content = $("#content_" + serverId);

      $(this).removeAttr('data-previndex');
      // +1 as we have added the local tiger proxy as server-content block!
      if (newIndex > oldIndex) {
        content.insertAfter($(".server-content").children()[newIndex + 1]);
      } else {
        content.insertBefore($(".server-content").children()[newIndex + 1]);
      }
      window.scrollTo(0, content.position().top - $('.navbar').outerHeight() - 10);
    }
  });

  // top menu

  $('.btn-open-testenv').click(handleOpenTestEnvironmentClick);
  $('.btn-save-testenv').click(function () {
    if (!currFile) {
      openFileSaveAsDialog(saveYamlFile)
    } else {
      saveYamlFile();
    }
  });

  $('.btn-save-as-testenv').click(function () {
    openFileSaveAsDialog(saveYamlFile)
  });

  $('.btn-new-testenv').click(function () {
    confirmNoDefault(unsavedModifications, 'Unsaved Modifications',
        'Do you really want to discard current changes?',
        function () {
          $('.testenv-sidebar-header').fadeOut();
          $('.sidebar-bottom-toolbar').toggleClass('hidden', true);
          setYamlFileName(null);
          discardChanges();
          notifyChangesToTestenvData(false);
        });
  });


  $('.btn-scroll-top').click(function() {
    window.scrollTo(0, 0);
  });

  $('.btn-toggle-sidebar').click(function() {
    const sidebarCol = $('.sidebar-col');
    const btnIcon = $(this).find('i');
    const contentCol = $('.content-col');
    const expanded = sidebarCol.hasClass('col-3');
    sidebarCol.toggleClass('col-3', !expanded);
    sidebarCol.toggleClass('col-0', expanded);
    sidebarCol.toggle(!expanded);
    contentCol.toggleClass('col-12', expanded);
    contentCol.toggleClass('col-9', !expanded);
    contentCol.toggleClass('offset-0', expanded);
    contentCol.toggleClass('offset-3', !expanded);
    btnIcon.toggleClass('fa-angle-double-right', expanded);
    btnIcon.toggleClass('fa-angle-double-left', !expanded);
  });

  $('.btn-add-server').click(function () {
    openAddServerModal();
  });

  // global key shortcuts
  $(document).keydown(function (ev) {
    if ((ev.metaKey || ev.ctrlKey) && ev.shiftKey) {
      switch (ev.keyCode) {
        case 65: // Ctrl + Shift + A
          openAddServerModal();
          ev.preventDefault();
          return false;
        case 79: // Ctrl + Shift + O
          handleOpenTestEnvironmentClick();
          ev.preventDefault();
          return false;
      }
    }
    return true;
  });

  showWelcomeCard();
});
