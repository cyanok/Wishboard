package cn.aobp.wishboard.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 公开便签，不包含投稿者 IP、关联用户名等管理信息。
 */
@Data
@Schema(name = "PublicWish")
public class PublicWish {

    private PublicMetadata metadata;
    private PublicSpec spec;

    public static PublicWish from(Wish wish) {
        var spec = wish.getSpec();
        String nickname = spec.getNickname();
        if (spec.isAnonymous() || nickname == null || nickname.isBlank()) {
            nickname = "匿名";
        }
        PublicWish result = new PublicWish();
        result.setMetadata(new PublicMetadata());
        result.getMetadata().setName(wish.getMetadata().getName());
        result.setSpec(new PublicSpec());
        result.getSpec().setContent(spec.getContent());
        result.getSpec().setNickname(nickname);
        result.getSpec().setType(spec.getType());
        result.getSpec().setColor(spec.getColor());
        result.getSpec().setStatus(spec.getStatus());
        result.getSpec().setAnonymous(spec.isAnonymous());
        result.getSpec().setAiReply(spec.getAiReply());
        result.getSpec().setEmotionTag(spec.getEmotionTag());
        result.getSpec().setDoneImage(spec.getDoneImage());
        result.getSpec().setDoneNote(spec.getDoneNote());
        result.getSpec().setPriority(spec.getPriority());
        result.getSpec().setCreatedAt(spec.getCreatedAt());
        result.getSpec().setCompletedAt(spec.getCompletedAt());
        return result;
    }

    @Data
    @Schema(name = "PublicWishMetadata")
    public static class PublicMetadata {
        private String name;
    }

    @Data
    @Schema(name = "PublicWishSpec")
    public static class PublicSpec {
        /** 便签内容 */
        private String content;
        /** 昵称（匿名或未填写时为“匿名”） */
        private String nickname;
        /** 类型 slug */
        private String type;
        /** 便签颜色 */
        private String color;
        /** 公开状态: approved/pending/doing/done */
        private String status;
        /** 是否匿名 */
        private boolean anonymous;
        /** AI 暖心回复 */
        private String aiReply;
        /** AI 情绪标签 emoji */
        private String emotionTag;
        /** 心愿达成后的纪念照片 */
        private String doneImage;
        /** 心愿达成感言 */
        private String doneNote;
        /** 优先级: normal/important/urgent */
        private String priority;
        /** 创建时间 */
        private Instant createdAt;
        /** 完成时间 */
        private Instant completedAt;
    }
}
