# Platform Android

原生 Android 客户端，使用 Kotlin、Jetpack Compose、Retrofit、OkHttp 和 DataStore，对接当前目录中的 FastAPI 后端。

## 打开项目

1. 打开 Android Studio。
2. 选择 `Open`。
3. 打开 `D:\Learn\软件工程\Platform\android-app`。
4. 等待 Gradle Sync 完成。

## 后端启动

在平台根目录运行：

```powershell
python -m uvicorn backend.main:app --reload --host 0.0.0.0 --port 8000
```

Android 模拟器访问电脑本机服务使用：

```text
http://10.0.2.2:8000
```

该地址已配置在 `app/build.gradle.kts` 的 `BuildConfig.API_BASE_URL`。

## 已实现页面

- 首页作品流、搜索、热门
- 登录、注册、演示验证码
- 作品详情、章节、点赞、收藏、评论、举报
- 发布作品
- 我的作品、我的收藏、退出登录
- 管理端统计、作品审核、举报处理、用户启停

## 默认账号

- 管理员：`admin / Admin@123456`
- 普通用户：`user / User@123456`
