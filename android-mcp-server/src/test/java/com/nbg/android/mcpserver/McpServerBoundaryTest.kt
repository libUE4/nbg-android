package com.nbg.android.mcpserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class McpServerBoundaryTest {
  @Test
  fun mcpServerServiceIsPrivateLoopbackOnlyAndReadOnlyBeta() {
    val manifest = File("src/main/AndroidManifest.xml").readText()
    val service = File("src/main/java/com/nbg/android/mcpserver/McpServerService.kt").readText()
    val activity = File("src/main/java/com/nbg/android/mcpserver/MainActivity.kt").readText()

    assertTrue(manifest.contains("android:allowBackup=\"false\""))
    assertTrue(manifest.contains("android:fullBackupContent=\"false\""))
    assertTrue(manifest.contains("android:name=\".McpServerService\""))
    assertTrue(manifest.contains("android:exported=\"false\""))
    assertTrue(service.contains("ServerSocket(port, 50, InetAddress.getByName(NBG_ANDROID_MCP_LOOPBACK_HOST))"))
    assertTrue(service.contains("Listening on http://127.0.0.1:${'$'}port/mcp"))
    assertTrue(service.contains("with bearer auth"))
    assertTrue(service.contains("ensureBearerToken(this)"))
    assertTrue(service.contains("bearerTokenProvider = { ensureBearerToken(this) }"))
    assertTrue(service.contains("onListening = { listeningPort ->"))
    assertTrue(service.contains("markServerListening(listeningPort, bearerTokenPresent = bearerToken.isNotBlank())"))
    assertTrue(service.contains("markServerStopped(error.message)"))
    assertTrue(service.contains("markServerStopped(clearError = false)"))
    assertTrue(service.contains("if (stopped) break"))
    assertTrue(service.contains("throw error"))
    assertTrue(service.contains("if (!nbgAndroidMcpRequestAuthorized(headers, bearerTokenProvider()))"))
    assertTrue(service.contains("writeUnauthorized(client)"))
    assertTrue(service.contains("WWW-Authenticate: Bearer realm=\\\"NBG Android MCP\\\""))
    assertTrue(service.contains("KEY_BEARER_TOKEN"))
    assertTrue(service.contains("KEY_BEARER_TOKEN_PRESENT"))
    assertTrue(service.contains("KEY_LAST_ERROR"))
    assertTrue(service.contains("nbgGenerateAndroidMcpBearerToken"))
    assertTrue(activity.contains("ClipDescription.EXTRA_IS_SENSITIVE"))
    assertTrue(activity.contains("CLIPBOARD_TOKEN_TTL_MS"))
    assertTrue(activity.contains("clearPrimaryClip()"))
    assertTrue(activity.contains("ClipData.newPlainText(\"NBG MCP\", token)"))
    assertTrue(activity.contains("ClipboardManager.OnPrimaryClipChangedListener"))
    assertTrue(activity.contains("clipboardChangedAfterTokenCopy = true"))
    assertTrue(activity.contains("!clipboardChangedAfterTokenCopy"))
    assertTrue(activity.contains("removePrimaryClipChangedListener(tokenClipboardListener)"))
    assertFalse(activity.contains("clipboard.primaryClip"))
    assertFalse(activity.contains("coerceToText("))
    assertFalse(activity.contains("ClipData.newPlainText(\"NBG MCP Bearer Token\""))
    assertFalse(service.contains("notification(\"Bearer"))
    assertTrue(service.contains("\"android_device_info\""))
    assertTrue(service.contains("\"android_echo\""))
    assertFalse(service.contains("InetAddress.getByName(\"0.0.0.0\")"))
    assertFalse(service.contains("Runtime.getRuntime()"))
    assertFalse(service.contains("ProcessBuilder("))
    assertFalse(service.contains("FileOutputStream("))
    assertFalse(service.contains("\"DELETE\" -> callTool"))
  }

  @Test
  fun bearerAuthorizationRequiresExactLocalToken() {
    val token = "abcdefghijklmnopqrstuvwxyzABCDEF123456"

    assertTrue(nbgAndroidMcpRequestAuthorized(mapOf("authorization" to "Bearer $token"), token))
    assertTrue(nbgAndroidMcpRequestAuthorized(mapOf("Authorization" to "bearer $token"), token))
    assertFalse(nbgAndroidMcpRequestAuthorized(emptyMap(), token))
    assertFalse(nbgAndroidMcpRequestAuthorized(mapOf("authorization" to "Bearer wrong-token"), token))
    assertFalse(nbgAndroidMcpRequestAuthorized(mapOf("authorization" to "Basic $token"), token))
    assertFalse(nbgAndroidMcpRequestAuthorized(mapOf("authorization" to "Bearer $token extra"), token))
    assertFalse(nbgAndroidMcpRequestAuthorized(mapOf("authorization" to "Bearer $token"), "short"))
  }

  @Test
  fun mcpDiscoveryAndSideEffectPoliciesStayClosedInV1() {
    val noDiscoveryEvidence = nbgReviewAndroidMcpEphemeralPortMigration(
      mainAppDiscoveryContractPresent = false,
      clientMigrationPlanPresent = false,
      diagnosticsRedactionUpdatePresent = false,
    )
    val fullDiscoveryEvidence = nbgReviewAndroidMcpEphemeralPortMigration(
      mainAppDiscoveryContractPresent = true,
      clientMigrationPlanPresent = true,
      diagnosticsRedactionUpdatePresent = true,
    )
    val noSideEffectEvidence = nbgReviewAndroidMcpStandaloneSideEffectTool(
      mainAppPermissionUiPresent = false,
      permissionRiskMappingPresent = false,
      diagnosticsRedactionUpdatePresent = false,
      securityReviewPresent = false,
    )
    val fullSideEffectEvidence = nbgReviewAndroidMcpStandaloneSideEffectTool(
      mainAppPermissionUiPresent = true,
      permissionRiskMappingPresent = true,
      diagnosticsRedactionUpdatePresent = true,
      securityReviewPresent = true,
    )

    assertEquals(NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION, noDiscoveryEvidence.policyVersion)
    assertEquals(listOf("main_app_discovery_contract", "client_migration_plan", "diagnostics_redaction_update"), noDiscoveryEvidence.requiredEvidence)
    assertEquals(emptyList<String>(), noDiscoveryEvidence.presentEvidence)
    assertFalse(noDiscoveryEvidence.useEphemeralPort)
    assertEquals(NBG_ANDROID_MCP_PORT, noDiscoveryEvidence.port)

    assertEquals(listOf("main_app_discovery_contract", "client_migration_plan", "diagnostics_redaction_update"), fullDiscoveryEvidence.presentEvidence)
    assertFalse("v1 must keep the fixed loopback MCP port even when future evidence labels are present", fullDiscoveryEvidence.useEphemeralPort)
    assertEquals(NBG_ANDROID_MCP_PORT, fullDiscoveryEvidence.port)

    assertEquals(NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION, noSideEffectEvidence.policyVersion)
    assertEquals(
      listOf("main_app_permission_ui", "permission_risk_mapping", "diagnostics_redaction_update", "security_review"),
      noSideEffectEvidence.requiredEvidence,
    )
    assertEquals(emptyList<String>(), noSideEffectEvidence.presentEvidence)
    assertFalse(noSideEffectEvidence.allowStandaloneSideEffectTool)

    assertEquals(
      listOf("main_app_permission_ui", "permission_risk_mapping", "diagnostics_redaction_update", "security_review"),
      fullSideEffectEvidence.presentEvidence,
    )
    assertFalse("v1 must keep standalone MCP side-effect tools disabled even when future evidence labels are present", fullSideEffectEvidence.allowStandaloneSideEffectTool)
    assertTrue(fullSideEffectEvidence.reason.contains("read-only/echo-only"))
  }

  @Test
  fun mcpHttpServerRejectsUnauthenticatedRequestsBeforeProtocolHandling() {
    val token = "abcdefghijklmnopqrstuvwxyzABCDEF123456"
    withMcpServer({ token }) { port ->
      val unauthGet = rawHttp(
        port,
        "GET /mcp HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
      )
      assertTrue(unauthGet.startsWith("HTTP/1.1 401"))
      assertFalse(unauthGet.contains("text/event-stream"))
      assertFalse(unauthGet.contains("event: endpoint"))

      val unauthPost = postRpc(port, token = null)
      assertTrue(unauthPost.startsWith("HTTP/1.1 401"))
      assertFalse(unauthPost.contains("serverInfo"))
      assertFalse(unauthPost.contains(NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION))

      val unauthDelete = rawHttp(
        port,
        "DELETE /mcp HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n",
      )
      assertTrue(unauthDelete.startsWith("HTTP/1.1 401"))
      assertFalse(unauthDelete.startsWith("HTTP/1.1 204"))

      val authPost = postRpc(port, token = token)
      assertTrue(authPost.startsWith("HTTP/1.1 200"))
      assertTrue(authPost.contains(NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION))
    }
  }

  @Test
  fun mcpHttpServerReadsCurrentBearerTokenForEveryRequest() {
    val oldToken = "oldabcdefghijklmnopqrstuvwxyzABCDEF123456"
    val newToken = "newabcdefghijklmnopqrstuvwxyzABCDEF123456"
    var currentToken = oldToken

    withMcpServer({ currentToken }) { port ->
      val oldAuthorized = postRpc(port, token = oldToken)
      assertTrue(oldAuthorized.startsWith("HTTP/1.1 200"))

      currentToken = newToken

      val oldRejected = postRpc(port, token = oldToken)
      assertTrue(oldRejected.startsWith("HTTP/1.1 401"))

      val newAuthorized = postRpc(port, token = newToken)
      assertTrue(newAuthorized.startsWith("HTTP/1.1 200"))
      assertTrue(newAuthorized.contains(NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION))
    }
  }

  @Test
  fun mcpHttpServerPropagatesUnexpectedAcceptFailure() {
    val token = "abcdefghijklmnopqrstuvwxyzABCDEF123456"
    val server = AndroidMcpHttpServer(port = 0, bearerTokenProvider = { token })
    var failure: Throwable? = null
    val thread = Thread {
      try {
        server.start()
      } catch (error: Throwable) {
        failure = error
      }
    }.apply {
      isDaemon = true
      start()
    }
    val deadline = System.currentTimeMillis() + 3_000
    while (server.boundPort == 0 && thread.isAlive && System.currentTimeMillis() < deadline) {
      Thread.sleep(10)
    }
    assertTrue("MCP test server did not bind", server.boundPort > 0)

    val socketField = AndroidMcpHttpServer::class.java.getDeclaredField("socket").apply {
      isAccessible = true
    }
    (socketField.get(server) as ServerSocket).close()

    thread.join(2_000)
    assertFalse("unexpected accept failure should stop the server thread", thread.isAlive)
    assertTrue("unexpected accept failure should propagate", failure != null)
  }

  @Test
  fun androidMcpServerContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/android-mcp-server-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val contractSource = File("src/main/java/com/nbg/android/mcpserver/AndroidMcpServerContract.kt").readText()
    val service = File("src/main/java/com/nbg/android/mcpserver/McpServerService.kt").readText()

    assertEquals("nbg-android-mcp-server-v1", NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION)
    assertEquals("127.0.0.1", NBG_ANDROID_MCP_LOOPBACK_HOST)
    assertEquals(37666, NBG_ANDROID_MCP_PORT)
    assertEquals(setOf("android_device_info", "android_echo"), nbgAndroidMcpToolNames())
    assertTrue(NBG_ANDROID_MCP_TOOLS.all { it.boundary in setOf(AndroidMcpToolBoundary.ReadOnly, AndroidMcpToolBoundary.EchoOnly) })

    listOf(
      "Android MCP Server Contract",
      "nbg-android-mcp-server-v1",
      "127.0.0.1",
      "37666",
      "android_device_info",
      "android_echo",
      "ReadOnly",
      "EchoOnly",
      "Authorization: Bearer",
      "401",
      "local bearer token",
      "NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION",
      "NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION",
      "main_app_discovery_contract",
      "main_app_permission_ui",
      "SideEffectBlocked",
      "None for v1",
      "McpServerService",
      "android:exported=\"false\"",
      "Test Oracle",
    ).forEach {
      assertTrue(contract.contains(it))
    }

    assertTrue(index.contains("android-mcp-server-contract"))
    assertTrue(index.contains("P1 active"))
    assertTrue(index.contains("AndroidMcpServerContract.kt"))

    assertTrue(contractSource.contains("NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_AUTH_SCHEME"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_BEARER_TOKEN_BYTES"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_BEARER_TOKEN_MIN_LENGTH"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION"))
    assertTrue(contractSource.contains("nbgReviewAndroidMcpEphemeralPortMigration"))
    assertTrue(contractSource.contains("nbgReviewAndroidMcpStandaloneSideEffectTool"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_EPHEMERAL_PORT_REQUIRED_EVIDENCE"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_SIDE_EFFECT_REQUIRED_EVIDENCE"))
    assertTrue(contractSource.contains("nbgAndroidMcpRequestAuthorized"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_TOOLS"))
    assertTrue(File("src/test/java/com/nbg/android/mcpserver/McpServerBoundaryTest.kt").readText().contains("mcpDiscoveryAndSideEffectPoliciesStayClosedInV1"))
    assertTrue(service.contains("NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION"))
    assertTrue(service.contains("KEY_BEARER_TOKEN"))
    assertTrue(service.contains("writeUnauthorized(client)"))
    assertTrue(service.contains("NBG_ANDROID_MCP_TOOLS.forEach"))
    assertTrue(service.contains("nbgAndroidMcpToolNames()"))
    assertTrue(service.contains(".put(\"boundary\", contract.boundary.name)"))
    assertTrue(service.contains(".put(\"isError\", true)"))
  }

  private fun withMcpServer(tokenProvider: () -> String, block: (Int) -> Unit) {
    val server = AndroidMcpHttpServer(port = 0, bearerTokenProvider = tokenProvider)
    val thread = Thread { server.start() }.apply {
      isDaemon = true
      start()
    }
    val deadline = System.currentTimeMillis() + 3_000
    while (server.boundPort == 0 && thread.isAlive && System.currentTimeMillis() < deadline) {
      Thread.sleep(10)
    }
    assertTrue("MCP test server did not bind", server.boundPort > 0)
    try {
      block(server.boundPort)
    } finally {
      server.stop()
      thread.join(2_000)
    }
  }

  private fun postRpc(port: Int, token: String?): String {
    val body = """{"jsonrpc":"2.0","id":1,"method":"initialize"}"""
    val authHeader = token?.let { "Authorization: Bearer $it\r\n" }.orEmpty()
    return rawHttp(
      port,
      "POST /mcp HTTP/1.1\r\n" +
        "Host: 127.0.0.1\r\n" +
        authHeader +
        "Content-Type: application/json\r\n" +
        "Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n" +
        "\r\n" +
        body,
    )
  }

  private fun rawHttp(port: Int, request: String): String {
    Socket(NBG_ANDROID_MCP_LOOPBACK_HOST, port).use { socket ->
      socket.soTimeout = 2_000
      socket.getOutputStream().write(request.toByteArray(StandardCharsets.UTF_8))
      socket.getOutputStream().flush()
      val buffer = ByteArray(8192)
      val response = StringBuilder()
      while (true) {
        val read = try {
          socket.getInputStream().read(buffer)
        } catch (_: SocketTimeoutException) {
          break
        }
        if (read <= 0) break
        response.append(String(buffer, 0, read, StandardCharsets.UTF_8))
      }
      return response.toString()
    }
  }
}
