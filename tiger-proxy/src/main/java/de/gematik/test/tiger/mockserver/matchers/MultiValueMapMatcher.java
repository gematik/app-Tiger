/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.mockserver.collections.NottableStringMultiMap;
import de.gematik.test.tiger.mockserver.model.KeyMatchStyle;
import de.gematik.test.tiger.mockserver.model.KeyToMultiValue;
import de.gematik.test.tiger.mockserver.model.KeysToMultiValues;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class MultiValueMapMatcher
    extends NotMatcher<KeysToMultiValues<? extends KeyToMultiValue, ? extends KeysToMultiValues>> {
  private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
  private final NottableStringMultiMap matcher;
  private final KeysToMultiValues keysToMultiValues;
  private final boolean controlPlaneMatcher;
  private Boolean allKeysNotted;
  private Boolean allKeysOptional;

  MultiValueMapMatcher(
      KeysToMultiValues<? extends KeyToMultiValue, ? extends KeysToMultiValues> keysToMultiValues,
      boolean controlPlaneMatcher) {
    this.keysToMultiValues = keysToMultiValues;
    this.controlPlaneMatcher = controlPlaneMatcher;
    if (keysToMultiValues != null) {
      this.matcher =
          new NottableStringMultiMap(
              this.controlPlaneMatcher,
              keysToMultiValues.getKeyMatchStyle(),
              keysToMultiValues.getEntries());
    } else {
      this.matcher = null;
    }
  }

  public boolean matches(
      final MatchDifference context,
      KeysToMultiValues<? extends KeyToMultiValue, ? extends KeysToMultiValues> matched) {
    boolean result;

    if (matcher == null || matcher.isEmpty()) {
      result = true;
    } else if (matched == null || matched.isEmpty()) {
      if (allKeysNotted == null) {
        allKeysNotted = matcher.allKeysNotted();
      }
      if (allKeysOptional == null) {
        allKeysOptional = matcher.allKeysOptional();
      }
      result = allKeysNotted || allKeysOptional;
    } else {
      result =
          new NottableStringMultiMap(
                  controlPlaneMatcher,
                  matched.getKeyMatchStyle(),
                  matched.getEntries())
              .containsAll(context, matcher);
    }

    if (!result && context != null) {
      context.addDifference(
          "multimap match failed expected:{}found:{}failed because:{}",
          keysToMultiValues,
          matched != null ? matched : "none",
          matched != null
              ? (matcher.getKeyMatchStyle() == KeyMatchStyle.SUB_SET
                  ? "multimap is not a subset"
                  : "multimap values don't match")
              : "none is not a subset");
    }

    return not != result;
  }

  public boolean isBlank() {
    return matcher == null || matcher.isEmpty();
  }

  @Override
  @JsonIgnore
  protected String[] fieldsExcludedFromEqualsAndHashCode() {
    return EXCLUDED_FIELDS;
  }
}
