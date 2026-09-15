const TOKEN_KEY = "AIOT_ADMIN_TOKEN";
let currentWorkbench = null;

function getBearerToken() {
  const token = (localStorage.getItem(TOKEN_KEY) || "").trim();
  if (!token) {
    throw new Error("请先保存 Token");
  }
  return token.startsWith("Bearer ") ? token : `Bearer ${token}`;
}

async function httpGet(url) {
  const resp = await fetch(url, {
    headers: { Authorization: getBearerToken() }
  });
  if (!resp.ok) {
    throw new Error(`请求失败: ${resp.status}`);
  }
  return resp.json();
}

function currentHomeId() {
  const selector = document.getElementById("homeSelector");
  return selector ? (selector.value || "").trim() : "";
}

function resolveHomeId(inputId) {
  const input = document.getElementById(inputId);
  const typed = input ? (input.value || "").trim() : "";
  return typed || currentHomeId();
}

function renderStats(data) {
  const items = [
    ["家庭数", data.homeCount || 0],
    ["成员数", data.memberCount || 0],
    ["产品数", data.productCount || 0],
    ["设备数", data.deviceCount || 0],
    ["OTA任务数", data.otaTaskCount || 0],
    ["今日告警", data.todayAlarmCount || 0],
    ["待处理工单", data.pendingWorkOrderCount || 0],
    ["一次修复率", data.oneTimeResolveRate || 0],
    ["AI报告就绪", data.aiEvalReadyCount || 0],
    ["AI门禁通过", data.aiEvalPassedCount || 0],
    ["AI最近生成", formatDateTime(data.aiEvalLastGeneratedAt)],
    ["AI请求模式", data.aiPersistenceConfiguredReadMode || data.aiPersistenceReadMode || "-"],
    ["AI生效模式", data.aiPersistenceReadMode || "-"],
    ["AI MySQL就绪", data.aiPersistenceMysqlReady ? "是" : "否"],
    ["AI切读就绪", data.aiPersistenceMysqlCutoverReady ? "是" : "否"],
    ["AI切读阻断", data.aiPersistenceMysqlCutoverBlockReason || "-"],
    ["AI回填存在", data.aiPersistenceBackfillManifestExists ? "是" : "否"],
    ["AI回填写入", data.aiPersistenceBackfillWrittenTotal || 0],
    ["AI一致性报告", data.aiPersistenceConsistencyReportExists ? "是" : "否"],
    ["AI一致性通过", data.aiPersistenceConsistencyPassed ? "是" : "否"],
    ["AI一致性差异", data.aiPersistenceConsistencyTotalMismatch || 0],
    ["AI约束报告", data.aiPersistenceConstraintsReportExists ? "是" : "否"],
    ["AI约束通过", data.aiPersistenceConstraintsGatePassed ? "是" : "否"],
    ["AI约束失败数", data.aiPersistenceConstraintsFailedCount || 0],
    ["AI迁移门禁", data.aiPersistenceMigrationGatePassed ? "通过" : "未通过"],
    ["AI控制面去Redis完成", data.aiPersistenceControlPlaneRedisDrainCompleted ? "是" : "否"],
    ["AI业务活体验证", formatNullableBoolean(data.aiPersistenceBusinessLiveFlowSuccess)],
    ["AI业务验证时间", formatDateTime(data.aiPersistenceBusinessLiveFlowVerifiedAt)],
    ["AI业务验证场景", data.aiPersistenceBusinessLiveFlowScene || "-"],
    ["AI业务验证全局设备ID", data.aiPersistenceBusinessLiveFlowGlobalDeviceId || data.aiPersistenceBusinessLiveFlowDeviceId || "-"],
    ["AI业务验证认证标识", data.aiPersistenceBusinessLiveFlowAuthIdentity || "-"],
    ["AI业务验证设备SN", data.aiPersistenceBusinessLiveFlowDeviceSn || "-"],
    ["AI业务验证旧设备ID", data.aiPersistenceBusinessLiveFlowDeviceId || "-"],
    ["AI业务验证事件", data.aiPersistenceBusinessLiveFlowEventId || "-"],
    ["AI业务诊断ID", data.aiPersistenceBusinessLiveFlowDiagnosisId || "-"],
    ["AI业务反馈ID", data.aiPersistenceBusinessLiveFlowFeedbackId || "-"],
    ["AI业务案例ID", data.aiPersistenceBusinessLiveFlowCaseId || "-"],
    ["AI业务报告存在", formatNullableBoolean(data.aiPersistenceBusinessLiveFlowReportExists)],
    ["AI业务报告路径", data.aiPersistenceBusinessLiveFlowReportPath || "-"],
    ["AI业务MySQL写补偿", formatNullableNumber(data.aiPersistenceBusinessLiveFlowMysqlWriteOutboxCount)],
    ["AI业务案例补偿", formatNullableNumber(data.aiPersistenceBusinessLiveFlowCaseMaterializationTaskCount)],
    ["AI业务诊断镜像", formatNullableBoolean(data.aiPersistenceBusinessLiveFlowDiagnosisMirrorExists)],
    ["AI业务反馈镜像", formatNullableBoolean(data.aiPersistenceBusinessLiveFlowFeedbackMirrorExists)],
    ["AI业务案例镜像", formatNullableBoolean(data.aiPersistenceBusinessLiveFlowCaseMirrorExists)],
    ["AI最近Drain时间", formatDateTime(data.aiPersistenceControlPlaneLastDrainAt)],
    ["AI最近Drain执行人", data.aiPersistenceControlPlaneLastDrainOperator || "-"],
    ["AI最近Drain模式", formatDrainMode(data.aiPersistenceControlPlaneLastDrainDryRun)],
    ["AI最近Drain接受", data.aiPersistenceControlPlaneLastDrainAccepted ? "是" : "否"],
    ["AI最近Drain信息", data.aiPersistenceControlPlaneLastDrainMessage || "-"],
    ["AI Drain报告存在", data.aiPersistenceControlPlaneDrainReportExists ? "是" : "否"],
    ["AI Drain报告来源", data.aiPersistenceControlPlaneDrainReportSource || "-"],
    ["AI Drain报告时间", formatDateTime(data.aiPersistenceControlPlaneDrainReportExecutedAt)],
    ["AI Drain报告执行人", data.aiPersistenceControlPlaneDrainReportOperator || "-"],
    ["AI Drain报告模式", formatDrainMode(data.aiPersistenceControlPlaneDrainReportDryRun)],
    ["AI Drain报告接受", formatNullableBoolean(data.aiPersistenceControlPlaneDrainReportAccepted)],
    ["AI Drain报告批次", formatNullableNumber(data.aiPersistenceControlPlaneDrainReportBatchSize)],
    ["AI Drain报告Stores", data.aiPersistenceControlPlaneDrainReportRequestedStores || "-"],
    ["AI Drain报告路径", data.aiPersistenceControlPlaneDrainReportPath || "-"],
    ["AI Drain报告信息", data.aiPersistenceControlPlaneDrainReportMessage || "-"],
    ["AI写补偿报告Redis待迁", formatNullableNumber(data.aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisPending)],
    ["AI写补偿报告MySQL写入", formatNullableNumber(data.aiPersistenceControlPlaneDrainReportMysqlWriteOutboxMysqlWritten)],
    ["AI写补偿报告Redis剩余", formatNullableNumber(data.aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisRemaining)],
    ["AI案例报告Redis待迁", formatNullableNumber(data.aiPersistenceControlPlaneDrainReportCaseMaterializationRedisPending)],
    ["AI案例报告MySQL写入", formatNullableNumber(data.aiPersistenceControlPlaneDrainReportCaseMaterializationMysqlWritten)],
    ["AI案例报告Redis剩余", formatNullableNumber(data.aiPersistenceControlPlaneDrainReportCaseMaterializationRedisRemaining)],
    ["AI写补偿后端", data.aiPersistenceMysqlWriteOutboxStoreMode || "-"],
    ["AI写补偿Redis遗留", data.aiPersistenceMysqlWriteOutboxLegacyRedisPendingCount || 0],
    ["AI写补偿重放", data.aiPersistenceMysqlWriteOutboxReplayEnabled ? "开" : "关"],
    ["AI写补偿批次", data.aiPersistenceMysqlWriteOutboxReplayBatchSize || 0],
    ["AI写补偿延迟ms", data.aiPersistenceMysqlWriteOutboxReplayFixedDelayMs || 0],
    ["AI写补偿积压", data.aiPersistenceMysqlWriteOutboxPendingCount || 0],
    ["AI补偿最老秒数", data.aiPersistenceMysqlWriteOutboxOldestAgeSeconds || 0],
    ["AI案例补偿后端", data.aiPersistenceCaseMaterializationStoreMode || "-"],
    ["AI案例Redis遗留", data.aiPersistenceCaseMaterializationLegacyRedisPendingCount || 0],
    ["AI案例重放", data.aiPersistenceCaseMaterializationReplayEnabled ? "开" : "关"],
    ["AI案例批次", data.aiPersistenceCaseMaterializationReplayBatchSize || 0],
    ["AI案例延迟ms", data.aiPersistenceCaseMaterializationReplayFixedDelayMs || 0],
    ["AI案例补偿积压", data.aiCaseMaterializationPendingCount || 0],
    ["AI案例最老秒数", data.aiCaseMaterializationOldestAgeSeconds || 0]
  ];
  const root = document.getElementById("statsCards");
  root.innerHTML = items
    .map(([label, value]) => `<div class="card"><div class="label">${escapeHtml(label)}</div><div class="value">${escapeHtml(value)}</div></div>`)
    .join("");
}

