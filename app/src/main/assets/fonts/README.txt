Google Sans Flex 字体接入说明
============================

App 默认/优先字体为 Google Sans Flex（见 ui/theme/FontStore.kt）。
请把字体文件放到本目录，文件名必须精确为：

    google_sans_flex.ttf

即最终路径：app/src/main/assets/fonts/google_sans_flex.ttf

获取方式（OFL 开源，可免费商用）：
1. 打开 https://fonts.google.com/specimen/Google+Sans+Flex
2. 点 "Get font" → "Download all"，解压 ZIP
3. 取其中的可变字体 .ttf（如 GoogleSansFlex[...].ttf），重命名为 google_sans_flex.ttf 放到本目录

说明：
- 未放置该文件时，App 会自动回退到系统字体（不会崩溃），Debug 面板「字体」区块会显示「未内置(回退系统)」。
- 放置后重新构建即可生效，Debug 面板显示「已内置」，全局默认使用 Google Sans Flex。
- 中文等 Google Sans Flex 未覆盖的字形会由系统字体逐字回退。
- 该文件通常 1~4MB（可变字体），会计入 APK 体积。
