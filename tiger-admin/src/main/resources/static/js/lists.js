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

"use strict"

const listItemPatterns = {
  ".tigerProxyCfg.proxyCfg.proxyRoutes": [
    ["single", "", "$id", ": "],
    ["single", " [", "$basicAuth.username", "@"],
    ["single", "", "$basicAuth.password", "]"],
    ["single", " ", "$from", ""],
    ["single", " ↦ ", "$to", ""]
  ],
  ".tigerProxyCfg.proxyCfg.modifications": [
    ["single", "", "$name", ": "], "$condition", "$targetElement"
  ],
  "pkiKeys": [
    "$id", "(", "$type", ")"
  ]
}

function getListItem(text, active) {
  // TODO why <span><span> ??
  return `<li class="list-group-item ${active ? 'active ' : ''}">` +
      '<i title="Click to rearrange item in list" class="fas fa-grip-lines draghandle"></i>'
      + `<span><span>${text}</span></span><i class="far fa-trash-alt btn-list-delete"></i></li>`;
}

//
// add callback methods for list items (editable and non editable)
//

function abortOtherEditing() {
  const editing = $('.editing');
  if (editing.length) {
    warn('Aborting other editing4');
  }
  $.each(editing, function () {
    $(this).handleEnterEscOnEditableContent({
      keyCode: 27, preventDefault: function () {
      }
    });
  });
}

function handleAddButtonOnSimpleList() {
  const listGroup = $(this).parents('.row:first').find(".list-group");
  const activeItem = listGroup.find('.active');
  listGroup.find('.active').removeClass('active');

  addItemToList(listGroup, activeItem)
  $.each(listGroup.find('.list-group-item > span'), function () {
    $(this).addClickNKeyCallbacks2ListItem(true);
  });
  // start editing
  listGroup.find('.active > span').click();
}

function setDefaultValuesInFieldset(editFieldSet) {
  editFieldSet.find("*[name][type]").each(function () {
    const fieldName = $(this).attr('name');
    const type = $(this).attr('type');
    let defValue = getDefaultValueFor(fieldName);
    if (type === 'checkbox') {
      $(this).prop('checked', defValue === null ? false : defValue);
    } else {
      $(this).val(defValue === null ? '' : defValue);
    }
  });
}

function handleAddButtonOnComplexList() {
  const fieldSet = $(this).parents('fieldset');
  fieldSet.enableSubSetFields(true);
  const listGroup = $(this).parents(".row:first").find(".list-group");
  const activeItem = listGroup.find('.active');
  if (activeItem.length) {
    const origData = activeItem.data("listdata");
    if (!objectDeepEquals(origData, fieldSet.getNewDataFromSubsetFieldset(false))) {
      warn('Aborting other editing3', 10000);
    }
    activeItem.removeClass('active');
  }
  addItemToList(listGroup, activeItem);

  const editFieldSet = fieldSet.find('fieldset');
  if (activeItem.length) {
    // if no active item dont skip entered data as its not very user friendly
    fieldSet.updateDataAndLabelForActiveItem(true);
  }
  // respect default value of fields
  setDefaultValuesInFieldset(editFieldSet);

  // start editing
  editFieldSet.find("*[name]:first").focus();
  fieldSet.find(".btn-list-apply").show();
}

function addItemToList(listGroup, activeItem) {
  const newItem = $(getListItem("", true));
  if (activeItem.length === 0) {
    listGroup.prepend(newItem);
  } else {
    newItem.insertAfter(activeItem);
  }
  newItem.find('.btn-list-delete').click(handleDeleteButtonOnList);
}

function handleDeleteButtonOnList() {
  // select previous list entry Or if no more select next and if none disable apply
  const fieldSet = $(this).parents('fieldset');
  const activeItem = $(this).parent();
  let nextActive = activeItem.prev();
  if (!nextActive.length) {
    nextActive = activeItem.next();
  }
  if (nextActive.length) {
    nextActive.addClass('active');
    let data = nextActive.data("listdata");
    const section = fieldSet.attr("section");
    for (const field in data) {
      fieldSet.setObjectFieldInForm(data, field, section);
    }
  } else {
    setDefaultValuesInFieldset(fieldSet.find('fieldset'));
    fieldSet.find('.btn-list-apply').hide();
  }
  activeItem.remove();
}

function handleApplyButtonOnList() {
  const fieldSet = $(this).parents('fieldset.complex-list');
  fieldSet.updateDataAndLabelForActiveItem(false);
}