function statusClass(status) {
  if (status === "ATTENTION") {
    return "attention";
  }
  if (status === "OPTIONAL") {
    return "optional";
  }
  return "";
}

function statusLabel(status) {
  if (status === "ATTENTION") {
    return "待补齐";
  }
  if (status === "OPTIONAL") {
    return "可选";
  }
  return "已就绪";
}

function renderHomeSelector(homes, selectedHomeId) {
  const selector = document.getElementById("homeSelector");
  const list = homes || [];
  selector.innerHTML = list.length
    ? list.map((home) => {
        const id = escapeHtml(home.id || "");
        const name = escapeHtml(home.name || home.homeName || home.id || "-");
        const selected = String(home.id || "") === String(selectedHomeId || "") ? " selected" : "";
        return `<option value="${id}"${selected}>${name}</option>`;
      }).join("")
    : `<option value="">暂无家庭</option>`;
}

function renderWorkbenchSummary(data) {
  const root = document.getElementById("workbenchSummaryCards");
  const overview = data.overview || {};
  const items = [
    ["当前家庭", data.selectedHomeId || "-"],
    ["成员协同", `${(data.members || []).length} 人`],
    ["产品建模", `${(data.products || []).length} 个`],
    ["设备纳管", `${(data.devices || []).length} 台`],
    ["OTA运营", `${(data.otaTasks || []).length} 单`],
    ["运营待办", `${overview.pendingWorkOrderCount || 0} 单`]
  ];
  root.innerHTML = items
    .map(([label, value]) => `<div class="card"><div class="label">${escapeHtml(label)}</div><div class="summary-number">${escapeHtml(value)}</div></div>`)
    .join("");
}

