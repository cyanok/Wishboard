package cn.aobp.wishboard;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.theme.finders.Finder;
import run.halo.app.extension.ListResult;
import cn.aobp.wishboard.model.PublicWish;
import cn.aobp.wishboard.model.Wish;
import cn.aobp.wishboard.model.WishType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 便签 Finder，注入到 Thymeleaf 模板上下文中。
 * 主题模板可通过 {@code wishFinder.listApproved()} 等方法获取便签数据，
 * 支持 {@code th:each} 服务端渲染。
 */
@Finder("wishFinder")
@RequiredArgsConstructor
public class WishFinder {

    private final WishService wishService;
    private final WishTypeService wishTypeService;
    /** 保留给现有主题模板的可用性检查。 */
    public boolean isAvailable() {
        return true;
    }

    public Mono<ListResult<PublicWish>> listPublic(Integer page, Integer size) {
        return listPublic(page, size, null, null, null);
    }

    public Mono<ListResult<PublicWish>> listPublic(Integer page, Integer size,
                                                  String type, String status, String sort) {
        return Mono.defer(() -> wishService.listPublic(PublicWishQuery.of(page, size, type, status, sort)));
    }

    /**
     * 获取所有已审核通过的便签
     */
    public Flux<Wish> listApproved() {
        return wishService.listApproved();
    }

    /**
     * 按类型获取已审核通过的便签
     */
    public Flux<Wish> listByType(String type) {
        return wishService.listApproved()
            .filter(w -> type.equals(w.getSpec().getType()));
    }

    /**
     * 获取所有便签类型
     */
    public Flux<WishType> listTypes() {
        return wishTypeService.listAll();
    }

    /**
     * 获取已审核通过的便签总数
     */
    public Mono<Long> countApproved() {
        return wishService.listApproved().count();
    }

    /**
     * 按类型统计已审核通过的便签数
     */
    public Mono<Long> countByType(String type) {
        return wishService.listApproved()
            .filter(w -> type.equals(w.getSpec().getType()))
            .count();
    }

    /**
     * 获取类型 slug → displayName 映射
     */
    public Mono<Map<String, String>> getTypeMap() {
        return wishTypeService.listAll()
            .collectList()
            .map(types -> types.stream()
                .collect(Collectors.toMap(
                    t -> t.getSpec().getSlug(),
                    t -> t.getSpec().getDisplayName(),
                    (a, b) -> a
                ))
            );
    }
}
