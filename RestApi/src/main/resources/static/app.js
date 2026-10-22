const API = "/api";
const state = {
  students: [],
  companies: [],
  jobs: [],
  applications: [],
  meta: { statuses: [], jobTypes: [], transitions: {} },
  editing: null,
};
const $ = (id) => document.getElementById(id);
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
function safeUrl(value) {
  if (!value) return "";
  try {
    const url = new URL(String(value).trim());
    return url.protocol === "http:" || url.protocol === "https:"
      ? url.href
      : "";
  } catch {
    return "";
  }
}
const today = () => new Date().toISOString().slice(0, 10);
const TOKEN_KEY = "pathway.token";
const USER_KEY = "pathway.user";
const token = () => sessionStorage.getItem(TOKEN_KEY);
const currentUser = () => {
  try {
    return JSON.parse(sessionStorage.getItem(USER_KEY) || "null");
  } catch {
    return null;
  }
};
const canWrite = () => currentUser()?.role === "ADMIN";
function statusChoices(current) {
  const next = state.meta.transitions?.[current] || [];
  return [current, ...next.filter((status) => status !== current)];
}

async function request(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };
  if (token()) headers.Authorization = `Bearer ${token()}`;
  const res = await fetch(API + path, { ...options, headers });
  if (res.status === 401) {
    endSession();
    showLogin("Your session expired. Sign in again.");
    applyRole();
    throw new Error("Please sign in to continue.");
  }
  if (res.status === 204) return null;
  const text = await res.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  if (!res.ok)
    throw new Error(
      body?.detail ||
        body?.message ||
        body?.error ||
        (typeof body === "string" ? body : `Request failed (${res.status})`),
    );
  return body;
}
function notify(message, error = false) {
  const n = $("notice");
  n.textContent = message;
  n.classList.toggle("error", error);
  n.classList.remove("hidden");
  window.scrollTo({ top: 0, behavior: "smooth" });
  setTimeout(() => n.classList.add("hidden"), 5000);
}
function endSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}
function showLogin(message = "") {
  $("login-overlay").classList.remove("hidden");
  $("login-error").textContent = message;
  $("login-error").classList.toggle("hidden", !message);
  $("login-username").focus();
}
function hideLogin() {
  $("login-overlay").classList.add("hidden");
  $("login-error").classList.add("hidden");
}
function applyRole() {
  const user = currentUser();
  $("current-user").textContent = user
    ? `${user.username} · ${user.role}`
    : "Not signed in";
  $("logout-btn").classList.toggle("hidden", !user);
  ["add-student", "add-company", "add-job", "add-application"].forEach((id) =>
    $(id)?.classList.toggle("hidden", !canWrite()),
  );
  document.body.classList.toggle("read-only", !canWrite());
}
const PAGES = ["dashboard", "students", "companies", "jobs", "applications"];
let renderedPage = null;
function nav(page, updateHash = true) {
  const target = PAGES.includes(page) ? page : "dashboard";
  document.querySelectorAll(".page").forEach((p) => p.classList.add("hidden"));
  $(`${target}-page`).classList.remove("hidden");
  document.querySelectorAll(".nav-link").forEach((button) => {
    const active = button.dataset.page === target;
    button.classList.toggle("active", active);
    if (active) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  });
  const titles = {
    dashboard: "Overview",
    students: "Students",
    companies: "Companies",
    jobs: "Job openings",
    applications: "Applications",
  };
  $("page-title").textContent = titles[target] || "Overview";
  document.title = `${titles[target] || "Overview"} | Pathway`;
  if (updateHash && location.hash !== `#${target}`) location.hash = target;
  renderedPage = target;
  if (target === "dashboard") loadDashboard();
}
window.addEventListener("hashchange", () => {
  const page = location.hash.slice(1) || "dashboard";
  if (page !== renderedPage) nav(page, false);
});
function applyInitialRoute() {
  const initial = location.hash.slice(1);
  if (PAGES.includes(initial) && initial !== "dashboard") nav(initial, false);
}
async function refreshAll() {
  try {
    const [students, companies, jobs, applications] = await Promise.all([
      request("/students"),
      request("/companies"),
      request("/jobs"),
      request("/applications"),
    ]);
    Object.assign(state, { students, companies, jobs, applications });
    renderStudents();
    renderCompanies();
    renderJobs();
    renderApplications();
    await loadDashboard();
  } catch (e) {
    if (!token()) return;
    notify(
      `Could not load data: ${e.message}. Check that Spring Boot and MySQL are running.`,
      true,
    );
  }
}
const dashboardCharts = {};
const chartPalette = [
  "#6558d9",
  "#3677d4",
  "#20a17a",
  "#e5a13b",
  "#8d65c5",
  "#de6576",
  "#8b93a6",
  "#42a6b5",
];
let dashboardRequestId = 0;
function updateDashboardChart(id, type, labels, datasets, options = {}) {
  const canvas = $(`${id}-chart`);
  const config = {
    type,
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: "index", intersect: false },
      plugins: { legend: { position: type === "doughnut" ? "bottom" : "top" } },
      scales:
        type === "doughnut"
          ? {}
          : {
              x: { beginAtZero: true, grid: { display: false } },
              y: { beginAtZero: true, ticks: { precision: 0 } },
            },
      ...options,
    },
  };
  if (dashboardCharts[id]) {
    dashboardCharts[id].data = config.data;
    dashboardCharts[id].options = config.options;
    dashboardCharts[id].update();
  } else {
    dashboardCharts[id] = new Chart(canvas, config);
  }
}
function setDashboardFilters({ department, status }) {
  if (department !== undefined) $("dashboard-department").value = department;
  if (status !== undefined) $("dashboard-status").value = status;
  loadDashboard();
}
function populateDashboardFilters(filters) {
  const department = $("dashboard-department").value;
  const year = $("dashboard-year").value;
  $("dashboard-department").innerHTML =
    '<option value="">All departments</option>' +
    filters.departments
      .map((name) => `<option value="${esc(name)}">${esc(name)}</option>`)
      .join("");
  $("dashboard-year").innerHTML =
    '<option value="">All years</option>' +
    filters.graduationYears
      .map((value) => `<option value="${value}">${value}</option>`)
      .join("");
  $("dashboard-department").value = filters.departments.includes(department)
    ? department
    : "";
  $("dashboard-year").value = filters.graduationYears.includes(Number(year))
    ? year
    : "";
}
function renderDashboardCharts(data) {
  const statusColors = chartPalette.slice(0, data.statusDistribution.length);
  updateDashboardChart(
    "status",
    "doughnut",
    data.statusDistribution.map((item) => item.status),
    [
      {
        data: data.statusDistribution.map((item) => item.count),
        backgroundColor: statusColors,
        borderWidth: 2,
        borderColor: "#fff",
      },
    ],
    {
      onClick: (_event, elements) => {
        if (elements.length) {
          const selected = data.statusDistribution[elements[0].index];
          setDashboardFilters({
            status:
              $("dashboard-status").value === selected.status
                ? ""
                : selected.status,
          });
        }
      },
      plugins: { legend: { position: "bottom" } },
    },
  );
  updateDashboardChart(
    "funnel",
    "bar",
    data.funnel.map((item) => item.stage),
    [
      {
        label: "Applications reaching stage",
        data: data.funnel.map((item) => item.count),
        backgroundColor: chartPalette.slice(0, data.funnel.length),
      },
    ],
    {
      indexAxis: "y",
      plugins: { legend: { display: false } },
      scales: {
        x: { beginAtZero: true, ticks: { precision: 0 } },
        y: { grid: { display: false } },
      },
    },
  );
  updateDashboardChart(
    "department",
    "bar",
    data.departmentStats.map((item) => item.department),
    [
      {
        label: "Applications",
        data: data.departmentStats.map((item) => item.applications),
        backgroundColor: chartPalette[0],
      },
      {
        label: "Offers",
        data: data.departmentStats.map((item) => item.offers),
        backgroundColor: chartPalette[2],
      },
    ],
    {
      onClick: (_event, elements) => {
        if (elements.length) {
          const department = data.departmentStats[elements[0].index].department;
          setDashboardFilters({
            department:
              $("dashboard-department").value === department ? "" : department,
          });
        }
      },
      scales: {
        x: {
          beginAtZero: true,
          ticks: { precision: 0 },
          grid: { display: false },
        },
        y: { beginAtZero: true, ticks: { precision: 0 } },
      },
    },
  );
  updateDashboardChart(
    "company",
    "bar",
    data.companyOffers.map((item) => item.company),
    [
      {
        label: "Offers",
        data: data.companyOffers.map((item) => item.offers),
        backgroundColor: chartPalette[2],
      },
    ],
    {
      indexAxis: "y",
      scales: {
        x: { beginAtZero: true, ticks: { precision: 0 } },
        y: { grid: { display: false } },
      },
    },
  );
  updateDashboardChart(
    "monthly",
    "line",
    data.monthlyApplications.map((item) => item.month),
    [
      {
        label: "Applications",
        data: data.monthlyApplications.map((item) => item.applications),
        borderColor: chartPalette[1],
        backgroundColor: `${chartPalette[1]}33`,
        fill: true,
        tension: 0.3,
      },
    ],
  );
}
async function loadDashboard() {
  const requestId = ++dashboardRequestId;
  const query = new URLSearchParams();
  if ($("dashboard-department").value)
    query.set("department", $("dashboard-department").value);
  if ($("dashboard-year").value)
    query.set("graduationYear", $("dashboard-year").value);
  if ($("dashboard-status").value)
    query.set("status", $("dashboard-status").value);
  $("dashboard-updated").textContent = "Updating dashboard…";
  try {
    const data = await request(
      `/analytics/dashboard${query.toString() ? `?${query}` : ""}`,
    );
    if (requestId !== dashboardRequestId) return;
    populateDashboardFilters(data.filters);
    $("m-applications").textContent = data.summary.applications;
    $("m-interviews").textContent = data.summary.interviews;
    $("m-offers").textContent = data.summary.offers;
    $("m-students").textContent = data.summary.students;
    $("m-placement-rate").textContent = `${data.placementRate ?? 0}%`;
    $("m-median-package").textContent = data.medianPackage
      ? `${data.medianPackage} LPA`
      : "—";
    const counts = data.statusDistribution;
    const max = Math.max(1, ...counts.map((item) => item.count));
    $("pipeline").innerHTML = counts
      .map(
        (item) =>
          `<div class="pipeline-row"><div class="pipeline-label"><span>${esc(item.status)}</span><span>${item.count}</span></div><div class="bar-track"><div class="bar-fill" style="width:${(item.count / max) * 100}%;opacity:${item.count ? 1 : 0.25}"></div></div></div>`,
      )
      .join("");
    renderDashboardCharts(data);
    $("dashboard-updated").textContent =
      `Updated ${new Date().toLocaleTimeString()}`;
  } catch (error) {
    if (requestId !== dashboardRequestId) return;
    $("dashboard-updated").textContent = "Analytics unavailable";
    notify(`Could not load the interactive dashboard: ${error.message}`, true);
  }
}
function actionButtons(type, id) {
  const historyButton =
    type === "application"
      ? `<button class="icon-btn" data-history="${id}">History</button>`
      : "";
  if (!canWrite())
    return `<div class="row-actions">${historyButton}<span class="muted">Read only</span></div>`;
  return `<div class="row-actions">${historyButton}<button class="icon-btn" data-edit="${type}" data-id="${id}">Edit</button><button class="icon-btn" data-delete="${type}" data-id="${id}">Delete</button></div>`;
}
function formatTimestamp(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}
async function showHistory(id) {
  const dialog = $("history-dialog");
  const application = state.applications.find((item) => item.id === id);
  $("history-title").textContent = `APP-${String(id).padStart(4, "0")}`;
  $("history-subtitle").textContent = application
    ? `${application.studentName || studentName(application.studentId)} · ${application.jobTitle || jobName(application.jobId)} · ${application.status}`
    : "";
  $("history-notes").textContent = application?.notes || "";
  $("history-list").innerHTML =
    '<li class="history-loading muted">Loading history…</li>';
  if (!dialog.open) {
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
  }
  try {
    const entries = await request(`/applications/${id}/history`);
    $("history-list").innerHTML = entries.length
      ? entries
          .map(
            (entry) => `
        <li class="history-item">
          <span class="history-dot" data-status="${esc(entry.status.toLowerCase())}" aria-hidden="true"></span>
          <div class="history-content">
            <div class="history-top"><strong>${esc(entry.status)}</strong><time datetime="${esc(entry.changedAt)}">${esc(formatTimestamp(entry.changedAt))}</time></div>
            <p>${esc(entry.note || "Status updated")}</p>
            <small>${esc(entry.changedBy || "system")}</small>
          </div>
        </li>`,
          )
          .join("")
      : '<li class="history-loading muted">No status history recorded yet.</li>';
  } catch (error) {
    $("history-list").innerHTML = `<li class="history-loading error-text">${esc(error.message)}</li>`;
  }
}
function renderStudents() {
  const q = ($("students-search")?.value || "").toLowerCase();
  const list = state.students.filter((x) =>
    `${x.fullName} ${x.rollNo} ${x.department} ${x.email || ""}`
      .toLowerCase()
      .includes(q),
  );
  $("students-count").textContent = `${list.length} record(s)`;
  $("students-table").innerHTML = list.length
    ? list
        .map(
          (x) =>
            `<tr><td><div class="person-cell"><span class="person-avatar">${esc((x.fullName || "?").slice(0, 1).toUpperCase())}</span><span><strong>${esc(x.fullName)}</strong><small>${esc(x.email || "No email")}</small></span></div></td><td>${esc(x.rollNo)}</td><td>${esc(x.department)}</td><td>${x.cgpa ?? "—"}</td><td>${x.graduationYear ?? "—"}</td><td>${actionButtons("student", x.id)}</td></tr>`,
        )
        .join("")
    : '<tr><td colspan="6"><div class="empty-state"><strong>No students yet</strong>Add a student to begin tracking applications.</div></td></tr>';
}
function renderCompanies() {
  const q = ($("companies-search")?.value || "").toLowerCase();
  const list = state.companies.filter((x) =>
    `${x.companyName} ${x.industry || ""} ${x.location || ""}`
      .toLowerCase()
      .includes(q),
  );
  $("companies-count").textContent = `${list.length} record(s)`;
  $("companies-table").innerHTML = list.length
    ? list
        .map(
          (x) =>
            `<tr><td><div class="person-cell"><span class="person-avatar">${esc((x.companyName || "?").slice(0, 1).toUpperCase())}</span><strong>${esc(x.companyName)}</strong></div></td><td>${esc(x.industry || "—")}</td><td>${esc(x.location || "—")}</td><td>${safeUrl(x.website) ? `<a href="${esc(safeUrl(x.website))}" target="_blank" rel="noreferrer">Open ↗</a>` : "—"}</td><td>${actionButtons("company", x.id)}</td></tr>`,
        )
        .join("")
    : '<tr><td colspan="5"><div class="empty-state"><strong>No companies yet</strong>Add the companies students apply to.</div></td></tr>';
}
function renderJobs() {
  const q = ($("jobs-search")?.value || "").toLowerCase();
  const list = state.jobs.filter((x) =>
    `${x.jobTitle} ${x.jobType} ${x.companyName || ""}`
      .toLowerCase()
      .includes(q),
  );
  $("jobs-count").textContent = `${list.length} record(s)`;
  $("jobs-table").innerHTML = list.length
    ? list
        .map(
          (x) =>
            `<tr><td><strong>${esc(x.jobTitle)}</strong></td><td>${esc(x.companyName || companyName(x.companyId))}</td><td>${esc(x.jobType)}</td><td>${x.packageLpa ?? "—"}</td><td>${x.minCgpa ?? "—"}</td><td>${esc(x.deadline || "—")}</td><td>${actionButtons("job", x.id)}</td></tr>`,
        )
        .join("")
    : '<tr><td colspan="7"><div class="empty-state"><strong>No job openings yet</strong>Add a company first, then add a job opening.</div></td></tr>';
}
function renderApplications() {
  const q = ($("applications-search")?.value || "").toLowerCase();
  const filter = $("status-filter")?.value || "";
  const list = state.applications.filter(
    (x) =>
      (!filter || x.status === filter) &&
      `${x.id} ${x.studentName || ""} ${x.jobTitle || ""} ${x.status} ${x.appliedDate}`
        .toLowerCase()
        .includes(q),
  );
  $("applications-count").textContent = `${list.length} record(s)`;
  $("applications-table").innerHTML = list.length
    ? list
        .map(
          (x) =>
            `<tr><td><strong>APP-${String(x.id).padStart(4, "0")}</strong></td><td>${esc(x.studentName || studentName(x.studentId))}</td><td>${esc(x.jobTitle || jobName(x.jobId))}</td><td>${esc(x.appliedDate)}</td><td><span class="status status-${esc(x.status).toLowerCase()}">${esc(x.status)}</span></td><td>${esc(x.interviewDate || "—")}</td><td>${actionButtons("application", x.id)}</td></tr>`,
        )
        .join("")
    : '<tr><td colspan="7"><div class="empty-state"><strong>No applications found</strong>Add a student and job opening before recording an application.</div></td></tr>';
}
function companyName(id) {
  return state.companies.find((x) => x.id === id)?.companyName || "Company";
}
function studentName(id) {
  return state.students.find((x) => x.id === id)?.fullName || "Student";
}
function jobName(id) {
  return state.jobs.find((x) => x.id === id)?.jobTitle || "Job";
}
function showForm(type, item = null) {
  if (!canWrite()) {
    notify("Your account is read-only.", true);
    return;
  }
  const map = {
    student: {
      container: "student-form",
      title: item ? "Edit student" : "Add student",
      fields: [
        ["rollNo", "Roll number", "text", true],
        ["fullName", "Full name", "text", true],
        ["email", "Email", "email", false],
        ["department", "Department", "text", true],
        ["cgpa", "CGPA", "number", false],
        ["graduationYear", "Graduation year", "number", false],
      ],
    },
    company: {
      container: "company-form",
      title: item ? "Edit company" : "Add company",
      fields: [
        ["companyName", "Company name", "text", true],
        ["industry", "Industry", "text", false],
        ["location", "Location", "text", false],
        ["website", "Website URL", "url", false],
      ],
    },
    job: {
      container: "job-form",
      title: item ? "Edit job opening" : "Add job opening",
      fields: [
        ["companyId", "Company", "company", true],
        ["jobTitle", "Job title", "text", true],
        ["jobType", "Job type", "jobtype", true],
        ["packageLpa", "Package (LPA)", "number", false],
        ["minCgpa", "Minimum CGPA", "number", false],
        ["postedDate", "Posted date", "date", false],
        ["deadline", "Application deadline", "date", false],
      ],
    },
    application: {
      container: "application-form",
      title: item ? "Edit application" : "Record application",
      fields: [
        ["studentId", "Student", "student", true],
        ["jobId", "Job opening", "job", true],
        ["appliedDate", "Applied date", "date", true],
        ["status", "Status", "status", true],
        ["interviewDate", "Interview date", "date", false],
        ["offerPackageLpa", "Offer package (LPA)", "number", false],
        ["notes", "Notes", "textarea", false],
      ],
    },
  };
  const cfg = map[type],
    root = $(cfg.container);
  state.editing = { type, id: item?.id || null };
  const controls = cfg.fields
    .map(([key, label, kind, required]) => {
      let value =
        item?.[key] ??
        (key === "appliedDate"
          ? today()
          : key === "postedDate"
            ? today()
            : key === "status"
              ? "Applied"
              : "");
      let input = "";
      if (kind === "company")
        input = `<select name="${key}" ${required ? "required" : ""}><option value="">Choose company</option>${state.companies.map((c) => `<option value="${c.id}" ${String(value) === String(c.id) ? "selected" : ""}>${esc(c.companyName)}</option>`).join("")}</select>`;
      else if (kind === "student")
        input = `<select name="${key}" required><option value="">Choose student</option>${state.students.map((c) => `<option value="${c.id}" ${String(value) === String(c.id) ? "selected" : ""}>${esc(c.fullName)} (${esc(c.rollNo)})</option>`).join("")}</select>`;
      else if (kind === "job")
        input = `<select name="${key}" required><option value="">Choose job</option>${state.jobs.map((c) => `<option value="${c.id}" ${String(value) === String(c.id) ? "selected" : ""}>${esc(c.jobTitle)} — ${esc(companyName(c.companyId))}</option>`).join("")}</select>`;
      else if (kind === "jobtype") {
        const types = state.meta.jobTypes?.length
          ? state.meta.jobTypes
          : ["Full-time", "Internship", "Part-time", "Contract"];
        input = `<select name="${key}" required>${types.map((s) => `<option ${value === s ? "selected" : ""}>${s}</option>`).join("")}</select>`;
      } else if (kind === "status") {
        const statuses = item
          ? statusChoices(value)
          : state.meta.statuses?.length
            ? state.meta.statuses
            : [
                "Applied",
                "Shortlisted",
                "Interview",
                "Offered",
                "Accepted",
                "Rejected",
                "Withdrawn",
              ];
        input = `<select name="${key}" required>${statuses.map((s) => `<option ${value === s ? "selected" : ""}>${s}</option>`).join("")}</select>`;
      } else if (kind === "textarea")
        input = `<textarea name="${key}" placeholder="Optional notes">${esc(value)}</textarea>`;
      else
        input = `<input name="${key}" type="${kind}" value="${esc(value)}" ${required ? "required" : ""} ${kind === "number" ? (key === "cgpa" || key === "minCgpa" ? 'min="0" max="10" step="0.01"' : 'min="0" step="0.01"') : ""} ${key === "email" ? "" : ""}>`;
      return `<div class="field"><label>${label}${required ? " *" : ""}</label>${input}</div>`;
    })
    .join("");
  root.innerHTML = `<div class="form-title"><h3>${cfg.title}</h3><button class="icon-btn" type="button" data-close-form="${type}">✕ Close</button></div><form id="active-form"><div class="form-grid">${controls}</div><div class="form-actions"><button type="button" class="btn btn-secondary" data-close-form="${type}">Cancel</button><button class="btn btn-primary" type="submit">${item ? "Save changes" : "Save record"}</button></div></form>`;
  root.classList.remove("hidden");
  root.scrollIntoView({ behavior: "smooth", block: "start" });
  $("active-form").addEventListener("submit", (e) =>
    saveForm(e, type, item?.id),
  );
}
async function saveForm(e, type, id) {
  e.preventDefault();
  const form = e.currentTarget,
    data = Object.fromEntries(new FormData(form).entries());
  const numberKeys = [
    "cgpa",
    "graduationYear",
    "companyId",
    "packageLpa",
    "minCgpa",
    "studentId",
    "jobId",
    "offerPackageLpa",
  ];
  numberKeys.forEach((k) => {
    if (data[k] === "") data[k] = null;
    else if (data[k] !== undefined) data[k] = Number(data[k]);
  });
  if (type === "student" && !data.cgpa) data.cgpa = null;
  if (type === "job" && !data.packageLpa) data.packageLpa = null;
  if (type === "application" && !data.offerPackageLpa)
    data.offerPackageLpa = null;
  const path = {
    student: "/students",
    company: "/companies",
    job: "/jobs",
    application: "/applications",
  }[type];
  try {
    await request(path + (id ? `/${id}` : ""), {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(data),
    });
    $(`${type === "job" ? "job" : type}-form`)?.classList.add("hidden");
    if (type === "application") $("application-form").classList.add("hidden");
    await refreshAll();
    notify(
      id ? "Record updated successfully." : "Record created successfully.",
    );
  } catch (err) {
    notify(err.message, true);
  }
}
async function deleteRecord(type, id) {
  if (!confirm("Delete this record? Related records may prevent deletion."))
    return;
  const path = {
    student: "/students",
    company: "/companies",
    job: "/jobs",
    application: "/applications",
  }[type];
  try {
    await request(`${path}/${id}`, { method: "DELETE" });
    await refreshAll();
    notify("Record deleted.");
  } catch (e) {
    notify(e.message, true);
  }
}
document
  .querySelectorAll(".nav-link")
  .forEach((b) => b.addEventListener("click", () => nav(b.dataset.page)));
