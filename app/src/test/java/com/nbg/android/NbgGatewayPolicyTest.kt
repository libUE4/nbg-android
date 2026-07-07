package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgGatewayPolicyTest {
  @Test
  fun externalGatewayMessagesCannotBypassToolPermissionModel() {
    val loopback = nbgGatewayInboxMessage(
      source = NbgInboundMessageSource.LoopbackHttp,
      senderLabel = "127.0.0.1",
      text = "read status",
      canExecuteTools = true,
      nowMs = 1L,
    )
    val external = nbgGatewayInboxMessage(
      source = NbgInboundMessageSource.ExternalPlatform,
      senderLabel = "telegram",
      text = "run status",
      canExecuteTools = true,
      nowMs = 1L,
    )

    assertTrue(loopback.canExecuteTools)
    assertEquals(NbgGatewayInboxStatus.Accepted, loopback.status)
    assertFalse(external.canExecuteTools)
    assertEquals(NbgGatewayInboxStatus.Received, external.status)
  }

}
