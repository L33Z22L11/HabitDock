package dev.habitdock;

import java.util.List;

/** Consecutive, non-wrapping pages in widget creation order. */
final class WidgetAllocation {
    static <T> List<T> page(List<T> recommendations, int offset, int capacity) {
        int start = Math.min(recommendations.size(), Math.max(0, offset));
        int end = start + Math.min(recommendations.size() - start, Math.max(0, capacity));
        return recommendations.subList(start, end);
    }
}
