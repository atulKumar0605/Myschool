const app = document.querySelector("#app");

const state = {
  session: null,
  view: "dashboard",
  selectedDate: new Date().toISOString().slice(0, 10)
};

const school = {
  name: "Maharana Pratap Senior Secondary School",
  place: "Gharaunda, Karnal",
  initials: "MP"
};

const teacherTabs = [
  ["dashboard", "Dashboard"],
  ["students", "Students"],
  ["attendance", "Attendance"],
  ["reports", "Reports"]
];

async function api(path, options = {}) {
  const response = await fetch(path, {
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  if (response.status === 401) {
    state.session = null;
    renderLogin();
    throw new Error("Unauthorized");
  }

  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : {};
  if (!response.ok) {
    throw new Error(body.message || "Request failed");
  }
  return body;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function moneylessPercent(value) {
  return `${Math.max(0, Math.min(100, Number(value) || 0))}%`;
}

function reportPercent(report) {
  if (!report.maxMarks) {
    return 0;
  }
  return Math.round((Number(report.marksObtained) * 100) / Number(report.maxMarks));
}

function attendancePercent(attendance) {
  if (!attendance.total) {
    return 0;
  }
  return Math.round((Number(attendance.present) * 100) / Number(attendance.total));
}

function statusBadge(status) {
  const safeStatus = escapeHtml(status || "UNMARKED");
  return `<span class="badge ${safeStatus}">${safeStatus}</span>`;
}

function renderLogin(error = "") {
  document.title = `${school.name} | Login`;
  app.innerHTML = `
    <main class="login-view">
      <section class="login-hero" aria-label="${escapeHtml(school.name)} campus">
        <div class="login-hero-image"></div>
        <div class="login-hero-content">
          <div class="school-mark large">${escapeHtml(school.initials)}</div>
          <p class="school-label">Senior Secondary School</p>
          <h1>${escapeHtml(school.name)}</h1>
          <p>${escapeHtml(school.place)}</p>
          <div class="login-badges" aria-label="School portals">
            <span>Teacher Portal</span>
            <span>Student Portal</span>
            <span>Digital Attendance</span>
          </div>
        </div>
      </section>
      <form class="login-panel stack" id="loginForm">
        <div class="login-panel-head">
          <div class="school-mark">${escapeHtml(school.initials)}</div>
          <div>
            <p class="school-label">${escapeHtml(school.place)}</p>
            <h2>Welcome Back</h2>
          </div>
        </div>
        <p class="muted">Sign in to the school workspace</p>
        ${error ? `<div class="alert">${escapeHtml(error)}</div>` : ""}
        <label>
          Email
          <input name="username" type="email" autocomplete="username" placeholder="name@myschool.local" required>
        </label>
        <label>
          Password
          <input name="password" type="password" autocomplete="current-password" placeholder="Enter password" required>
        </label>
        <button class="primary" type="submit">Sign in</button>
      </form>
    </main>
  `;

  document.querySelector("#loginForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      state.session = await api("/api/login", {
        method: "POST",
        body: JSON.stringify({
          username: form.get("username"),
          password: form.get("password")
        })
      });
      state.view = state.session.role === "TEACHER" ? "dashboard" : "student";
      renderShell();
    } catch (err) {
      renderLogin(err.message);
    }
  });
}

