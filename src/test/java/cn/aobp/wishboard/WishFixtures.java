package cn.aobp.wishboard;

import cn.aobp.wishboard.model.Wish;
import java.time.Instant;
import java.util.Map;
import run.halo.app.extension.Metadata;

final class WishFixtures {
    private WishFixtures() {}

    static Wish wish(String name, String status, String type) {
        var wish = new Wish();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setLabels(Map.of("private-label", "private-value"));
        metadata.setAnnotations(Map.of("private-note", "private-value"));
        wish.setMetadata(metadata);
        var spec = new Wish.WishSpec();
        spec.setContent("A public wish");
        spec.setNickname("Original nickname");
        spec.setAuthor("private-account");
        spec.setIp("192.0.2.1");
        spec.setType(type);
        spec.setStatus(status);
        spec.setColor("blue");
        spec.setAiReply("A kind reply");
        spec.setEmotionTag("happy");
        spec.setDoneImage("/images/done.png");
        spec.setDoneNote("Completed");
        spec.setPriority("important");
        spec.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        spec.setCompletedAt(Instant.parse("2026-01-02T00:00:00Z"));
        wish.setSpec(spec);
        return wish;
    }
}
