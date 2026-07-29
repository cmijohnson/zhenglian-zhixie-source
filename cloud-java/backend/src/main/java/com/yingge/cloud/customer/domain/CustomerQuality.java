package com.yingge.cloud.customer.domain;

import java.util.List;

public final class CustomerQuality {
    public static final String CATEGORY = "客户质量";
    public static final List<Level> LEVELS = List.of(
            new Level("A", "A类用户", "高质量客户，有急迫需求且明确需要此方向产品", "#16A34A"),
            new Level("B", "B类用户", "对产品有一定兴趣，但目前阶段暂无迫切需求", "#F59E0B"),
            new Level("C", "C类用户", "对产品需求很低，或当前购买、支付能力不足", "#64748B")
    );

    private CustomerQuality() {
    }

    public static boolean isReserved(String name, String category) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedCategory = category == null ? "" : category.trim();
        return CATEGORY.equals(normalizedCategory)
                || LEVELS.stream().anyMatch(level -> level.label().equalsIgnoreCase(normalizedName));
    }

    public static boolean hasMultipleQualityTags(List<String> categories) {
        return categories.stream().filter(CATEGORY::equals).limit(2).count() > 1;
    }

    public record Level(String code, String label, String description, String color) {
    }
}
