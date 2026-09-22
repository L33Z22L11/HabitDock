package dev.habitdock;
import java.util.*;

/** Shared by collection, ranking and pins: privacy exclusions always win. */
public final class PrivacyPolicy {
    public static Set<String> eligible(Set<String> installed, Set<String> hidden) {
        Set<String> result = new HashSet<>(installed);
        result.removeAll(hidden);
        return result;
    }
}
