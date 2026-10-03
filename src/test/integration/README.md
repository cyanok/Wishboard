# 公开分页集成验证

`public-pagination.mjs` 在真实 Halo 中验证公开分页 API、Finder 模板、匿名权限和历史数据升级。它不随 Gradle 单元测试自动执行，需要 Node.js 22+、JDK 21 和 Docker。

**仅对全新、可销毁的本地 Halo 实例执行。** 脚本会初始化管理员、安装及升级插件、创建测试便签、安装并启用测试主题。不要传入现有站点地址或挂载站点数据。管理员密码随机生成，仅保存在进程内存中。

## 准备测试产物

在仓库根目录使用 PowerShell 执行。先将 `JAVA_HOME` 指向本机 JDK 21；以下构建不重建已跟踪的 Console 产物。

```powershell
.\gradlew.bat test build -x buildFrontend --console=plain --no-daemon
New-Item -ItemType Directory -Force build/runtime/baseline | Out-Null
git archive --format=tar --output=build/runtime/baseline.tar 81f256031d759200a8644b632608c3ad4130d459
tar -xf build/runtime/baseline.tar -C build/runtime/baseline
.\gradlew.bat -p build/runtime/baseline build -x buildFrontend '-PpluginVersion=1.6.5' --console=plain --no-daemon
& "$env:JAVA_HOME/bin/jar.exe" --create --file build/runtime/pagination-theme.zip --no-manifest -C src/test/integration/theme .
$currentVersion = (Get-Content gradle.properties | Where-Object { $_ -match '^pluginVersion=' }) -replace '^pluginVersion=', ''
$currentJar = "build/libs/plugin-wishboard-$currentVersion.jar"
```

基线提交是新增分页前的代码，测试构建使用 `1.6.5` 作为升级前版本标识，不代表下载或验证了该历史发布包。当前待测 JAR 必须包含本次分页实现。

## 运行双版本验证

端口 `18090`、`18091` 必须空闲。容器只绑定本机，不挂载数据目录；测试完成后停止并自动删除。脚本会等待初始化页面、升级后的分页路由和测试主题生效，但不会重试失败的分页断言。

```powershell
foreach ($case in @(@{ Version = '2.25.0'; Port = 18090 }, @{ Version = '2.26.0'; Port = 18091 })) {
    $name = "wishboard-pagination-$($case.Version.Replace('.', '-'))"
    $base = "http://127.0.0.1:$($case.Port)"
    docker run --detach --rm --name $name --publish "127.0.0.1:$($case.Port):8090" --memory 1g "registry.fit2cloud.com/halo/halo:$($case.Version)" --halo.security.basic-auth.disabled=false "--halo.external-url=$base"
    if ($LASTEXITCODE -ne 0) { throw "无法创建临时容器 $name" }
    try {
        node src/test/integration/public-pagination.mjs $base build/runtime/baseline/build/libs/plugin-wishboard-1.6.5.jar $currentJar build/runtime/pagination-theme.zip "build/runtime/halo-$($case.Version)-report.json"
        if ($LASTEXITCODE -ne 0) { throw "Halo $($case.Version) 集成验证失败" }
    } finally {
        docker stop $name
    }
}
```

## 覆盖范围

- 先用旧插件创建 58 条记录，再升级并验证新索引可查询历史数据，无数据迁移。
- 首页、中间页、末页、越界页、无匹配类型、类型与状态组合，以及分页总数和翻页标记。
- 四种公开状态白名单；待审核、拒绝、未知、缺失状态和带删除时间但仍保留在存储中的记录均被排除。
- 创建时间正反序、相同时间按名称排序、缺失时间、最大合法偏移和非法参数。
- JSON 字段隔离、匿名昵称脱敏和原始管理数据不变；匿名访问原始资源及管理接口仍被拒绝。
- 真实 Thymeleaf 调用两个分页 Finder 重载，核对 API 与模板结果及旧 Finder 计数规则。
- 审核状态改变后，索引查询结果和总数随之变化。

每个版本通过后生成 JSON 报告。测试不验证 Console UI 或性能容量，也不修改内置页面；临时 H2 实例的结果不等同于生产数据库、第三方插件组合或跨请求快照一致性验证。