function renderShell() {
  document.title = `${school.name} | Dashboard`;
  const isTeacher = state.session?.role === "TEACHER";
  const tabs = isTeacher ? teacherTabs : [["student", "Dashboard"]];
  app.innerHTML = `
    <div class="app-shell">
      <header class="topbar">
        <div class="brand">
          <span class="school-mark">${escapeHtml(school.initials)}</span>
          <div>
            <strong>${escapeHtml(school.name)}</strong>
            <span>${escapeHtml(school.place)}</span>
          </div>
        </div>
        <div class="top-actions">
          <span class="user-name">${escapeHtml(state.session?.displayName || "")}</span>
          <span class="badge">${escapeHtml(state.session?.role || "")}</span>
          <button id="logoutBtn" type="button">Logout</button>
        </div>
      </header>
      <div class="layout">
        <nav class="nav">
          ${tabs.map(([key, label]) => `
            <button type="button" data-view="${key}" class="${state.view === key ? "active" : ""}">
              ${label}
            </button>
          `).join("")}
        </nav>
        <main class="content" id="content"></main>
      </div>
    </div>
  `;

  document.querySelectorAll("[data-view]").forEach((button) => {
    button.addEventListener("click", () => {
      state.view = button.dataset.view;
      renderShell();
    });
  });

  document.querySelector("#logoutBtn").addEventListener("click", async () => {
    await api("/api/logout", { method: "POST", body: "{}" }).catch(() => null);
    state.session = null;
    renderLogin();
  });

  if (isTeacher) {
    renderTeacherView();
  } else {
    renderStudentDashboard();
  }
}

async function renderTeacherView() {
  const content = document.querySelector("#content");
  content.innerHTML = `<div class="panel">Loading...</div>`;
  try {
    if (state.view === "students") {
      await renderStudents(content);
    } else if (state.view === "attendance") {
      await renderAttendance(content);
    } else if (state.view === "reports") {
      await renderReports(content);
    } else {
      await renderTeacherDashboard(content);
    }
  } catch (err) {
    content.innerHTML = `<div class="alert">${escapeHtml(err.message)}</div>`;
  }
}

async function renderTeacherDashboard(content) {
  const data = await api("/api/teacher/dashboard");
  content.innerHTML = `
    <section class="section-head">
      <div>
        <h1>Teacher Dashboard</h1>
        <p class="muted">${escapeHtml(data.classInfo.name)} ${escapeHtml(data.classInfo.section)}</p>
      </div>
    </section>
    <section class="grid stats">
      ${stat("Students", data.studentCount)}
      ${stat("Subjects", data.subjectCount)}
      ${stat("Reports", data.reportCount)}
      ${stat("Present Today", data.attendanceSummary.present)}
    </section>
    <section class="grid two" style="margin-top:16px">
      <div class="panel">
        <h2>Students</h2>
        ${studentTable(data.students.slice(0, 8))}
      </div>
      <div class="panel">
        <h2>Schedule</h2>
        ${scheduleTable(data.schedule)}
      </div>
    </section>
  `;
}

function stat(label, value) {
  return `<div class="panel stat"><strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span></div>`;
}

async function renderStudents(content) {
  const data = await api("/api/teacher/students");
  content.innerHTML = `
    <section class="section-head">
      <div>
        <h1>Students</h1>
        <p class="muted">${escapeHtml(data.classInfo.name)} ${escapeHtml(data.classInfo.section)}</p>
      </div>
    </section>
    <section class="grid">
      <form class="panel" id="studentForm">
        <h2>Add Student</h2>
        <div class="form-grid">
          <label>Full name<input name="fullName" required></label>
          <label>Date of birth<input name="dateOfBirth" type="date" required></label>
          <label>Email<input name="email" type="email" required></label>
          <label>Guardian<input name="guardianName" required></label>
          <label>Phone<input name="guardianPhone" required></label>
          <label>Blood group<input name="bloodGroup" required></label>
          <label class="wide">Address<textarea name="address" required></textarea></label>
        </div>
        <div class="form-actions"><button class="primary" type="submit">Add student</button></div>
      </form>
      <div class="panel">
        <h2>Student List</h2>
        ${studentTable(data.students)}
      </div>
    </section>
  `;

  document.querySelector("#studentForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget).entries());
    await api("/api/teacher/students", { method: "POST", body: JSON.stringify(form) });
    await renderStudents(content);
  });
}

