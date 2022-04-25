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
  ".pkiKeys": [
    "$id", "(", "$type", ")"
  ]
}

function getListItem(text, active, editable) {
  let applyIcon = '';
  if (editable){
    applyIcon = '<i class="fas fa-check btn-list-simply-apply"></i>';
  }
  if (editable && text==='') {
    text= 'Press ENTER to apply';
  }
  // TODO why <span><span> ??
  return `<li class="list-group-item ${active ? 'active ' : ''}">` +
      '<i title="Click to rearrange item in list" class="fas fa-grip-lines draghandle"></i>'
      + `<span><span>${text}</span></span>${applyIcon}<i class="far fa-trash-alt btn-list-delete"></i></li>`;
}

//
// add callback methods for list items (editable and non editable)
//

function abortOtherEditing() {
  const editing = $('.editing');
  $.each(editing, function () {
    $(this).handleEnterEscOnEditableContent({
      keyCode: 13, preventDefault: function () {
      }
    });
  });
}

function handleAddButtonOnSimpleList() {
  const listGroup = $(this).parents('.row:first').find(".list-group");
  const activeItem = listGroup.find('.active');
  listGroup.find('.active').removeClass('active');
  listGroup.find('.list-empty-info').hide();

  addItemToList(listGroup, activeItem, true)
  $.each(listGroup.find('.list-group-item > span'), function () {
    $(this).addClickNKeyCallbacks2ListItem(true);
  });
  // start editing
  const activeSpan = listGroup.find('.active > span')
  activeSpan.click();
  const sel = window.getSelection();
  window.setTimeout(function () {
    const range = document.createRange(); //range object
    range.selectNodeContents(activeSpan[0]); //sets Range
    sel.removeAllRanges(); //remove all ranges from selection
    sel.addRange(range);//add Range to a Selection.
  }, 1);
}

function setDefaultValuesInFieldset(editFieldSet) {
  editFieldSet.find("*[name]").each(function () {
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
  fieldSet.find('fieldset.subset').show();
  const listGroup = $(this).parents("fieldset").find(".list-group");
  listGroup.find('.list-empty-info').hide();

  let activeItem = listGroup.find('.active');
  if (activeItem.length) {
    fieldSet.updateDataAndLabelForActiveItem(false);
    activeItem = listGroup.find('.active');
    activeItem.removeClass('active');
  }

  addItemToList(listGroup, activeItem, false);

  const editFieldSet = fieldSet.find('fieldset');
  // respect default value of fields
  setDefaultValuesInFieldset(editFieldSet);

  // start editing
  editFieldSet.find("*[name]:first").focus();
  fieldSet.find(".btn-list-apply").show();
}

function addItemToList(listGroup, activeItem, addEnterToApplyInfo) {
  const newItem = $(getListItem('', true, addEnterToApplyInfo));
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
  if (!nextActive.length || nextActive.length && nextActive[0].tagName !== 'LI') {
    nextActive = activeItem.next();
  }
  if (nextActive.length && nextActive[0].tagName === 'LI') {
    nextActive.addClass('active');
    let data = nextActive.data("listdata");
    const section = fieldSet.attr("section");
    for (const field in data) {
      fieldSet.setObjectFieldInForm(data, field, section);
    }
  } else {
    const listGroup = $(this).parents('.list-group');
    listGroup.find('.list-empty-info').show();
    if (fieldSet.hasClass('complex-list')) {
      fieldSet.find('fieldset.subset').hide();
      setDefaultValuesInFieldset(fieldSet.find('fieldset'));
      fieldSet.enableSubSetFields(false);
      fieldSet.find('.btn-list-apply').hide();
    }
  }
  activeItem.remove();
}

function handleApplyButtonOnList() {
  const fieldSet = $(this).parents('fieldset.complex-list');
  fieldSet.updateDataAndLabelForActiveItem(false);
  fieldSet.find('fieldset.subset').hide();
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
        }
        $(this).focus();
        $(this).keydown((ev) => {
          return $(this).handleEnterEscOnEditableContent(ev);
        });
        ev.preventDefault();
        return false;
      });
    }

    $(this).parent().click(function () {
      const fieldSet = $(this).parents('fieldset');
      if ($(this).hasClass('active')) {
        if (!editable) {
          fieldSet.find('fieldset.subset').show();
        }
        return true;
      }
      let curActive = $(this).parents('.list-group').find('.active');

      fieldSet.enableSubSetFields(true);
      if (!editable) {
        const origData = curActive.data("listdata");
        const newData = fieldSet.getNewDataFromSubsetFieldset(false);
        if (origData && !objectDeepEquals(origData, newData)) {
          fieldSet.updateDataAndLabelForActiveItem(false);
          curActive = $(this).parents('.list-group').find('.active');
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
        fieldSet.find('fieldset.subset').show();
        $(this).parents('fieldset').find('.btn-list-apply').show();
        fieldSet.find('fieldset').find("*[name]:first").focus();
      } else {
        $(this).focus();
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
    const editable = fieldSet.hasClass('editableList');
    const data = $(this).getNewDataFromSubsetFieldset(emptyValues)
    if (emptyValues) {
      let notEmpty = fieldSet.find("*[name]").find(field => fieldSet.getValueOfInput($(field).attr('name')))
      if (notEmpty.length) {
        warn('Aborting other editing1');
      }
    }
    elem.replaceWith(getListItem($('<div/>').text(listGroup.generateListItemLabel(data)).html(), true, editable));
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
