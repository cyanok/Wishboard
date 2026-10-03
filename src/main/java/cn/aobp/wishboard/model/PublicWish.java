package cn.aobp.wishboard.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Value;

/** 新公开分页接口的展示模型，显式选择字段以隔离管理数据。 */
@Value
@Schema(name = "PublicWish")
public class PublicWish {
    PublicMetadata metadata;
    PublicSpec spec;

    public static PublicWish from(Wish wish) {
        var spec = wish.getSpec();
        var nickname = spec.getNickname();
        if (spec.isAnonymous() || nickname == null || nickname.isBlank()) {
            nickname = "匿名";
        }
        return new PublicWish(new PublicMetadata(wish.getMetadata().getName()),
            new PublicSpec(spec.getContent(), nickname, spec.getType(), spec.getColor(),
                spec.getStatus(), spec.isAnonymous(), spec.getAiReply(), spec.getEmotionTag(),
                spec.getDoneImage(), spec.getDoneNote(), spec.getPriority(), spec.getCreatedAt(),
                spec.getCompletedAt()));
    }

    @Value
    @Schema(name = "PublicWishMetadata")
    public static class PublicMetadata {
        String name;
    }

    @Value
    @Schema(name = "PublicWishSpec")
    public static class PublicSpec {
        String content;
        String nickname;
        String type;
        String color;
        String status;
        boolean anonymous;
        String aiReply;
        String emotionTag;
        String doneImage;
        String doneNote;
        String priority;
        Instant createdAt;
        Instant completedAt;
    }
}
