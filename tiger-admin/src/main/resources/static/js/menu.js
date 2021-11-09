let currEnvironment;

// =============================================================================
//
// menu.js
//
//
// =============================================================================
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
      currEnvironment = res;
      populateServersFromYaml(res);
      $('.cfg-file-label').text($('#file').val().replace(/C:\\fakepath\\/i, ''));
    },
    error: function (xhr) {
      bs5Utils.Modal.show({
        type: 'danger',
        title: `Error`,
        content:
            '<div>Leider konnten wir ihre YAML Konfiguration nicht laden!</div>' +
            '<hr class="dropdown-divider" style="display: none;">' +
            '<small><div class="detailedMessage" style="display: none"></div></small>',
        buttons: [
          {
            text: 'Advanced details',
            class: 'btn btn-sm btn-info',
            handler: (ev) => {
             const detMsg = $(ev.target).parents('.modal-dialog').find('.detailedMessage');
             const divider = $(ev.target).parents('.modal-dialog').find('.dropdown-divider');
             detMsg.text(xhr.responseText);
             if($('.btn.btn-sm.btn-info').text() ==='Close'){
               divider.hide();
               detMsg.hide();
               $('.btn.btn-sm.btn-info').text('Advanced details');
             }else{
               divider.show();
               detMsg.show();
               $('.btn.btn-sm.btn-info').text('Close');
             }
            }
          },
        ],
        centered: true,
        dismissible: true,
        backdrop: 'static',
        keyboard: false,
        focus: false
      });
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
        '<div id="sidebar_server_' + serverKey + '" class="box sidebar-item row">'
        + '<div class="col-1"><i class="fas fa-grip-lines draghandle"></i></div>'
        + '<div class="col-10 server-label">' + serverKey + '</div>'
        + '<div class="col-1 context-menu-one btn btn-neutral"> <i class="fas fa-ellipsis-v"></i> </div> </div>');

    // create server content form tag
    serverContent.append('<form id="content_server_' + serverKey
        + '" class="col server-formular"></form>')
    // init formular with data
    $('#content_server_' + serverKey).initFormular(serverKey,
        testEnvYaml[serverKey]);

    // update proxied select field in all formulars
    updateServerLists(Object.keys(testEnvYaml));

    $('#sidebar_server_' + serverKey + ' .server-label').click(function (ev) {
        const formid = $(this).parents('.sidebar-item').attr('id').replace('sidebar_server_', 'content_server_');
          $('#' + formid + ' h1')[0].scrollIntoView(true);
        }
    );
  }
}

updateServerLists = function (serverList, replacedSelection, optNewSelection) {
  $('form.server-formular').updateServerList(serverList, replacedSelection, optNewSelection);
}

