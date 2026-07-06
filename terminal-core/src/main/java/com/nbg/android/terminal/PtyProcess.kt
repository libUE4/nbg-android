package com.nbg.android.terminal

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class PtyProcess private constructor(
  private val pid: Int,
  private val fd: Int,
  private val descriptor: ParcelFileDescriptor,
) {
  val input: InputStream = FileInputStream(descriptor.fileDescriptor)
  val output: OutputStream = FileOutputStream(descriptor.fileDescriptor)

  fun resize(rows: Int, cols: Int): Boolean = setPtyWindowSize(fd, pid, rows, cols) == 0

  fun isAlive(): Boolean =
    runCatching { android.system.Os.kill(pid, 0); true }.getOrDefault(false)

  fun destroy() {
    runCatching { android.os.Process.sendSignal(pid, 1) }
    runCatching { android.os.Process.sendSignal(pid, 9) }
    runCatching { descriptor.close() }
  }

  fun waitFor(): Int = waitForPid(pid)

  private external fun setPtyWindowSize(fd: Int, pid: Int, rows: Int, cols: Int): Int

  companion object {
    init {
      System.loadLibrary("nbgpty")
    }

    fun start(
      command: List<String>,
      environment: Map<String, String>,
      workingDir: File,
      initialSize: TerminalPtySize = TerminalPtySize(),
    ): PtyProcess {
      val envArray = environment.map { (key, value) -> "$key=$value" }.toTypedArray()
      val size = initialSize.clamped()
      val result = createSubprocess(command.toTypedArray(), envArray, workingDir.absolutePath, size.rows, size.cols)
      val pid = result[0]
      val fd = result[1]
      return PtyProcess(pid, fd, ParcelFileDescriptor.adoptFd(fd))
    }

    private external fun createSubprocess(
      cmdArray: Array<String>,
      envArray: Array<String>,
      workingDir: String,
      rows: Int,
      cols: Int,
    ): IntArray

    private external fun waitForPid(pid: Int): Int
  }
}

data class TerminalPtySize(
  val rows: Int = 40,
  val cols: Int = 100,
) {
  fun clamped(): TerminalPtySize = TerminalPtySize(
    rows = rows.coerceAtLeast(1),
    cols = cols.coerceAtLeast(1),
  )
}
