package com.maimai.catalog.assistant;

import com.maimai.common.BizException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class VisionImagePayloadTest {
    static byte[] jpeg(int width, int height) throws Exception { return image(width, height, "jpeg"); }
    static byte[] image(int width, int height, String format) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.GRAY); graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.RED); graphics.fillRect(0, 0, Math.max(1, width / 3), height);
            var output = new ByteArrayOutputStream();
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        } finally { graphics.dispose(); image.flush(); }
    }

    static byte[] decoded(String url) {
        assertThat(url).startsWith("data:image/jpeg;base64,");
        return Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1));
    }

    @Test void resizesLongEdgeWithoutUpscalingAndEmitsOnlyJpeg() throws Exception {
        var values = VisionImagePayload.dataUrls(List.of(jpeg(2400, 1200), jpeg(240, 480), jpeg(1200, 2400)));
        for (int index = 0; index < values.size(); index++) {
            var image = ImageIO.read(new ByteArrayInputStream(decoded(values.get(index))));
            int[] width = {960, 240, 480}, height = {480, 480, 960};
            assertThat(image.getWidth()).isEqualTo(width[index]);
            assertThat(image.getHeight()).isEqualTo(height[index]);
            assertThat(decoded(values.get(index)).length).isLessThan(1024 * 1024);
        }
    }

    @Test void stripsCommentMetadataAndTrailingBytesBeforeSending() throws Exception {
        byte[] original = jpeg(40, 20);
        byte[] note = "PRIVATE-EXIF-GPS-ACCOUNT-AND-PASSWORD".getBytes(StandardCharsets.US_ASCII);
        var tagged = new ByteArrayOutputStream();
        tagged.write(original, 0, 2);
        tagged.write(255); tagged.write(254); tagged.write(0); tagged.write(note.length + 2); tagged.write(note);
        tagged.write(original, 2, original.length - 2); tagged.write(note);
        String result = VisionImagePayload.dataUrls(List.of(tagged.toByteArray())).getFirst();
        assertThat(new String(decoded(result), StandardCharsets.ISO_8859_1)).doesNotContain("PRIVATE-EXIF-GPS", "ACCOUNT-AND-PASSWORD");
        assertThat(ImageIO.read(new ByteArrayInputStream(decoded(result))).getWidth()).isEqualTo(40);
    }

    @Test void rejectsWrongFormatRatherThanTrustingAClaimedMimeType() throws Exception {
        for (byte[] bytes : List.of(image(16, 16, "png"), "https://localhost/private.jpg".getBytes(StandardCharsets.UTF_8),
                "<svg onload='steal()'/>".getBytes(StandardCharsets.UTF_8), new byte[]{(byte) 255, (byte) 216, (byte) 255, 0})) {
            assertThatThrownBy(() -> VisionImagePayload.dataUrls(List.of(bytes)))
                    .isInstanceOf(BizException.class).hasMessage("照片无法识别，请重新上传清晰的商品照片");
        }
    }

    @Test void checksBatchCountAndBytesBeforeDecoding() throws Exception {
        assertThatThrownBy(() -> VisionImagePayload.dataUrls(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> VisionImagePayload.dataUrls(List.of())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> VisionImagePayload.dataUrls(Collections.nCopies(4, jpeg(12, 12)))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> VisionImagePayload.dataUrls(Collections.singletonList(null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> VisionImagePayload.dataUrls(List.of(new byte[0]))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> VisionImagePayload.dataUrls(List.of(new byte[VisionImagePayload.MAX_IMAGE_BYTES + 1])))
                .isInstanceOf(BizException.class).hasMessageContaining("5MB");
    }

    @Test void rejectsLargeHeaderBeforeDecodingPixelBombAndReleasesDecoderAfterFailure() throws Exception {
        byte[] bomb = jpeg(16, 16);
        boolean changed = false;
        for (int i = 2; i < bomb.length - 10; i++) {
            if ((bomb[i] & 255) == 255 && (bomb[i + 1] & 255) == 192) {
                // JPEG SOF0: marker, length, precision, height, width.
                bomb[i + 5] = 0x13; bomb[i + 6] = (byte) 0x88;
                bomb[i + 7] = 0x13; bomb[i + 8] = (byte) 0x88;
                changed = true; break;
            }
        }
        assertThat(changed).isTrue();
        assertThatThrownBy(() -> VisionImagePayload.dataUrls(List.of(bomb))).isInstanceOf(BizException.class).hasMessageContaining("1600万");
        assertThat(VisionImagePayload.dataUrls(List.of(jpeg(16, 16)))).hasSize(1);
    }
}
