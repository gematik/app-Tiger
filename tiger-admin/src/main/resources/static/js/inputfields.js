"use strict"

function initiateEditingServerKeyField(ev) {
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
    $(this).keydown(handleKeysForServerKeyEditing);
    $(this).on('paste', function (ev) {
      // TODO check to insert only the text part and NO tags!
    });
  }
}

function handleKeysForServerKeyEditing(ev) {
  if (ev.keyCode === 13) {
    const text = $(this).text();
    if (text.indexOf(' ') !== -1) {
      snack(
          'No SPACES allowed in server key!<br/>Replacing spaces with underscores!',
          'warning');
      $(this).text(text.replace(/\s/g, '_'));
    } else if (/[^A-Za-z0-9_]+/g.test(text)) {
      snack(
          'Only ASCII characters, digits and underscore allowed in server key! Please choose a valid name!',
          'warning');
      return false;
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
        danger(`Server key "${newServerKey}" already used!`);
        ev.keyCode = 27;
      } else {
        const sidebarHandle = $('#sidebar_server_' + oldServerKey);
        sidebarHandle.attr('id', 'sidebar_server_' + newServerKey);
        sidebarHandle.find('.server-label').text(newServerKey);
        const srvContentHandle = $('#content_server_' + oldServerKey);
        srvContentHandle.attr('id', 'content_server_' + newServerKey);
        currEnvironment[newServerKey] = currEnvironment[oldServerKey];
        delete currEnvironment[oldServerKey];

        const serverList = Object.keys(currEnvironment).sort();
        $.each(serverList, function () {
          const form = $("#content_server_" + this);
          const serverList2 = [...serverList].filter(e => e != this);
          if (currEnvironment[this].type === 'tigerProxy') {
            form.updateServerList(serverList2, oldServerKey, newServerKey);
          }
          form.updateDependsUponList(serverList2, oldServerKey, newServerKey);
        });
      }
    }
  }
  $(this).off('paste');
  return $(this).handleEnterEscOnEditableContent(ev);
}

$.fn.setValue = function (value) {
  if (!this.length) {
    throw new Error(
        `Trying to set value on not found item! (value was ${value})`);
  }
  checkInputField('setValue', this);
  return this.each(function () {
    if ($(this).attr("type") === "checkbox") {
      if (!value) {
        value = false;
      }
      $(this).prop("checked", value);
    } else if (this.tagName === "SELECT") {
      $.each($(this).find('option'), function () {
        $(this).attr('selected', false);
      });
      if (value) {
        $.each($(this).find('option'), function () {
          $(this).prop('selected',
              $(this).text() === value || $(this).val() === value);
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
}

// for multiple input or select
$.fn.getValue = function () {
  checkInputField('getValue', this);
  checkSingle('getValue', this);
  if (this.attr("type") === "checkbox") {
    return this.prop("checked");
  } else if (this[0].tagName === "SELECT") {
    return this.val();
  } else {
    if (this.attr('type') === 'Number') {
      return Number(this.val());
    } else {
      return this.val();
    }
  }
}

// for multiple input fields
$.fn.saveInputValueInData = function (section, data, defaultToEmptyValues, skipEmptyOrDefaultValues) {
  checkInputField('saveInputValueInData', this);
  return this.each(function () {
    let fieldName = $(this).attr('name');
    const fqName = $(this).attr('name');

    if (section) {
      fieldName = fieldName.substring(section.length + 1);
    }
    let pathCursor = data;
    if (fieldName.indexOf(".") !== -1) {
      // auto create all struct nodes as empty objects
      const path = fieldName.split('.');
      if (section == null) {
        path.splice(0, 1);
      }
      fieldName = path.pop();
      $.each(path, function () {
        if (!pathCursor[this]) {
          pathCursor[this] = {};
        }
        pathCursor = pathCursor[this];
      });
    }
    if (defaultToEmptyValues) {
      if ($(this).attr('type') === 'checkbox') {
        pathCursor[fieldName] = false;
      } else if (this.tagName === 'UL') {
        pathCursor[fieldName] = [];
      } else {
        pathCursor[fieldName] = null;
      }
    } else {
      const defValue = getDefaultValueFor(fqName);
      if ($(this).attr('type') === 'checkbox') {
        if (skipEmptyOrDefaultValues && $(this).prop('checked') !== defValue) {
          pathCursor[fieldName] = $(this).prop('checked');
        }
      } else if (this.tagName === 'UL') {
        const fieldSet = $(this).closest('fieldset');
        const values = [];
        if (fieldSet.hasClass('complex-list')) {
          $(this).find('li').each(function () {
            values.push($(this).data('listdata'));
          });
        } else {
          $(this).find('li > span').each(function () {
            values.push($(this).text());
          });
        }
        pathCursor[fieldName] = values;
      } else {
        const val = $(this).val();
        if ($(this).attr('type') === 'number') {
          if (!skipEmptyOrDefaultValues || (val && Number(val) !== defValue)) {
            pathCursor[fieldName] = Number(val);
          }
        } else {
          if (!skipEmptyOrDefaultValues || (val && val !== defValue)) {
            pathCursor[fieldName] = val;
          }
        }
      }
    }
  });
}

// for single editing elem
$.fn.handleEnterEscOnEditableContent = function (ev) {
  checkSingle('handleEnterEscOnEditableContent', this)
  if (!$(this).hasClass('editing')) {
    console.log(
        `WARN Used handleEnterEscOnEditableContent on non .editing element ${$(
            this).attr('class')}`);
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
}

function getDefaultValueFor(fieldName) {
  if (fieldName === 'localProxyActive') {
    return true;
  }
  if (fieldName === 'startupTimeoutSec') {
    return 20;
  }
  let pathCursor = configScheme.properties;
  if (fieldName.startsWith(".")) {
    fieldName = fieldName.substr(1);
  }
  if (fieldName.indexOf(".") !== -1) {
    const path = fieldName.split('.');
    fieldName = path.pop();
    $.each(path, function () {
      if (!pathCursor[this]) {
        pathCursor[this] = {};
      }
      if (pathCursor[this].items) {
        pathCursor = pathCursor[this].items.properties;
      } else {
        pathCursor = pathCursor[this].properties;
      }
    });
  }
  if (!pathCursor || !pathCursor[fieldName]) {
    console.log("DEFVALUE " + fieldName + " NOT FOUND");
  } else if (pathCursor[fieldName].hasOwnProperty('default')) {
    return pathCursor[fieldName].default;
  }
  return null;
}
