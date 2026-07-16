package com.bmi.view.util;

import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * 响应式栅格助手（对齐用户需求「自适应布局」）。
 *
 * <p>布局规则（严格遵循 ui_design.md 通用规范 + 用户需求）：
 * <ul>
 *   <li>窗口宽度 &gt; 1200px → 三列</li>
 *   <li>800 ~ 1200px     → 两列</li>
 *   <li>&lt; 800px         → 单列堆叠</li>
 * </ul>
 * 列宽使用 {@link ColumnConstraints#setPercentWidth(double)} 百分比约束，均分填满。
 *
 * <p>监听 <b>窗口宽与高</b> 属性（Scene.width / Scene.height），任意一边变化即自动重排。
 * 所有栅格单元强制设置最小宽高（{@link #CELL_MIN_W}/{@link #CELL_MIN_H}），
 * 窗口缩放时控件只放大、不被压缩变形；控件自身高度由 styles.css 锁定（输入框 32px / 按钮 34px）。
 */
public final class Responsive {

    /** 响应式断点。 */
    private static final double THREE_COL = 1200;
    private static final double TWO_COL = 800;

    /** 栅格列间距 / 行间距。 */
    private static final double GAP = 14;

    /** 栅格单元最小宽高：保证缩放不压缩变形（控件内实际高度由 CSS 锁定）。 */
    private static final double CELL_MIN_W = 200;
    private static final double CELL_MIN_H = 46;

    private Responsive() {
    }

    /**
     * 绑定响应式栅格：cells 中的控件将按当前窗口宽高自动换行排布。
     * 需在 grid 已加入场景后调用（或自动等待加入场景再绑定）。
     */
    public static void bind(GridPane grid, List<Node> cells) {
        grid.setHgap(GAP);
        grid.setVgap(GAP);
        grid.setMaxWidth(Double.MAX_VALUE);

        final boolean[] bound = {false};
        Runnable relayout = () -> relayout(grid, cells,
                columnsFor(grid.getScene() != null ? grid.getScene().getWidth() : 1000));

        grid.sceneProperty().addListener((o, ov, nv) -> {
            if (nv != null && !bound[0]) {
                bound[0] = true;
                // 监听窗口「宽」与「高」：任一变化即重排
                nv.widthProperty().addListener((o2, w1, w2) -> relayout.run());
                nv.heightProperty().addListener((o2, h1, h2) -> relayout.run());
                relayout.run();
            }
        });
        if (grid.getScene() != null) {
            bound[0] = true;
            grid.getScene().widthProperty().addListener((o2, w1, w2) -> relayout.run());
            grid.getScene().heightProperty().addListener((o2, h1, h2) -> relayout.run());
            relayout.run();
        }
    }

    /** 按窗口宽度决定列数：3 / 2 / 1。 */
    private static int columnsFor(double width) {
        if (width >= THREE_COL) {
            return 3;
        }
        if (width >= TWO_COL) {
            return 2;
        }
        return 1;
    }

    private static void relayout(GridPane grid, List<Node> cells, int cols) {
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();

        // 列宽百分比约束：每列均分 100/cols，随窗口缩放自动拉伸
        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.SOMETIMES);
            grid.getColumnConstraints().add(cc);
        }

        int i = 0;
        for (Node n : cells) {
            // 严格设置最小宽高，缩放不压缩变形
            if (n instanceof Region) {
                Region r = (Region) n;
                r.setMinWidth(CELL_MIN_W);
                r.setMinHeight(CELL_MIN_H);
                r.setMaxWidth(Double.MAX_VALUE);
            }
            GridPane.setHgrow(n, Priority.ALWAYS);
            GridPane.setFillWidth(n, true);
            grid.add(n, i % cols, i / cols);
            i++;
        }
    }
}
