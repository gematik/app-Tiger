/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GenericPrettyPrinter<T> {

    private final Predicate<T> isLeaf;
    private final Function<T, String> nodePrinter;
    private final Function<T, Stream<T>> leafRetriever;
    private String openingBrace = "[";
    private String closingBrace = "]";
    private String depthStepper = "\t";

    public String prettyPrint(T root) {
        return prettyPrint(root, 0);
    }

    private String prettyPrint(T node, int depth) {
        if (isLeaf.test(node)) {
            return depthStepper.repeat(depth) + openingBrace + "\n"
                + leafRetriever.apply(node)
                    .map(leaf -> prettyPrint(leaf, depth + 1))
                    .collect(Collectors.joining(",\n"))
                + "\n" + depthStepper.repeat(depth) + closingBrace;
        } else {
            return depthStepper.repeat(depth) + nodePrinter.apply(node);
        }

    }
}