function renderClosureStages(stages) {
  const root = document.getElementById("closureStageCards");
  root.innerHTML = (stages || [])
    .map((stage) => `<div class="card stage-card ${statusClass(stage.status)}">
      <div class="label">${escapeHtml(stage.stageName || "-")}</div>
      <div class="value">${escapeHtml(statusLabel(stage.status))}</div>
      <div class="hint">${escapeHtml(stage.summary || "-")}</div>
      <div class="hint">${escapeHtml(stage.actionHint || "-")}</div>
    </div>`)
    .join("");
}

function renderSnapshotPanels(data) {
  const ops = data.opsOverview || {};
  const aiEvalReports = data.aiEvalReports || [];
  const members = data.members || [];
  const devices = data.devices || [];
  const otaTasks = data.otaTasks || [];
  const panels = [
    {
      title: "家庭与成员",
      items: members.length
        ? members.slice(0, 5).map((member) => `${member.nickname || member.name || member.id || "-"} (${member.role || "member"})`)
        : ["当前家庭暂无成员数据"]
    },
    {
      title: "设备与状态",
      items: devices.length
        ? devices.slice(0, 5).map((device) => `${device.deviceName || device.id || "-"} / ${statusText(device.status)}`)
        : ["当前家庭暂无设备"]
    },
    {
      title: "OTA 与运营",
      items: otaTasks.length
        ? otaTasks.slice(0, 5).map((task) => `${task.taskId || "-"} / ${otaStatusText(task.status)} / ${task.targetVersion || "-"}`)
        : [`今日告警 ${ops.todayAlarmCount || 0} / 待处理工单 ${ops.pendingWorkOrderCount || 0}`]
    },
    {
      title: "AI 门禁",
      items: aiEvalReports.length
        ? aiEvalReports.map((item) => `${item.sceneType || "-"} / ${item.gatePassed ? "通过" : "未通过"}`)
        : ["暂无 AI 门禁结果"]
    }
  ];
  document.getElementById("snapshotPanels").innerHTML = panels
    .map((panel) => `<div class="card snapshot-card">
      <h3>${escapeHtml(panel.title)}</h3>
      <ul class="snapshot-list">${panel.items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
    </div>`)
    .join("");
}

