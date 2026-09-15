package com.maimai.catalog.service;

import com.maimai.common.BizException;
import com.maimai.config.MaimaiProperties;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

/** Bounded, on-disk derivatives of public product photos. Original images remain untouched. */
@Service
public class ProductThumbnailService {
    private static final Set<Integer> WIDTHS = Set.of(160, 480, 960);
    private final Path products;
    private final Object rendering = new Object();
    public ProductThumbnailService(MaimaiProperties properties) {
        products = Path.of(properties.getUploadDir()).toAbsolutePath().normalize().resolve("products");
    }
    public record Thumbnail(Path path, String etag, long lastModified) {}
    public Thumbnail get(int width, String name) throws IOException {
        if (!WIDTHS.contains(width) || !name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,120}\\.(jpg|jpeg|png)"))
            throw BizException.notFound("图片不存在");
        Path source = products.resolve(name);
        if (Files.isSymbolicLink(products) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
            throw BizException.notFound("图片不存在");
        long modified = Files.getLastModifiedTime(source).toMillis(), size = Files.size(source);
        if (size > 5 * 1024 * 1024) throw BizException.notFound("图片不可用");
        String signature = width + "-" + modified + "-" + size;
        Path cache = products.resolve(".thumbnails");
        if (Files.isSymbolicLink(cache)) throw new IOException("Invalid image cache directory");
        Path target = cache.resolve(name + "." + signature + ".jpg");
        synchronized (rendering) {
            if (Files.isSymbolicLink(target)) throw new IOException("Invalid image cache file");
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(cache);
                render(source, target, width);
            }
        }
        return new Thumbnail(target, '"' + name + '-' + signature + '"', modified);
    }
    private void render(Path source, Path target, int width) throws IOException {
        try (var input = ImageIO.createImageInputStream(source.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw BizException.notFound("图片不可用");
            var reader = readers.next();
            BufferedImage original = null, scaled = null;
            Path temporary = null;
            try {
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > 10000 || h > 10000 || (long) w*h > 16_000_000)
                    throw BizException.notFound("图片尺寸不可用");
                original = reader.read(0);
                double ratio = Math.min(1d, (double) width / Math.max(w, h));
                scaled = new BufferedImage(Math.max(1,(int)Math.round(w*ratio)), Math.max(1,(int)Math.round(h*ratio)), BufferedImage.TYPE_INT_RGB);
                var graphics = scaled.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0,0,scaled.getWidth(),scaled.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(original,0,0,scaled.getWidth(),scaled.getHeight(),null);
                } finally { graphics.dispose(); }
                temporary = Files.createTempFile(target.getParent(), "thumb-", ".tmp");
                var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                try (var output = ImageIO.createImageOutputStream(temporary.toFile())) {
                    writer.setOutput(output);
                    var params = writer.getDefaultWriteParam();
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); params.setCompressionQuality(.8f);
                    writer.write(null,new IIOImage(scaled,null,null),params);
                } finally { writer.dispose(); }
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
            } finally {
                reader.dispose();
                if (original != null) original.flush();
                if (scaled != null) scaled.flush();
                if (temporary != null) Files.deleteIfExists(temporary);
            }
        }
    }
}
