package cn.aobp.wishboard;

import cn.aobp.wishboard.model.PublicWish;
import tools.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.app.plugin.ReactiveSettingFetcher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WishPublicRouterTest {
    private WishService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(WishService.class);
        var router = new WishPublicRouter(service, mock(AiService.class),
            mock(ReactiveSettingFetcher.class));
        client = WebTestClient.bindToRouterFunction(router.endpoint()).build();
    }

    @Test
    void defaultGetSerializesPaginationAndSafePublicItem() {
        var wish = WishFixtures.wish("one", "approved", "treehole");
        wish.getSpec().setAnonymous(true);
        when(service.listPublic(any(PublicWishQuery.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 20, 21, List.of(PublicWish.from(wish)))));

        var body = client.get().uri("/wishes").exchange().expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(body);
        assertEquals(1, body.get("page").asInt());
        assertEquals(20, body.get("size").asInt());
        assertEquals(21, body.get("total").asLong());
        assertEquals(2, body.get("totalPages").asLong());
        assertTrue(body.get("hasNext").asBoolean());
        assertFalse(body.get("hasPrevious").asBoolean());
        assertTrue(body.get("first").asBoolean());
        assertFalse(body.get("last").asBoolean());
        assertEquals("one", body.at("/items/0/metadata/name").asText());
        assertEquals("匿名", body.at("/items/0/spec/nickname").asText());
        assertTrue(body.at("/items/0/spec/ip").isMissingNode());
        assertTrue(body.at("/items/0/spec/author").isMissingNode());
        assertTrue(body.at("/items/0/metadata/annotations").isMissingNode());
        verify(service).listPublic(PublicWishQuery.of(null, null, null, null, null));
    }

    @Test
    void filtersAndPageOutsideResultsKeepRequestedPageAndTotal() {
        when(service.listPublic(any(PublicWishQuery.class)))
            .thenReturn(Mono.just(new ListResult<>(8, 10, 3, List.of())));

        var body = client.get()
            .uri("/wishes?page=8&size=10&type=wish&status=doing&sort=createdAt,asc")
            .exchange().expectStatus().isOk().expectBody(JsonNode.class)
            .returnResult().getResponseBody();

        assertNotNull(body);
        assertEquals(8, body.get("page").asInt());
        assertEquals(3, body.get("total").asLong());
        assertEquals(0, body.get("items").size());
        assertFalse(body.get("hasNext").asBoolean());
        assertTrue(body.get("hasPrevious").asBoolean());
        verify(service).listPublic(PublicWishQuery.of(8, 10, "wish", "doing", "createdAt,asc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "page=0", "page=-1", "page=abc", "page=1.5", "page=2147483648", "page=",
        "size=0", "size=-1", "size=101", "size=abc", "size=",
        "page=2147483647&size=2", "status=pending_review", "status=rejected",
        "status=unknown", "sort=createdAt", "sort=priority,desc"
    })
    void malformedQueryReturnsJsonErrorBeforeQuerying(String query) {
        var body = client.get().uri("/wishes?" + query).exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(body);
        assertTrue(body.hasNonNull("error"));
        assertFalse(body.get("error").asText().isBlank());
        verifyNoInteractions(service);
    }
}
