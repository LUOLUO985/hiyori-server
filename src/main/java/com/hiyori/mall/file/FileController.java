package com.hiyori.mall.file;

import com.hiyori.mall.common.BizException;
import com.hiyori.mall.common.ErrorCode;
import com.hiyori.mall.common.Result;
import com.hiyori.mall.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品图片上传（管理员）。
 * 文件名由服务端生成，不使用用户传的文件名，避免路径穿越；类型按文件头魔数判断，
 * 不信任客户端声明的 Content-Type。以后量大了可以换成对象存储（COS / OSS / S3），接口不变。
 */
@RestController
public class FileController {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;
    private final String publicBaseUrl;

    public FileController(AppProperties props) {
        this.root = Path.of(props.upload().dir()).toAbsolutePath().normalize();
        String base = props.upload().publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @PostMapping("/api/admin/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件为空");
        }
        String ext;
        try (InputStream in = file.getInputStream()) {
            ext = detectImageExt(in.readNBytes(12));
        }
        if (ext == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只支持 JPG / PNG / WEBP 图片");
        }

        String month = YearMonth.now().format(MONTH);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = root.resolve(month);
        Files.createDirectories(dir);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        return Result.ok(Map.of("url", publicBaseUrl + "/" + month + "/" + name));
    }

    static String detectImageExt(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "png";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
