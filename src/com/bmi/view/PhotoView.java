package com.bmi.view;

import com.bmi.controller.PhotoController;
import com.bmi.controller.RecordController;
import com.bmi.i18n.AppConfig;
import com.bmi.i18n.LangChangeListener;
import com.bmi.i18n.ThemeChangeListener;
import com.bmi.model.BodyRecord;
import com.bmi.model.User;
import com.bmi.model.db.DbException;
import com.bmi.view.util.BmiFloatingCard;
import com.bmi.view.util.I18nUtil;
import com.bmi.view.util.PageNavigator;
import com.bmi.view.util.StyleFactory;
import com.bmi.view.util.ThemeConstant;
import com.bmi.view.util.ToastBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;

/**
 * 体型照片管理页（对齐 V17 高优先级：左上传区 + 右个人资料表单）。
 *
 * <p>左侧：选择关联体检记录 → 本地图片上传（仅复制文件到用户目录并写路径，不存字节），
 * 预览（直接读取本地文件）、删除（解绑并可选删除本地文件）。右侧：个人资料表单，
 * 昵称持久化到 {@link AppConfig}。无记录时显示空态。
 */
public class PhotoView extends StackPane implements LangChangeListener, ThemeChangeListener {

    private final User user;
    private final PhotoController photoController;
    private final RecordController recordController;
    private final ToastBar toast;

    private final BorderPane root = new BorderPane();
    private final HBox topBar = new HBox(12);
    private final BmiFloatingCard bmiCard = BmiFloatingCard.create();
    private final VBox leftPanel = new VBox(12);
    private final VBox rightPanel = new VBox(12);
    private final Label titleLeft = StyleFactory.sectionTitle("photo.title");
    private final Label titleRight = StyleFactory.sectionTitle("photo.profile.title");
    private final Label recLabel = new Label();
    private final Label previewLabel = new Label();
    private final Label profileNameLabel = new Label();
    private final ComboBox<BodyRecord> recordCombo = StyleFactory.comboBox();
    private final Button uploadBtn = StyleFactory.primaryButton("photo.upload");
    private final Button deleteBtn = StyleFactory.dangerButton("photo.delete");
    private final Label pathLabel = new Label();
    private final ImageView preview = new ImageView();
    private final TextField nameField = StyleFactory.textField("photo.profile.name");
    private final Button saveBtn = StyleFactory.primaryButton("photo.profile.save");

    public PhotoView(User user, PhotoController photoController, RecordController recordController,
                     ToastBar toast) {
        this.user = user;
        this.photoController = photoController;
        this.recordController = recordController;
        this.toast = toast;

        recLabel.getStyleClass().add("bmi-field-label");
        previewLabel.getStyleClass().add("bmi-field-label");
        profileNameLabel.getStyleClass().add("bmi-field-label");

        buildTopBar();
        buildLeft();
        buildRight();

        HBox split = new HBox(16, leftPanel, rightPanel);
        split.setPadding(new Insets(16));
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.NEVER);
        root.setTop(topBar);
        root.setCenter(split);