function renderWorkbench(data) {
  currentWorkbench = data || null;
  renderHomeSelector(data.homes || [], data.selectedHomeId);
  renderWorkbenchSummary(data);
  renderClosureStages(data.closureStages || []);
  renderSnapshotPanels(data);
  renderStats(data.overview || {});
  renderAiEvalTable(data.aiEvalReports || [], "默认展示工作台闭环场景的 AI 门禁结果");
  document.getElementById("closureScoreBadge").textContent = `闭环评分 ${data.closureScore || 0}`;
  document.getElementById("selectedHomeHint").textContent = `selectedHomeId=${data.selectedHomeId || "-"}`;
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(Number(value));
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString("zh-CN", { hour12: false });
}

function formatDrainMode(value) {
  if (value === undefined || value === null) {
    return "-";
  }
  return value ? "DRY_RUN" : "APPLY";
}

function formatNullableBoolean(value) {
  if (value === undefined || value === null) {
    return "-";
  }
  return value ? "是" : "否";
}

function formatNullableNumber(value) {
  if (value === undefined || value === null) {
    return "-";
  }
  return value;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatHistoryResult(item) {
  if (item.success !== undefined && item.success !== null) {
    return item.success ? "成功" : "失败";
  }
  if (item.accepted !== undefined && item.accepted !== null) {
    return item.accepted ? "接受" : "拒绝";
  }
  return "-";
}

function formatHistoryMode(item) {
  if (item.dryRun === undefined || item.dryRun === null) {
    return "-";
  }
  return item.dryRun ? "DRY_RUN" : "APPLY";
}

function buildHistoryDetailButton(item) {
  const reportType = item.reportType === undefined || item.reportType === null ? "" : String(item.reportType);
  const occurredAt = item.occurredAt === undefined || item.occurredAt === null ? "" : String(item.occurredAt);
  if (!reportType || !occurredAt) {
    return "-";
  }
  return `<button type="button" data-report-type="${escapeHtml(reportType)}" data-occurred-at="${escapeHtml(occurredAt)}" onclick="loadAiPersistenceHistoryDetailSafely(this.dataset.reportType, this.dataset.occurredAt)">查看详情</button>`;
}

function renderHistoryRows(items) {
  if (!items || !items.length) {
    return `<tr><td colspan="8">暂无历史记录</td></tr>`;
  }
  return items
    .map((item) => `<tr>
      <td>${escapeHtml(item.reportType || "-")}</td>
      <td>${escapeHtml(formatDateTime(item.occurredAt))}</td>
      <td>${escapeHtml(item.operator || "-")}</td>
      <td>${escapeHtml(item.scene || "-")}</td>
      <td>${escapeHtml(formatHistoryResult(item))}</td>
      <td>${escapeHtml(formatHistoryMode(item))}</td>
      <td>${buildHistoryDetailButton(item)}<div>${escapeHtml(item.reportPath || "-")}</div></td>
      <td>${escapeHtml(item.message || "-")}</td>
    </tr>`)
    .join("");
}

function renderHistoryDetailMeta(detail) {
  const items = [
    ["类型", detail.reportType || "-"],
    ["时间", formatDateTime(detail.occurredAt)],
    ["执行人", detail.operator || "-"],
    ["场景", detail.scene || "-"],
    ["成功", formatNullableBoolean(detail.success)],
    ["接受", formatNullableBoolean(detail.accepted)],
    ["模式", formatHistoryMode(detail)],
    ["报告存在", formatNullableBoolean(detail.exists)],
    ["报告路径", detail.reportPath || "-"],
    ["信息", detail.message || "-"]
  ];
  return items.map(([label, value]) => `<tr><td>${escapeHtml(label)}</td><td>${escapeHtml(value)}</td></tr>`).join("");
}

function resetAiPersistenceHistoryDetail(message = "暂无详情") {
  document.getElementById("aiPersistenceHistoryDetailMetaBody").innerHTML = "";
  document.getElementById("aiPersistenceHistoryDetailContent").textContent = message;
}

function toQuery(params) {
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== "") {
      usp.append(k, String(v).trim());
    }
  });
  return usp.toString();
}

