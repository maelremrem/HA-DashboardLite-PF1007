package dev.maelab.haframe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class ThemePresetsRunner {
    public static void main(String[] args) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        // Tests each generated CSS preset; the JSON escape sequence conversion is sufficient for the fixtures.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"([a-z]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        int count = 0;
        while (m.find()) {
            String css = m.group(2).replace("\\n", "\n").replace("\\\\\"", "\"");
            CanvasTheme.parse(css);
            count++;
        }
        if (count != 14) throw new AssertionError("Unexpected preset count: " + count);
        System.out.println("All 14 CSS presets validated by Java");
    }
}
