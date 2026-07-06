package com.nbg.android

import androidx.compose.runtime.mutableStateListOf

internal class NbgAgentMessageState {
  val messages = mutableStateListOf<NbgAgentMessage>()
  private val messageIndexById = mutableMapOf<Long, Int>()
  private val toolMessageIndexByKey = mutableMapOf<String, Int>()
  private var localMessageId = -1L

  fun toolMessageKey(message: NbgAgentMessage): String? =
    message.toolStatus
      ?.key
      ?.takeIf { message.role == NbgAgentRole.Tool && it.isNotBlank() }

  fun rebuildMessageIndex() {
    messageIndexById.clear()
    toolMessageIndexByKey.clear()
    messages.forEachIndexed { index, message ->
      messageIndexById.putIfAbsent(message.id, index)
      toolMessageKey(message)?.let { key ->
        toolMessageIndexByKey.putIfAbsent(key, index)
      }
    }
  }

  fun clearMessages() {
    messages.clear()
    messageIndexById.clear()
    toolMessageIndexByKey.clear()
  }

  fun appendMessage(message: NbgAgentMessage): Long {
    messages += message
    messageIndexById.putIfAbsent(message.id, messages.lastIndex)
    toolMessageKey(message)?.let { key ->
      toolMessageIndexByKey.putIfAbsent(key, messages.lastIndex)
    }
    return message.id
  }

  fun replaceMessages(nextMessages: List<NbgAgentMessage>) {
    messages.clear()
    messages.addAll(nextMessages)
    rebuildMessageIndex()
  }

  fun findMessageIndexById(messageId: Long): Int {
    val cached = messageIndexById[messageId]
    if (cached != null && cached in messages.indices && messages[cached].id == messageId) {
      return cached
    }
    val found = messages.indexOfFirst { it.id == messageId }
    if (found >= 0) {
      messageIndexById[messageId] = found
    } else {
      messageIndexById.remove(messageId)
    }
    return found
  }

  fun messageExistsById(messageId: Long): Boolean =
    findMessageIndexById(messageId) >= 0

  fun setMessageAt(index: Int, message: NbgAgentMessage) {
    val oldId = messages[index].id
    val oldToolKey = toolMessageKey(messages[index])
    messages[index] = message
    if (oldId != message.id) {
      rebuildMessageIndex()
    } else {
      messageIndexById.putIfAbsent(message.id, index)
      val nextToolKey = toolMessageKey(message)
      if (oldToolKey != nextToolKey) {
        oldToolKey?.let { key ->
          if (toolMessageIndexByKey[key] == index) toolMessageIndexByKey.remove(key)
        }
      }
      nextToolKey?.let { key ->
        toolMessageIndexByKey[key] = index
      }
    }
  }

  fun removeMessageAt(index: Int) {
    messages.removeAt(index)
    rebuildMessageIndex()
  }

  fun removeMessagesMatching(predicate: (NbgAgentMessage) -> Boolean) {
    messages.removeAll(predicate)
    rebuildMessageIndex()
  }

  fun replaceAllMessages(transform: (NbgAgentMessage) -> NbgAgentMessage) {
    messages.replaceAll(transform)
    rebuildMessageIndex()
  }

  fun findToolMessageIndexByKey(key: String): Int {
    val cached = toolMessageIndexByKey[key]
    if (
      cached != null &&
      cached in messages.indices &&
      messages[cached].role == NbgAgentRole.Tool &&
      messages[cached].toolStatus?.key == key
    ) {
      return cached
    }
    val found = messages.indexOfFirst { message ->
      message.role == NbgAgentRole.Tool && message.toolStatus?.key == key
    }
    if (found >= 0) {
      toolMessageIndexByKey[key] = found
    } else {
      toolMessageIndexByKey.remove(key)
    }
    return found
  }

  fun resetLocalMessageIds() {
    localMessageId = -1L
  }

  fun reseedLocalMessageIds(existingMessages: List<NbgAgentMessage> = messages) {
    localMessageId = (existingMessages.map { it.id }.filter { it < 0L }.minOrNull() ?: 0L) - 1L
  }

  fun nextLocalMessageId(): Long = localMessageId--
}
