package dev.habitdock;
import java.util.*;
import java.text.Collator;

public final class AppCatalog {
    public static class Entry {
        public final String pkg, label;
        public Entry(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }
    public static <T extends Entry> List<T> filter(List<T> apps, Set<String> selected, String query) {
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<T> result = new ArrayList<>();
        for (T app : apps) {
            String haystack = (app.label + " " + app.pkg).toLowerCase(Locale.ROOT);
            boolean match = true;
            for (String word : words)
                if (!haystack.contains(word)) {
                    match = false;
                    break;
                }
            if (match)
                result.add(app);
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort(Comparator.comparing((T a) -> !selected.contains(a.pkg))
                .thenComparing(a -> a.label, collator).thenComparing(a -> a.pkg));
        return result;
    }
}
