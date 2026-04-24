const TOKEN_KEY = "AIOT_ADMIN_TOKEN";

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

function renderStats(data) {
  const items = [
    ["家庭数", data.homeCount || 0],
    ["成员数", data.memberCount || 0],
    ["产品数", data.productCount || 0],
    ["设备数", data.deviceCount || 0],
    ["OTA任务数", data.otaTaskCount || 0],
    ["今日告警", data.todayAlarmCount || 0],
    ["待处理工单", data.pendingWorkOrderCount || 0],
    ["一次修复率", data.oneTimeResolveRate || 0]
  ];
  const root = document.getElementById("statsCards");
  root.innerHTML = items
    .map(([label, value]) => `<div class="card"><div class="label">${label}</div><div class="value">${value}</div></div>`)
    .join("");
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

async function loadDevices() {
  const query = toQuery({
    homeId: document.getElementById("deviceHomeId").value,
    productKey: document.getElementById("deviceProductKey").value,
    status: document.getElementById("deviceStatus").value,
    pageNo: document.getElementById("devicePageNo").value || 1,
    pageSize: document.getElementById("devicePageSize").value || 20
  });
  const json = await httpGet(`/api/v1/admin-console/devices/page?${query}`);
  const tbody = document.getElementById("deviceTableBody");
  tbody.innerHTML = (json.records || [])
    .map((x) => `<tr>
      <td>${x.id || ""}</td>
      <td>${x.deviceName || ""}</td>
      <td>${x.homeId || ""}</td>
      <td>${x.productKey || ""}</td>
      <td>${statusText(x.status)}</td>
      <td>${x.firmwareVersion || ""}</td>
    </tr>`)
    .join("");
  document.getElementById("devicePageHint").textContent =
    `total=${json.total || 0}, pageNo=${json.pageNo || 1}, pageSize=${json.pageSize || 20}`;
}

async function loadOtaTasks() {
  const query = toQuery({
    homeId: document.getElementById("otaHomeId").value,
    productKey: document.getElementById("otaProductKey").value,
    status: document.getElementById("otaStatus").value,
    pageNo: document.getElementById("otaPageNo").value || 1,
    pageSize: document.getElementById("otaPageSize").value || 20
  });
  const json = await httpGet(`/api/v1/admin-console/ota/tasks/page?${query}`);
  const tbody = document.getElementById("otaTableBody");
  tbody.innerHTML = (json.records || [])
    .map((x) => `<tr>
      <td>${x.taskId || ""}</td>
      <td>${x.homeId || ""}</td>
      <td>${x.productKey || ""}</td>
      <td>${x.targetVersion || ""}</td>
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
  document.getElementById("loadDevicesBtn").addEventListener("click", loadDevicesSafely);
  document.getElementById("loadOtaBtn").addEventListener("click", loadOtaSafely);
}

async function safeReloadAll() {
  try {
    await loadOverview();
    await loadDevices();
    await loadOtaTasks();
  } catch (e) {
    alert(e.message || "加载失败");
  }
}

async function loadDevicesSafely() {
  try {
    await loadDevices();
  } catch (e) {
    alert(e.message || "设备查询失败");
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
