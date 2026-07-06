package com.nbg.android

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class NbgFtpFileServerTest {
  @Test
  fun ftpLoginListStoreRetrieveRenameAndDeleteWork() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      val port = requireNotNull(server.port)
      FtpClient("127.0.0.1", port).use { ftp ->
        assertTrue(ftp.banner.startsWith("220"))
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))
        assertTrue(ftp.command("PWD").contains("\"/\""))
        assertTrue(ftp.command("MKD projects").startsWith("257"))
        assertTrue(ftp.command("CWD projects").startsWith("250"))
        ftp.store("hello.txt", "hello ftp")
        assertTrue(File(root, "projects/hello.txt").exists())

        val listed = ftp.list(".")
        assertTrue(listed.contains("hello.txt"))

        val retrieved = ftp.retrieve("hello.txt")
        assertEquals("hello ftp", retrieved)

        assertTrue(ftp.command("RNFR hello.txt").startsWith("350"))
        assertTrue(ftp.command("RNTO renamed.txt").startsWith("250"))
        assertFalse(File(root, "projects/hello.txt").exists())
        assertTrue(File(root, "projects/renamed.txt").exists())

        assertTrue(ftp.command("DELE renamed.txt").startsWith("250"))
        assertFalse(File(root, "projects/renamed.txt").exists())
      }
    }
  }

  @Test
  fun readOnlyModeAllowsListingAndRetrievalButRejectsWrites() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    File(root, "keep.txt").writeText("keep")
    File(root, "existing-dir").mkdirs()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
      accessMode = NbgFileShareAccessMode.ReadOnly,
    )

    server.use {
      server.start()
      val port = requireNotNull(server.port)
      FtpClient("127.0.0.1", port).use { ftp ->
        assertTrue(ftp.banner.startsWith("220"))
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        assertTrue(ftp.list(".").contains("keep.txt"))
        assertEquals("keep", ftp.retrieve("keep.txt"))

        assertTrue(ftp.command("MKD blocked").startsWith("550"))
        assertTrue(ftp.command("DELE keep.txt").startsWith("550"))
        assertTrue(ftp.command("RMD existing-dir").startsWith("550"))
        assertTrue(ftp.command("RNFR keep.txt").startsWith("550"))
        assertTrue(ftp.command("RNTO moved.txt").startsWith("550"))
        assertTrue(ftp.command("APPE keep.txt").startsWith("550"))
        assertTrue(ftp.command("PASV").startsWith("227"))
        assertTrue(ftp.command("STOR new.txt").startsWith("550"))
        assertTrue(ftp.command("NOOP").startsWith("200"))

        assertEquals("keep", File(root, "keep.txt").readText())
        assertTrue(File(root, "existing-dir").isDirectory)
        assertFalse(File(root, "blocked").exists())
        assertFalse(File(root, "moved.txt").exists())
        assertFalse(File(root, "new.txt").exists())
      }
    }
  }

  @Test
  fun closingServerCancelsPendingPassiveStoreBeforeFilesystemMutation() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
      accessMode = NbgFileShareAccessMode.ReadWrite,
    )

    server.start()
    FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
      assertTrue(ftp.command("USER nbg").startsWith("331"))
      assertTrue(ftp.command("PASS secret").startsWith("230"))
      val (pre, dataPort) = ftp.startStoreWithoutOpeningDataConnection("late/write.txt")
      assertTrue(pre.startsWith("150"))

      server.close()
      runCatching {
        Socket("127.0.0.1", dataPort).use { data ->
          data.getOutputStream().write("must not persist".toByteArray(StandardCharsets.UTF_8))
          data.getOutputStream().flush()
        }
      }
      Thread.sleep(100)

      assertFalse(File(root, "late").exists())
      assertFalse(File(root, "late/write.txt").exists())
    }
  }

  @Test
  fun fallsBackToEphemeralPortWhenPreferredPortIsBusy() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { occupied ->
      val server = NbgFtpFileServer(
        fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
        credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
        bindAddress = InetAddress.getByName("127.0.0.1"),
        preferredPort = occupied.localPort,
      )

      server.use {
        server.start()

        val fallbackPort = requireNotNull(server.port)
        assertTrue(server.isRunning)
        assertFalse(occupied.localPort == fallbackPort)
        FtpClient("127.0.0.1", fallbackPort).use { ftp ->
          assertTrue(ftp.banner.startsWith("220"))
          assertTrue(ftp.command("USER nbg").startsWith("331"))
          assertTrue(ftp.command("PASS secret").startsWith("230"))
        }
      }
    }
  }

  @Test
  fun relativeFtpPathsPreserveNormalPathSegments() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))
        assertTrue(ftp.command("MKD projects").startsWith("257"))
        assertTrue(ftp.command("CWD projects").startsWith("250"))
        assertTrue(ftp.command("MKD nested").startsWith("257"))

        ftp.store("nested/file.txt", "nested data")

        assertTrue(File(root, "projects/nested/file.txt").isFile)
      }
    }
  }

  @Test
  fun renameToSamePathDoesNotDeleteTheSourceFile() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))
        ftp.store("same.txt", "keep me")

        assertTrue(ftp.command("RNFR same.txt").startsWith("350"))
        assertTrue(ftp.command("RNTO same.txt").startsWith("250"))

        assertEquals("keep me", File(root, "same.txt").readText())
      }
    }
  }

  @Test
  fun renameDirectoryIntoItsOwnChildIsRejected() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))
        assertTrue(ftp.command("MKD parent").startsWith("257"))
        assertTrue(ftp.command("MKD parent/child").startsWith("257"))

        assertTrue(ftp.command("RNFR parent").startsWith("350"))
        assertTrue(ftp.command("RNTO parent/child/moved").startsWith("550"))

        assertTrue(File(root, "parent").isDirectory)
        assertFalse(File(root, "parent/child/moved").exists())
      }
    }
  }

  @Test
  fun protectedVirtualRootsCannotBeDeletedOrReplacedByRename() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    File(root, "keep.txt").writeText("root")
    File(root, "incoming.txt").writeText("incoming")
    File(uploads, "upload.txt").writeText("upload")
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        assertTrue(ftp.command("RMD /").startsWith("550"))
        assertTrue(ftp.command("RMD /nbg-uploads").startsWith("550"))
        assertTrue(ftp.command("RNFR /").startsWith("550"))
        assertTrue(ftp.command("RNFR /nbg-uploads").startsWith("550"))
        assertTrue(ftp.command("RNFR incoming.txt").startsWith("350"))
        assertTrue(ftp.command("RNTO /").startsWith("550"))
        assertTrue(ftp.command("RNFR incoming.txt").startsWith("350"))
        assertTrue(ftp.command("RNTO /nbg-uploads").startsWith("550"))

        assertTrue(root.isDirectory)
        assertTrue(uploads.isDirectory)
        assertEquals("root", File(root, "keep.txt").readText())
        assertEquals("incoming", File(root, "incoming.txt").readText())
        assertEquals("upload", File(uploads, "upload.txt").readText())
      }
    }
  }

  @Test
  fun removeDirectoryCommandDoesNotDeleteRegularFiles() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    File(root, "keep.txt").writeText("do not remove")
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        assertTrue(ftp.command("RMD keep.txt").startsWith("550"))

        assertEquals("do not remove", File(root, "keep.txt").readText())
      }
    }
  }

  @Test
  fun failedRenameTargetDoesNotDiscardPendingRenameSource() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    File(root, "incoming.txt").writeText("incoming")
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        assertTrue(ftp.command("RNFR incoming.txt").startsWith("350"))
        assertTrue(ftp.command("RNTO /").startsWith("550"))
        assertTrue(ftp.command("RNTO moved.txt").startsWith("250"))

        assertFalse(File(root, "incoming.txt").exists())
        assertEquals("incoming", File(root, "moved.txt").readText())
      }
    }
  }

  @Test
  fun passiveTransferTimesOutWhenClientNeverOpensDataConnection() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        val responses = ftp.listWithoutOpeningDataConnection(".")

        assertTrue(responses.first.startsWith("150"))
        assertTrue(responses.second.startsWith("425"))
        assertTrue(ftp.command("NOOP").startsWith("200"))
      }
    }
  }

  @Test
  fun storeWithoutDataConnectionDoesNotCreateParentDirectories() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        val responses = ftp.storeWithoutOpeningDataConnection("nested/missing/file.txt")

        assertTrue(responses.first.startsWith("150"))
        assertTrue(responses.second.startsWith("425"))
        assertFalse(File(root, "nested").exists())
        assertFalse(File(root, "nested/missing/file.txt").exists())
        assertTrue(ftp.command("NOOP").startsWith("200"))
      }
    }
  }

  @Test
  fun closingServerDisconnectsExistingControlClients() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.start()
    FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
      assertTrue(ftp.command("USER nbg").startsWith("331"))
      assertTrue(ftp.command("PASS secret").startsWith("230"))

      server.close()

      runCatching { ftp.command("NOOP") }
        .onSuccess { reply -> fail("Expected FTP control connection to close, got: $reply") }
        .onFailure { error -> assertTrue(error.message.orEmpty().contains("FTP connection closed")) }
    }
  }

  @Test
  fun fileSharePathsKeepLiteralPlusWhileDecodingPercentEscapes() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads)
    File(root, "a+b.txt").writeText("plus")
    File(root, "a b.txt").writeText("space")

    assertEquals("a+b.txt", fileSystem.entry("/a+b.txt").name)
    assertEquals("a+b.txt", fileSystem.entry("/a%2Bb.txt").name)
    assertEquals("a b.txt", fileSystem.entry("/a%20b.txt").name)
    assertEquals("%broken.txt", fileSystem.entry("/%broken.txt").name)
  }

  @Test
  fun directoryListingsHideSymlinksEscapingSharedRoots() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val outside = createTempDirectory(prefix = "nbg-ftp-outside").toFile()
    File(root, "inside.txt").writeText("inside")
    File(uploads, "upload.txt").writeText("upload")
    File(outside, "secret.txt").writeText("secret")
    Files.createSymbolicLink(File(root, "outside-link").toPath(), outside.toPath())
    Files.createSymbolicLink(File(uploads, "outside-upload-link").toPath(), outside.toPath())
    val fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads)

    val rootNames = fileSystem.children("/").map { it.name }
    val uploadNames = fileSystem.children("/nbg-uploads").map { it.name }

    assertTrue("inside.txt" in rootNames)
    assertTrue("nbg-uploads" in rootNames)
    assertFalse("outside-link" in rootNames)
    assertTrue("upload.txt" in uploadNames)
    assertFalse("outside-upload-link" in uploadNames)
  }

  @Test
  fun ftpDeleteOfInternalSymlinkDoesNotDeleteTargetDirectory() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val target = File(root, "target").apply { mkdirs() }
    File(target, "keep.txt").writeText("keep")
    Files.createSymbolicLink(File(root, "target-link").toPath(), target.toPath())
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        assertTrue(ftp.command("DELE target-link").startsWith("250"))

        assertFalse(Files.exists(File(root, "target-link").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals("keep", File(target, "keep.txt").readText())
      }
    }
  }

  @Test
  fun ftpRenameOfInternalSymlinkMovesLinkNotTargetDirectory() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    val target = File(root, "target").apply { mkdirs() }
    File(target, "keep.txt").writeText("keep")
    Files.createSymbolicLink(File(root, "target-link").toPath(), target.toPath())
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        assertTrue(ftp.command("RNFR target-link").startsWith("350"))
        assertTrue(ftp.command("RNTO moved-link").startsWith("250"))

        assertFalse(Files.exists(File(root, "target-link").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.isSymbolicLink(File(root, "moved-link").toPath()))
        assertEquals("keep", File(target, "keep.txt").readText())
      }
    }
  }

  @Test
  fun listWithUnixFlagsStillUsesTheProvidedPath() {
    val root = createTempDirectory(prefix = "nbg-ftp-root").toFile()
    val uploads = createTempDirectory(prefix = "nbg-ftp-uploads").toFile()
    File(root, "projects").mkdirs()
    File(root, "projects/flagged.txt").writeText("listed")
    val server = NbgFtpFileServer(
      fileSystem = NbgFileShareFileSystem(rootHomeDir = root, uploadsDir = uploads),
      credentials = NbgFileShareCredentials(username = "nbg", password = "secret"),
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = 0,
    )

    server.use {
      server.start()
      FtpClient("127.0.0.1", requireNotNull(server.port)).use { ftp ->
        assertTrue(ftp.command("USER nbg").startsWith("331"))
        assertTrue(ftp.command("PASS secret").startsWith("230"))

        val listed = ftp.list("-la projects")

        assertTrue(listed.contains("flagged.txt"))
      }
    }
  }
}

