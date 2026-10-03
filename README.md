# 心愿便签（Wishboard）

适用于 Halo 2.25+ 的心愿墙与树洞插件，为站点提供便签展示、访客投稿、内容审核和主题集成能力。

## 功能概览

- **便签墙页面**：提供独立的心愿墙与树洞页面，便签以卡片形式展示，桌面端支持拖动，新便签发布后无需刷新即可显示
- **多种便签类型**：内置心愿和树洞两种类型，并支持在后台创建自定义类型
- **访客投稿**：访客可以匿名或署名发布便签，并选择便签颜色和类型
- **投稿开关**：可随时关闭前台投稿，保留已有便签的公开展示
- **内容审核**：支持免审核、人工审核和 AI 自动审核
- **AI 能力**：支持暖心回复、情绪标签、内容审核和投稿文案润色；AI 模型与服务商由 Halo AI Foundation 统一管理，Wishboard 使用其中配置的默认语言模型
- **心愿追踪**：支持待处理、进行中和已达成等状态，达成后可添加纪念照片与感言
- **纪念日计数**：可在便签墙显示纪念日天数和双方名称
- **访问保护**：支持访问频率限制和敏感词过滤
- **数据管理**：支持便签与类型的导入、导出、批量管理和审核
- **主题适配**：提供独立页面、主题模板覆盖和 Finder 数据访问能力
- **公开分页**：提供匿名只读 API 和分页 Finder，支持按类型、公开状态筛选和创建时间排序

## 基本使用

安装并启用插件后，在插件设置中配置页面标题、投稿方式、审核模式和便签类型。

需要直接使用插件页面时，可启用内置页面并访问 `/wishes`。也可以在 Halo 后台创建便签墙页面，由当前主题提供 `wishes.html` 模板。

## 主题集成

### 使用主题模板

在主题的 `templates/` 目录中创建 `wishes.html`，插件会优先使用主题模板渲染便签墙。主题可以自行控制页面布局和样式，便签数据通过 `wishFinder` 获取。

### 显示便签

```html
<th:block th:if="${wishFinder != null}"
          th:with="wishes=${wishFinder.listApproved()}, typeMap=${wishFinder.getTypeMap()}">
  <article th:each="wish : ${wishes}">
    <span th:text="${typeMap[wish.spec.type]}">类型</span>
    <p th:text="${wish.spec.content}">便签内容</p>
    <span th:text="${wish.spec.anonymous ? '匿名' : wish.spec.nickname}">昵称</span>
  </article>
</th:block>
```

### 按类型显示

```html
<th:block th:if="${wishFinder != null}"
          th:with="wishes=${wishFinder.listByType('wish')}">
  <article th:each="wish : ${wishes}">
    <p th:text="${wish.spec.content}">心愿内容</p>
    <span th:text="${wish.spec.status}">状态</span>
  </article>
</th:block>
```

可将 `wish` 改为 `treehole` 或后台创建的自定义类型标识。主题还可以使用：

- `wishFinder.listTypes()`：获取便签类型
- `wishFinder.countApproved()`：获取公开便签总数
- `wishFinder.countByType('wish')`：获取指定类型数量
- `wishFinder.getTypeMap()`：获取类型名称映射

自定义 `wishes.html` 时，可直接使用插件注入的页面标题、副标题、纪念日设置、投稿开关和 AI 开关等模板变量。

### 分页显示

使用 `wishFinder.listPublic(page, size)` 分页获取公开便签，或使用 `wishFinder.listPublic(page, size, type, status, sort)` 按类型、状态筛选和排序。`page`、`size` 传 `null` 时，分别使用默认值 `1`、`20`。

以下示例显示第一页、每页 20 条的进行中心愿：

```html
<th:block th:if="${wishFinder != null}">
  <th:block th:with="result=${wishFinder.listPublic(1, 20, 'wish', 'doing', 'createdAt,desc')}">
    <article th:each="wish : ${result.items}">
      <p th:text="${wish.spec.content}">心愿内容</p>
      <span th:text="${wish.spec.nickname}">昵称</span>
    </article>
    <p th:if="${#lists.isEmpty(result.items)}">暂无便签</p>
    <p th:text="|共 ${result.total} 条，第 ${result.page} 页|">分页信息</p>
  </th:block>
</th:block>
```

