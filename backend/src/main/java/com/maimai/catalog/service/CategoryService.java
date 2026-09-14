package com.maimai.catalog.service;

import com.maimai.catalog.domain.Category;
import com.maimai.catalog.dto.CatalogDtos.CategoryNode;
import com.maimai.catalog.repo.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** 分类树：仅 ACTIVE，按 sort 排序。 */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<Category> categories = categoryRepository.findByStatusOrderBySort(Category.Status.ACTIVE);
        Map<Long, List<Category>> childrenOf = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category category : categories) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenOf.computeIfAbsent(category.getParentId(), k -> new ArrayList<>()).add(category);
            }
        }
        List<CategoryNode> result = new ArrayList<>();
        for (Category root : roots) {
            result.add(toNode(root, childrenOf));
        }
        return result;
    }

    private CategoryNode toNode(Category category, Map<Long, List<Category>> childrenOf) {
        List<CategoryNode> children = new ArrayList<>();
        for (Category child : childrenOf.getOrDefault(category.getId(), List.of())) {
            children.add(toNode(child, childrenOf));
        }
        return new CategoryNode(category.getId(), category.getName(), children);
    }
}
