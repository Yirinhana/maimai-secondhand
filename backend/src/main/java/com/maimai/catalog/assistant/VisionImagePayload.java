package com.maimai.catalog.assistant;

import com.maimai.common.BizException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.Semaphore;

/** Accepts only already-owned JPEG bytes. No URL fetching, disk cache or original metadata is sent. */
public final class VisionImagePayload {
    public static final int MAX_IMAGES = 3;
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_LONG_SIDE = 960;
    public static final long MAX_PIXELS = 16_000_000;
    private static final int MAX_ENCODED_BYTES = 1024 * 1024;
    private static final Semaphore DECODER = new Semaphore(1);

    private VisionImagePayload() {}

    public static List<String> dataUrls(List<byte[]> images) {
        if (images == null || images.isEmpty() || images.size() > MAX_IMAGES)
            throw BizException.badRequest("LISTING_AI_IMAGES", "请先上传1至3张商品照片，再让麦仔识图");
        // Check the entire batch before allocating any decoded image buffers.
        for (byte[] image : images) {
            if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES)
                throw BizException.badRequest("LISTING_AI_IMAGE_SIZE", "每张商品照片须在5MB以内，请压缩后重试");
        }
        if (!DECODER.tryAcquire()) throw BizException.tooMany("麦仔正在处理图片，请稍后重试");
        try {
            var result = new ArrayList<String>(images.size());
            for (byte[] image : images)
                result.add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(reencode(image)));
            return List.copyOf(result);
        } finally { DECODER.release(); }
    }

    private static byte[] reencode(byte[] bytes) {
        if (bytes.length < 4 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216 || (bytes[2] & 255) != 255)
            throw format();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw format();
            var reader = readers.next();
            BufferedImage source = null, scaled = null;
            try {
                if (!Set.of("JPEG", "JPG").contains(reader.getFormatName().toUpperCase(Locale.ROOT))) throw format();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 10_000 || height > 10_000 || (long) width * height > MAX_PIXELS)
                    throw BizException.badRequest("LISTING_AI_IMAGE_DIMENSION", "照片分辨率过大，请缩小至1600万像素以内");
                var read = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / MAX_LONG_SIDE);
                read.setSourceSubsampling(sample, sample, 0, 0);
                source = reader.read(0, read);
                double factor = Math.min(1.0, (double) MAX_LONG_SIDE / Math.max(source.getWidth(), source.getHeight()));
                scaled = new BufferedImage(Math.max(1, (int) (source.getWidth() * factor)),
                        Math.max(1, (int) (source.getHeight() * factor)), BufferedImage.TYPE_INT_RGB);
                var graphics = scaled.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, scaled.getWidth(), scaled.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
                } finally { graphics.dispose(); }
                var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                try (var outputBytes = new ByteArrayOutputStream(); var output = new MemoryCacheImageOutputStream(outputBytes)) {
                    writer.setOutput(output);
                    var parameters = writer.getDefaultWriteParam();
                    parameters.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    parameters.setCompressionQuality(0.82f);
                    writer.write(null, new javax.imageio.IIOImage(scaled, null, null), parameters);
                    output.flush();
                    if (outputBytes.size() > MAX_ENCODED_BYTES)
                        throw BizException.badRequest("LISTING_AI_IMAGE_SIZE", "照片细节过多，请压缩后重试");
                    return outputBytes.toByteArray();
                } finally { writer.dispose(); }
            } finally {
                reader.dispose();
                if (source != null) source.flush();
                if (scaled != null) scaled.flush();
            }
        } catch (BizException expected) { throw expected; }
        catch (IOException | RuntimeException error) { throw format(); }
    }

    private static BizException format() {
        return BizException.badRequest("LISTING_AI_IMAGE_FORMAT", "照片无法识别，请重新上传清晰的商品照片");
    }
}
