/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package io.cucumber.core.plugin.report;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import de.gematik.test.tiger.common.report.ReportDataKeys;
import de.gematik.test.tiger.glue.TigerParameterTypeDefinitions;
import de.gematik.test.tiger.glue.annotation.FirstColumnKeyTable;
import de.gematik.test.tiger.glue.annotation.FirstRowKeyTable;
import de.gematik.test.tiger.glue.annotation.ResolvableArgument;
import io.cucumber.plugin.event.Argument;
import io.cucumber.plugin.event.DataTableArgument;
import io.cucumber.plugin.event.DocStringArgument;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCase;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public class StepDescription {
  private static final TigerTypedConfigurationKey<Integer> MAX_STEP_DESCRIPTION_DISPLAY_LENGTH =
      new TigerTypedConfigurationKey<>(
          "tiger.lib.maxStepDescriptionDisplayLengthOnWebUi", Integer.class, 300);
  private final PickleStepTestStep step;

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{[^}]*+}");
  private static final Pattern GENERICS_PATTERN = Pattern.compile("<[^<>]*>");

  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  private final Boolean shouldResolveStepArgument = shouldResolveStepArgument();

  @Getter(value = AccessLevel.PRIVATE, lazy = true)
  private final List<Annotation> methodAnnotations = collectMethodAnnotations();

  public static List<StepDescription> extractStepDescriptions(TestCase testCase) {
    return testCase.getTestSteps().stream()
        .filter(PickleStepTestStep.class::isInstance)
        .map(PickleStepTestStep.class::cast)
        .map(StepDescription::of)
        .toList();
  }

  public String getTooltip() {
    return getStepDescription(false, false);
  }

  public String getResolvedDescriptionPlain() {
    return getStepDescription(false, true);
  }

  public String getResolvedDescriptionHtml() {
    return getStepDescription(true, true);
  }

  public String getUnresolvedDescriptionPlain() {
    return getStepDescription(false, false);
  }

  public String getUnresolvedDescriptionHtml() {
    return getStepDescription(true, false);
  }

  private String getStepDescription(boolean convertToHtml, boolean resolve) {

    return resolveStepDescriptionFull(convertToHtml, resolve);
  }

  private static String tryResolvePlaceholders(String input) {
    try {
      return abbreviate(TigerGlobalConfiguration.resolvePlaceholders(input));
    } catch (JexlException | TigerJexlException | TigerConfigurationException e) {
      log.trace("Could not resolve placeholders in {}", input, e);
      return input;
    }
  }

  private static String tryResolvePlaceholders(Argument argument) {
    if (TigerParameterTypeDefinitions.isResolvedVariableType(argument.getParameterTypeName())) {
      return StepDescription.tryResolvePlaceholders(argument.getValue());
    } else {
      return argument.getValue();
    }
  }

  private String resolveStepDescriptionFull(boolean convertToHtml, boolean resolve) {

    var keyWord = step.getStep().getKeyword();
    var stepText = resolve ? resolveStepDescriptionPrefix() : step.getStep().getText();
    var converter = converter(convertToHtml);
    var prefix =
        converter.apply(
            keyWord
                + stepText); // Keyword already has a trailing space, so we don't need to add one

    var docstringOrTable = extractDocStringOrTable(convertToHtml, resolve);

    return docstringOrTable.map(s -> prefix + System.lineSeparator() + s).orElse(prefix);
  }

  private String resolveStepDescriptionPrefix() {
    var stepDefinitionText = step.getPattern();
    var arguments = step.getDefinitionArgument();

    var argumentsToResolve =
        arguments.stream().map(StepDescription::tryResolvePlaceholders).toList();

    if (argumentsToResolve.isEmpty()) {
      return stepDefinitionText;
    }

    return replacePlaceHoldersInOrder(stepDefinitionText, argumentsToResolve);
  }

  public void recordResolvedDescription() {
    var resolvedDescription = resolveStepDescriptionFull(false, true);
    Serenity.recordReportData()
        .withTitle(ReportDataKeys.TIGER_RESOLVED_STEP_DESCRIPTION_KEY)
        .andContents(StringEscapeUtils.escapeJava(resolvedDescription));
  }

  public void recordMultilineDocstringArgument() {
    if (hasDocStringArgument()) {
      Serenity.recordReportData()
          .withTitle(ReportDataKeys.COMPLETE_UNRESOLVED_MULTILINE_DOCSTRING)
          .andContents(StringEscapeUtils.escapeJava(getUnresolvedDescriptionPlain()));
    }
  }

  private Optional<String> extractDocStringOrTable(boolean convertToHtml, boolean resolve) {
    var argument = step.getStep().getArgument();
    if (argument == null) {
      return Optional.empty();
    }
    if (argument instanceof DataTableArgument dataTableArgument) {
      if (convertToHtml) {
        return Optional.of(dataTableHtml(dataTableArgument, resolve));
      } else {
        return Optional.of(dataTablePlainText(dataTableArgument, resolve));
      }
    } else if (argument instanceof DocStringArgument docStringArgument) {
      if (convertToHtml) {
        return Optional.of(docStringHtml(docStringArgument, resolve));
      } else {
        return Optional.of(docStringPlainText(docStringArgument, resolve));
      }
    } else {
      return Optional.empty();
    }
  }

  private boolean hasDocStringArgument() {
    var argument = step.getStep().getArgument();
    return argument instanceof DocStringArgument;
  }

  private String dataTablePlainText(DataTableArgument dataTableArgument, boolean resolve) {
    var resolver = resolver(resolve);
    return dataTableArgument.cells().stream()
        .map(row -> row.stream().map(resolver).collect(Collectors.joining(" | ", "| ", " |")))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private String dataTableHtml(DataTableArgument dataTableArgument, boolean resolve) {
    var cssClasses = "table table-sm table-data-table";
    if (isFirstColumnKey()) {
      cssClasses += " table-first-column-key";
    }
    if (isFirstRowKey()) {
      cssClasses += " table-first-row-key";
    }
    var result = new StringBuilder("<br/><table class=\"%s\">".formatted(cssClasses));
    var resolver = resolver(resolve);
    dataTableArgument
        .cells()
        .forEach(
            row -> {
              result.append("<tr>");
              row.forEach(
                  cellText ->
                      result
                          .append("<td>")
                          .append(StringEscapeUtils.escapeHtml4(resolver.apply(cellText)))
                          .append("</td>"));
              result.append("</tr>");
            });
    result.append("</table>");
    return result.toString();
  }

  private UnaryOperator<String> resolver(boolean resolve) {
    return resolve && getShouldResolveStepArgument()
        ? StepDescription::tryResolvePlaceholders
        : UnaryOperator.identity();
  }

  private UnaryOperator<String> converter(boolean convertToHtml) {
    return convertToHtml ? StringEscapeUtils::escapeHtml4 : UnaryOperator.identity();
  }

  private String docStringHtml(DocStringArgument docStringArgument, boolean resolve) {
    return "<div class=\"steps-docstring\">"
        + StringEscapeUtils.escapeHtml4(docStringPlainText(docStringArgument, resolve))
        + "</div>";
  }

  private String docStringPlainText(DocStringArgument docStringArgument, boolean resolve) {
    var resolver = resolver(resolve);
    return resolver.apply(docStringArgument.getContent());
  }

  private static String replacePlaceHoldersInOrder(String input, List<String> replacements) {
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);

    StringBuilder result = new StringBuilder();
    for (var replacementIterator = replacements.iterator(); matcher.find(); ) {
      var replacement = Matcher.quoteReplacement(replacementIterator.next());
      matcher.appendReplacement(result, replacement);
    }
    matcher.appendTail(result);
    return result.toString();
  }

  boolean isMethodResolvable() {
    return getMethodAnnotations().stream()
        .anyMatch(a -> a.annotationType().equals(ResolvableArgument.class));
  }

  /**
   * collects the annotations in the method defined in step.getCodeLocation(). The method signature
   * returned by step.getCodeLocation() maybe INCLUDES generics information! The return type is not
   * part of the method signature!
   *
   * @return list of annotations present in the step method
   */
  private List<Annotation> collectMethodAnnotations() {
    var methodSignature = step.getCodeLocation();
    int parenIndex = methodSignature.indexOf("(");
    int lastDotInMethodPath = methodSignature.lastIndexOf('.', parenIndex - 1);
    String className = methodSignature.substring(0, lastDotInMethodPath);
    String methodName = methodSignature.substring(lastDotInMethodPath + 1);

    methodName = removeGenericsArguments(methodName);

    try {
      Method method = BeanUtils.resolveSignature(methodName, Class.forName(className));
      if (method == null) {
        return List.of();
      }
      return Arrays.asList(method.getAnnotations());
    } catch (ClassNotFoundException | IllegalArgumentException e) {
      return List.of();
    }
  }

  private static @NotNull String removeGenericsArguments(String methodName) {
    // For argument lists like (Map<B, List<A>>, List<B>)
    // regex can not do this recursively so we do it in loop till there is no more match
    // e.g. (Map<B, List<A>>, List<B>) -> (Map<B, List>, List) -> (Map, List)
    int formerMethodNameLength;
    do {
      formerMethodNameLength = methodName.length();
      methodName = GENERICS_PATTERN.matcher(methodName).replaceAll("");
    } while (formerMethodNameLength > methodName.length());
    return methodName;
  }

  // The return value is cached in the shouldResolveStepArgument field. The @Getter(lazy = true)
  // annotation ensures this is only called once. Call getShouldResolveStepArgument() to access the
  // value.
  private boolean shouldResolveStepArgument() {
    return isMethodResolvable();
  }

  private static String abbreviate(String input) {
    return StringUtils.abbreviate(input, MAX_STEP_DESCRIPTION_DISPLAY_LENGTH.getValueOrDefault());
  }

  private boolean isFirstColumnKey() {
    return getMethodAnnotations().stream()
        .anyMatch(a -> a.annotationType().equals(FirstColumnKeyTable.class));
  }

  private boolean isFirstRowKey() {
    return getMethodAnnotations().stream()
        .anyMatch(a -> a.annotationType().equals(FirstRowKeyTable.class));
  }
}
