"use strict"

const summaryPatterns = {
  "node-settings": [
    ["single", "<b>", "$hostname", "</b>"],
    ["group", "(", ", ",
      ["$active", "$type", "$template", ["", "$startupTimeoutSec", " sec"]], ")"
    ]
  ],
  "source-settings": [
    "$source",
    ["single", ":", "$version", ""]
  ],
  ".dockerOptions.dockerSettings": [
    ["single", "Cmd: ", "$.dockerOptions.entryPoint", "&nbsp;"],
    ["single", "Proxied ", "$.dockerOptions.proxied", "&nbsp;"],
    ["single", "Oneshot ", "$.dockerOptions.oneShot", "&nbsp;"]
  ],
  ".dockerOptions.serviceHealthchecks": [
    "$.dockerOptions.serviceHealthchecks"
  ],
  ".externalJarOptions.externalSettings": [
    ["single", "Folder ", "$.externalJarOptions.workingDir", "<br/>"],
    ["single", "CheckURL", "$.externalJarOptions.healthcheck", ""]
  ],
  ".externalJarOptions.options": [
    "$.externalJarOptions.options"
  ],
  ".externalJarOptions.arguments": [
    "$.externalJarOptions.arguments"
  ],
  ".tigerProxyCfg": [
    ["single", "Proxied ", "$.tigerProxyCfg.proxiedServer", "@ "],
    ["single", "", "$.tigerProxyCfg.proxyProtocol", "://localhost"],
    ["single", ":", "$.tigerProxyCfg.proxyPort", "", "RANDOMPORT"],
    "<br/>",
    ["single", "WebUI http://localhost:", "$.tigerProxyCfg.serverPort", "/webui<br/>"],
    ["single", "Loglevel ", "$.tigerProxyCfg.proxyCfg.proxyLogLevel", "<br/>"],
    ["group", "", "<br/>",
      [
        ["Rbel Endpoint ", "$.tigerProxyCfg.proxyCfg.activateRbelEndpoint", ""],
        ["ASN1 parsing ", "$.tigerProxyCfg.proxyCfg.activateAsn1Parsing", ""],
        ["Log traffic even without routes ", "$.tigerProxyCfg.proxyCfg.activateForwardAllLogging", ""],
        ["Parse Rbel ", "$.tigerProxyCfg.proxyCfg.disableRbelParsing", ""]
      ], ""
    ]
  ],
  ".tigerProxyCfg.proxyCfg.proxyRoutes": [
    "$.tigerProxyCfg.proxyCfg.proxyRoutes"
  ],
  ".tigerProxyCfg.proxyCfg.forwardToProxy": [
    "$.tigerProxyCfg.proxyCfg.forwardToProxy.type",
    ["single", "://", "$.tigerProxyCfg.proxyCfg.forwardToProxy.hostname", ""],
    ["single", ":", "$.tigerProxyCfg.proxyCfg.forwardToProxy.port", ""]
  ],
  ".tigerProxyCfg.proxyCfg.trafficEndpoints": [
    ["single", "Endpoints <br/>", "$.tigerProxyCfg.proxyCfg.trafficEndpoints}", "<br/>"],
    ["single", "Timeout ", "$.tigerProxyCfg.proxyCfg.connectionTimeoutInSeconds", " sec<br/>"],
    ["group", "Buffers in Mb ", ", ",
      ["$.tigerProxyCfg.proxyCfg.stompClientBufferSizeInMb",
        "$.tigerProxyCfg.proxyCfg.perMessageBufferSizeInMb",
        "$.tigerProxyCfg.proxyCfg.rbelBufferSizeInMb"], "<br/>"],
    ["single", "Skip subscription at startup ", "$.tigerProxyCfg.proxyCfg.skipTrafficEndpointsSubscription", ""]
  ],
  ".tigerProxyCfg.proxyCfg.modifications": [
    "$.tigerProxyCfg.proxyCfg.modifications"
  ],
  ".tigerProxyCfg.proxyCfg.tls": [
    ["single", "<b>", "$.tigerProxyCfg.proxyCfg.tls.domainName", "</b><br/>"],
    ["single", "RootCa ", "$.tigerProxyCfg.proxyCfg.tls.serverRootCa.fileLoadingInformation", "<br/>"],
    ["single", "Mutual TLS Identity ", "$.tigerProxyCfg.proxyCfg.tls.forwardMutualTlsIdentity.fileLoadingInformation",
      "<br/>"],
    ["single", "Server Identity ", "$.tigerProxyCfg.proxyCfg.tls.serverIdentity.fileLoadingInformation", "<br/>"],
    ["single", "Additional Names:<br/> ", "$.tigerProxyCfg.proxyCfg.tls.alternativeNames", "<br/>"],
    ["single", "Server SSL Suites:<br/> ", "$.tigerProxyCfg.proxyCfg.tls.serverSslSuites", ""]
  ],
  "pkiKeys": [
    "$pkiKeys"
  ],
  "environment": [
    "$environment"
  ],
  "exports": [
    "$exports"
  ],
  "urlMappings": [
    "$urlMappings"
  ],
}

