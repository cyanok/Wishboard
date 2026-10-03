package cn.aobp.wishboard;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PublicWishQueryTest {
    @Test
    void defaultsAndBlankFilters() {
        var query = PublicWishQuery.of(null, null, " \t", " ", " ");
        assertEquals(1, query.page());
        assertEquals(20, query.size());
        assertNull(query.type());
        assertNull(query.status());
        assertEquals("createdAt,desc", query.sort());
    }

    @Test
    void preservesExactTypeAndAcceptsMaximumOffset() {
        var query = PublicWishQuery.of(Integer.MAX_VALUE, 1, "custom-type", "doing",
            "createdAt,asc");
        assertEquals(Integer.MAX_VALUE, query.page());
        assertEquals("custom-type", query.type());
        assertEquals("doing", query.status());
        assertEquals("createdAt,asc", query.sort());
        assertEquals(100, PublicWishQuery.of(1, 100, null, null, null).size());
        assertEquals(21_474_836,
            PublicWishQuery.of(21_474_836, 100, null, null, null).page());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "pending", "doing", "done"})
    void acceptsOnlyPublicStatuses(String status) {
        assertEquals(status, PublicWishQuery.of(1, 20, null, status, null).status());
    }

    @ParameterizedTest
    @MethodSource("invalidParameters")
    void rejectsInvalidParameters(Integer page, Integer size, String status, String sort) {
        assertThrows(IllegalArgumentException.class,
            () -> PublicWishQuery.of(page, size, null, status, sort));
    }

    private static Stream<Arguments> invalidParameters() {
        return Stream.of(
            Arguments.of(0, 20, null, null),
            Arguments.of(-1, 20, null, null),
            Arguments.of(1, 0, null, null),
            Arguments.of(1, -1, null, null),
            Arguments.of(1, 101, null, null),
            Arguments.of(21_474_837, 100, null, null),
            Arguments.of(Integer.MAX_VALUE, 2, null, null),
            Arguments.of(1, 20, "pending_review", null),
            Arguments.of(1, 20, "rejected", null),
            Arguments.of(1, 20, "unknown", null),
            Arguments.of(1, 20, null, "createdAt"),
            Arguments.of(1, 20, null, "createdAt,DESC"),
            Arguments.of(1, 20, null, "metadata.name,asc"),
            Arguments.of(1, 20, null, "createdAt,desc,metadata.name,asc")
        );
    }
}
