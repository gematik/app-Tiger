$(function () {
    $("#sortable").sortable();
});

$(function () {
    $.contextMenu({
        selector: '.context-menu-one',
        trigger: 'left',
        callback: function (key, options) {
            var m = "clicked: " + key;
            window.console && console.log(m) || alert(m);
        },
        items: {
            "start": {name: "Start", icon: "fas fa-play"},
            "restart": {name: "Restart", icon: "fas fa-undo"},
            "stop": {name: "Stop", icon: "fas fa-stop"},
            "delete": {name: "Delete", icon: "fas fa-trash-alt"},
            "logs": {name: "Logs", icon: "fas fa-terminal"},
        }
    });
});