// for single .list-group
$.fn.generateListItemLabel = function (data) {
  checkClass('generateListItemLabel', this, 'list-group');
  checkSingle('generateListItemLabel', this);
  const dataProvider = {
    init: function (data) {
      this.data = data;
    },
    data: null,
    getData: function (name) {
      if (name.indexOf('.') !== -1) {
        const path = name.split('.');
        let pathCursor = this.data;
        $.each(path, function () {
          if (!pathCursor) {
            return false;
          }
          pathCursor = pathCursor[this];
        });
        return pathCursor || "";
      } else {
        return this.data[name] || "";
      }
    }
  }
  dataProvider.init(data);
  return constructSummaryFromPattern(listItemPatterns[this.attr("name")], dataProvider);
}

// for multiple span in .list-group-item
$.fn.addClickNKeyCallbacks2ListItem = function (editable) {
  checkTag('addClickNKeyCallbacks2ListItem', this, 'SPAN')
  this.each(function () {
    if (editable) {
      const li = $(this).parent();
      li.off("click")
      li.click(() => {
        abortOtherEditing();
      });
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
        }
        $(this).keydown((ev) => {
          return $(this).handleEnterEscOnEditableContent(ev);
        });
        ev.preventDefault();
        return false;
      });
    }

    $(this).parent().click(function () {
      if ($(this).hasClass('active')) {
        return true;
      }
      const fieldSet = $(this).parents('fieldset');
      const curActive = $(this).parents('.list-group').find('.active');

      fieldSet.enableSubSetFields(true);
      if (!editable) {
        const origData = curActive.data("listdata");
        if (origData && !objectDeepEquals(origData, fieldSet.getNewDataFromSubsetFieldset(false))) {
          warn('Aborting other editing2');
        }
      }

      curActive.removeClass('active');
      $(this).addClass('active');
      const section = fieldSet.attr("section");
      // for complex lists also populate the edit fieldset
      if (!editable) {
        setDefaultValuesInFieldset(fieldSet.find('fieldset'));
        let data = $(this).data("listdata");
        for (const field in data) {
          fieldSet.setObjectFieldInForm(data, field, section);
        }
        $(this).parents('fieldset').find('.btn-list-apply').show();
      }
    });
  });
}

// on fieldset level
// for single fieldset
$.fn.setObjectFieldInForm = function (data, field, path) {
  checkTag('setObjectFieldInForm', this, 'FIELDSET')
  checkSingle('setObjectFieldInForm', this);
  if (typeof data[field] === "object" && data[field] !== null) {
    for (const child in data[field]) {
      this.setObjectFieldInForm(data[field], child, path + "." + field);
    }
  } else {
    const inputField = this.find(`*[name='${path}.${field}']`);
    checkSingle('setObjectFieldInForm -> input field', inputField);
    inputField.setValue(data[field]);
  }
}

// for single fieldset
$.fn.enableSubSetFields = function (state) {
  checkTag('enableSubSetFields', this, 'FIELDSET');
  this.find('fieldset.subset input').setEnabled(state);
  this.find('fieldset.subset textarea').setEnabled(state);
  this.find('fieldset.subset select').setEnabled(state);
  this.find('fieldset.subset .form-switch').setEnabled(state);
}

// for multiple fieldsets
$.fn.updateDataAndLabelForActiveItem = function (emptyValues) {
  checkTag('updateDataAndLabelForActiveItem', this, 'FIELDSET');
  this.each(function () {
    if ($(this).hasClass('subset')) {
      throw new Error(
          `updateDataAndLabelForActiveItem is NOT for fieldsets with class subset! (used on ${this.tagName})`);
    }
    const listGroup = $(this).find('.list-group');
    let elem = listGroup.find(".list-group-item.active");
    const fieldSet = $(this).find('fieldset.subset');
    const data = $(this).getNewDataFromSubsetFieldset(emptyValues)
    if (emptyValues) {
      let notEmpty = fieldSet.find("*[name]").find(field => fieldSet.getValueOfInput($(field).attr('name')))
      if (notEmpty.length) {
        warn('Aborting other editing1');
      }
    }
    elem.replaceWith(getListItem($('<div/>').text(listGroup.generateListItemLabel(data)).html(), true));
    elem = listGroup.find(".list-group-item.active");
    elem.find('.btn-list-delete').click(handleDeleteButtonOnList);
    elem.data("listdata", data);
    elem.find('span:first').addClickNKeyCallbacks2ListItem(false);
  });
}

// for single fieldset
$.fn.getNewDataFromSubsetFieldset = function (emptyValues) {
  checkTag('getNewDataFromSubsetFieldset', this, 'FIELDSET');
  checkSingle('getNewDataFromSubsetFieldset', this);
  const section = this.attr("section");
  const clonedData = {};
  this.find('fieldset').find("*[name]").saveInputValueInData(section, clonedData, emptyValues);
  return clonedData;
}