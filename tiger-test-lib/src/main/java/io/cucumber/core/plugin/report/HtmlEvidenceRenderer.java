/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.cucumber.core.plugin.report;

import freemarker.core.HTMLOutputFormat;
import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateHashModel;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.TimeZone;

public class HtmlEvidenceRenderer {

  private final Configuration freemarkerConfiguration;

  public HtmlEvidenceRenderer() {
    freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_32);

    // Specify the source where the template files come from. Here I set a
    // plain directory for it, but non-file-system sources are possible too:
    freemarkerConfiguration.setClassLoaderForTemplateLoading(
        getClass().getClassLoader(),
        "/" + getClass().getPackageName().replace('.', '/') + "/templates");
    freemarkerConfiguration.setDefaultEncoding("UTF-8");
    freemarkerConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    freemarkerConfiguration.setLogTemplateExceptions(false);
    freemarkerConfiguration.setWrapUncheckedExceptions(true);
    freemarkerConfiguration.setFallbackOnNullLoopVariable(false);
    freemarkerConfiguration.setSQLDateAndTimeTimeZone(TimeZone.getDefault());
    freemarkerConfiguration.setAutoEscapingPolicy(
        Configuration.ENABLE_IF_SUPPORTED_AUTO_ESCAPING_POLICY);
    freemarkerConfiguration.setOutputFormat(HTMLOutputFormat.INSTANCE);
  }

  public String render(EvidenceReport report) throws IOException {
    var reportTemplate = freemarkerConfiguration.getTemplate("EvidenceReport.ftl");

    try {
      BeansWrapper wrapper = new BeansWrapperBuilder(Configuration.VERSION_2_3_32).build();
      TemplateHashModel staticModels = wrapper.getStaticModels();
      TemplateHashModel me =
          (TemplateHashModel) staticModels.get(EvidenceReportJsonConverter.class.getName());

      var resultContainer = new StringWriter();
      reportTemplate.process(
          Map.of(
              "report", report,
              "jsonConverter", me),
          resultContainer);

      return resultContainer.toString();
    } catch (TemplateException e) {
      throw new IOException("Error processing Evidence Template", e);
    }
  }
}
