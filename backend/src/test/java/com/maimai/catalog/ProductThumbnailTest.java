package com.maimai.catalog;

import com.maimai.catalog.service.ProductThumbnailService;
import com.maimai.common.BizException;
import com.maimai.config.MaimaiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ProductThumbnailTest {
    @TempDir Path root;
    ProductThumbnailService service() {
        var properties=new MaimaiProperties(); properties.setUploadDir(root.toString());
        return new ProductThumbnailService(properties);
    }
    @Test void createsBoundedDerivativePreservesOriginalAndReusesCache() throws Exception {
        Files.createDirectory(root.resolve("products"));
        Path source=root.resolve("products/photo.jpg");
        ImageIO.write(new BufferedImage(1200,800,BufferedImage.TYPE_INT_RGB),"jpeg",source.toFile());
        byte[] original=Files.readAllBytes(source);
        var service=service();var first=service.get(160,"photo.jpg");
        var image=ImageIO.read(first.path().toFile());
        assertThat(image.getWidth()).isEqualTo(160);assertThat(image.getHeight()).isEqualTo(107);
        var modified=Files.getLastModifiedTime(first.path());
        assertThat(service.get(160,"photo.jpg")).isEqualTo(first);
        assertThat(Files.getLastModifiedTime(first.path())).isEqualTo(modified);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        Files.setLastModifiedTime(source,java.nio.file.attribute.FileTime.fromMillis(first.lastModified()+1000));
        assertThat(service.get(160,"photo.jpg").etag()).isNotEqualTo(first.etag());
        assertThat(Files.exists(first.path())).isTrue();
    }
    @Test void rejectsTraversalArbitrarySizesAndNonImages() throws Exception {
        var service=service();
        for(String path:new String[]{"../private.jpg","sub/photo.jpg","photo.env",".secret.jpg"})
            assertThatThrownBy(()->service.get(160,path)).isInstanceOf(BizException.class);
        assertThatThrownBy(()->service.get(150,"photo.jpg")).isInstanceOf(BizException.class);
        Files.createDirectory(root.resolve("products"));Files.writeString(root.resolve("products/bad.jpg"),"not an image");
        assertThatThrownBy(()->service.get(160,"bad.jpg")).isInstanceOf(BizException.class);
        assertThat(Files.exists(root.resolve("products/.thumbnails/bad.jpg"))).isFalse();
    }
}
