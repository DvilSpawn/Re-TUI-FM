package com.dvil.retui.fm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal enum class OperationState { QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED, INTERRUPTED }

internal data class OperationRecord(
    val id: String,
    val kind: String,
    val move: Boolean,
    val sourceCount: Int,
    val destination: String,
    val state: OperationState,
    val completed: Int = 0,
    val error: String? = null,
    val current: String = "",
    val startedAt: Long = 0L,
    val sources: List<String> = emptyList(),
    val completedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

internal data class OperationRequest(
    val id: String,
    val kind: String,
    val sources: List<String>,
    val destination: String,
    val move: Boolean,
    val choice: ConflictChoice,
    val named: Boolean,
    val selected: Set<String>,
    val restoreOriginal: Boolean
)

internal object OperationStore {
    private const val PREFS = "retui_fm_operations"
    private const val REQUEST_PREFIX = "request_"

    fun enqueue(
        context: Context,
        id: String,
        kind: String,
        sources: List<String>,
        destination: String,
        move: Boolean,
        choice: ConflictChoice,
        named: Boolean,
        selected: Set<String>,
        restoreOriginal: Boolean
    ) {
        val now = System.currentTimeMillis()
        put(context, OperationRecord(id, kind, move, sources.size, destination, OperationState.QUEUED, startedAt = now, sources = sources))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(REQUEST_PREFIX + id + ".sources", sources.joinToString(SOURCE_SEPARATOR))
            .putString(REQUEST_PREFIX + id + ".kind", kind)
            .putString(REQUEST_PREFIX + id + ".destination", destination)
            .putBoolean(REQUEST_PREFIX + id + ".move", move)
            .putString(REQUEST_PREFIX + id + ".choice", choice.name)
            .putBoolean(REQUEST_PREFIX + id + ".named", named)
            .putString(REQUEST_PREFIX + id + ".selected", selected.joinToString(SOURCE_SEPARATOR))
            .putBoolean(REQUEST_PREFIX + id + ".restore_original", restoreOriginal)
            .apply()
    }

    fun request(context: Context, id: String): OperationRequest? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sources = prefs.getString(REQUEST_PREFIX + id + ".sources", null)
            ?.split(SOURCE_SEPARATOR)
            ?.filter(String::isNotEmpty)
            ?: return null
        return OperationRequest(
            id = id,
            kind = prefs.getString(REQUEST_PREFIX + id + ".kind", "transfer").orEmpty(),
            sources = sources,
            destination = prefs.getString(REQUEST_PREFIX + id + ".destination", null).orEmpty(),
            move = prefs.getBoolean(REQUEST_PREFIX + id + ".move", false),
            choice = runCatching { ConflictChoice.valueOf(prefs.getString(REQUEST_PREFIX + id + ".choice", ConflictChoice.KEEP_BOTH.name).orEmpty()) }
                .getOrDefault(ConflictChoice.KEEP_BOTH),
            named = prefs.getBoolean(REQUEST_PREFIX + id + ".named", false),
            selected = prefs.getString(REQUEST_PREFIX + id + ".selected", "")
                .orEmpty().split(SOURCE_SEPARATOR).filter(String::isNotEmpty).toSet(),
            restoreOriginal = prefs.getBoolean(REQUEST_PREFIX + id + ".restore_original", false)
        )
    }

    fun all(context: Context): List<OperationRecord> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.asSequence()
            .filterNot { it.key.startsWith(REQUEST_PREFIX) }
            .mapNotNull { (id, raw) -> read(id, raw as? String) }
            .sortedByDescending { it.startedAt }
            .toList()
    }

    fun nextQueued(context: Context): OperationRecord? = all(context).firstOrNull { it.state == OperationState.QUEUED }

    fun clearFinished(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            all(context).filter { it.state != OperationState.QUEUED && it.state != OperationState.RUNNING }
                .forEach { record ->
                    remove(record.id)
                    remove(REQUEST_PREFIX + record.id + ".sources")
                    remove(REQUEST_PREFIX + record.id + ".kind")
                    remove(REQUEST_PREFIX + record.id + ".destination")
                    remove(REQUEST_PREFIX + record.id + ".move")
                    remove(REQUEST_PREFIX + record.id + ".choice")
                    remove(REQUEST_PREFIX + record.id + ".named")
                    remove(REQUEST_PREFIX + record.id + ".selected")
                    remove(REQUEST_PREFIX + record.id + ".restore_original")
                }
        }.apply()
    }

    fun put(context: Context, record: OperationRecord) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(record.id, listOf(
                record.move,
                record.kind,
                record.sourceCount,
                record.destination,
                record.state.name,
                record.completed,
                record.error.orEmpty(),
                record.current,
                record.startedAt,
                record.sources.joinToString(SOURCE_SEPARATOR),
                record.completedBytes,
                record.totalBytes
            ).joinToString("\n"))
            .apply()
    }

    fun markRunningInterrupted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.all.forEach { (id, raw) ->
            val lines = (raw as? String)?.lines().orEmpty()
            if (lines.getOrNull(4) == OperationState.RUNNING.name) {
                lines.getOrNull(3)?.let(::File)?.parentFile?.listFiles().orEmpty()
                    .filter { it.name.contains(".retui-tmp-") || it.name.startsWith(".retui-rename-") }
                    .forEach(File::deleteRecursively)
                put(context, OperationRecord(
                    id = id,
                    kind = lines.getOrNull(1).orEmpty().ifBlank { "transfer" },
                    move = lines.getOrNull(0).toBoolean(),
                    sourceCount = lines.getOrNull(2)?.toIntOrNull() ?: 0,
                    destination = lines.getOrNull(3).orEmpty(),
                    state = OperationState.INTERRUPTED,
                    completed = lines.getOrNull(5)?.toIntOrNull() ?: 0,
                    error = "Operation was interrupted before completion",
                    current = lines.getOrNull(7).orEmpty(),
                    startedAt = lines.getOrNull(8)?.toLongOrNull() ?: 0L,
                    sources = lines.getOrNull(9).orEmpty().split(SOURCE_SEPARATOR).filter(String::isNotEmpty),
                    completedBytes = lines.getOrNull(10)?.toLongOrNull() ?: 0L,
                    totalBytes = lines.getOrNull(11)?.toLongOrNull() ?: 0L
                ))
            }
        }
    }

    fun active(context: Context): OperationRecord? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.asSequence()
            .mapNotNull { (id, raw) -> read(id, raw as? String) }
            .firstOrNull { it.state == OperationState.RUNNING }
    }

    private fun read(id: String, raw: String?): OperationRecord? {
        val lines = raw?.lines().orEmpty()
        val state = lines.getOrNull(4)?.let { value -> runCatching { OperationState.valueOf(value) }.getOrNull() }
            ?: return null
        return OperationRecord(
            id = id,
            kind = lines.getOrNull(1).orEmpty().ifBlank { "transfer" },
            move = lines.getOrNull(0).toBoolean(),
            sourceCount = lines.getOrNull(2)?.toIntOrNull() ?: 0,
            destination = lines.getOrNull(3).orEmpty(),
            state = state,
            completed = lines.getOrNull(5)?.toIntOrNull() ?: 0,
            error = lines.getOrNull(6).orEmpty().ifBlank { null },
            current = lines.getOrNull(7).orEmpty(),
            startedAt = lines.getOrNull(8)?.toLongOrNull() ?: 0L,
            sources = lines.getOrNull(9).orEmpty().split(SOURCE_SEPARATOR).filter(String::isNotEmpty),
            completedBytes = lines.getOrNull(10)?.toLongOrNull() ?: 0L,
            totalBytes = lines.getOrNull(11)?.toLongOrNull() ?: 0L
        )
    }

    private const val SOURCE_SEPARATOR = "\u0001"
}

