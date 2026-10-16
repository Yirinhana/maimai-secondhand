package com.maimai.catalog.assistant;

import java.util.List;

/** Photo recognition is advisory; the service authorizes draft images and the owner confirms publication. */
public interface ListingVisionModel {
    boolean configured();
    String modelName();
    Recognition recognize(List<byte[]> images, List<CategoryOption> categories);

    record CategoryOption(long id, String name) {}
    record Recognition(String title, Long categoryId, String description,
                       List<String> visibleFeatures, List<String> checklist) {}
}
