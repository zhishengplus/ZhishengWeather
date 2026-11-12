package com.zhisheng.weather.data.badge

import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.MessageDigest

internal fun badgeBackupDigest(code: String): String = MessageDigest.getInstance("SHA-256")
    .digest(("badge-backup-confirm-v1:" + code).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal class BadgeRecoveryBackup(val entries: List<Entry>, val createdAt: Long = System.currentTimeMillis()) {
    class Entry(val name: String, val serial: Long, val publicId: String?, val code: String) {
        override fun toString() = "RecoveryEntry(serial=$serial, code=[redacted])"
    }
    override fun toString() = "BadgeRecoveryBackup(count=${entries.size})"
    fun fileText(): String = buildString {
        appendLine("枳生天气 · 徽章恢复码备份")
        appendLine("保存时间：" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(createdAt)))
        appendLine("共 ${entries.size} 枚。每枚使用各自的 ZB1 恢复码，通过「转入一枚徽章」逐枚恢复。")
        appendLine("持有码的人可以转走对应徽章，请勿公开分享。转入后旧码失效；新增、转入徽章后请重新备份。")
        for ((index, entry) in entries.withIndex()) {
            appendLine(); appendLine("${index + 1}. ${entry.name} · NO.${entry.serial.toString().padStart(6, '0')}")
            entry.publicId?.takeIf { it.isNotBlank() }?.let { appendLine("纪念 / 自定义 ID：$it") }
            appendLine(entry.code)
        }
    }
}

/** All codes must be prepared before a document is opened. Success includes flush and close. */
internal fun writeBadgeRecoveryBackup(backup: BadgeRecoveryBackup, open: () -> OutputStream?) {
    require(backup.entries.isNotEmpty())
    val bytes = backup.fileText().toByteArray(Charsets.UTF_8)
    try { (open() ?: throw IOException("Backup destination unavailable")).use { it.write(bytes); it.flush() } }
    finally { bytes.fill(0) }
}
