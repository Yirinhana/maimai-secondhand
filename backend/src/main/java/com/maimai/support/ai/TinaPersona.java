package com.maimai.support.ai;

import com.maimai.support.service.SupportFaq;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Versioned website persona; never loads the owner's personal Hermes profile. */
public final class TinaPersona {
    private TinaPersona() { }
    public static final String SYSTEM = load();
    private static String load() {
        try (var stream = TinaPersona.class.getResourceAsStream("/support/tina-system-prompt.txt")) {
            if (stream == null) throw new IllegalStateException("Missing Tina persona");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8) + "\n\n麦麦公开规则：\n"
                    + SupportFaq.items().stream().map(item -> item.title() + "：" + item.answer()).collect(Collectors.joining("\n"));
        } catch (IOException error) { throw new IllegalStateException("Cannot load Tina persona", error); }
    }
}
