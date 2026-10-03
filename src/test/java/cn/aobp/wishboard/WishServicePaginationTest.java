package cn.aobp.wishboard;

import cn.aobp.wishboard.model.Wish;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WishServicePaginationTest {
    @ParameterizedTest
    @MethodSource("pages")
    void preservesNativePageMetadataAndMapsOnlyReturnedItems(int page, int size, long total,
        int itemCount, long totalPages, boolean next, boolean previous) {
        var client = mock(ReactiveExtensionClient.class);
        var source = WishFixtures.wish("current-page-item", "doing", "wish");
        source.getSpec().setAnonymous(true);
        var items = itemCount == 0 ? List.<Wish>of() : List.of(source);
        when(client.listBy(eq(Wish.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(page, size, total, items)));
        var service = new WishService(client);

        StepVerifier.create(service.listPublic(PublicWishQuery.of(page, size, null, null, null)))
            .assertNext(result -> {
                assertEquals(page, result.getPage());
                assertEquals(size, result.getSize());
                assertEquals(total, result.getTotal());
                assertEquals(itemCount, result.getItems().size());
                assertEquals(totalPages, result.getTotalPages());
                assertEquals(next, result.hasNext());
                assertEquals(previous, result.hasPrevious());
                assertEquals(!previous, result.isFirst());
                assertEquals(!next, result.isLast());
                if (itemCount > 0) {
                    assertEquals("current-page-item", result.getItems().getFirst().getMetadata().getName());
                    assertEquals("匿名", result.getItems().getFirst().getSpec().getNickname());
                }
            }).verifyComplete();
        var request = ArgumentCaptor.forClass(PageRequest.class);
        verify(client).listBy(eq(Wish.class), any(ListOptions.class), request.capture());
        assertEquals(page, request.getValue().getPageNumber());
        assertEquals(size, request.getValue().getPageSize());
        verifyNoMoreInteractions(client);
        assertEquals("Original nickname", source.getSpec().getNickname());
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void delegatesStableDateSortToHalo(String direction) {
        var client = mock(ReactiveExtensionClient.class);
        when(client.listBy(eq(Wish.class), any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(2, 10, 0, List.of())));
        var service = new WishService(client);

        StepVerifier.create(service.listPublic(
            PublicWishQuery.of(2, 10, "wish", "doing", "createdAt," + direction)))
            .expectNextCount(1).verifyComplete();

        var request = ArgumentCaptor.forClass(PageRequest.class);
        verify(client).listBy(eq(Wish.class), any(ListOptions.class), request.capture());
        assertEquals(List.of(
            new Sort.Order(Sort.Direction.fromString(direction), "spec.createdAt"),
            Sort.Order.asc("metadata.name")), request.getValue().getSort().toList());
        verifyNoMoreInteractions(client);
    }

    private static Stream<Arguments> pages() {
        return Stream.of(
            Arguments.of(1, 1, 3L, 1, 3L, true, false),
            Arguments.of(2, 1, 3L, 1, 3L, true, true),
            Arguments.of(3, 1, 3L, 1, 3L, false, true),
            Arguments.of(4, 1, 3L, 0, 3L, false, true),
            Arguments.of(1, 20, 0L, 0, 0L, false, false)
        );
    }
}
