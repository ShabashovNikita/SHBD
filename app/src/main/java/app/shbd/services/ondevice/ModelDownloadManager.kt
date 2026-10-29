package app.shbd.services.ondevice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.shbd.R
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

sealed class OnDeviceDownloadState {
    object NotDownloaded : OnDeviceDownloadState()
    data class Downloading(val progressPercent: Int) : OnDeviceDownloadState()
    /** Queued: the required network (Wi-Fi for "Wi-Fi only") is not available yet. */
    data class WaitingForNetwork(val progressPercent: Int) : OnDeviceDownloadState()
    object Verifying : OnDeviceDownloadState()
    object Downloaded : OnDeviceDownloadState()
    data class Failed(val message: String) : OnDeviceDownloadState()
}

/**
 * Orchestrates the on-device model download via [ModelDownloadWorker] and
 * exposes its live state. The actual network/verify/atomic-rename work lives
 * in the worker so it survives process death; this class is just the
 * WorkManager-facing handle used by Settings UI.
 */
class ModelDownloadManager(private val context: Context) {
    private val _storageEpoch = MutableStateFlow(0)
    /** Bumps when files are deleted so Settings can refresh leftover size. */
    val storageEpoch: StateFlow<Int> = _storageEpoch.asStateFlow()

    fun modelFile(entry: OnDeviceModelEntry = ModelCatalog.default): File =
        File(modelsDir(), entry.filename)

    fun isDownloaded(entry: OnDeviceModelEntry = ModelCatalog.default): Boolean =
        modelFile(entry).exists()

    fun modelsDir(): File = File(context.filesDir, "models")

    fun occupiedBytes(): Long =
        ModelDownloadStorage.occupiedBytes(modelsDir(), context.cacheDir)

    fun startDownload(entry: OnDeviceModelEntry, overWifiOnly: Boolean) {
        ModelDownloadWorker.enqueue(context, entry, overWifiOnly)
    }

    fun cancelDownload() {
        WorkManager.getInstance(context).cancelUniqueWork(ModelDownloadWorker.UNIQUE_NAME)
    }

    fun delete(entry: OnDeviceModelEntry = ModelCatalog.default): Boolean {
        cancelDownload()
        val file = modelFile(entry)
        val partFile = File(modelsDir(), "${entry.filename}.part")
        partFile.delete()
        ModelDownloadHasher.deleteSidecar(partFile)
        val gone = !file.exists() || file.delete()
        _storageEpoch.value++
        return gone
    }

    /**
     * Deletes files of models the app no longer offers (E4B) without touching
     * a running download of the embedded model.
     */
    fun deleteRetiredModels() {
        val retired = ModelCatalog.allKnown - ModelCatalog.entries.toSet()
        for (entry in retired) {
            val partFile = File(modelsDir(), "${entry.filename}.part")
            partFile.delete()
            ModelDownloadHasher.deleteSidecar(partFile)
            modelFile(entry).delete()
        }
        _storageEpoch.value++
    }

    /** Wipes every model file, leftover `.part`, sidecar, and LiteRT cache. */
    fun deleteAll() {
        cancelDownload()
        ModelDownloadStorage.deleteAll(modelsDir(), context.cacheDir)
        _storageEpoch.value++
    }

    /** Live download state for [modelId], driven by [ModelDownloadWorker] + WorkManager. */
    fun state(modelId: String): Flow<OnDeviceDownloadState> {
        val entry = ModelCatalog.forModelId(modelId)
        // storageEpoch re-maps after a delete: WorkManager does not re-emit for file changes.
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME)
            .combine(storageEpoch) { infos, _ -> mapWorkState(infos.firstOrNull(), entry) }
    }

    private fun mapWorkState(info: WorkInfo?, entry: OnDeviceModelEntry): OnDeviceDownloadState {
        val activeVersion = info?.tags
            ?.firstOrNull { it.startsWith("model:") }
            ?.removePrefix("model:")
        val workMatchesEntry = activeVersion == null || activeVersion == entry.version
        val part = File(modelsDir(), "${entry.filename}.part")
        val partLength = part.length()
        return ModelDownloadPolicy.mapState(
            workState = info?.state,
            workMatchesEntry = workMatchesEntry,
            workProgressPercent = info?.progress?.getInt(ModelDownloadWorker.PROGRESS_PERCENT, 0) ?: 0,
            partProgress = ModelDownloadPolicy.partProgress(partLength, entry.sizeBytes),
            partComplete = ModelDownloadPolicy.isComplete(partLength, entry.sizeBytes),
            isDownloaded = isDownloaded(entry),
            failureReason = info?.outputData?.getString(ModelDownloadWorker.FAILURE_REASON),
            defaultFailure = context.getString(R.string.on_device_download_failed),
            networkUnavailable = info?.state == WorkInfo.State.ENQUEUED &&
                !networkAvailable(wifiOnly = ModelDownloadWorker.WIFI_ONLY_TAG in info.tags),
        )
    }

    /** Whether the network the queued download needs is up right now. */
    private fun networkAvailable(wifiOnly: Boolean): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
