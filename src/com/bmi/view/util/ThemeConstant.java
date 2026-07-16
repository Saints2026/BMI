package com.bmi.view.util;

import javafx.scene.Parent;
import javafx.scene.Scene;

public final class ThemeConstant {

    private ThemeConstant() {
    }

    public enum Theme {
        FRESH_BLUE("fresh", "theme.fresh", "rgb(248,248,250)", "rgb(30,30,30)", "rgb(242,242,245)", "rgb(200,200,200)", "rgb(150,150,150)", "rgb(45,140,220)", "rgb(35,110,180)",  "rgb(40,160,60)", "rgb(232,245,235)", "rgb(240,170,20)", "rgb(252,243,224)", "rgb(220,40,40)", "rgb(250,230,230)"),
        EYE_CARE(  "eye",   "theme.eye",   "rgb(240,241,245)", "rgb(42,47,56)", "rgb(228,230,234)", "rgb(201,205,212)", "rgb(112,119,132)", "rgb(99,143,201)", "rgb(74,122,184)",  "rgb(106,184,154)", "rgb(234,246,240)", "rgb(212,148,90)", "rgb(248,242,232)", "rgb(217,107,108)", "rgb(248,234,234)"),
        WARM_WHITE("warm",  "theme.warm",  "rgb(255,251,242)", "rgb(74,63,53)", "rgb(247,239,230)", "rgb(236,220,200)", "rgb(160,138,118)", "rgb(90,159,216)", "rgb(61,139,199)",  "rgb(124,184,138)", "rgb(239,248,241)", "rgb(212,160,90)", "rgb(253,246,236)", "rgb(217,122,107)", "rgb(249,238,235)");

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

    public static final String STATUS_NORMAL = "rgb(40,160,60)";
    public static final String STATUS_WARN   = "rgb(240,170,20)";
    public static final String STATUS_DANGER = "rgb(220,40,40)";

    public static final String LINE_BLUE   = "rgb(45,140,220)";
    public static final String LINE_GREEN  = "rgb(40,160,60)";
    public static final String LINE_ORANGE = "rgb(240,170,20)";

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
