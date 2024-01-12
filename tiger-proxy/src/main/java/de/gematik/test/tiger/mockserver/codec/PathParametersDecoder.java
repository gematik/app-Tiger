package de.gematik.test.tiger.mockserver.codec;

import com.google.common.base.Joiner;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.Parameters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class PathParametersDecoder {

  private static final Pattern PATH_VARIABLE_NAME_PATTERN = Pattern.compile("\\{[.;]?([^*]+)\\*?}");

  public String validatePath(HttpRequest matcher) {
    String error = "";
    if (matcher.getPath() != null) {
      if (matcher.getPathParameters() != null && !matcher.getPathParameters().isEmpty()) {
        List<String> actualParameterNames = new ArrayList<>();
        for (String matcherPathPart : matcher.getPath().split("/")) {
          Matcher pathParameterName = PATH_VARIABLE_NAME_PATTERN.matcher(matcherPathPart);
          if (pathParameterName.matches()) {
            actualParameterNames.add(pathParameterName.group(1));
          }
        }
        List<String> expectedParameterNames =
            matcher.getPathParameters().keySet().stream().toList();
        if (!expectedParameterNames.equals(actualParameterNames)) {
          error =
              "path parameters specified "
                  + expectedParameterNames
                  + " but found "
                  + actualParameterNames
                  + " in path matcher";
        }
      }
    }
    return error;
  }

  public String normalisePathWithParametersForMatching(HttpRequest matcher) {
    String result = null;
    if (matcher.getPath() != null) {
      if (matcher.getPathParameters() != null && !matcher.getPathParameters().isEmpty()) {
        String value = matcher.getPath();
        if (value.contains("{")) {
          List<String> pathParts = new ArrayList<>();
          for (String pathPart : matcher.getPath().split("/")) {
            Matcher pathParameterName = PATH_VARIABLE_NAME_PATTERN.matcher(pathPart);
            if (pathParameterName.matches()) {
              pathParts.add(".*");
            } else {
              pathParts.add(pathPart);
            }
          }
          result = Joiner.on("/").join(pathParts) + (value.endsWith("/") ? "/" : "");
        } else {
          result = matcher.getPath();
        }
      } else {
        result = matcher.getPath();
      }
    }
    // Unable to load API spec attribute paths.'/pets/{petId}'. Declared path parameter petId needs
    // to be defined as a path parameter in path or operation level
    return result;
  }

  public Parameters extractPathParameters(HttpRequest matcher, HttpRequest matched) {
    Parameters parsedParameters =
        matched.getPathParameters() != null ? matched.getPathParameters() : new Parameters();
    if (matcher.getPathParameters() != null && !matcher.getPathParameters().isEmpty()) {
      String[] matcherPathParts = getPathParts(matcher.getPath());
      String[] matchedPathParts = getPathParts(matched.getPath());
      if (matcherPathParts.length != matchedPathParts.length) {
        throw new IllegalArgumentException(
            "expected path "
                + matcher.getPath()
                + " has "
                + matcherPathParts.length
                + " parts but path "
                + matched.getPath()
                + " has "
                + matchedPathParts.length
                + " part"
                + (matchedPathParts.length > 1 ? "s " : " "));
      }
      for (int i = 0; i < matcherPathParts.length; i++) {
        Matcher pathParameterName = PATH_VARIABLE_NAME_PATTERN.matcher(matcherPathParts[i]);
        if (pathParameterName.matches()) {
          String parameterName = pathParameterName.group(1);
          List<String> parameterValues = new ArrayList<>();
          Matcher pathParameterValue =
              Pattern.compile("[.;]?(?:" + parameterName + "=)?([^,]++)[.,;]?")
                  .matcher(matchedPathParts[i]);
          while (pathParameterValue.find()) {
            parameterValues.add(pathParameterValue.group(1));
          }
          parsedParameters.withEntry(parameterName, parameterValues);
        }
      }
    }
    return parsedParameters;
  }

  private String[] getPathParts(String path) {
    return path != null
        ? Arrays.stream(StringUtils.removeStart(path, "/").split("/"))
            .filter(StringUtils::isNotBlank)
            .toArray(String[]::new)
        : new String[0];
  }
}