function constructSummaryFromPattern(summaryPattern, dataProvider) {
  try {
    let html = '';
    summaryPattern.forEach(v => {
      if (!Array.isArray(v)) {
        if (v.startsWith("$")) {
          html += dataProvider.getData(v.substr(1));
        } else {
          html += v;
        }
        return;
      }
      switch (v[0]) {
        case 'single':
          const value = dataProvider.getData(v[2].substr(1));
          if (value) {
            html += v[1] + value + v[3];
          } else if (v.length === 5) {
            html += v[1] + v[4] + v[3];
          }
          break;
        case 'group':
          const values = [];
          v[3].forEach(vv => {
            if (Array.isArray(vv)) {
              const value = dataProvider.getData(vv[1].substr(1));
              if (value) {
                values.push(vv[0] + value + vv[2]);
              }
            } else {
              const val = dataProvider.getData(vv.substr(1))
              if (val) {
                values.push(val);
              }
            }
          });
          if (values.length) {
            html += v[1] + values.join(v[2]) + v[4];
          }
          break;
        default:
          if (v[0].startsWith("$")) {
            html += dataProvider.getData(v[0].substr(1));
          } else {
            html += v[0];
          }
      }
    });
    return html;
  } catch (e) {
    console.error(`Failed to parse pattern "${summaryPattern}"`);
    return `Failed to parse pattern "${summaryPattern}"`;
  }
}

// for single fieldset
$.fn.generateSummary = function () {
  checkTag('generateSummary', this, 'FIELDSET');

  const fieldSetSummaryProvider = {
    init: function (fieldset) {
      this.fieldSet = fieldset;
    },
    fieldSet: null,
    getData: function (name) {
      return this.fieldSet.getValueOfInput(name, true);
    }
  };
  fieldSetSummaryProvider.init(this);

  return '<span class="text summary fs-6">' +
      constructSummaryFromPattern(
          summaryPatterns[this.attr("section")],
          fieldSetSummaryProvider)
      + '</span>';
}

// for single fieldset
$.fn.getValueOfInput = function (name) {
  checkTag('getValue', this, 'FIELDSET');
  checkSingle('getValue', this);
  const elem = this.find(`*[name='${name}']`);
  let str;
  if (elem.prop("tagName") === 'UL') {
    str = this.getListValue(name);
    return str;
  } else if (elem.attr("type") === 'checkbox') {
    return elem.prop('checked') ? 'ON' : 'OFF';
  } else {
    str = this.find(`*[name='${name}']`).val();
    return $('<span>').text(str).html();
  }
}

// for single fieldset
// TODO migrate to getValueOfInput
$.fn.getListValue = function (name) {
  checkTag('getListValue', this, 'FIELDSET');
  checkSingle('getListValue', this);
  const lis = this.find(`ul[name='${name}'] > li`);
  let csv = "";
  $.each(lis, function (idx, el) {
    csv += $('<span>').text($(el).text()).html() + ",<br/>";
  });
  if (csv === "") {
    return "No entries";
  }
  return csv.substr(0, csv.length - ",<br/>".length);
}

// for multiple legend
$.fn.setSummary = function () {
  checkTag('setSummary', this, 'LEGEND');
  return this.each(function () {
    const fieldSet = $(this).parent();
    const fsName = fieldSet.attr("section");
    const summarySpan = fieldSet.find("span.fieldset-summary");
    const collapsed = $(this).find('i.collapse-icon').isCollapsed();
    if (collapsed) {
      switch (fsName) {
        case "source":
          summarySpan.html(fieldSet.generateSummary());
          break;
        case ".tigerProxyCfg.proxyCfg.forwardToProxy":
          if (fieldSet.getValueOfInput("enableForwardProxy") === 'ON') {
            summarySpan.html(fieldSet.generateSummary());
          } else {
            summarySpan.html(
                '<span class="text summary fs-6">DISABLED</span>');
          }
          break;
        default:
          summarySpan.html(fieldSet.generateSummary());
          break;
      }
    } else {
      summarySpan.text("");
    }
  });
}
