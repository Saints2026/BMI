package com.bmi.controller;

import com.bmi.model.BodyRecord;
import com.bmi.model.db.RecordDao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 体型照片控制器（对齐 ui_design.md 第六章「体型照片管理页面」+ db_design.md §8.3.1）。
 *
 * 职责边界（宪章分层铁律 + 一.5 本地图片规则）：
 *  - 数据库仅写入 {@code body_record.photo_path} 路径字符串，绝不读写图片二进制、不做预览；
 *  - 上传时把本地图片复制到 {@code user.home/bmi/photos/{recordId}_{ts}.ext}，再把绝对路径写回记录；
 *  - 绑定/解绑前校验记录归属（recordId 属于 userId），防越权（FR-05 / AC-05）；
 *  - 路径白名单校验：禁止 http/ftp 等远程协议前缀（db_design.md §6.4）。
 */
public class PhotoController {

    private final RecordDao recordDao;
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyyMMddHHmmss");

    public PhotoController(RecordDao recordDao) {
        this.recordDao = recordDao;
    }

    /**
     * 上传并绑定：复制本地图片到 photos 目录并重命名，路径写入记录的 photo_path。
     *
     * @param recordId     目标体检记录 ID
     * @param userId       当前登录用户（用于归属校验，防越权）
     * @param srcLocalPath 本地源图片路径（仅允许本地文件，非远程 URL）
     * @return 是否绑定成功
     */
    public boolean bindPhoto(long recordId, long userId, String srcLocalPath) {
        if (srcLocalPath == null || srcLocalPath.trim().isEmpty()) {
            return false;
        }
        if (!isLocalPathAllowed(srcLocalPath)) {
            return false; // 拒绝远程协议前缀
        }
        Path src = Paths.get(srcLocalPath);
        if (!Files.isRegularFile(src)) {
            return false;
        }

        // 归属校验：记录必须存在且属于该用户
        BodyRecord rec = recordDao.findById(recordId, userId);
        if (rec == null || rec.getUserId() != userId) {
            return false;
        }

        try {
            Path dir = Paths.get(System.getProperty("user.home"), "bmi", "photos");
            Files.createDirectories(dir);
            String fileName = recordId + "_" + TS.format(new Date()) + extension(srcLocalPath);
            Path dest = dir.resolve(fileName);
            try (InputStream in = Files.newInputStream(src)) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            // 仅写路径，不碰图片字节（db_design.md §6.4）
            rec.setPhotoPath(dest.toAbsolutePath().toString());
            recordDao.update(rec);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 解绑：photo_path 置 NULL（可选同步删除本地文件）。
     *
     * @param deleteLocal 是否同时删除本地图片文件
     */
    public boolean unbindPhoto(long recordId, long userId, boolean deleteLocal) {
        BodyRecord rec = recordDao.findById(recordId, userId);
        if (rec == null || rec.getUserId() != userId) {
            return false;
        }
        String path = rec.getPhotoPath();
        if (deleteLocal && path != null) {
            try {
                Files.deleteIfExists(Paths.get(path));
            } catch (IOException ignored) {
                // 本地文件删除失败不阻断解绑，仅置空路径
            }
        }
        rec.setPhotoPath(null);
        recordDao.update(rec);
        return true;
    }

    /**
     * 读取已绑定路径（供 UI 仅展示文本，不预览，呼应一.5）。
     */
    public String getPhotoPath(long recordId, long userId) {
        BodyRecord rec = recordDao.findById(recordId, userId);
        if (rec == null || rec.getUserId() != userId) {
            return null;
        }
        return rec.getPhotoPath();
    }

    /** 仅允许本地盘符/用户目录路径，禁止远程协议前缀。 */
    private boolean isLocalPathAllowed(String p) {
        String lower = p.toLowerCase();
        return !lower.startsWith("http://") && !lower.startsWith("https://")
                && !lower.startsWith("ftp://") && !lower.startsWith("ftp.");
    }

    /** 取扩展名（含点），缺省 .jpg。 */
    private String extension(String p) {
        int i = p.lastIndexOf('.');
        return (i >= 0 && i < p.length() - 1) ? p.substring(i).toLowerCase() : ".jpg";
    }
}