        getChildren().addAll(root, toast);
        AppConfig.getInstance().addListener(this);
        AppConfig.getInstance().addThemeListener(this);
        refreshTexts();
        refreshRecords();
    }

    private void buildLeft() {
        recordCombo.setButtonCell(recordCell());
        recordCombo.setCellFactory(lv -> recordCell());
        recordCombo.setOnAction(e -> refreshPreview());

        uploadBtn.setMaxWidth(Double.MAX_VALUE);
        uploadBtn.setOnAction(e -> upload());
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setOnAction(e -> deletePhoto());

        preview.setFitWidth(220);
        preview.setFitHeight(220);
        preview.setPreserveRatio(true);
        preview.setStyle("-fx-background-color:-bmi-panel; -fx-border-color:-bmi-border;"
                + "-fx-border-width:1px dashed; -fx-border-radius:6;");
        VBox previewBox = new VBox(6, previewLabel, preview);
        previewBox.setAlignment(Pos.CENTER);

        pathLabel.setStyle("-fx-font-size:11px; -fx-text-fill:-bmi-muted; -fx-wrap-text:true;");

        leftPanel.getChildren().addAll(titleLeft, recLabel, recordCombo, uploadBtn, deleteBtn,
                previewBox, pathLabel);
        leftPanel.setStyle("-fx-background-color:-bmi-panel-solid; -fx-background-radius:10;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1; -fx-border-radius:10;"
                + "-fx-padding:16;");
    }

    private void buildRight() {
        nameField.setText(AppConfig.getInstance().getProfileName());
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setOnAction(e -> {
            AppConfig.getInstance().setProfileName(nameField.getText().trim());
            toast.success(I18nUtil.t("photo.profile.saved"));
        });
        rightPanel.getChildren().addAll(titleRight, profileNameLabel, nameField, saveBtn);
        rightPanel.setStyle("-fx-background-color:-bmi-panel-solid; -fx-background-radius:10;"
                + "-fx-border-color:-bmi-border; -fx-border-width:1; -fx-border-radius:10;"
                + "-fx-padding:16;");
        rightPanel.setMinWidth(300);
        rightPanel.setMaxWidth(340);
    }

    private void refreshRecords() {
        List<BodyRecord> list;
        try {
            list = recordController.queryRecords(user.getId(), null, null);
        } catch (DbException e) {
            list = List.of();
        }
        recordCombo.getItems().setAll(list);
        if (list.isEmpty()) {
            recordCombo.setDisable(true);
            uploadBtn.setDisable(true);
            deleteBtn.setDisable(true);
            pathLabel.setText(I18nUtil.t("photo.empty"));
            preview.setImage(null);
            bmiCard.clear();
        } else {
            recordCombo.setDisable(false);
            uploadBtn.setDisable(false);
            recordCombo.setValue(list.get(list.size() - 1));
            refreshPreview();
        }
    }

    private void refreshPreview() {
        BodyRecord r = recordCombo.getValue();
        if (r == null) {
            preview.setImage(null);
            pathLabel.setText("");
            deleteBtn.setDisable(true);
            bmiCard.clear();
            return;
        }
        String p = photoController.getPhotoPath(r.getId(), user.getId());
        if (p != null && !p.isEmpty()) {
            try {
                preview.setImage(new Image(new File(p).toURI().toString()));
                pathLabel.setText(I18nUtil.t("photo.path") + ": " + p);
                deleteBtn.setDisable(false);
            } catch (Exception ex) {
                preview.setImage(null);
                pathLabel.setText(I18nUtil.t("photo.path") + ": " + p);
                deleteBtn.setDisable(false);
            }
        } else {
            preview.setImage(null);
            pathLabel.setText(I18nUtil.t("photo.empty"));
            deleteBtn.setDisable(true);
        }
        refreshBmiCard();
    }

    /** 用当前选中记录的 BMI 刷新右上角浮动卡（无选中/无数据则清空）。 */
    private void refreshBmiCard() {
        BodyRecord r = recordCombo.getValue();
        if (r != null && r.getBmi() > 0) bmiCard.update(r.getBmi());
        else bmiCard.clear();
    }

    private void upload() {
        BodyRecord r = recordCombo.getValue();
        if (r == null) {
            toast.warning(I18nUtil.t("photo.empty"));
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle(I18nUtil.t("photo.upload"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (f == null) {
            return;
        }
        boolean ok = photoController.bindPhoto(r.getId(), user.getId(), f.getAbsolutePath());
        if (ok) {
            toast.success(I18nUtil.t("photo.bindOk"));
            refreshPreview();
        } else {
            toast.error(I18nUtil.t("photo.bindFail"));
        }
    }

    private void deletePhoto() {
        BodyRecord r = recordCombo.getValue();
        if (r == null) {
            return;
        }
        boolean ok = photoController.unbindPhoto(r.getId(), user.getId(), true);
        if (ok) {
            toast.success(I18nUtil.t("photo.unbindOk"));
            refreshPreview();
        } else {
            toast.error(I18nUtil.t("photo.bindFail"));
        }
    }

    private void buildTopBar() {
        Button backBtn = StyleFactory.secondaryButton("common.back");
        backBtn.setOnAction(e -> PageNavigator.toMain(user));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 16, 12, 16));
        topBar.getStyleClass().add("bmi-topbar");
        topBar.getChildren().setAll(backBtn, spacer, bmiCard.node());
    }

    /** 语言切换时刷新所有静态文案（标题 / 标签 / 按钮）。 */
    private void refreshTexts() {
        titleLeft.setText(I18nUtil.t("photo.title"));
        titleRight.setText(I18nUtil.t("photo.profile.title"));
        recLabel.setText(I18nUtil.t("photo.selectRecord"));
        previewLabel.setText(I18nUtil.t("photo.preview"));
        profileNameLabel.setText(I18nUtil.t("photo.profile.name"));
        uploadBtn.setText(I18nUtil.t("photo.upload"));
        deleteBtn.setText(I18nUtil.t("photo.delete"));
        saveBtn.setText(I18nUtil.t("photo.profile.save"));
        nameField.setPromptText(I18nUtil.t("photo.profile.name"));
    }

    private javafx.scene.control.ListCell<BodyRecord> recordCell() {
        return new javafx.scene.control.ListCell<BodyRecord>() {
            @Override
            protected void updateItem(BodyRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getMeasureTime() == null ? "#" + item.getId()
                            : item.getMeasureTime().toString());
                }
            }
        };
    }

    @Override
    public void onLangChange() {
        refreshTexts();
        bmiCard.refresh();
        refreshRecords();
    }

    @Override
    public void onThemeChange() {
        ThemeConstant.apply(this, ThemeConstant.fromCssClass(AppConfig.getInstance().getTheme()));
        refreshBmiCard(); // 主题切换后重绘 BMI 卡片分级配色
    }
}
