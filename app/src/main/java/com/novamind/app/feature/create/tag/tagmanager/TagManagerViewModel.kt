package com.novamind.app.feature.create.tag.tagmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novamind.app.data.NoteRepository
import com.novamind.app.data.TagRepository
import com.novamind.app.feature.create.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagManagerUiState())
    val uiState = _uiState.asStateFlow()

    private var domainNotes: List<Note> = emptyList()

    init {
        combine(tagRepository.tags, noteRepository.notes) { tags, notes ->
            tags to notes
        }
            .onEach { (tags, notes) ->
                domainNotes = notes
                // 统计每个标签名关联的笔记数
                val counts = HashMap<String, Int>()
                notes.forEach { note ->
                    note.tags.forEach { t -> counts[t.name] = (counts[t.name] ?: 0) + 1 }
                }
                _uiState.update {
                    it.copy(
                        tags = tags.map { tag ->
                            TagRowItem(
                                id = tag.id,
                                name = tag.name,
                                colorHex = tag.colorHex,
                                noteCount = counts[tag.name] ?: 0,
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

    /** 重命名标签：改写标签库与所有笔记中的同名标签。 */
    fun renameTag(oldName: String, newName: String) {
        val from = oldName.trim()
        val to = newName.trim()
        if (from.isEmpty() || to.isEmpty() || from.equals(to, ignoreCase = true)) return
        viewModelScope.launch {
            tagRepository.rename(from, to)
            domainNotes.filter { note -> note.tags.any { it.name == from } }.forEach { note ->
                val newTags = note.tags.map { if (it.name == from) it.copy(name = to) else it }
                noteRepository.addOrUpdate(note.copy(tags = newTags))
            }
        }
    }

    /** 保存拖拽后的标签顺序（持久化 sortIndex）。 */
    fun reorderTags(orderedNames: List<String>) {
        viewModelScope.launch { tagRepository.setOrder(orderedNames) }
    }

    /** 修改标签颜色：更新标签库与所有笔记中同名标签的颜色。 */
    fun changeTagColor(name: String, colorHex: String) {
        val target = name.trim()
        if (target.isEmpty() || colorHex.isBlank()) return
        viewModelScope.launch {
            tagRepository.setColor(target, colorHex)
            domainNotes.filter { note -> note.tags.any { it.name == target } }.forEach { note ->
                val newTags = note.tags.map { if (it.name == target) it.copy(colorHex = colorHex) else it }
                noteRepository.addOrUpdate(note.copy(tags = newTags))
            }
        }
    }

    /** 删除标签：从标签库与所有笔记中移除该标签。 */
    fun deleteTag(name: String) {
        val target = name.trim()
        if (target.isEmpty()) return
        viewModelScope.launch {
            tagRepository.delete(target)
            domainNotes.filter { note -> note.tags.any { it.name == target } }.forEach { note ->
                noteRepository.addOrUpdate(note.copy(tags = note.tags.filterNot { it.name == target }))
            }
        }
    }
}