function statusText(code) {
  if (code === 0) return "未激活";
  if (code === 1) return "在线";
  if (code === 2) return "离线";
  return String(code ?? "-");
}

function otaStatusText(code) {
  if (code === 1) return "进行中";
  if (code === 2) return "已完成";
  return String(code ?? "-");
}

async function loadOverview() {
  const json = await httpGet("/api/v1/admin-console/overview");
  renderStats(json);
}

function renderAiEvalTable(json, hintText) {
  const tbody = document.getElementById("aiEvalTableBody");
  tbody.innerHTML = (json || [])
    .map((item) => {
      const content = item.content || {};
      const passRate = content.schema_pass_rate === undefined || content.schema_pass_rate === null
        ? "-"
        : Number(content.schema_pass_rate).toFixed(4);
      return `<tr>
        <td>${escapeHtml(item.sceneType || "")}</td>
        <td>${item.exists ? "是" : "否"}</td>
        <td>${item.gatePassed ? "通过" : "未通过"}</td>
        <td>${escapeHtml(formatDateTime(item.generatedAt))}</td>
        <td>${escapeHtml(content.total || 0)}</td>
        <td>${escapeHtml(passRate)}</td>
      </tr>`;
    })
    .join("");
  document.getElementById("aiEvalHint").textContent = hintText || `reports=${(json || []).length}`;
}

async function loadWorkbench() {
  const homeId = currentHomeId();
  const query = homeId ? `?${toQuery({ homeId })}` : "";
  const json = await httpGet(`/api/v1/admin-console/workbench${query}`);
  renderWorkbench(json);
}

async function loadAiEvalReports() {
  const rawScenes = (document.getElementById("aiEvalScenes").value || "").trim();
  const query = rawScenes ? `?${toQuery({ sceneTypes: rawScenes.split(",").map((x) => x.trim()).filter(Boolean) })}` : "";
  const json = await httpGet(`/api/v1/admin-console/ai/evals/regression-gate${query}`);
  renderAiEvalTable(json, `reports=${(json || []).length}, 已按统一 admin-console 聚合 rule-engine regression gate 报告`);
}

