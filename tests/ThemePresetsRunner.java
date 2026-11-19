package dev.maelab.haframe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class ThemePresetsRunner {
    public static void main(String[] args) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"(original|ocean|amber|light)\\\"\\s*:\\s*\\\"((?:\\\\\\.|[^\\\"])*)\\\"").matcher(json);
        int count = 0;
        while (m.find()) {
            String css = m.group(2).replace("\\n", "\n").replace("\\\"", "\"");
            CanvasTheme.parse(css);
            count++;
        }
        if (count != 4) throw new AssertionError("Unexpected preset count: " + count);
        System.out.println("All 4 CSS presets validated by Java");
    }
}
