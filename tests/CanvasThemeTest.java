package dev.maelab.haframe;

public final class CanvasThemeTest {
    private static void check(boolean result) { if (!result) throw new AssertionError(); }
    private static void invalid(String css) {
        try { CanvasTheme.parse(css); throw new AssertionError("Expected error: " + css); }
        catch (IllegalArgumentException expected) { /* Expected */ }
    }
    public static void main(String[] args) {
        CanvasTheme theme = CanvasTheme.parse(":root { --accent: #abc; } body { background-color: #10151e; } " +
            ".metric, .control { border-radius: 26px; } .metric-1 { background-color: var(--accent); } " +
            ".clock { font-size: 44px; font-weight: normal; }");
        check(theme.color(0, "background-color", "body") == 0xFF10151E);
        check(theme.color(0, "background-color", ".metric", ".metric-1") == 0xFFAABBCC);
        check(theme.size(0, "border-radius", ".metric") == 26);
        check(theme.size(0, "font-size", ".clock") == 44);
        check(!theme.bold(true, ".clock"));
        check(CanvasTheme.parse("").color(123, "color", ".clock") == 123);
        CanvasTheme pages = CanvasTheme.parse(".page-tab { background-color: #101820; } " +
            ".page-tab.active { color: #f0f; } .card { border-radius: 18px; } " +
            ".metric-12, .control-12 { font-size: 19px; }");
        check(pages.color(0, "color", ".page-tab", ".page-tab.active") == 0xFFFF00FF);
        check(pages.size(0, "border-radius", ".card") == 18);
        invalid(".unknown { color: #fff; }");
        invalid(".metric { background-image: url(https://example.com/a); }");
        invalid(".metric { color: var(--missing); }");
        invalid(".metric { font-size: 999px; }");
        invalid(".metric { color: red; }");
        invalid(".metric { color: #aabbcc;");
        System.out.println("Canvas theme tests passed");
    }
}
