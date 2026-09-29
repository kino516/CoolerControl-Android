package com.example.kernelsustyleuikit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.CcGraph
import com.example.kernelsustyleuikit.templateApp
import com.example.kernelsustyleuikit.ui.screen.cooling.CoolingFanUi
import com.example.kernelsustyleuikit.ui.screen.cooling.CoolingSection
import com.example.kernelsustyleuikit.ui.screen.cooling.CoolingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 冷却页 ViewModel：模式切换、风扇曲线、风扇控制、设备重命名、压力测试。
 *
 * 与主页一致：无参构造 + [CcGraph] 取依赖。
 */
class CoolingViewModel : ViewModel() {

    private val repo = CcGraph.repository
    private val prefs = CcGraph.prefs
    private val session = CcGraph.session

    private val _uiState = MutableStateFlow(CoolingUiState())
    val uiState: StateFlow<CoolingUiState> = _uiState.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val runningStress = MutableStateFlow<Set<String>>(emptySet())
    private val selectedGpuId = MutableStateFlow<String?>(null)
    private val selectedDriveId = MutableStateFlow<String?>(null)
    private val selectedStressKind = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { repo.devices.collect { rebuild() } }
        viewModelScope.launch { repo.latestStatus.collect { rebuild() } }
        viewModelScope.launch { repo.profiles.collect { rebuild() } }
        viewModelScope.launch { repo.modes.collect { rebuild() } }
        viewModelScope.launch { repo.activeModeUid.collect { rebuild() } }
        viewModelScope.launch { repo.channelProfileUids.collect { rebuild() } }
        viewModelScope.launch { runningStress.collect { rebuild() } }

