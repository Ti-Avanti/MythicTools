package gg.fotia.mythictools.gui;

import java.util.List;
import java.util.Objects;

/** Builds a stable page of loaded MythicMob choices for a mob member draft. */
final class MobMemberMobSelectorModel {
    private MobMemberMobSelectorModel() {
    }

    static Page page(List<String> loadedMobIds, String currentMobId, int requestedPage, int pageSize) {
        Objects.requireNonNull(loadedMobIds, "loadedMobIds");
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        List<String> available = loadedMobIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        int maximumPage = Math.max(0, (available.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        int start = page * pageSize;
        int end = Math.min(available.size(), start + pageSize);
        List<Option> options = available.subList(start, end).stream()
                .map(id -> new Option(id, id.equals(currentMobId)))
                .toList();
        return new Page(options, page);
    }

    record Page(List<Option> options, int number) {
    }

    record Option(String id, boolean selected) {
    }
}
