# CLAUDE.md

Guidance for Claude Code working on the **My Novie Android app** (`com.novamind.app`).

Start with **[README.md](README.md)** for architecture, module layout, and build commands.

## Skills (invoke when relevant)

- `/android-project` — feature development, MVVM/Compose conventions, network envelope, commit format
- `/android-code-review` — structured PR/change review before merge

## Architecture (must follow)

- MVVM + unidirectional data flow: `XxxRoute` (stateful) + `XxxScreen` (stateless, `@Preview`)
- Single immutable `UiState` exposed via `StateFlow`; collect with `collectAsStateWithLifecycle()`
- Network: `Response<ApiResponse<T>>` + `apiCall {}` → `ApiResult` (Success / BizError / NetworkError)
- Logging: `AppLog` only — no `Log`, `Timber`, `println`
- Colors: `ui/colors/` semantic tokens only — no hardcoded `Color(0x…)`

## Key paths

```
feature/<name>/     Route · Screen · ViewModel · UiState
common/net/         Retrofit, ApiResponse, apiCall
common/log/         AppLog, PiiMasker
ui/colors/          Design system
data/               Room repositories
```

## Docs

- `docs/编辑页光标键盘方案.md` — editor cursor/keyboard behaviour