function studentTable(students) {
  return `
    <div class="table-wrap">
      <table>
        <thead><tr><th>Roll</th><th>Name</th><th>Admission</th><th>Guardian</th><th>Phone</th></tr></thead>
        <tbody>
          ${students.map((student) => `
            <tr>
              <td>${escapeHtml(student.rollNumber)}</td>
              <td>${escapeHtml(student.fullName)}</td>
              <td>${escapeHtml(student.admissionNumber)}</td>
              <td>${escapeHtml(student.guardianName)}</td>
              <td>${escapeHtml(student.guardianPhone)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

async function renderAttendance(content) {
  const data = await api(`/api/teacher/attendance?date=${encodeURIComponent(state.selectedDate)}`);
  state.selectedDate = data.selectedDate;
  content.innerHTML = `
    <section class="section-head">
      <div>
        <h1>Attendance</h1>
        <p class="muted">${escapeHtml(data.classInfo.name)} ${escapeHtml(data.classInfo.section)}</p>
      </div>
      <input id="attendanceDate" type="date" value="${escapeHtml(data.selectedDate)}" style="max-width:180px">
    </section>
    <section class="grid stats">
      ${stat("Present", data.attendanceSummary.present)}
      ${stat("Absent", data.attendanceSummary.absent)}
      ${stat("Late", data.attendanceSummary.late)}
      ${stat("Unmarked", data.attendanceSummary.unmarked)}
    </section>
    <form class="panel" id="attendanceForm" style="margin-top:16px">
      <h2>Attendance Sheet</h2>
      <div class="table-wrap">
        <table>
          <thead><tr><th>Roll</th><th>Name</th><th>Status</th><th>Note</th></tr></thead>
          <tbody>
            ${data.attendanceRows.map((row) => `
              <tr data-student-id="${row.studentId}">
                <td>${escapeHtml(row.rollNumber)}</td>
                <td>${escapeHtml(row.fullName)}</td>
                <td>
                  <select name="status">
                    ${["PRESENT", "ABSENT", "LATE", "EXCUSED"].map((status) => `
                      <option value="${status}" ${row.status === status ? "selected" : ""}>${status}</option>
                    `).join("")}
                  </select>
                </td>
                <td><input name="note" value="${escapeHtml(row.note)}"></td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
      <div class="form-actions"><button class="primary" type="submit">Save attendance</button></div>
    </form>
  `;

  document.querySelector("#attendanceDate").addEventListener("change", async (event) => {
    state.selectedDate = event.target.value;
    await renderAttendance(content);
  });

  document.querySelector("#attendanceForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const entries = [...event.currentTarget.querySelectorAll("tbody tr")].map((row) => ({
      studentId: Number(row.dataset.studentId),
      status: row.querySelector('[name="status"]').value,
      note: row.querySelector('[name="note"]').value
    }));
    await api("/api/teacher/attendance", {
      method: "POST",
      body: JSON.stringify({ attendanceDate: state.selectedDate, entries })
    });
    await renderAttendance(content);
  });
}

async function renderReports(content) {
  const data = await api("/api/teacher/reports");
  content.innerHTML = `
    <section class="section-head">
      <div>
        <h1>Reports</h1>
        <p class="muted">${escapeHtml(data.classInfo.name)} ${escapeHtml(data.classInfo.section)}</p>
      </div>
    </section>
    <section class="grid">
      <form class="panel" id="reportForm">
        <h2>Add Report</h2>
        <div class="form-grid">
          <label>Student<select name="studentId" required>${data.students.map((student) => `<option value="${student.id}">${escapeHtml(student.rollNumber)} - ${escapeHtml(student.fullName)}</option>`).join("")}</select></label>
          <label>Subject<select name="subjectId" required>${data.subjects.map((subject) => `<option value="${subject.id}">${escapeHtml(subject.name)}</option>`).join("")}</select></label>
          <label>Test name<input name="testName" required></label>
          <label>Test date<input name="testDate" type="date" required></label>
          <label>Marks obtained<input name="marksObtained" type="number" min="0" required></label>
          <label>Max marks<input name="maxMarks" type="number" min="1" required></label>
          <label class="wide">Remarks<textarea name="teacherRemarks"></textarea></label>
        </div>
        <div class="form-actions"><button class="primary" type="submit">Add report</button></div>
      </form>
      <div class="panel">
        <h2>Report List</h2>
        ${reportsTable(data.reports)}
      </div>
    </section>
  `;

  document.querySelector("#reportForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget).entries());
    form.studentId = Number(form.studentId);
    form.subjectId = Number(form.subjectId);
    form.marksObtained = Number(form.marksObtained);
    form.maxMarks = Number(form.maxMarks);
    await api("/api/teacher/reports", { method: "POST", body: JSON.stringify(form) });
    await renderReports(content);
  });
}

function reportsTable(reports) {
  return `
    <div class="table-wrap">
      <table>
        <thead><tr><th>Date</th><th>Student</th><th>Subject</th><th>Test</th><th>Score</th></tr></thead>
        <tbody>
          ${reports.map((report) => `
            <tr>
              <td>${escapeHtml(report.testDate)}</td>
              <td>${escapeHtml(report.studentName)}</td>
              <td>${escapeHtml(report.subjectName)}</td>
              <td>${escapeHtml(report.testName)}</td>
              <td>${escapeHtml(report.marksObtained)}/${escapeHtml(report.maxMarks)} (${moneylessPercent(reportPercent(report))})</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function scheduleTable(schedule) {
  return `
    <div class="table-wrap">
      <table>
        <thead><tr><th>Day</th><th>Time</th><th>Subject</th><th>Room</th></tr></thead>
        <tbody>
          ${schedule.map((entry) => `
            <tr>
              <td>${escapeHtml(entry.dayOfWeek)}</td>
              <td>${escapeHtml(entry.startTime)} - ${escapeHtml(entry.endTime)}</td>
              <td>${escapeHtml(entry.subjectName)}</td>
              <td>${escapeHtml(entry.room)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

async function renderStudentDashboard() {
  const content = document.querySelector("#content");
  content.innerHTML = `<div class="panel">Loading...</div>`;
  try {
    const data = await api("/api/student/dashboard");
    content.innerHTML = `
      <section class="section-head">
        <div>
          <h1>Student Dashboard</h1>
          <p class="muted">${escapeHtml(data.classInfo.name)} ${escapeHtml(data.classInfo.section)}</p>
        </div>
      </section>
      <section class="grid stats">
        ${stat("Attendance", moneylessPercent(attendancePercent(data.attendance)))}
        ${stat("Reports", data.reports.length)}
        ${stat("Subjects", data.subjects.length)}
        ${stat("Present Days", data.attendance.present)}
      </section>
      <section class="grid two" style="margin-top:16px">
        <div class="panel">
          <h2>Recent Attendance</h2>
          <div class="table-wrap">
            <table>
              <thead><tr><th>Date</th><th>Status</th><th>Note</th></tr></thead>
              <tbody>
                ${data.recentAttendance.map((row) => `
                  <tr><td>${escapeHtml(row.attendanceDate)}</td><td>${statusBadge(row.status)}</td><td>${escapeHtml(row.note)}</td></tr>
                `).join("")}
              </tbody>
            </table>
          </div>
        </div>
        <div class="panel">
          <h2>Reports</h2>
          ${reportsTable(data.reports)}
        </div>
      </section>
    `;
  } catch (err) {
    content.innerHTML = `<div class="alert">${escapeHtml(err.message)}</div>`;
  }
}

async function boot() {
  try {
    state.session = await api("/api/session");
    if (state.session.authenticated) {
      state.view = state.session.role === "TEACHER" ? "dashboard" : "student";
      renderShell();
    } else {
      renderLogin();
    }
  } catch {
    renderLogin();
  }
}

boot();
