package cn.aobp.wishboard;

import java.util.Set;

/** 新公开分页入口共用的查询参数，不改变旧 Finder 的查询规则。 */
public record PublicWishQuery(int page, int size, String type, String status, String sort) {
    static final Set<String> PUBLIC_STATUSES = Set.of("approved", "pending", "doing", "done");

    public PublicWishQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page 必须是从 1 开始的正整数");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size 必须在 1 到 100 之间");
        }
        // Halo 2.25 使用 int 计算分页偏移及末尾位置。
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page 与 size 的乘积不能超过 2147483647");
        }
        type = optionalText(type);
        status = optionalText(status);
        if (status != null && !PUBLIC_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status 仅支持 approved、pending、doing、done");
        }
        sort = optionalText(sort);
        if (sort == null) {
            sort = "createdAt,desc";
        }
        if (!"createdAt,desc".equals(sort) && !"createdAt,asc".equals(sort)) {
            throw new IllegalArgumentException("sort 仅支持 createdAt,desc 或 createdAt,asc");
        }
    }

    public static PublicWishQuery of(Integer page, Integer size, String type, String status, String sort) {
        return new PublicWishQuery(page == null ? 1 : page, size == null ? 20 : size,
            type, status, sort);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
