package com.dormradar.app.core.device

import com.dormradar.app.core.protocol.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** 命令超时（能力探测时上层捕获后按"不支持"降级处理） */
class CommandTimeoutException(val commandWord: Int) :
    Exception("命令 0x%04X 等待 ACK 超时".format(commandWord))

/**
 * 命令会话：串行发送命令并等待匹配的 ACK。
 *
 * - ACK 匹配规则：ackCommand == 原命令字 | 0x0100（docs/03 §5.4）；
 * - 同一时刻只允许一条命令在途（Mutex 串行，符合协议约束）；
 * - 纯 Kotlin（仅协程），可单测。
 */
class CommandSession(
    private val write: suspend (ByteArray) -> Unit,
    private val defaultTimeoutMs: Long = 2_000,
) {
    private val mutex = Mutex()
    private val acks = Channel<Frame.Ack>(Channel.UNLIMITED)

    /** 收到设备 ACK 时由连接层回调 */
    fun onAck(ack: Frame.Ack) {
        acks.trySend(ack)
    }

    /**
     * 发送命令并等待对应 ACK。
     * @param commandWord 原命令字（如 0x00A8），用于匹配 ACK（0x01A8）
     * @throws CommandTimeoutException 超时未收到匹配 ACK
     */
    suspend fun send(
        command: ByteArray,
        commandWord: Int,
        timeoutMs: Long = defaultTimeoutMs,
    ): Frame.Ack = mutex.withLock {
        drainStale()
        write(command)
        awaitAck(commandWord, timeoutMs)
    }

    /** 等待匹配的 ACK，期间丢弃其他命令的陈旧 ACK */
    private suspend fun awaitAck(commandWord: Int, timeoutMs: Long): Frame.Ack {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            val ack = withTimeoutOrNull(remainingMs.coerceAtLeast(1)) { acks.receive() }
                ?: throw CommandTimeoutException(commandWord)
            if (ack.ackCommand == (commandWord or 0x0100)) return ack
            // 非本次命令的 ACK（陈旧帧）：丢弃并继续等
        }
    }

    /** 丢弃上一次连接/命令可能残留的 ACK，避免串扰 */
    private fun drainStale() {
        while (true) {
            val result = acks.tryReceive()
            if (result.isFailure) break
        }
    }
}
