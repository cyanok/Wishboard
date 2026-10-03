package cn.aobp.wishboard;

import cn.aobp.wishboard.model.PublicWish;
import cn.aobp.wishboard.model.Wish;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WishFinderTest {
    @Test
    void publicOverloadsShareServiceQueryAndResult() {
        var service = mock(WishService.class);
        var types = mock(WishTypeService.class);
        var finder = new WishFinder(service, types);
        var result = new ListResult<>(1, 20, 1,
            List.of(PublicWish.from(WishFixtures.wish("one", "approved", "treehole"))));
        when(service.listPublic(any(PublicWishQuery.class))).thenReturn(Mono.just(result));

        StepVerifier.create(finder.listPublic(null, null)).expectNext(result).verifyComplete();
        verify(service).listPublic(PublicWishQuery.of(null, null, null, null, null));
        StepVerifier.create(finder.listPublic(2, 10, "wish", "doing", "createdAt,asc"))
            .expectNext(result).verifyComplete();
        verify(service).listPublic(PublicWishQuery.of(2, 10, "wish", "doing", "createdAt,asc"));
        verifyNoInteractions(types);
    }

    @Test
    void invalidFinderParametersAreReactiveErrors() {
        var service = mock(WishService.class);
        var finder = new WishFinder(service, mock(WishTypeService.class));

        StepVerifier.create(finder.listPublic(0, 20))
            .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(finder.listPublic(1, 20, null, "pending_review", null))
            .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(finder.listPublic(Integer.MAX_VALUE, 2))
            .expectError(IllegalArgumentException.class).verify();
        verifyNoInteractions(service);
    }

    @Test
    void legacyFindersKeepTheirExistingVisibilityAndCounts() {
        var approved = WishFixtures.wish("approved", "approved", "treehole");
        var custom = WishFixtures.wish("custom", "custom-status", "wish");
        var missing = WishFixtures.wish("missing", null, "wish");
        var review = WishFixtures.wish("review", "pending_review", "treehole");
        var rejected = WishFixtures.wish("rejected", "rejected", "treehole");
        var client = mock(ReactiveExtensionClient.class);
        when(client.listAll(eq(Wish.class), any(ListOptions.class), isNull()))
            .thenReturn(Flux.just(approved, custom, missing, review, rejected));
        var finder = new WishFinder(new WishService(client), mock(WishTypeService.class));

        StepVerifier.create(finder.listApproved()).expectNext(approved, custom, missing)
            .verifyComplete();
        StepVerifier.create(finder.listByType("wish")).expectNext(custom, missing)
            .verifyComplete();
        StepVerifier.create(finder.countApproved()).expectNext(3L).verifyComplete();
        StepVerifier.create(finder.countByType("wish")).expectNext(2L).verifyComplete();
        assertTrue(finder.isAvailable());
    }
}