document
  .querySelectorAll("[data-goto]")
  .forEach((b) => b.addEventListener("click", () => nav(b.dataset.goto)));
$("refresh-btn").addEventListener("click", refreshAll);
$("add-student").addEventListener("click", () => showForm("student"));
$("add-company").addEventListener("click", () => showForm("company"));
$("add-job").addEventListener("click", () => {
  if (!state.companies.length) {
    notify("Add a company first, then create a job opening.", true);
    return;
  }
  showForm("job");
});
$("add-application").addEventListener("click", () => {
  if (!state.students.length || !state.jobs.length) {
    notify(
      "Add at least one student and one job opening before recording an application.",
      true,
    );
    return;
  }
  showForm("application");
});
$("students-search").addEventListener("input", renderStudents);
$("companies-search").addEventListener("input", renderCompanies);
$("jobs-search").addEventListener("input", renderJobs);
$("applications-search").addEventListener("input", renderApplications);
$("status-filter").addEventListener("change", renderApplications);
$("dashboard-department").addEventListener("change", loadDashboard);
$("dashboard-year").addEventListener("change", loadDashboard);
$("dashboard-status").addEventListener("change", loadDashboard);
$("clear-dashboard-filters").addEventListener("click", () => {
  $("dashboard-department").value = "";
  $("dashboard-year").value = "";
  $("dashboard-status").value = "";
  loadDashboard();
});
document.addEventListener("click", (e) => {
  const historyButton = e.target.closest("[data-history]");
  if (historyButton) {
    showHistory(Number(historyButton.dataset.history));
    return;
  }
  const edit = e.target.closest("[data-edit]");
  if (edit) {
    const type = edit.dataset.edit,
      id = Number(edit.dataset.id),
      list = {
        student: state.students,
        company: state.companies,
        job: state.jobs,
        application: state.applications,
      }[type];
    showForm(
      type,
      list.find((x) => x.id === id),
    );
    return;
  }
  const del = e.target.closest("[data-delete]");
  if (del) {
    deleteRecord(del.dataset.delete, Number(del.dataset.id));
    return;
  }
  const close = e.target.closest("[data-close-form]");
  if (close) {
    const type = close.dataset.closeForm;
    const id = type === "job" ? "job-form" : `${type}-form`;
    $(id).classList.add("hidden");
  }
});
$("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const submit = $("login-submit");
  submit.disabled = true;
  try {
    const response = await fetch("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: $("login-username").value.trim(),
        password: $("login-password").value,
      }),
    });
    const body = await response.json().catch(() => null);
    if (!response.ok)
      throw new Error(body?.message || "Invalid username or password.");
    sessionStorage.setItem(TOKEN_KEY, body.token);
    sessionStorage.setItem(
      USER_KEY,
      JSON.stringify({ username: body.username, role: body.role }),
    );
    $("login-password").value = "";
    hideLogin();
    applyRole();
    await loadMeta();
    await refreshAll();
    applyInitialRoute();
  } catch (error) {
    $("login-error").textContent = error.message;
    $("login-error").classList.remove("hidden");
  } finally {
    submit.disabled = false;
  }
});
$("logout-btn").addEventListener("click", () => {
  endSession();
  applyRole();
  showLogin();
});
$("history-dialog").addEventListener("click", (event) => {
  if (
    event.target === $("history-dialog") ||
    event.target.closest("[data-close-history]")
  ) {
    const dialog = $("history-dialog");
    if (typeof dialog.close === "function") dialog.close();
    else dialog.removeAttribute("open");
  }
});
$("export-csv").addEventListener("click", async () => {
  try {
    const response = await fetch(`${API}/analytics/export.csv`, {
      headers: token() ? { Authorization: `Bearer ${token()}` } : {},
    });
    if (!response.ok) throw new Error(`Export failed (${response.status})`);
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = "applications.csv";
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    notify("Application data exported.");
  } catch (error) {
    notify(error.message, true);
  }
});
async function loadMeta() {
  try {
    state.meta = await request("/meta");
    const statuses = state.meta.statuses || [];
    const options =
      '<option value="">All statuses</option>' +
      statuses
        .map(
          (status) => `<option value="${esc(status)}">${esc(status)}</option>`,
        )
        .join("");
    $("dashboard-status").innerHTML = options;
    $("status-filter").innerHTML = options;
  } catch (error) {
    if (token()) console.warn("Could not load application metadata", error);
  }
}
async function bootstrap() {
  applyRole();
  if (!token()) {
    showLogin();
    return;
  }
  hideLogin();
  await loadMeta();
  await refreshAll();
  applyInitialRoute();
}
bootstrap();
