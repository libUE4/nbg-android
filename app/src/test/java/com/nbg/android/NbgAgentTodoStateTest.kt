package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentTodoStateTest {
  @Test
  fun initialStateIsEmpty() {
    val state = NbgAgentTodoState()

    assertFalse(state.hasTodos)
    assertTrue(state.todos.isEmpty())
  }

  @Test
  fun replaceStoresLatestTodos() {
    val state = NbgAgentTodoState()
    val todos = listOf(todo("Inspect"), todo("Build", status = "in_progress"))

    state.replace(todos)

    assertTrue(state.hasTodos)
    assertSame(todos, state.todos)
    assertEquals("Build", state.todos[1].content)
  }

  @Test
  fun replaceWithEmptyListClearsTodos() {
    val state = NbgAgentTodoState()
    state.replace(listOf(todo("Inspect")))

    state.replace(emptyList())

    assertFalse(state.hasTodos)
    assertTrue(state.todos.isEmpty())
  }

  @Test
  fun clearRemovesTodos() {
    val state = NbgAgentTodoState()
    state.replace(listOf(todo("Inspect")))

    state.clear()

    assertFalse(state.hasTodos)
    assertTrue(state.todos.isEmpty())
  }

  private fun todo(content: String, status: String = "pending"): HanakoTodoItem =
    HanakoTodoItem(
      content = content,
      activeForm = "正在$content",
      status = status,
    )
}
