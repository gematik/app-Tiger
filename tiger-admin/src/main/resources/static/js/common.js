"use strict"

// common helper methods

function objectDeepEquals(obj1, obj2) {
  if ((!obj1 && obj2) || (obj1 && !obj2)) {
    return false;
  }
  let props1 = Object.getOwnPropertyNames(obj1);
  let props2 = Object.getOwnPropertyNames(obj2);
  if (props1.length !== props2.length) {
    return false;
  }
  for (let i = 0; i < props1.length; i++) {
    let prop = props1[i];
    let bothAreObjects = typeof (obj1[prop]) === 'object' && typeof (obj2[prop]) === 'object';
    if ((!bothAreObjects && (obj1[prop] !== obj2[prop]))
        || (bothAreObjects && !objectDeepEquals(obj1[prop], obj2[prop]))) {
      return false;
    }
  }
  return true;
}

// internal helper methods for jquery checks (Do not use unless discussed with Thomas)

function checkSingle(method, $elem) {
  if ($elem.length > 1) {
    throw new Error("Only single items supported!");
  }
}

function checkTag(method, $elem, tagName) {
  $.each($elem, function () {
    if (this.tagName !== tagName) {
      throw new Error(`${method} is only for ${tagName} items! (used on ${this.tagName})`);
    }
  });
}

function checkInputField(method, $elem) {
  $.each($elem, function () {
    if (!['INPUT', 'TEXTAREA', 'SELECT'].includes(this.tagName) && $(this).attr('class').indexOf('list-group') === -1) {
      throw new Error(`${method} is only for named input/textarea/select/list-group items! (used on ${this.tagName})`);
    }
  });
}

function checkClass(method, $elem, className) {
  $.each($elem, function () {
    if (!$(this).hasClass(className)) {
      throw new Error(`${method} is only for items of class ${className}! (used on ${this.tagName})`);
    }
  });
}

function checkTagNClass(method, $elem, tagName, className) {
  checkTag(method, $elem, tagName);
  checkClass(method, $elem, className);
}

//
// JQuery addons
//

$.fn.setEnabled = function (enabled) {
  return this.each(function () {
    $(this).attr('disabled', !enabled);
    $(this).toggleClass('disabled', !enabled);
  });
}
