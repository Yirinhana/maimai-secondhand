package com.maimai.common;

import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;

/** 私有证据/聊天图片共享解码预算，拒绝伪图片并重新编码去除元数据。 */
public final class SafeImageEncoder {
    private static final Semaphore ENCODER = new Semaphore(1);
    private SafeImageEncoder() {}
    public static byte[] encode(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw BizException.badRequest(prefix+"_IMAGE_SIZE", "请选择5MB以内的JPG或PNG图片");
        if (!ENCODER.tryAcquire()) throw BizException.tooMany("正在处理图片，请稍后重试");
        try (InputStream input = file.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw format(prefix);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw format(prefix);
            ImageReader reader = readers.next();
            try {
                if (!Set.of("JPEG", "JPG", "PNG").contains(reader.getFormatName().toUpperCase(Locale.ROOT))) throw format(prefix);
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 16_000_000 || width > 10000 || height > 10000)
                    throw BizException.badRequest(prefix+"_IMAGE_DIMENSION", "图片分辨率过大，请先压缩");
                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0, 1600.0 / Math.max(width, height));
                BufferedImage output = new BufferedImage(Math.max(1, (int)(width*scale)), Math.max(1, (int)(height*scale)), BufferedImage.TYPE_INT_RGB);
                try {
                    Graphics2D graphics = output.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
                    } finally { graphics.dispose(); }
                    var bytes = new ByteArrayOutputStream();
                    if (!ImageIO.write(output, "jpeg", bytes)) throw new IOException("JPEG encoder unavailable");
                    return bytes.toByteArray();
                } finally {source.flush();output.flush();}
            } finally {reader.dispose();}
        } finally {ENCODER.release();}
    }
    private static BizException format(String prefix) {return BizException.badRequest(prefix+"_IMAGE_FORMAT", "仅支持JPG和PNG图片");}
}