async function loadBusinessLiveFlow() {
  const json = await httpGet("/api/v1/admin-console/ai/persistence/business-live-flow");
  const items = [
    ["报告存在", formatNullableBoolean(json.reportExists)],
    ["验证成功", formatNullableBoolean(json.success)],
    ["验证时间", formatDateTime(json.verifiedAt)],
    ["场景", json.scene || "-"],
    ["全局设备ID", json.globalDeviceId || json.deviceId || "-"],
    ["认证标识", json.authIdentity || "-"],
    ["设备SN", json.deviceSn || "-"],
    ["旧设备ID", json.deviceId || "-"],
    ["事件ID", json.eventId || "-"],
    ["诊断ID", json.diagnosisId || "-"],
    ["反馈ID", json.feedbackId || "-"],
    ["案例ID", json.caseId || "-"],
    ["MySQL写补偿", formatNullableNumber(json.mysqlWriteOutboxCount)],
    ["案例补偿", formatNullableNumber(json.caseMaterializationTaskCount)],
    ["诊断镜像", formatNullableBoolean(json.diagnosisMirrorExists)],
    ["反馈镜像", formatNullableBoolean(json.feedbackMirrorExists)],
    ["案例镜像", formatNullableBoolean(json.caseMirrorExists)],
    ["报告路径", json.reportPath || "-"]
  ];
  document.getElementById("businessLiveFlowTableBody").innerHTML = items
    .map(([label, value]) => `<tr><td>${escapeHtml(label)}</td><td>${escapeHtml(value)}</td></tr>`)
    .join("");
  document.getElementById("businessLiveFlowHint").textContent =
    `来源=admin-console 独立聚合接口, success=${json.success ? "true" : "false"}`;
}

async function loadAiPersistenceHistory() {
  const json = await httpGet("/api/v1/admin-console/ai/persistence/query");
  const businessHistory = (((json || {}).businessLiveFlow || {}).recentHistory) || [];
  const drainHistory = (((json || {}).controlPlaneDrain || {}).recentHistory) || [];
  const merged = [...businessHistory, ...drainHistory]
    .sort((left, right) => Number(right.occurredAt || 0) - Number(left.occurredAt || 0));
  document.getElementById("aiPersistenceHistoryTableBody").innerHTML = renderHistoryRows(merged);
  document.getElementById("aiPersistenceHistoryHint").textContent =
    `business=${businessHistory.length}, drain=${drainHistory.length}, 来源=admin-console 统一 query contract`;
  if (!merged.length) {
    resetAiPersistenceHistoryDetail();
  }
}

async function loadAiPersistenceHistoryDetail(reportType, occurredAt) {
  document.getElementById("aiPersistenceHistoryDetailContent").textContent = "详情加载中...";
  const query = toQuery({ reportType, occurredAt });
  const json = await httpGet(`/api/v1/admin-console/ai/persistence/history/detail?${query}`);
  document.getElementById("aiPersistenceHistoryDetailMetaBody").innerHTML = renderHistoryDetailMeta(json || {});
  const content = (json || {}).content || {};
  const hasContent = Object.keys(content).length > 0;
  document.getElementById("aiPersistenceHistoryDetailContent").textContent =
    hasContent ? JSON.stringify(content, null, 2) : (json && json.exists === false ? "未找到归档详情" : "{}");
}

async function loadDevices() {
  const query = toQuery({
    homeId: resolveHomeId("deviceHomeId"),
    productKey: document.getElementById("deviceProductKey").value,
    status: document.getElementById("deviceStatus").value,
    pageNo: document.getElementById("devicePageNo").value || 1,
    pageSize: document.getElementById("devicePageSize").value || 20
  });
  const json = await httpGet(`/api/v1/admin-console/devices/page?${query}`);
  const tbody = document.getElementById("deviceTableBody");
  tbody.innerHTML = (json.records || [])
    .map((x) => `<tr>
      <td>${escapeHtml(x.id || "")}</td>
      <td>${escapeHtml(x.deviceName || "")}</td>
      <td>${escapeHtml(x.homeId || "")}</td>
      <td>${escapeHtml(x.productKey || "")}</td>
      <td>${statusText(x.status)}</td>
      <td>${escapeHtml(x.firmwareVersion || "")}</td>
    </tr>`)
    .join("");
  document.getElementById("devicePageHint").textContent =
    `total=${json.total || 0}, pageNo=${json.pageNo || 1}, pageSize=${json.pageSize || 20}`;
}

