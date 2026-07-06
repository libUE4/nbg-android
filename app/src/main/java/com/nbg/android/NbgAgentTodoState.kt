package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentTodoState {
  var todos by mutableStateOf<List<HanakoTodoItem>>(emptyList())
    private set

  val hasTodos: Boolean
    get() = todos.isNotEmpty()

  fun replace(nextTodos: List<HanakoTodoItem>) {
    todos = nextTodos
  }

  fun clear() {
    todos = emptyList()
  }
}
