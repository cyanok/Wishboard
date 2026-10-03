package cn.aobp.wishboard;

import cn.aobp.wishboard.model.PublicWish;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PublicWishTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesOnlyPublicFieldsWithoutChangingStoredWish() {
        var wish = WishFixtures.wish("wish-one", "done", "wish");
        wish.getSpec().setAnonymous(true);
        var before = mapper.valueToTree(wish);

        var result = PublicWish.from(wish);
        JsonNode json = mapper.valueToTree(result);

        assertEquals(Set.of("metadata", "spec"), fieldNames(json));
        assertEquals(Set.of("name"), fieldNames(json.get("metadata")));
        assertEquals("wish-one", json.at("/metadata/name").asText());
        assertEquals(Set.of("content", "nickname", "type", "color", "status", "anonymous",
            "aiReply", "emotionTag", "doneImage", "doneNote", "priority", "createdAt",
            "completedAt"), fieldNames(json.get("spec")));
        assertEquals("匿名", json.at("/spec/nickname").asText());
        assertTrue(json.at("/spec/anonymous").asBoolean());
        assertEquals(before, mapper.valueToTree(wish));
        assertNotSame(wish.getMetadata(), result.getMetadata());
        assertNotSame(wish.getSpec(), result.getSpec());
        for (String field : fieldNames(json.get("spec"))) {
            if (!field.equals("nickname")) {
                assertEquals(before.get("spec").get(field), json.get("spec").get(field), field);
            }
        }
        assertFalse(json.toString().contains("private-account"));
        assertFalse(json.toString().contains("192.0.2.1"));
        assertFalse(json.toString().contains("Original nickname"));
        assertFalse(json.toString().contains("private-value"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void missingNicknameIsAnonymous(String nickname) {
        var wish = WishFixtures.wish("wish-one", "approved", "treehole");
        wish.getSpec().setNickname(nickname);
        assertEquals("匿名", PublicWish.from(wish).getSpec().getNickname());
        assertEquals(nickname, wish.getSpec().getNickname());
    }

    @Test
    void visibleNicknameAndMissingDatesRemainSupported() {
        var wish = WishFixtures.wish("wish-one", "pending", "wish");
        wish.getSpec().setCreatedAt(null);
        wish.getSpec().setCompletedAt(null);
        var result = PublicWish.from(wish);
        assertEquals("Original nickname", result.getSpec().getNickname());
        assertNull(result.getSpec().getCreatedAt());
        assertNull(result.getSpec().getCompletedAt());
    }

    private static Set<String> fieldNames(JsonNode node) {
        var names = new HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
