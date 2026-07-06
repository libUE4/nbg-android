package com.nbg.android.terminal

import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

typealias TermuxTerminalSession = com.termux.terminal.TerminalSession

class TerminalSession internal constructor(
  val termuxSession: TermuxTerminalSession,
  private val sessionClient: NbgTerminalSessionClient,
) {
  private val _screenText = MutableStateFlow("")
  val screenText: StateFlow<String> = _screenText.asStateFlow()

  init {
    sessionClient.owner = this
  }

  fun setScreenChangedListener(listener: (() -> Unit)?) {
    sessionClient.onScreenChanged = listener
  }

  internal fun onTermuxTextChanged() {
    val transcript = termuxSession.getEmulator()?.getScreen()?.getTranscriptText().orEmpty()
    _screenText.value = transcript.takeLast(MAX_BUFFER_CHARS)
  }

  fun send(text: String) {
    termuxSession.write(text)
  }

  fun sendPaste(text: String) {
    termuxSession.getEmulator()?.paste(text) ?: send(TerminalInputSequence.paste(text, termuxModes()))
  }

  fun sendEnter() {
    send(TerminalInputSequence.enter)
  }

  fun sendCtrlC() {
    sendCtrl('C')
  }

  fun sendCtrl(letter: Char) {
    send(TerminalInputSequence.ctrl(letter))
  }

  fun sendEscape() {
    send(TerminalInputSequence.escape)
  }

  fun sendTab(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.tab(modifiers))
  }

  fun sendBackspace() {
    send(TerminalInputSequence.backspace)
  }

  fun sendArrowUp(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.arrowUp(termuxModes(), modifiers))
  }

  fun sendArrowDown(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.arrowDown(termuxModes(), modifiers))
  }

  fun sendArrowLeft(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.arrowLeft(termuxModes(), modifiers))
  }

  fun sendArrowRight(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.arrowRight(termuxModes(), modifiers))
  }

  fun sendHome(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.home(modifiers))
  }

  fun sendEnd(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.end(modifiers))
  }

  fun sendPageUp(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.pageUp(modifiers))
  }

  fun sendPageDown(modifiers: TerminalInputModifiers = TerminalInputModifiers()) {
    send(TerminalInputSequence.pageDown(modifiers))
  }

  fun resize(rows: Int, cols: Int) {
    Log.d("NBG_RESIZE", "termux rows=$rows cols=$cols")
    termuxSession.updateSize(cols.coerceAtLeast(1), rows.coerceAtLeast(1))
  }

  fun close() {
    termuxSession.finishIfRunning()
  }

  private fun termuxModes(): TerminalModes {
    val emulator = termuxSession.getEmulator()
    return TerminalModes(
      applicationCursorKeys = emulator?.isCursorKeysApplicationMode ?: false,
      bracketedPaste = false,
      applicationKeypad = emulator?.isKeypadApplicationMode ?: false,
    )
  }

  companion object {
    private const val MAX_BUFFER_CHARS = 80_000
  }
}

class EmbeddedTerminalManager(
  private val environment: TerminalEnvironment,
  private val clipboard: TerminalClipboard = NoOpTerminalClipboard,
) {
  fun createLocalUbuntuIoSession(initialSize: TerminalPtySize = TerminalPtySize()): TerminalIoSession {
    val commonScript = environment.prepare()
    val filesDir = commonScript.parentFile ?: error("Missing terminal files dir")
    val bash = File(filesDir, "usr/bin/bash")
    val command = listOf(bash.absolutePath, "-c", "source ${commonScript.absolutePath} && start_shell")
    val pty = PtyProcess.start(command, environment.processEnvironment(), filesDir, initialSize)
    return TerminalIoSession.from(pty)
  }

  fun prepareLocalUbuntuSessionConfig(): TerminalLaunchConfig {
    val commonScript = environment.prepare()
    val filesDir = commonScript.parentFile ?: error("Missing terminal files dir")
    val bash = File(filesDir, "usr/bin/bash")
    return TerminalLaunchConfig(
      shellPath = bash.absolutePath,
      cwd = filesDir.absolutePath,
      args = termuxShellCommandArgs(bash.absolutePath, "source ${commonScript.absolutePath} && start_shell"),
      env = environment.processEnvironment().map { (key, value) -> "$key=$value" }.toTypedArray(),
    )
  }

  fun createLocalUbuntuSession(config: TerminalLaunchConfig): TerminalSession {
    val client = NbgTerminalSessionClient(clipboard)
    val delegate = TermuxTerminalSession(
      config.shellPath,
      config.cwd,
      config.args,
      config.env,
      TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
      client,
    )
    return TerminalSession(delegate, client)
  }
}

data class TerminalLaunchConfig(
  val shellPath: String,
  val cwd: String,
  val args: Array<String>,
  val env: Array<String>,
)

internal fun termuxShellCommandArgs(shellPath: String, command: String): Array<String> =
  arrayOf(File(shellPath).name.ifBlank { "sh" }, "-c", command)

internal class NbgTerminalSessionClient(
  clipboard: TerminalClipboard = NoOpTerminalClipboard,
) : TerminalSessionClient {
  var owner: TerminalSession? = null
  var onScreenChanged: (() -> Unit)? = null
  private val clipboardController = TerminalClipboardController(clipboard) { text ->
    owner?.sendPaste(text)
  }

  override fun onTextChanged(session: TermuxTerminalSession) {
    owner?.onTermuxTextChanged()
    onScreenChanged?.invoke()
  }

  override fun onTitleChanged(session: TermuxTerminalSession) = Unit

  override fun onSessionFinished(session: TermuxTerminalSession) {
    owner?.onTermuxTextChanged()
    onScreenChanged?.invoke()
  }

  override fun onCopyTextToClipboard(session: TermuxTerminalSession, text: String) {
    clipboardController.copy(text)
  }

  override fun onPasteTextFromClipboard(session: TermuxTerminalSession) {
    clipboardController.paste()
  }

  override fun onBell(session: TermuxTerminalSession) = Unit

  override fun onColorsChanged(session: TermuxTerminalSession) {
    onScreenChanged?.invoke()
  }

  override fun onTerminalCursorStateChange(state: Boolean) {
    onScreenChanged?.invoke()
  }

  override fun getTerminalCursorStyle(): Int? = null

  override fun logError(tag: String, message: String) {
    Log.e(tag, message)
  }

  override fun logWarn(tag: String, message: String) {
    Log.w(tag, message)
  }

  override fun logInfo(tag: String, message: String) {
    Log.i(tag, message)
  }

  override fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
  }

  override fun logVerbose(tag: String, message: String) {
    Log.v(tag, message)
  }

  override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
    Log.e(tag, message, e)
  }

  override fun logStackTrace(tag: String, e: Exception) {
    Log.e(tag, "Terminal session error", e)
  }
}
