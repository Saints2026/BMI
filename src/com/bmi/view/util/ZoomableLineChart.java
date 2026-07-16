package com.bmi.view.util;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

/**
 * 支持鼠标滚轮缩放与拖拽平移的折线图（对齐用户需求「拖拽缩放」）。
 *
 * 滚轮：以鼠标位置为 pivot 同时缩放横纵轴；
 * 拖拽（按住左键移动）：整体平移曲线。
 * 用于独立弹窗趋势图表（ChartPopup）。
 */
public class ZoomableLineChart extends LineChart<Number, Number> {

    private double lastX;
    private double lastY;

    public ZoomableLineChart(NumberAxis xAxis, NumberAxis yAxis) {
        super(xAxis, yAxis);
        setAnimated(false);
        setCreateSymbols(true);
        setOnScroll(this::onScroll);
        setOnMousePressed(this::onPress);
        setOnMouseDragged(this::onDrag);
    }

    private void onScroll(ScrollEvent e) {
        NumberAxis x = (NumberAxis) getXAxis();
        NumberAxis y = (NumberAxis) getYAxis();
        // 手动缩放改为显式边界（关闭自动范围）
        x.setAutoRanging(false);
        y.setAutoRanging(false);
        double factor = e.getDeltaY() < 0 ? 1.12 : 0.89;
        zoom(x, pivot(e.getX(), getXAxis().getWidth()), factor);
        zoom(y, pivot(e.getY(), getYAxis().getHeight()), factor);
        e.consume();
    }

    private double pivot(double mousePos, double axisLength) {
        if (axisLength <= 0) {
            return 0.5;
        }
        return Math.min(1, Math.max(0, mousePos / axisLength));
    }

    private void zoom(NumberAxis axis, double pivot, double factor) {
        double lo = axis.getLowerBound();
        double hi = axis.getUpperBound();
        double span = hi - lo;
        double newLo = lo + span * pivot * (1 - 1 / factor);
        double newHi = hi - span * (1 - pivot) * (1 - 1 / factor);
        axis.setLowerBound(newLo);
        axis.setUpperBound(newHi);
    }

    private void onPress(MouseEvent e) {
        lastX = e.getX();
        lastY = e.getY();
    }

    /** 复位缩放：恢复横纵轴自动适配数据范围。 */
    public void resetZoom() {
        ((NumberAxis) getXAxis()).setAutoRanging(true);
        ((NumberAxis) getYAxis()).setAutoRanging(true);
    }

    private void onDrag(MouseEvent e) {
        if (!e.isPrimaryButtonDown()) {
            return;
        }
        NumberAxis x = (NumberAxis) getXAxis();
        NumberAxis y = (NumberAxis) getYAxis();
        double dx = e.getX() - lastX;
        double dy = e.getY() - lastY;
        lastX = e.getX();
        lastY = e.getY();
        double xw = getXAxis().getWidth();
        double yh = getYAxis().getHeight();
        if (xw > 0) {
            double xRange = x.getUpperBound() - x.getLowerBound();
            x.setLowerBound(x.getLowerBound() - dx / xw * xRange);
            x.setUpperBound(x.getUpperBound() - dx / xw * xRange);
        }
        if (yh > 0) {
            double yRange = y.getUpperBound() - y.getLowerBound();
            y.setLowerBound(y.getLowerBound() + dy / yh * yRange);
            y.setUpperBound(y.getUpperBound() + dy / yh * yRange);
        }
    }
}
