package dev.habitdock;

import java.util.*;
import java.util.stream.IntStream;

public final class WidgetAllocationTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int count : new int[]{0, 1, 8, 9, 17, 20, 100}) {
            List<Integer> pool = IntStream.range(0, count).boxed().toList();
            for (int[] capacities : new int[][]{{9, 8}, {8, 9}, {29, 29, 29, 29}, {3, 1, 8}}) {
                List<Integer> visible = new ArrayList<>();
                int offset = 0, total = 0;
                for (int capacity : capacities) {
                    List<Integer> page = WidgetAllocation.page(pool, offset, capacity);
                    if (page.size() > capacity)
                        throw new AssertionError("overflow");
                    visible.addAll(page);
                    offset += page.size();
                    total += capacity;
                }
                if (!visible.equals(pool.subList(0, Math.min(count, total))))
                    throw new AssertionError("missing, repeated or reordered recommendations");
                checks++;
            }
        }
        System.out.println("PASS " + checks + " allocation scenarios");
    }
}
