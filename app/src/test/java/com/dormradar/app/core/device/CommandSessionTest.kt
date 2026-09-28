package com.dormradar.app.core.device

import com.dormradar.app.core.protocol.CommandBuilder
import com.dormradar.app.core.protocol.Frame
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 命令会话测试：ACK 匹配、陈旧 ACK 丢弃、超时 */
class CommandSessionTest {

    @Test
    fun `发送命令并匹配对应ACK`() = runTest {
        var sent: ByteArray? = null
        val session = CommandSession(write = { sent = it })
        launch {
            // 先回一条陈旧 ACK（如上一条命令的迟到回复），再回目标命令的 ACK
            session.onAck(Frame.Ack(ackCommand = 0x01FF, status = 0, payload = ByteArray(0)))
            session.onAck(Frame.Ack(ackCommand = 0x01A8, status = 0, payload = ByteArray(0)))
        }

        val ack = session.send(CommandBuilder.authenticate("HiLink"), 0x00A8, timeoutMs = 1000)
        assertEquals(0x01A8, ack.ackCommand)
        assertEquals(0, ack.status)
        org.junit.Assert.assertArrayEquals(CommandBuilder.authenticate("HiLink"), sent!!)
    }

    @Test
    fun `超时抛出CommandTimeoutException`() = runTest {
        val session = CommandSession(write = { /* 设备不回复 */ })
        val error = runCatching {
            session.send(CommandBuilder.readParameters(), 0x0061, timeoutMs = 50)
        }.exceptionOrNull()
        assertTrue(error is CommandTimeoutException)
    }

    @Test
    fun `ACK状态非0时原样返回由上层判断`() = runTest {
        val session = CommandSession(write = { })
        launch {
            session.onAck(Frame.Ack(ackCommand = 0x0161, status = 1, payload = ByteArray(0)))
        }
        val ack = session.send(CommandBuilder.readParameters(), 0x0061, timeoutMs = 1000)
        assertEquals(false, ack.success)
    }
}
