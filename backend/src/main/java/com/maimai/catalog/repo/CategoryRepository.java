package com.maimai.catalog.repo;

import com.maimai.catalog.domain.Category;
import com.maimai.catalog.domain.Category.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByStatusOrderBySort(Status status);
}
