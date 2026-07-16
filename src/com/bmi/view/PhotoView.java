package com.bmi.view;

import com.bmi.controller.PhotoController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 体型照片管理页面（对齐 ui_design.md 第六章）。
 * 提供照片上传绑定、查看当前路径、解绑功能。
 */
public class PhotoView extends VBox implements LangChangeListener, ThemeChangeListener {

    private final PhotoController photoController;
    private final long userId;
    private final Label title;
    private final Label statusLabel;
    private final ListView<String> photoList;
    private final Button uploadBtn;
    private final Button removeBtn;

    public PhotoView(PhotoController photoController, long userId) {
        this.photoController = photoController;
        this.userId = userId;
        setPadding(new Insets(24));
        setSpacing(15);
        setStyle("-fx-background-color:" + ThemeConstant.DEFAULT_THEME.bg() + ";");

        title = StyleFactory.title("nav.photo");

        statusLabel = new Label(I18nUtil.t("photo.filename"));
        statusLabel.setStyle("-fx-font-size: 13px;");

        photoList = new ListView<>();
        photoList.setPrefHeight(300);

        uploadBtn = StyleFactory.primaryButton("photo.upload");
        uploadBtn.setOnAction(e -> onUpload());

        removeBtn = StyleFactory.secondaryButton("common.delete");
        removeBtn.setOnAction(e -> onRemove());

        HBox btnBox = new HBox(16, uploadBtn, removeBtn);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, statusLabel, photoList, btnBox);

        AppConfig.getInstance().addListener(this);
    }

    private void onUpload() {
        // Demo: 绑定示例照片路径（实际应由文件选择器选取本地图片）
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String demoPath = System.getProperty("user.home") + "/bmi/photos/demo_" + ts + ".jpg";
        boolean ok = photoController.bindPhoto(1, userId, demoPath);
        statusLabel.setText(ok ? "绑定成功：" + demoPath : "绑定失败（请先录入一条数据）");
    }

    private void onRemove() {
        boolean ok = photoController.unbindPhoto(1, userId, true);
        statusLabel.setText(ok ? I18nUtil.t("common.delete") + "成功" : I18nUtil.t("common.delete") + "失败");
    }

    @Override
    public void onLangChange() {
        title.setText(I18nUtil.t("nav.photo"));
        statusLabel.setText(I18nUtil.t("photo.filename"));
        uploadBtn.setText(I18nUtil.t("photo.upload"));
        removeBtn.setText(I18nUtil.t("common.delete"));
    }

    @Override
    public void onThemeChange() {
        setStyle("-fx-background-color:" + ThemeConstant.DEFAULT_THEME.bg() + ";");
    }

    public void dispose() {
        AppConfig.getInstance().removeListener(this);
    }
}