internal class OperationService : Service() {
    private var cancellation: AtomicBoolean? = null
    private var activeId: String? = null
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        OperationStore.markRunningInterrupted(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getStringExtra(EXTRA_ID)
            if (id == activeId) cancellation?.set(true)
            else if (!id.isNullOrBlank()) {
                OperationStore.put(this, OperationStore.all(this).firstOrNull { it.id == id }?.copy(
                    state = OperationState.CANCELLED,
                    error = "Cancelled before starting"
                ) ?: return START_NOT_STICKY)
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_WAKE) {
            if (worker == null && OperationStore.nextQueued(this) != null) {
                startForeground(NOTIFICATION_ID, notification("Queued operations", true, "queue"))
                worker = Thread { processQueue() }.also(Thread::start)
            }
            return START_NOT_STICKY
        }
        val operationId = intent?.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_TRANSFER
        val sources = intent.getStringArrayListExtra(EXTRA_SOURCES).orEmpty().map(::File)
        val destination = intent.getStringExtra(EXTRA_DESTINATION)?.let(::File) ?: return START_NOT_STICKY
        val move = intent.getBooleanExtra(EXTRA_MOVE, false)
        val choice = runCatching {
            ConflictChoice.valueOf(intent.getStringExtra(EXTRA_CHOICE) ?: ConflictChoice.KEEP_BOTH.name)
        }.getOrDefault(ConflictChoice.KEEP_BOTH)
        OperationStore.enqueue(
            this,
            operationId,
            kind,
            sources.map(File::getAbsolutePath),
            destination.absolutePath,
            move,
            choice,
            intent.getBooleanExtra(EXTRA_NAMED, false),
            intent.getStringArrayListExtra(EXTRA_SELECTED).orEmpty().toSet(),
            intent.getBooleanExtra(EXTRA_RESTORE_ORIGINAL, false)
        )
        if (worker == null) {
            startForeground(NOTIFICATION_ID, notification("Queued operations", true, operationId))
            worker = Thread { processQueue() }.also(Thread::start)
        }
        return START_NOT_STICKY
    }

