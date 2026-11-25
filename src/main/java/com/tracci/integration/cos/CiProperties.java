package com.tracci.integration.cos;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.integration.cos.ci")
public class CiProperties {

    private boolean enabled = true;

    private String thumbnailRule = "imageMogr2/thumbnail/720x/quality/85";

    private String previewRule = "imageView2/2/w/1080/q/85";

    private String exportRule = "imageMogr2/thumbnail/1080x>/quality/90";

    /** 9:16 全屏故事页背景，1080×1920，智能裁剪 */
    private String storyPortraitRule = "imageMogr2/scrop/1080x1920/format/webp/quality/88";

    /** 3:4 回顾列表卡 / 启动弹层封面，1080×1440，智能裁剪 */
    private String storyCardRule = "imageMogr2/scrop/1080x1440/format/webp/quality/88";

    /** 1:1 小卡片 / 网格拼贴，720×720，智能裁剪 */
    private String squareRule = "imageMogr2/scrop/720x720/format/webp/quality/85";

    private boolean watermarkEnabled = false;

    private String watermarkRule = "";
}
