package com.maimai.catalog;

import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.catalog.service.ProductAssembler;
import com.maimai.identity.repo.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ProductImageMappingTest {
    @ParameterizedTest
    @ValueSource(strings={"products/seed-1.jpg","/uploads/products/seed-1.jpg"})
    void seedAndUploadedFormatsUseTheSamePublicUrlOnce(String storedPath) {
        var images=mock(ProductImageRepository.class);var users=mock(UserRepository.class);
        var item=new ProductImage();item.setId(9L);item.setProductId(1L);item.setPath(storedPath);item.setSort(0);
        when(images.findByProductIdOrderBySort(1L)).thenReturn(List.of(item));
        var assembler=new ProductAssembler(images,users);
        assertThat(assembler.coverImage(1L)).isEqualTo("/uploads/products/seed-1.jpg");
        assertThat(assembler.images(1L).getFirst().path()).isEqualTo("/uploads/products/seed-1.jpg");
    }
}
