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
    },
    error: function () {
      alert("There was an error")
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

