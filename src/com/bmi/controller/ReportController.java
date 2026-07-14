package com.bmi.controller;

import com.bmi.model.BodyRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 报告导出控制器（对齐 ui_design.md 第七章「报告导出页面」+ db_design.md §8.3.2）。
 *
 * 职责：聚合历史数据（RecordController）+ 趋势图（ChartController）+ 已生成 AI 建议（AiController），
 * 渲染为单文件 HTML 报告，保存到 {@code user.home/bmi/reports/bmi_report_{userId}_{date}.html}（本地数据，不入库、不提交仓库）。
 *
 * 趋势图以内联 SVG 折线呈现（宪章白名单：仅 JDK + JavaFX，无第三方图表库；报告内嵌 SVG 不影响 DB）。
 * 报告内容章节由 {@link ReportOptions} 控制（基础数据 / 趋势图 / AI 建议）。
 */
public class ReportController {

    private final RecordController recordController;
    private final ChartController chartController;
    private final AiController aiController;

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE = new SimpleDateFormat("yyyyMMdd");

    public ReportController(RecordController recordController,
                            ChartController chartController,
                            AiController aiController) {
        this.recordController = recordController;
        this.chartController = chartController;
        this.aiController = aiController;
    }

    /** 报告内容选项（章节开关）。 */
    public static class ReportOptions {
        public boolean includeBasic = true; // 基础数据表
        public boolean includeTrend = true; // 趋势 SVG 图
        public boolean includeAi = true;    // AI 健康建议
    }

    /**
     * 生成并保存 HTML 报告。
     *
     * @param userId 用户 ID（文件名防覆盖 + 数据隔离）
     * @param start  时间下界（null=不限）
     * @param end    时间上界（null=不限）
     * @param options 章节开关（null=全部默认开启）
     * @return 报告本地绝对路径；失败返回 null
     */
    public String exportHtml(long userId, Timestamp start, Timestamp end, ReportOptions options) {
        if (options == null) {
            options = new ReportOptions();
        }
        List<BodyRecord> records = recordController.queryRecords(userId, start, end);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='zh'><head><meta charset='utf-8'>")
                .append("<title>BMI 体质评估报告</title>")
                .append("<style>body{font-family:system-ui,'Microsoft YaHei',sans-serif;margin:24px;color:#222}")
                .append("table{border-collapse:collapse;width:100%;margin:12px 0}")
                .append("th,td{border:1px solid #ddd;padding:6px 10px;text-align:center}")
                .append("th{background:#f5f5f5}.section{margin:20px 0}h2{color:#1565c0}")
                .append("pre{white-space:pre-wrap;background:#fafafa;padding:12px;border-radius:6px}</style>")
                .append("</head><body>");
        html.append("<h1>BMI 体质评估报告</h1>");
        html.append("<p>用户ID：").append(userId)
                .append(" ｜ 生成时间：").append(FMT.format(new java.util.Date())).append("</p>");

        if (options.includeBasic) {
            html.append("<div class='section'><h2>一、基础数据</h2>");
            if (records.isEmpty()) {
                html.append("<p>暂无记录数据。</p>");
            } else {
                html.append("<table><tr>")
                        .append(th("测量时间")).append(th("身高(cm)")).append(th("体重(kg)"))
                        .append(th("BMI")).append(th("体脂率(%)"))
                        .append(th("腰围")).append(th("臀围")).append(th("腕围"))
                        .append(th("高压")).append(th("低压")).append(th("心率"))
                        .append(th("内脏脂肪")).append(th("既往疾病"))
                        .append("</tr>");
                for (BodyRecord r : records) {
                    html.append("<tr>")
                            .append(td(r.getMeasureTime() == null ? "" : FMT.format(r.getMeasureTime())))
                            .append(td(r.getHeight())).append(td(r.getWeight()))
                            .append(td(round1(r.getBmi()))).append(td(round1(r.getBodyFat())))
                            .append(td(r.getWaistCircum())).append(td(r.getHipCircum())).append(td(r.getWristCircum()))
                            .append(td(r.getSystolicBp())).append(td(r.getDiastolicBp())).append(td(r.getHeartRate()))
                            .append(td(r.getVisceralFat())).append(td(r.getDiseases()))
                            .append("</tr>");
                }
                html.append("</table></div>");
            }
        }

        if (options.includeTrend && !records.isEmpty()) {
            html.append("<div class='section'><h2>二、趋势图表</h2>");
            html.append(buildSvgTrend(records, "BMI 趋势", BodyRecord::getBmi));
            html.append(buildSvgTrend(records, "体重趋势 (kg)", BodyRecord::getWeight));
            html.append(buildSvgTrend(records, "体脂率趋势 (%)", BodyRecord::getBodyFat));
            html.append("</div>");
        }

        if (options.includeAi) {
            html.append("<div class='section'><h2>三、AI 健康建议</h2>");
            String advice = aiController.getAdvice(userId);
            html.append("<pre>").append(escapeHtml(advice)).append("</pre></div>");
        }

        html.append("</body></html>");

        try {
            Path dir = Paths.get(System.getProperty("user.home"), "bmi", "reports");
            Files.createDirectories(dir);
            String fileName = "bmi_report_" + userId + "_" + FILE.format(new java.util.Date()) + ".html";
            Path dest = dir.resolve(fileName);
            try (BufferedWriter w = Files.newBufferedWriter(dest, StandardCharsets.UTF_8)) {
                w.write(html.toString());
            }
            return dest.toAbsolutePath().toString();
        } catch (IOException e) {
            return null;
        }
    }

    // ============ HTML / SVG 辅助 ============

    private String th(String s) {
        return "<th>" + escapeHtml(s) + "</th>";
    }

    private String td(Object v) {
        return "<td>" + (v == null ? "—" : escapeHtml(String.valueOf(v))) + "</td>";
    }

    /** 用内联 SVG 折线绘制单指标趋势（无第三方依赖）。 */
    private String buildSvgTrend(List<BodyRecord> records, String title, ToDoubleFunction<BodyRecord> f) {
        final int w = 600, h = 240, pad = 40;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (BodyRecord r : records) {
            double v = f.applyAsDouble(r);
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (min == max) {
            min -= 1;
            max += 1;
        }
        int n = records.size();
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double x = pad + (w - 2 * pad) * (n == 1 ? 0.5 : (double) i / (n - 1));
            double y = h - pad - (h - 2 * pad) * (f.applyAsDouble(records.get(i)) - min) / (max - min);
            pts.append(String.format("%.1f,%.1f ", x, y));
        }
        return "<div style='margin:8px 0'><strong>" + escapeHtml(title) + "</strong><br/>"
                + "<svg width='" + w + "' height='" + h + "' viewBox='0 0 " + w + " " + h + "'>"
                + "<polyline fill='none' stroke='#1565c0' stroke-width='2' points='"
                + pts.toString().trim() + "'/></svg></div>";
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