    private fun processQueue() {
        while (true) {
            val record = OperationStore.nextQueued(this) ?: break
            val request = OperationStore.request(this, record.id) ?: run {
                OperationStore.put(this, record.copy(state = OperationState.FAILED, error = "Operation request was lost"))
                continue
            }
            val token = AtomicBoolean(false)
            cancellation = token
            activeId = record.id
            OperationStore.put(this, record.copy(state = OperationState.RUNNING, startedAt = record.startedAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
            broadcast(record.id, OperationState.RUNNING, record.completed, record.sourceCount, record.kind, "Starting…")
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Starting…", true, record.id))
            val sources = request.sources.map(::File)
            val destination = File(request.destination)
            val report = runCatching {
                when (request.kind) {
                    KIND_ZIP -> createZip(sources, destination, token, record.id)
                    KIND_EXTRACT -> extract(
                        sources.first(),
                        destination,
                        request.named,
                        request.selected,
                        token,
                        record.id
                    )
                    KIND_DELETE -> delete(sources, token, record.id)
                    KIND_TRASH -> trash(sources, token, record.id)
                    KIND_RESTORE -> restore(sources, request.choice, destination, request.restoreOriginal, token, record.id)
                    else -> TransferEngine.transfer(sources, destination, request.move, request.choice, token::get) { progress ->
                        OperationStore.put(this, OperationRecord(
                            record.id,
                            request.kind,
                            request.move,
                            progress.total,
                            destination.absolutePath,
                            OperationState.RUNNING,
                            progress.completed,
                            current = progress.current,
                            startedAt = record.startedAt,
                            sources = request.sources,
                            completedBytes = progress.completedBytes,
                            totalBytes = progress.totalBytes
                        ))
                        updateProgress(progress.current, progress.completed, progress.total, record.id, request.kind, progress.completedBytes, progress.totalBytes)
                    }.let { transfer ->
                        ServiceResult(
                            completed = transfer.completed,
                            total = sources.size,
                            cancelled = transfer.cancelled,
                            error = transfer.failures.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                            failed = transfer.failures.isNotEmpty() && transfer.completed == 0,
                            completedBytes = transfer.completedBytes,
                            totalBytes = transfer.totalBytes
                        )
                    }
                }
            }
            val failure = report.exceptionOrNull()
            if (failure != null) {
                val message = failure.message ?: "Operation failed"
                broadcast(record.id, OperationState.FAILED, 0, sources.size, request.kind, message)
                OperationStore.put(this, record.copy(state = OperationState.FAILED, error = message))
                continue
            }
            val result = report.getOrThrow()
            val completed = result.completed
            val total = result.total
            val state = when {
                result.cancelled -> OperationState.CANCELLED
                result.failed -> OperationState.FAILED
                else -> OperationState.COMPLETED
            }
            val error = result.error
            OperationStore.put(this, record.copy(
                kind = request.kind,
                move = request.move,
                sourceCount = total,
                destination = destination.absolutePath,
                state = state,
                completed = completed,
                error = error,
                current = error.orEmpty(),
                sources = request.sources,
                completedBytes = result.completedBytes,
                totalBytes = result.totalBytes
            ))
            broadcast(record.id, state, completed, total, request.kind, error ?: "", result.completedBytes, result.totalBytes)
        }
        stopForeground(true)
        stopSelf()
    }

    private data class ServiceResult(
        val completed: Int,
        val total: Int,
        val cancelled: Boolean,
        val error: String? = null,
        val failed: Boolean = false,
        val completedBytes: Long = 0L,
        val totalBytes: Long = 0L
    )

    private fun createZip(sources: List<File>, target: File, cancelled: AtomicBoolean, operationId: String): ServiceResult {
        var completed = 0
        val temporary = File(target.parentFile, ".${target.name}.retui-tmp-${UUID.randomUUID()}")
        return try {
            ZipOutputStream(FileOutputStream(temporary)).use { zip ->
                sources.forEach { source ->
                    check(!cancelled.get()) { "Operation cancelled" }
                    addToZip(zip, source, source.name, cancelled)
                    completed++
                    updateProgress(source.name, completed, sources.size, operationId)
                }
            }
            if (!temporary.renameTo(target)) throw IllegalStateException("Could not publish ${target.name}")
            ServiceResult(completed, sources.size, false)
        } catch (error: Exception) {
            temporary.deleteRecursively()
            ServiceResult(completed, sources.size, cancelled.get(), error.message, failed = !cancelled.get())
        }
    }

    private fun addToZip(zip: ZipOutputStream, file: File, entryName: String, cancelled: AtomicBoolean) {
        check(!cancelled.get()) { "Operation cancelled" }
        if (file.isDirectory) {
            val children = file.listFiles().orEmpty()
            if (children.isEmpty()) {
                zip.putNextEntry(ZipEntry("${entryName.replace(File.separatorChar, '/')}/"))
                zip.closeEntry()
            } else children.forEach { addToZip(zip, it, "$entryName/${it.name}", cancelled) }
            return
        }
        zip.putNextEntry(ZipEntry(entryName.replace(File.separatorChar, '/')))
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                check(!cancelled.get()) { "Operation cancelled" }
                val read = input.read(buffer)
                if (read < 0) break
                zip.write(buffer, 0, read)
            }
        }
        zip.closeEntry()
    }