async function loadOtaTasks() {
  const query = toQuery({
    homeId: resolveHomeId("otaHomeId"),
    productKey: document.getElementById("otaProductKey").value,
    status: document.getElementById("otaStatus").value,
    pageNo: document.getElementById("otaPageNo").value || 1,
    pageSize: document.getElementById("otaPageSize").value || 20
  });
  const json = await httpGet(`/api/v1/admin-console/ota/tasks/page?${query}`);
  const tbody = document.getElementById("otaTableBody");
  tbody.innerHTML = (json.records || [])
    .map((x) => `<tr>
      <td>${escapeHtml(x.taskId || "")}</td>
      <td>${escapeHtml(x.homeId || "")}</td>
      <td>${escapeHtml(x.productKey || "")}</td>
      <td>${escapeHtml(x.targetVersion || "")}</td>
      <td>${otaStatusText(x.status)}</td>
      <td>${x.successCount || 0}/${x.failedCount || 0}/${x.totalCount || 0}</td>
    </tr>`)
    .join("");
  document.getElementById("otaPageHint").textContent =
    `total=${json.total || 0}, pageNo=${json.pageNo || 1}, pageSize=${json.pageSize || 20}`;
}

function bindEvents() {
  const tokenInput = document.getElementById("tokenInput");
  tokenInput.value = localStorage.getItem(TOKEN_KEY) || "";

  document.getElementById("saveTokenBtn").addEventListener("click", async () => {
    localStorage.setItem(TOKEN_KEY, tokenInput.value.trim());
    await safeReloadAll();
  });
  document.getElementById("reloadWorkbenchBtn").addEventListener("click", loadWorkbenchSafely);
  document.getElementById("homeSelector").addEventListener("change", async () => {
    await loadWorkbenchSafely();
    await loadDevicesSafely();
    await loadOtaSafely();
  });
  document.getElementById("loadAiEvalBtn").addEventListener("click", loadAiEvalSafely);
  document.getElementById("loadBusinessLiveFlowBtn").addEventListener("click", loadBusinessLiveFlowSafely);
  document.getElementById("loadAiPersistenceHistoryBtn").addEventListener("click", loadAiPersistenceHistorySafely);
  document.getElementById("loadDevicesBtn").addEventListener("click", loadDevicesSafely);
  document.getElementById("loadOtaBtn").addEventListener("click", loadOtaSafely);
}

async function safeReloadAll() {
  try {
    await loadWorkbench();
    await loadBusinessLiveFlow();
    await loadAiPersistenceHistory();
    await loadDevices();
    await loadOtaTasks();
  } catch (e) {
    alert(e.message || "加载失败");
  }
}

async function loadWorkbenchSafely() {
  try {
    await loadWorkbench();
  } catch (e) {
    alert(e.message || "工作台加载失败");
  }
}

async function loadAiEvalSafely() {
  try {
    await loadAiEvalReports();
  } catch (e) {
    alert(e.message || "AI评估加载失败");
  }
}

async function loadDevicesSafely() {
  try {
    await loadDevices();
  } catch (e) {
    alert(e.message || "设备查询失败");
  }
}

async function loadBusinessLiveFlowSafely() {
  try {
    await loadBusinessLiveFlow();
  } catch (e) {
    alert(e.message || "AI业务活体验证加载失败");
  }
}

async function loadAiPersistenceHistorySafely() {
  try {
    await loadAiPersistenceHistory();
  } catch (e) {
    alert(e.message || "AI持久化历史加载失败");
  }
}

async function loadAiPersistenceHistoryDetailSafely(reportType, occurredAt) {
  try {
    await loadAiPersistenceHistoryDetail(reportType, occurredAt);
  } catch (e) {
    resetAiPersistenceHistoryDetail("历史详情加载失败");
    alert(e.message || "AI持久化历史详情加载失败");
  }
}

async function loadOtaSafely() {
  try {
    await loadOtaTasks();
  } catch (e) {
    alert(e.message || "OTA查询失败");
  }
}

bindEvents();
safeReloadAll();
