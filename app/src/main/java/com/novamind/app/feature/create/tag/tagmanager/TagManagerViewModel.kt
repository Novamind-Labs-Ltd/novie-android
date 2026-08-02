package com.novamind.app.feature.create.tag.tagmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.data.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 标签默认色（与 [Tag] 默认一致）。 */
private const val DEFAULT_TAG_COLOR = "#3D7A5A"

/**
 * Tag 管理页 ViewModel：结合持久化标签与笔记，统计每个标签的关联笔记数；
 * 支持新建 / 重命名 / 删除（重命名/删除会同步改写笔记里的标签）。
 */
@HiltViewModel
class TagManagerViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagManagerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        tagRepository.tags
            .onEach { tags ->
                _uiState.update {
                    it.copy(
                        tags = tags.map { tag ->
                            TagRowItem(
                                id = tag.id,
                                name = tag.name,
                                colorHex = tag.colorHex,
                            )
                        },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** 新建标签（持久化，带颜色）。 */
    fun createTag(name: String, colorHex: String = DEFAULT_TAG_COLOR) {
        if (name.isBlank()) return
        viewModelScope.launch { tagRepository.create(name.trim(), colorHex.ifBlank { DEFAULT_TAG_COLOR }) }
    }

    /** 重命名本地标签。 */
    fun renameTag(oldName: String, newName: String) {
        val from = oldName.trim()
        val to = newName.trim()
        if (from.isEmpty() || to.isEmpty() || from.equals(to, ignoreCase = true)) return
        viewModelScope.launch {
            tagRepository.rename(from, to)
        }
    }

    /** 保存拖拽后的标签顺序（持久化 sortIndex）。 */
    fun reorderTags(orderedNames: List<String>) {
        viewModelScope.launch { tagRepository.setOrder(orderedNames) }
    }

    /** 修改本地标签颜色。 */
    fun changeTagColor(name: String, colorHex: String) {
        val target = name.trim()
        if (target.isEmpty() || colorHex.isBlank()) return
        viewModelScope.launch {
            tagRepository.setColor(target, colorHex)
        }
    }

    /** 删除本地标签。 */
    fun deleteTag(name: String) {
        val target = name.trim()
        if (target.isEmpty()) return
        viewModelScope.launch {
            tagRepository.delete(target)
        }
    }
}