    private fun extract(
        archive: File,
        destination: File,
        named: Boolean,
        selected: Set<String>,
        cancelled: AtomicBoolean,
        operationId: String
    ): ServiceResult {
        if (cancelled.get()) return ServiceResult(0, 1, true)
        val total = runCatching { ArchiveExtractor.entries(archive).size }.getOrDefault(1).coerceAtLeast(1)
        var completed = 0
        updateProgress("Extracting ${archive.name}", 0, total, operationId)
        return try {
            val progress: (String, Int, Int) -> Unit = { current, count, _ ->
                completed = count
                updateProgress(current, count, total, operationId)
            }
            if (named) {
                ArchiveExtractor.extractNamed(archive, selected, cancelled::get, progress)
            } else {
                ArchiveExtractor.extractTo(archive, destination, selected, cancelled::get, progress)
            }
            ServiceResult(total, total, false)
        } catch (error: Exception) {
            ServiceResult(completed, total, cancelled.get(), error.message, failed = !cancelled.get())
        }
    }

    private fun delete(sources: List<File>, cancelled: AtomicBoolean, operationId: String): ServiceResult {
        var completed = 0
        return try {
            sources.forEach { source ->
                check(!cancelled.get()) { "Operation cancelled" }
                if (source.exists() && !source.deleteRecursively()) throw IllegalStateException("Could not delete ${source.name}")
                completed++
                updateProgress(source.name, completed, sources.size, operationId)
            }
            ServiceResult(completed, sources.size, false)
        } catch (error: Exception) {
            ServiceResult(completed, sources.size, cancelled.get(), error.message, failed = !cancelled.get())
        }
    }

    private fun trash(sources: List<File>, cancelled: AtomicBoolean, operationId: String): ServiceResult {
        var completed = 0
        return try {
            sources.forEach { source ->
                check(!cancelled.get()) { "Operation cancelled" }
                val parent = source.parentFile ?: error("Cannot trash ${source.name}")
                val trash = File(parent, ".retui-trash")
                if (!trash.exists() && !trash.mkdirs()) error("Could not create trash folder")
                val target = uniqueFile(File(trash, source.name))
                if (!source.renameTo(target)) error("Could not move ${source.name} to trash")
                target.setLastModified(System.currentTimeMillis())
                rememberTrashDirectory(this, trash)
                completed++
                updateProgress(source.name, completed, sources.size, operationId)
            }
            ServiceResult(completed, sources.size, false)
        } catch (error: Exception) {
            ServiceResult(completed, sources.size, cancelled.get(), error.message, failed = !cancelled.get())
        }
    }