        viewModelScope.launch {
            if (session.state.value.isConnected) repo.refreshAll()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repo.refreshAll()
            // 压力测试目标与电源模式都不常变，跟着页面刷新一起拉
            repo.refreshStressTargets()
            repo.refreshPowerProfiles()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setEditMode(enabled: Boolean) {
        _editMode.value = enabled
    }

    // ---------- 区块管理 ----------

    fun setSectionOrder(order: List<String>) {
        prefs.coolingSections = order
        rebuild()
    }

    fun removeSection(id: String) {
        prefs.coolingSections = currentSections().filterNot { it == id }
        rebuild()
    }

    fun addSection(id: String) {
        val current = currentSections()
        if (id in current) return
        prefs.coolingSections = current + id
        rebuild()
    }

    fun availableSections(): List<Pair<String, String>> {
        val shown = currentSections().toSet()
        return CoolingSection.all.filterNot { it in shown }.map { it to sectionTitle(it) }
    }

    private fun currentSections(): List<String> =
        // 过滤掉已迁移到监控页的旧区块 id（例如 rename）
        prefs.coolingSections.filter { it in CoolingSection.all }.ifEmpty { CoolingSection.all }

    private fun sectionTitle(id: String): String = when (id) {
        CoolingSection.MODES -> templateApp.getString(R.string.cc_section_modes)
        CoolingSection.PROFILES -> templateApp.getString(R.string.cc_section_profiles)
        CoolingSection.FANS -> templateApp.getString(R.string.cc_section_fans)
        CoolingSection.STRESS -> templateApp.getString(R.string.cc_section_stress)
        CoolingSection.POWER -> templateApp.getString(R.string.cc_section_power)
        else -> id
    }

    // ---------- 曲线显隐 ----------

    fun toggleCurveVisibility(profileUid: String) {
        val current = prefs.hiddenCurves
        prefs.hiddenCurves = if (profileUid in current) current - profileUid else current + profileUid
        rebuild()
    }

    fun setHiddenCurves(ids: Set<String>) {
        prefs.hiddenCurves = ids
        rebuild()
    }

    // ---------- 风扇显隐 ----------

    fun setHiddenFans(keys: Set<String>) {
        prefs.hiddenFans = keys
        rebuild()
    }

    // ---------- 操作 ----------

    fun switchMode(modeUid: String) {
        viewModelScope.launch {
            repo.setActiveMode(modeUid).onFailure { _message.value = it.message }
        }
    }

    fun applyManual(deviceUid: String, channel: String, speed: Int) {
        viewModelScope.launch {
            repo.setManual(deviceUid, channel, speed).onFailure { _message.value = it.message }
        }
    }

    fun selectProfile(deviceUid: String, channel: String, profileUid: String) {
        viewModelScope.launch {
            repo.setProfile(deviceUid, channel, profileUid).onFailure { _message.value = it.message }
        }
    }

    fun restoreCurve(deviceUid: String, channel: String) {
        viewModelScope.launch {
            repo.restoreCurve(deviceUid, channel).onFailure { _message.value = it.message }
        }
    }

    fun renameDevice(deviceUid: String, name: String?) {
        viewModelScope.launch {
            repo.renameDevice(deviceUid, name).onFailure { _message.value = it.message }
        }
    }

    fun renameChannel(deviceUid: String, channel: String, label: String?) {
        viewModelScope.launch {
            repo.renameChannel(deviceUid, channel, label).onFailure { _message.value = it.message }
        }
    }

    fun startStress(kind: String) {
        viewModelScope.launch {
            // drive 必须给出设备路径（daemon 会以 400 拒绝空值）；
            // gpu 未选定具体目标时省略 gpu_id，表示对所有 GPU 施压
            val devicePath = if (kind == STRESS_DRIVE) {
                selectedDriveId.value ?: repo.stressDrives.value.firstOrNull()?.id
            } else {
                null
            }
            if (kind == STRESS_DRIVE && devicePath == null) {
                _message.value = templateApp.getString(R.string.cc_stress_no_drive)
                return@launch
            }

            repo.startStressTest(
                kind = kind,
                gpuId = if (kind == STRESS_GPU) selectedGpuId.value else null,
                devicePath = devicePath,
            )
                .onSuccess { runningStress.value = runningStress.value + kind }
                .onFailure { _message.value = it.message }
        }
    }

    fun selectStressGpu(id: String?) {
        selectedGpuId.value = id
        rebuild()
    }

    fun selectStressDrive(id: String?) {
        selectedDriveId.value = id
        rebuild()
    }

    /**
     * 选中压力测试类型（**不启动**）。
     *
     * 拆成「选中 → 开始测试」两步：压力测试会把 CPU / 磁盘真正跑满，
     * 误触的代价不小，所以不再点一下就下发。再次点击同一项可取消选中。
     */
    fun selectStressKind(kind: String) {
        selectedStressKind.value = if (selectedStressKind.value == kind) null else kind
        rebuild()
    }

    fun stopStress(kind: String) {
        viewModelScope.launch {
            repo.stopStressTest(kind)
                .onSuccess { runningStress.value = runningStress.value - kind }
                .onFailure { _message.value = it.message }
        }
    }

    fun stopAllStress() {
        viewModelScope.launch {
            // 逐个停止并检查结果：压力测试会让 CPU / 磁盘满载，
            // 若停止失败仍把状态清空，界面显示「已停止」而机器还在跑。
            val failed = runningStress.value.filter { kind ->
                repo.stopStressTest(kind).isFailure
            }
            if (failed.isEmpty()) {
                runningStress.value = emptySet()
            } else {
                // 保留失败项，让用户看到它们仍在运行，而不是给出虚假的「已停止」
                runningStress.value = failed.toSet()
                _message.value = "以下压力测试未能停止：${failed.joinToString("、")}"
            }
        }
    }

    // ---------- 内部 ----------

    private fun rebuild() {
        val devices = repo.devices.value
        val deviceNames = devices.associate { it.uid to it.name }
        val channelLabels = buildMap {
            devices.forEach { device ->
                device.channels.keys.forEach { channel ->
                    put("${device.uid}|$channel", device.channelDisplayName(channel))
                }
            }
        }

        val fans = buildList {
            devices.forEach { device ->
                device.channels.forEach { (channel, info) ->
                    // 只列出可调速的通道
                    if (info.speedOptions == null) return@forEach
                    add(
                        CoolingFanUi(
                            deviceUid = device.uid,
                            channel = channel,
                            deviceName = device.name,
                            channelLabel = device.channelDisplayName(channel),
                            rpm = repo.latestRpm(device.uid, channel),
                            duty = repo.latestDuty(device.uid, channel),
                            speedOptions = info.speedOptions,
                            profileName = repo.profileByUid(repo.boundProfileUid(device.uid, channel))?.name,
                        )
                    )
                }
            }
        }

        _uiState.value = CoolingUiState(
            modes = repo.modes.value,
            activeModeUid = repo.activeModeUid.value,
            profiles = repo.profiles.value,
            hiddenCurves = prefs.hiddenCurves,
            fans = fans,
            hiddenFans = prefs.hiddenFans,
            sections = currentSections(),
            editMode = _editMode.value,
            runningStress = runningStress.value,
            stressGpus = repo.stressGpus.value,
            stressDrives = repo.stressDrives.value,
            stressGpuId = selectedGpuId.value,
            stressDriveId = selectedDriveId.value,
            selectedStressKind = selectedStressKind.value,
            powerProfiles = repo.powerProfiles.value,
            deviceNames = deviceNames,
            channelLabels = channelLabels,
        )
    }

    private companion object {
        const val STRESS_GPU = "gpu"
        const val STRESS_DRIVE = "drive"
    }
}
