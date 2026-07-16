package com.bmi.view.util;

import javafx.scene.Parent;
import javafx.scene.Scene;

public final class ThemeConstant {

    private ThemeConstant() {
    }

    public enum Theme {
        FRESH_BLUE("fresh", "theme.fresh", "#ffffff", "#222222", "#f8f9fa", "#e5e7eb", "#86909c", "#4096ff", "#2979ff",  "#7cd9b5", "#e8ffef", "#faad14", "#fff7e8", "#f76b6c", "#ffece8"),
        EYE_CARE(  "eye",   "theme.eye",   "#f0f1f5", "#2a2f38", "#e4e6ea", "#c9cdd4", "#707784", "#638fc9", "#4a7ab8",  "#6ab89a", "#eaf6f0", "#d4945a", "#f8f2e8", "#d96b6c", "#f8eaea"),
        WARM_WHITE("warm",  "theme.warm",  "#fffbf2", "#4a3f35", "#f7efe6", "#ecdcc8", "#a08a76", "#5a9fd8", "#3d8bc7",  "#7cb88a", "#eff8f1", "#d4a05a", "#fdf6ec", "#d97a6b", "#f9eeeb");

        private final String cssClass;
        private final String nameKey;
        private final String bg, fg, panel, border, muted;
        private final String primary, primaryDark;
        private final String success, successBg;
        private final String warning, warningBg;
        private final String danger, dangerBg;

        Theme(String cssClass, String nameKey, String bg, String fg, String panel,
              String border, String muted, String primary, String primaryDark,
              String success, String successBg, String warning, String warningBg,
              String danger, String dangerBg) {
            this.cssClass = cssClass; this.nameKey = nameKey;
            this.bg = bg; this.fg = fg; this.panel = panel; this.border = border;
            this.muted = muted; this.primary = primary; this.primaryDark = primaryDark;
            this.success = success; this.successBg = successBg;
            this.warning = warning; this.warningBg = warningBg;
            this.danger = danger; this.dangerBg = dangerBg;
        }

        public String cssClass() { return cssClass; }
        public String nameKey() { return nameKey; }
        public String bg() { return bg; }
        public String fg() { return fg; }
        public String panel() { return panel; }
        public String border() { return border; }
        public String muted() { return muted; }
        public String primary() { return primary; }
        public String primaryDark() { return primaryDark; }
        public String success() { return success; }
        public String successBg() { return successBg; }
        public String warning() { return warning; }
        public String warningBg() { return warningBg; }
        public String danger() { return danger; }
        public String dangerBg() { return dangerBg; }

        public String lineA() { return primary; }
        public String lineB() { return success; }
        public String lineC() { return warning; }
    }

    public static final Theme DEFAULT_THEME = Theme.FRESH_BLUE;
    public static final Theme[] ALL = Theme.values();

    public static final String STATUS_NORMAL = "#7cd9b5";
    public static final String STATUS_WARN   = "#faad14";
    public static final String STATUS_DANGER = "#f76b6c";

    public static final String LINE_BLUE   = "#4096ff";
    public static final String LINE_GREEN  = "#7cd9b5";
    public static final String LINE_ORANGE = "#faad14";

    public static String seriesColor(Theme theme, int index) {
        Theme t = theme == null ? DEFAULT_THEME : theme;
        switch (((index % 3) + 3) % 3) {
            case 0: return t.lineA();
            case 1: return t.lineB();
            default: return t.lineC();
        }
    }

    public static String bmiGradeColor(double bmi) {
        if (bmi < 18.5) return LINE_BLUE;
        if (bmi < 24)   return STATUS_NORMAL;
        if (bmi < 28)   return STATUS_WARN;
        return STATUS_DANGER;
    }

    public static Theme fromCssClass(String cssClass) {
        if (cssClass == null) return DEFAULT_THEME;
        for (Theme t : ALL) {
            if (t.cssClass().equals(cssClass)) return t;
        }
        return DEFAULT_THEME;
    }

    public static void apply(Scene scene, Theme theme) {
        if (scene == null || scene.getRoot() == null) return;
        apply(scene.getRoot(), theme);
    }

    public static void apply(Parent root, Theme theme) {
        if (root == null) return;
        root.getStyleClass().removeIf(c -> c.startsWith("theme-"));
        root.getStyleClass().add("theme-" + theme.cssClass());
    }
}
