package gg.fotia.mythictools.gui;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 构建仅包含已加载且尚未分配掉落组的稳定选择页。 */
final class MobDropGroupSelectorModel {
    private MobDropGroupSelectorModel() {
    }

    static Page page(
            List<String> loadedGroupIds,
            List<String> assignedGroupIds,
            int requestedPage,
            int pageSize) {
        Objects.requireNonNull(loadedGroupIds, "loadedGroupIds");
        Objects.requireNonNull(assignedGroupIds, "assignedGroupIds");
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        Set<String> assigned = Set.copyOf(assignedGroupIds);
        List<String> available = loadedGroupIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .filter(id -> !assigned.contains(id))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        int maximumPage = Math.max(0, (available.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        int start = page * pageSize;
        int end = Math.min(available.size(), start + pageSize);
        return new Page(List.copyOf(available.subList(start, end)), page);
    }

    record Page(List<String> options, int number) {
    }
}