不筛选时可改用 `wishFinder.listPublic(1, 20)`，或将完整调用中的 `type`、`status` 传为 `null`。分页参数、公开规则和排序规则与下方 HTTP API 一致。

新分页仅包含 `approved`、`pending`、`doing`、`done` 四种状态且未删除的便签。其中 `pending` 表示心愿待处理，不是等待审核。待审核、已拒绝、未知状态和缺失状态的便签不会进入结果或总数。公开昵称已经过处理，可直接显示 `wish.spec.nickname`。

原有 `listApproved()`、`listByType()`、`countApproved()`、`countByType()` 和内置 `/wishes` 页面的行为保持不变，内置页面不会自动增加翻页界面。旧计数方法仍沿用旧公开规则，不能作为新分页的总数；分页展示应使用本次查询返回的 `result.total`。

## 公开分页 API

无需登录即可请求：

```http
GET /apis/anonymous.wishboard.aobp.cn/v1alpha1/wishes?page=1&size=20&type=wish&status=doing&sort=createdAt,desc
```

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `page` | `1` | 从 1 开始的正整数 |
| `size` | `20` | 每页条数，允许 `1–100` |
| `type` | 不筛选 | 类型 slug，精确匹配，例如 `wish`、`treehole` 或自定义类型标识 |
| `status` | 不筛选 | 仅支持 `approved`、`pending`、`doing`、`done` |
| `sort` | `createdAt,desc` | 创建时间降序；另支持 `createdAt,asc` 升序 |

省略或传空白 `type`、`status` 表示不筛选，省略或传空白 `sort` 使用默认排序。相同创建时间的便签按 `metadata.name` 升序排列；缺失创建时间的便签在升序时排在前面，降序时排在后面。类型和状态条件共同生效，筛选在分页之前完成。

成功返回 `200`，JSON 使用 Halo 的 `ListResult<PublicWish>` 结构：

```json
{
  "page": 1,
  "size": 20,
  "total": 0,
  "items": [],
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false,
  "first": true,
  "last": true
}
```

`total` 是同时满足公开规则、类型和状态条件的总数。类型不存在、没有匹配项或请求超出最后一页时，仍返回 `200` 和空 `items`，保留请求页码及实际总数。翻页时使用 `hasNext`、`hasPrevious` 判断是否有相邻页。

`items` 中的每条便签仅包含 `metadata.name` 和 `spec`。`spec` 字段为 `content`、`nickname`、`type`、`color`、`status`、`anonymous`、`aiReply`、`emotionTag`、`doneImage`、`doneNote`、`priority`、`createdAt`、`completedAt`。匿名投稿或昵称为空白时，`nickname` 统一为“匿名”；不返回 IP、关联用户名和其他元数据。

非法页码、每页条数、非公开状态或不支持的排序返回 `400`，错误体为 `{ "error": "说明" }`。为避免分页偏移溢出，`page * size` 不得超过 `2147483647`，Finder 遵循相同限制。采用普通页码分页，数据增删或筛选字段变化时，相邻请求的页面内容可能移动，不保证跨请求快照一致性。

## 交流

欢迎加入交流群了解更新、反馈问题和交流主题集成。

![交流群](./docs/community-group.png)

## 项目信息

- 作者：Serenity
- 主页：https://www.aobp.cn
- 仓库：https://github.com/atangccc/Wishboard
- 许可证：GPL-3.0

## 二次开发与借鉴说明

如果你基于本项目进行二次开发、功能移植或实现思路借鉴，请主动告知开发者。这有助于了解项目的实际使用情况、协调兼容性，并减少重复开发。

上述告知属于社区协作倡议，不限制 GPL-3.0 已授予的复制、修改和再分发权利。发布或分发衍生作品时，仍需遵守 GPL-3.0 的源代码提供、许可证保留和版权声明要求。

如需联系开发者，请访问 https://www.aobp.cn 或 https://github.com/atangccc。