    private fun restore(
        sources: List<File>,
        choice: ConflictChoice,
        destination: File,
        restoreOriginal: Boolean,
        cancelled: AtomicBoolean,
        operationId: String
    ): ServiceResult {
        var completed = 0
        return try {
            sources.forEach { source ->
                check(!cancelled.get()) { "Operation cancelled" }
                val original = if (restoreOriginal) {
                    restoreTarget(source) ?: error("Could not find original folder for ${source.name}")
                } else {
                    require(destination.isDirectory || destination.mkdirs()) { "Could not create restore destination" }
                    File(destination, source.name)
                }
                val target = when (choice) {
                    ConflictChoice.KEEP_BOTH -> uniqueFile(original)
                    ConflictChoice.SKIP -> if (original.exists()) null else original
                    ConflictChoice.REPLACE -> {
                        if (original.exists() && !original.deleteRecursively()) error("Could not replace ${original.name}")
                        original
                    }
                }
                if (target != null && !source.renameTo(target)) error("Could not restore ${source.name}")
                completed++
                updateProgress(source.name, completed, sources.size, operationId)
            }
            ServiceResult(completed, sources.size, false)
        } catch (error: Exception) {
            ServiceResult(completed, sources.size, cancelled.get(), error.message, failed = !cancelled.get())
        }
    }

    private fun restoreTarget(file: File): File? {
        var targetDir = file.parentFile ?: return null
        while (targetDir.name == ".retui-trash") targetDir = targetDir.parentFile ?: return null
        return File(targetDir, file.name)
    }

    private fun uniqueFile(base: File): File {
        var target = base
        var suffix = 1
        while (target.exists()) target = File(base.parentFile, "${base.name}.$suffix").also { suffix++ }
        return target
    }

    private fun updateProgress(
        current: String,
        completed: Int,
        total: Int,
        id: String,
        kind: String = KIND_TRANSFER,
        completedBytes: Long = 0L,
        totalBytes: Long = 0L
    ) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("$completed/$total  $current", true, id))
        broadcast(id, OperationState.RUNNING, completed, total, kind, current, completedBytes, totalBytes)
    }

    private fun broadcast(
        id: String,
        state: OperationState,
        completed: Int,
        total: Int,
        kind: String,
        message: String,
        completedBytes: Long = 0L,
        totalBytes: Long = 0L
    ) {
        sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_STATE, state.name)
            putExtra(EXTRA_COMPLETED, completed)
            putExtra(EXTRA_TOTAL, total)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_COMPLETED_BYTES, completedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
        })
    }

    private fun notification(text: String, ongoing: Boolean, operationId: String): Notification {
        val cancel = PendingIntent.getService(
            this, operationId.hashCode(), Intent(this, OperationService::class.java).setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, operationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (activeId?.let { operationId == it } == true) "Re:TUI Files operation" else "Re:TUI Files")
            .setContentText(text)
            .setOngoing(ongoing)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "File operations", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        cancellation?.set(true)
        worker = null
        cancellation = null
        activeId = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PROGRESS = "com.dvil.retui.fm.OPERATION_PROGRESS"
        const val ACTION_CANCEL = "com.dvil.retui.fm.OPERATION_CANCEL"
        const val EXTRA_ID = "operation_id"
        const val EXTRA_SOURCES = "operation_sources"
        const val EXTRA_DESTINATION = "operation_destination"
        const val EXTRA_MOVE = "operation_move"
        const val EXTRA_CHOICE = "operation_choice"
        const val EXTRA_KIND = "operation_kind"
        const val EXTRA_NAMED = "operation_named"
        const val EXTRA_SELECTED = "operation_selected"
        const val EXTRA_RESTORE_ORIGINAL = "operation_restore_original"
        const val EXTRA_STATE = "operation_state"
        const val EXTRA_COMPLETED = "operation_completed"
        const val EXTRA_TOTAL = "operation_total"
        const val EXTRA_MESSAGE = "operation_message"
        const val EXTRA_COMPLETED_BYTES = "operation_completed_bytes"
        const val EXTRA_TOTAL_BYTES = "operation_total_bytes"
        const val ACTION_WAKE = "com.dvil.retui.fm.OPERATION_WAKE"
        private const val CHANNEL_ID = "retui_file_operations"
        private const val NOTIFICATION_ID = 9024
        private const val KIND_TRANSFER = "transfer"
        const val KIND_ZIP = "zip"
        const val KIND_EXTRACT = "extract"
        const val KIND_DELETE = "delete"
        const val KIND_TRASH = "trash"
        const val KIND_RESTORE = "restore"
    }
}
