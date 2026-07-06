Google Sans Flex 字体接入说明
============================

App 默认/优先字体为 Google Sans Flex（见 ui/theme/FontStore.kt）。把字体文件放到本目录即可，
两种放法二选一，加载器会自动识别（都没有则回退系统字体，不会崩）：

【方式 A：静态多字重（推荐，体积小）】
适用于 Fontsource 下载的「按字重拆分」的静态文件（每个 latin 字重约 66KB）。
按需放入以下命名（有几个放几个，Bold/Medium 才能正确显示，否则是伪粗体）：

    google_sans_flex_regular.ttf    ← 用 latin-400
    google_sans_flex_medium.ttf     ← 用 latin-500（可选）
    google_sans_flex_semibold.ttf   ← 用 latin-600（可选）
    google_sans_flex_bold.ttf       ← 用 latin-700

即从 Fontsource 的 ttf 目录取 google-sans-flex-latin-<权重>-normal.ttf，重命名成上面的名字。
建议至少放 regular + bold（约 130KB），要更全就加 medium/semibold。

【方式 B：单个可变字体】
从 Google Fonts「Get font → Download all」取那个可变 ttf（含全部字重轴，约 1.5~4MB），
重命名为：

    google_sans_flex.ttf

说明：
- 方式 A 优先；A 的文件都不存在时才用 B 的单文件。
- 未放任何文件时回退系统字体；Debug 面板「字体」区块会显示「未内置(回退系统)」。
- 放好后重新构建即生效，显示「已内置」，全局默认 Google Sans Flex。
- 中文等未覆盖字形由系统字体逐字回退。
- OFL 开源，可免费商用。
