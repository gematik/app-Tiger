/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.jexl;

import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import java.util.*;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;

@Slf4j
public class TigerJexlExecutor {

  private static final Map<String, Object> NAMESPACE_MAP = new HashMap<>();
  private static final List<TigerJexlContextDecorator> CONTEXT_DECORATORS = new ArrayList<>();
  @Setter @Getter private static boolean activateJexlDebugging = false;
  private static BiFunction<String, TigerJexlContext, List<String>> expressionPreMapper =
      (exp, ctx) -> List.of(exp);

  static {
    NAMESPACE_MAP.put(null, InlineJexlToolbox.class);
    CONTEXT_DECORATORS.add(TigerJexlExecutor::tigerJexlMapDecorator);
  }

  public static boolean matchesAsJexlExpression(Object element, String jexlExpression) {
    return createNewExecutor().matchesAsJexlExpressionInternal(element, jexlExpression);
  }

  public static boolean matchesAsJexlExpression(
      Object element, String jexlExpression, Optional<String> keyInParentElement) {
    return matchesAsJexlExpression(
        jexlExpression,
        new TigerJexlContext()
            .withKey(keyInParentElement.orElse(null))
            .withCurrentElement(element));
  }

  public static boolean matchesAsJexlExpression(String jexlExpression, TigerJexlContext context) {
    return createNewExecutor().matchesAsJexlExpressionInternal(jexlExpression, context);
  }

  public static Optional<Object> evaluateJexlExpression(
      String jexlExpression, TigerJexlContext context) {
    final List<Object> resultList =
        createNewExecutor().evaluateJexlExpressionInternal(jexlExpression, context);
    if (resultList.size() > 1) {
      throw new TigerJexlException(
          "Evaluated '"
              + jexlExpression
              + "' and got more then one result. Expected one ore zero results.");
    }
    if (resultList.size() == 1) {
      return Optional.of(resultList.get(0));
    } else {
      return Optional.empty();
    }
  }

  public static void addContextDecorator(TigerJexlContextDecorator decorator) {
    CONTEXT_DECORATORS.add(decorator);
  }

  public static void setExpressionPreMapper(
      BiFunction<String, TigerJexlContext, List<String>> preMapper) {
    expressionPreMapper = preMapper;
  }

  private static TigerJexlExecutor createNewExecutor() {
    return new TigerJexlExecutor();
  }

  public static TigerJexlContext buildJexlMapContext(Object element, Optional<String> key) {
    final TigerJexlContext mapContext = new TigerJexlContext();

    CONTEXT_DECORATORS.forEach(decorator -> decorator.decorate(element, key, mapContext));

    return mapContext;
  }

  private static void tigerJexlMapDecorator(
      Object element, Optional<String> key, TigerJexlContext context) {
    context.put("element", element);
    if (element != null) {
      context.put("type", element.getClass().getSimpleName());
      context.put("content", getContent(element));
    }
  }

  private static String getContent(Object element) {
    return element.toString();
  }

  private static JexlEngine getJexlEngine() {
    JexlBuilder jexlBuilder =
        new JexlBuilder()
            .namespaces(NAMESPACE_MAP)
            .permissions(JexlPermissions.UNRESTRICTED)
            .strict(true);
    jexlBuilder.options().setStrictArithmetic(false);
    return jexlBuilder.create();
  }

  public static void registerAdditionalNamespace(String namespace, Object value) {
    NAMESPACE_MAP.put(namespace, value);
  }

  public static void deregisterNamespace(String namespace) {
    NAMESPACE_MAP.remove(namespace);
  }

  private boolean matchesAsJexlExpressionInternal(Object element, String jexlExpression) {
    return matchesAsJexlExpression(
        jexlExpression, new TigerJexlContext().withCurrentElement(element));
  }

  private boolean matchesAsJexlExpressionInternal(String jexlExpression, TigerJexlContext context) {
    final boolean result =
        createNewExecutor().evaluateJexlExpressionInternal(jexlExpression, context).stream()
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast)
            .reduce(Boolean.FALSE, Boolean::logicalOr);

    if (result && activateJexlDebugging) {
      printDebugMessage(context.getCurrentElement(), jexlExpression);
    }

    return result;
  }

  private List<Object> evaluateJexlExpressionInternal(
      final String jexlExpression, final TigerJexlContext externalContext) {
    try {
      final TigerJexlContext contextMap =
          buildJexlMapContext(
              externalContext.getCurrentElement(), Optional.ofNullable(externalContext.getKey()));
      contextMap.putAll(NAMESPACE_MAP);
      contextMap.putAll(externalContext);

      return buildExpressions(jexlExpression, contextMap).stream()
          .map(
              expression -> {
                Object result = expression.evaluate(contextMap);
                if (activateJexlDebugging) {
                  log.debug("Evaluated \"{}\" to '{}'", jexlExpression, result);
                }
                return result;
              })
          .toList();
    } catch (RuntimeException e) {
      if (e instanceof JexlException && (e.getCause() instanceof JexlArithmetic.NullOperand)) {
        return List.of();
      }
      if (e instanceof JexlException && !(e.getCause() instanceof NoSuchElementException)) {
        throw new TigerJexlException("Error while parsing expression '" + jexlExpression + "'", e);
      }
      log.warn("Error during Jexl-Evaluation.", e);
      return List.of();
    }
  }

  protected void printDebugMessage(Object element, String jexlExpression) {
    log.trace("Found match: '{}' matches '{}'", element, jexlExpression);
  }

  private List<JexlExpression> buildExpressions(
      String jexlExpression, TigerJexlContext mapContext) {
    return expressionPreMapper.apply(jexlExpression, mapContext).stream()
        .map(getJexlEngine()::createExpression)
        .toList();
  }

  public JexlScript buildScript(String jexlScript) {
    return getJexlEngine().createScript(jexlScript);
  }
}
