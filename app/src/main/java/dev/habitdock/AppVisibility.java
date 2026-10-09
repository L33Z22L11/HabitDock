package dev.habitdock;
import java.util.*;

/**
 * Presentation filter only; hidden apps remain eligible for collection and
 * learning.
 */
public final class AppVisibility {
    public static Set<String> eligible(Set<String> installed, Set<String> hidden) {
        Set<String> result = new HashSet<>(installed);
        result.removeAll(hidden);
        return result;
    }
}
