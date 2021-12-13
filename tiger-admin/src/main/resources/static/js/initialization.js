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

  loadTemplatesFromServer();

  // sidebar

  // noinspection JSUnusedGlobalSymbols
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
      "start": {name: "Start", icon: "fas text-success fa-play ctxt-start"},
      "restart": {name: "Restart", icon: "fas text-success fa-undo ctxt-restart"},
      "stop": {name: "Stop", icon: "fas text-warning fa-stop ctxt-stop"},
      "delete": {name: "Delete", icon: "fas text-danger fa-trash-alt ctxt-delete"},
      /* TODO logging is way to go
          "logs": {name: "Logs", icon: "fas fa-terminal"},*/
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
      window.scrollTo(0, content.position().top);
    }
  });

  // top menu

  $('.btn-open-testenv').click(handleOpenTestEnvironmentClick);
  $('.btn-save-testenv').click(function () {
    if (!currFile) {
      openFileSaveAsDialog(saveYamlFile)
    } else {
      snack("Save NOT implemented so far", "danger");
    }
  });

  $('.btn-save-as-testenv').click(function () {
    openFileSaveAsDialog(saveYamlFile)
  });

  $('.btn-new-testenv').click(function () {
    confirmNoDefault(unsavedModifications, 'Unsaved Modifications',
        'Do you really want to discard current changes?',
        function () {
          currEnvironment = {};
          $('.testenv-sidebar-header').fadeOut();
          setYamlFileName(null);
          populateServersFromYaml(currEnvironment);
          notifyChangesToTestenvData(false);
        });
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