private class FtpClient(
  host: String,
  port: Int,
) : AutoCloseable {
  private val control = Socket(host, port).apply {
    soTimeout = 5_000
  }
  private val reader = BufferedReader(InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8))
  private val writer = OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8)
  val banner: String = readReply("banner")

  fun command(command: String): String {
    writer.write(command)
    writer.write("\r\n")
    writer.flush()
    return readReply(command)
  }

  fun store(path: String, data: String): String {
    val pasv = command("PASV")
    val port = pasv.extractPassivePort()
    writer.write("STOR $path\r\n")
    writer.flush()
    val pre = readReply("STOR $path pre")
    assertTrue("Unexpected STOR pre-reply: $pre", pre.startsWith("150"))
    Socket("127.0.0.1", port).use { socket ->
      socket.getOutputStream().write(data.toByteArray(StandardCharsets.UTF_8))
      socket.getOutputStream().flush()
    }
    return readReply("STOR $path post")
  }

  fun list(path: String): String {
    val pasv = command("PASV")
    val port = pasv.extractPassivePort()
    writer.write("LIST $path\r\n")
    writer.flush()
    val pre = readReply("LIST $path pre")
    val data = Socket("127.0.0.1", port).apply { soTimeout = 5_000 }
    val result = data.use { socket -> socket.getInputStream().readBytes().toString(StandardCharsets.UTF_8) }
    val post = readReply("LIST $path post")
    return "$pre\n$result\n$post"
  }

  fun retrieve(path: String): String {
    val pasv = command("PASV")
    val port = pasv.extractPassivePort()
    writer.write("RETR $path\r\n")
    writer.flush()
    readReply("RETR $path pre")
    val data = Socket("127.0.0.1", port).apply { soTimeout = 5_000 }
    val result = data.use { socket -> socket.getInputStream().readBytes().toString(StandardCharsets.UTF_8) }
    readReply("RETR $path post")
    return result
  }

  fun listWithoutOpeningDataConnection(path: String): Pair<String, String> {
    command("PASV")
    writer.write("LIST $path\r\n")
    writer.flush()
    val pre = readReply("LIST $path pre")
    val post = readReply("LIST $path post")
    return pre to post
  }

  fun storeWithoutOpeningDataConnection(path: String): Pair<String, String> {
    command("PASV")
    writer.write("STOR $path\r\n")
    writer.flush()
    val pre = readReply("STOR $path pre")
    val post = readReply("STOR $path post")
    return pre to post
  }

  fun startStoreWithoutOpeningDataConnection(path: String): Pair<String, Int> {
    val pasv = command("PASV")
    val port = pasv.extractPassivePort()
    writer.write("STOR $path\r\n")
    writer.flush()
    val pre = readReply("STOR $path pre")
    return pre to port
  }

  private fun readReply(context: String): String =
    reader.readLine() ?: error("FTP connection closed while waiting for $context")

  private fun String.extractPassivePort(): Int {
    val numbers = Regex("""(\d+),(\d+)\)""").find(this)?.groupValues ?: error("No passive port: $this")
    return numbers[1].toInt() * 256 + numbers[2].toInt()
  }

  override fun close() {
    runCatching {
      writer.write("QUIT\r\n")
      writer.flush()
    }
    runCatching { control.close() }
  }
}
