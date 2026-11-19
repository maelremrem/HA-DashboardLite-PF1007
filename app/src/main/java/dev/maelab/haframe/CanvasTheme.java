package dev.maelab.haframe;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, strict CSS subset for Android Canvas. No HTML or WebView is involved. */
final class CanvasTheme {
    static final int MAX_CSS_LENGTH = 12000;
    private static final Pattern BLOCK = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern VARIABLE = Pattern.compile("var\\(\\s*(--[a-z][a-z0-9-]*)\\s*\\)");
    private static final Pattern HEX = Pattern.compile("#[a-fA-F0-9]{3}(?:[a-fA-F0-9]{3})?");
    private static final Pattern PIXELS = Pattern.compile("([0-9]{1,3})(?:px)?");
    private static final Pattern NAME = Pattern.compile("--[a-z][a-z0-9-]*");
    private static final String SELECTORS =
        "|body|.header|.subtitle|.clock|.date|.status|.settings|.section|" +
        ".metric|.metric-label|.metric-value|.control|.control.on|.control.pressed|" +
        ".control-label|.control-state|.accent|" +
        ".metric-1|.metric-2|.metric-3|" +
        ".control-1|.control-2|.control-3|.control-4|.control-5|.control-6|" +
        ".metric-4|.metric-5|.metric-6|.metric-7|.metric-8|.metric-9|.metric-10|.metric-11|.metric-12|" +
        ".control-7|.control-8|.control-9|.control-10|.control-11|.control-12|" +
        ".card|.page-tab|.page-tab.active|";

    private final Map<String, Map<String, String>> rules = new HashMap<>();
    private final Map<String, String> variables = new HashMap<>();

    static CanvasTheme parse(String css) {
        CanvasTheme theme = new CanvasTheme();
        if (css == null || css.trim().isEmpty()) return theme;
        if (css.length() > MAX_CSS_LENGTH) throw new IllegalArgumentException("CSS trop long (12 000 caractères maximum)");
        String clean = COMMENT.matcher(css).replaceAll("");
        Matcher blocks = BLOCK.matcher(clean);
        int end = 0;
        int count = 0;
        while (blocks.find()) {
            if (!clean.substring(end, blocks.start()).trim().isEmpty()) {
                throw new IllegalArgumentException("Syntaxe CSS invalide avant un sélecteur");
            }
            end = blocks.end();
            String[] selectors = blocks.group(1).trim().split(",");
            for (String rawSelector : selectors) {
                String selector = rawSelector.trim();
                if (!":root".equals(selector) && !SELECTORS.contains("|" + selector + "|")) {
                    throw new IllegalArgumentException("Sélecteur non pris en charge : " + selector);
                }
                Map<String, String> properties = theme.rules.get(selector);
                if (properties == null) {
                    properties = new HashMap<>();
                    theme.rules.put(selector, properties);
                }
                for (String declaration : blocks.group(2).split(";")) {
                    if (declaration.trim().isEmpty()) continue;
                    int delimiter = declaration.indexOf(':');
                    if (delimiter <= 0) throw new IllegalArgumentException("Déclaration CSS invalide : " + declaration.trim());
                    String property = declaration.substring(0, delimiter).trim().toLowerCase(Locale.US);
                    String value = declaration.substring(delimiter + 1).trim();
                    if (property.startsWith("--")) {
                        if (!":root".equals(selector) || !NAME.matcher(property).matches()) {
                            throw new IllegalArgumentException("Variable CSS invalide : " + property);
                        }
                        if (!HEX.matcher(value).matches()) throw new IllegalArgumentException("Variable CSS : utiliser une couleur #RRGGBB");
                        theme.variables.put(property, value);
                    } else {
                        if (!property.equals("color") && !property.equals("background-color") &&
                            !property.equals("border-radius") && !property.equals("font-size") &&
                            !property.equals("font-weight")) {
                            throw new IllegalArgumentException("Propriété non prise en charge : " + property);
                        }
                        properties.put(property, value);
                    }
                    if (++count > 180) throw new IllegalArgumentException("Trop de règles CSS");
                }
            }
        }
        if (!clean.substring(end).trim().isEmpty()) throw new IllegalArgumentException("Accolade CSS manquante ou contenu invalide");
        for (Map<String, String> properties : theme.rules.values()) {
            for (Map.Entry<String, String> property : properties.entrySet()) {
                String value = theme.resolve(property.getValue());
                switch (property.getKey()) {
                    case "color":
                    case "background-color":
                        if (!HEX.matcher(value).matches()) throw new IllegalArgumentException("Couleur CSS invalide : " + value);
                        break;
                    case "font-weight":
                        if (!"bold".equals(value) && !"normal".equals(value)) throw new IllegalArgumentException("font-weight : utiliser bold ou normal");
                        break;
                    case "border-radius":
                    case "font-size":
                        Matcher px = PIXELS.matcher(value);
                        if (!px.matches()) throw new IllegalArgumentException(property.getKey() + " : valeur px invalide");
                        int n = Integer.parseInt(px.group(1));
                        if (n > 72 || (property.getKey().equals("font-size") && n < 10)) {
                            throw new IllegalArgumentException(property.getKey() + " : valeur hors limites");
                        }
                        break;
                    default: break;
                }
            }
        }
        return theme;
    }

    private String resolve(String value) {
        Matcher m = VARIABLE.matcher(value);
        if (!m.matches()) return value;
        String resolved = variables.get(m.group(1));
        if (resolved == null) throw new IllegalArgumentException("Variable CSS inconnue : " + m.group(1));
        return resolved;
    }

    private String property(String name, String... selectors) {
        String value = null;
        for (String selector : selectors) {
            Map<String, String> props = rules.get(selector);
            if (props != null && props.containsKey(name)) value = props.get(name);
        }
        return value == null ? null : resolve(value);
    }

    int color(int fallback, String property, String... selectors) {
        String value = property(property, selectors);
        if (value == null) return fallback;
        String hex = value.substring(1);
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        return (int) (0xFF000000L | Long.parseLong(hex, 16));
    }

    float size(float fallback, String property, String... selectors) {
        String value = property(property, selectors);
        return value == null ? fallback : Integer.parseInt(PIXELS.matcher(value).replaceAll("$1"));
    }

    boolean bold(boolean fallback, String... selectors) {
        String value = property("font-weight", selectors);
        return value == null ? fallback : value.equals("bold");
    }
}
