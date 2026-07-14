const app = document.querySelector("#app");

const state = {
  session: null,
  view: "dashboard",
  selectedDate: new Date().toISOString().slice(0, 10),
  studentView: "profile",
  reportFilter: "ALL"
};

const school = {
  name: "Maharana Pratap Senior Secondary School",
  place: "Gharaunda, Karnal",
  initials: "MP",
  website: "https://www.maharanapartapschool.in/"
};

const classLoginOptions = Array.from({ length: 10 }, (_, index) => {
  const classNumber = index + 1;
  return {
    label: `Class ${classNumber} A`,
    username: `teacher-class${classNumber}@myschool.local`
  };
});

const teacherTabs = [
  ["dashboard", "Dashboard"],
  ["students", "Students"],
  ["attendance", "Attendance"],
  ["reports", "Reports"],
  ["migration", "Migration"],
  ["contact", "Contact us"],
  ["ownership", "Ownership"]
];

const studentTabs = [
  ["profile", "Profile"],
  ["timetable", "Time table"],
  ["attendance", "Attendance sheet"],
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

function roleLabel(role) {
  if (role === "TEACHER") {
    return "CLASS PORTAL";
  }
  if (role === "STUDENT") {
    return "STUDENT PORTAL";
  }
  return role || "";
}

function initialsFor(value) {
  const parts = String(value || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) {
    return "MP";
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

function formatDay(value) {
  return String(value || "")
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatTime(value) {
  return String(value || "").slice(0, 5);
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
            <span>Class-wise Portal</span>
            <span>Student Portal</span>
            <span>Annual Migration</span>
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
        <div class="login-copy">
          <p class="muted">Sign in to a class or student workspace</p>
          <span>Class-wise access</span>
        </div>
        <label>
          Class portal
          <select id="classLoginSelect">
            <option value="">Select class account</option>
            ${classLoginOptions.map((option) => `
              <option value="${escapeHtml(option.username)}">${escapeHtml(option.label)}</option>
            `).join("")}
          </select>
        </label>
        <div class="credential-panel" aria-label="Quick demo credentials">
          <button type="button" data-demo-username="teacher-class8@myschool.local" data-demo-password="teacher123">
            <span>Class 8 A</span>
            <strong>teacher-class8@myschool.local</strong>
          </button>
          <button type="button" data-demo-username="student@myschool.local" data-demo-password="student123">
            <span>Student</span>
            <strong>student@myschool.local</strong>
          </button>
        </div>
        ${error ? `<div class="alert">${escapeHtml(error)}</div>` : ""}
        <label>
          Login ID
          <input name="username" type="email" autocomplete="username" placeholder="name@myschool.local" required>
        </label>
        <label>
          Password
          <input name="password" type="password" autocomplete="current-password" placeholder="Enter password" required>
        </label>
        <button class="primary" type="submit">Open portal</button>
      </form>
    </main>
  `;

  document.querySelector("#classLoginSelect").addEventListener("change", (event) => {
    const form = document.querySelector("#loginForm");
    if (!event.target.value) {
      return;
    }
    form.elements.username.value = event.target.value;
    form.elements.password.value = "teacher123";
    form.elements.password.focus();
  });

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
      state.studentView = "profile";
      renderShell();
    } catch (err) {
      renderLogin(err.message);
    }
  });

  document.querySelectorAll("[data-demo-username]").forEach((button) => {
    button.addEventListener("click", () => {
      const form = document.querySelector("#loginForm");
      form.elements.username.value = button.dataset.demoUsername;
      form.elements.password.value = button.dataset.demoPassword;
      form.elements.username.focus();
    });
  });
}

function renderShell() {
  document.title = `${school.name} | Dashboard`;
  const isTeacher = state.session?.role === "TEACHER";
  if (!isTeacher) {
    renderStudentPortalShell();
    return;
  }

  const tabs = teacherTabs;
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
          <span class="badge">${escapeHtml(roleLabel(state.session?.role))}</span>
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

function renderStudentPortalShell() {
  document.title = `${school.name} | Student Portal`;
  app.innerHTML = `
    <div class="student-portal-page">
      <main class="student-portal-root" id="content"></main>
    </div>
  `;
  renderStudentDashboard();
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
    } else if (state.view === "migration") {
      await renderMigration(content);
    } else if (state.view === "contact") {
      await renderTeacherContact(content);
    } else if (state.view === "ownership") {
      renderTeacherOwnership(content);
    } else {
      await renderTeacherDashboard(content);
    }
  } catch (err) {
    content.innerHTML = `<div class="alert">${escapeHtml(err.message)}</div>`;
  }
}

async function renderTeacherContact(content) {
  const data = await api("/api/teacher/dashboard");
  content.innerHTML = contactUsSection(data.classInfo, "Class help desk");
}

function renderTeacherOwnership(content) {
  content.innerHTML = ownershipSection("Class portal");
}

async function renderTeacherDashboard(content) {
  const data = await api("/api/teacher/dashboard");
  content.innerHTML = `
    <section class="section-head">
      <div>
        <h1>Class Dashboard</h1>
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
      <button class="primary add-student-btn" id="openStudentForm" type="button">+ Add Student</button>
    </section>
    <section class="grid">
      <div class="panel">
        <h2>Student List</h2>
        ${studentTable(data.students)}
      </div>
    </section>
    <div class="modal-backdrop" id="studentModal" hidden>
      <form class="modal-panel stack" id="studentForm" role="dialog" aria-modal="true" aria-labelledby="studentModalTitle">
        <div class="modal-head">
          <h2 id="studentModalTitle">Add Student</h2>
          <button class="icon-button" id="closeStudentForm" type="button" aria-label="Close add student form">&times;</button>
        </div>
        <div class="form-grid">
          <label>Full name<input name="fullName" required></label>
          <label>Date of birth<input name="dateOfBirth" type="date" required></label>
          <label>Email<input name="email" type="email" required></label>
          <label>Guardian<input name="guardianName" required></label>
          <label>Phone<input name="guardianPhone" required></label>
          <label>Blood group<input name="bloodGroup" required></label>
          <label class="wide">Address<textarea name="address" required></textarea></label>
        </div>
        <div class="form-actions">
          <button id="cancelStudentForm" type="button">Cancel</button>
          <button class="primary" type="submit">Add student</button>
        </div>
      </form>
    </div>
  `;

  const modal = document.querySelector("#studentModal");
  const formElement = document.querySelector("#studentForm");
  const openModal = () => {
    modal.hidden = false;
    formElement.querySelector("input[name='fullName']").focus();
  };
  const closeModal = () => {
    formElement.reset();
    modal.hidden = true;
  };

  document.querySelector("#openStudentForm").addEventListener("click", openModal);
  document.querySelector("#closeStudentForm").addEventListener("click", closeModal);
  document.querySelector("#cancelStudentForm").addEventListener("click", closeModal);
  modal.addEventListener("click", (event) => {
    if (event.target === modal) {
      closeModal();
    }
  });

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
  content.innerHTML = `<div class="student-loading">Loading...</div>`;
  try {
    const data = await api("/api/student/dashboard");
    content.innerHTML = `
      <section class="student-portal">
        ${studentTopNav()}
        <div class="student-portal-grid">
          ${studentIdentityPanel(data)}
          <section class="student-main-area">
            ${studentPortalTabsHtml()}
            ${studentPortalContent(data)}
          </section>
        </div>
      </section>
    `;
    bindStudentPortalEvents();
  } catch (err) {
    content.innerHTML = `<div class="alert">${escapeHtml(err.message)}</div>`;
  }
}

function studentTopNav() {
  return `
    <header class="student-topnav">
      <div class="student-topnav-school">
        <span class="school-mark">${escapeHtml(school.initials)}</span>
        <div>
          <strong>${escapeHtml(school.name)}</strong>
          <span>${escapeHtml(school.place)}</span>
        </div>
      </div>
      <nav class="student-topnav-actions" aria-label="Student portal navigation">
        <button type="button" data-student-view="contact" class="${state.studentView === "contact" ? "active" : ""}">Contact us</button>
        <button type="button" data-student-view="ownership" class="${state.studentView === "ownership" ? "active" : ""}">Ownership</button>
        <button type="button" data-student-view="about" class="${state.studentView === "about" ? "active" : ""}">About us</button>
        <button id="studentLogout" type="button">Logout</button>
      </nav>
    </header>
  `;
}

function studentIdentityPanel(data) {
  const student = data.student;
  const classText = `${data.classInfo.name} ${data.classInfo.section}`;
  return `
    <aside class="student-identity-panel">
      <div class="student-photo-card">
        <span>${escapeHtml(initialsFor(student.fullName))}</span>
      </div>
      <div class="student-line"><span></span><strong>${escapeHtml(student.fullName)}</strong><span></span></div>
      <div class="student-line"><span></span><strong>${escapeHtml(classText)}</strong><span></span></div>
      <div class="student-line"><span></span><strong>Roll no ${escapeHtml(student.rollNumber)}</strong><span></span></div>
      <dl class="student-personal-details">
        <div><dt>Admission</dt><dd>${escapeHtml(student.admissionNumber)}</dd></div>
        <div><dt>Guardian</dt><dd>${escapeHtml(student.guardianName)}</dd></div>
        <div><dt>Phone</dt><dd>${escapeHtml(student.guardianPhone)}</dd></div>
        <div><dt>Blood group</dt><dd>${escapeHtml(student.bloodGroup)}</dd></div>
      </dl>
    </aside>
  `;
}

function studentPortalTabsHtml() {
  return `
    <nav class="student-tabs" aria-label="Student data tabs">
      ${studentTabs.map(([key, label]) => `
        <button type="button" data-student-view="${key}" class="${state.studentView === key ? "active" : ""}">
          ${escapeHtml(label)}
        </button>
      `).join("")}
    </nav>
  `;
}

function studentPortalContent(data) {
  if (state.studentView === "timetable") {
    return studentTimetableView(data);
  }
  if (state.studentView === "attendance") {
    return studentAttendanceView(data);
  }
  if (state.studentView === "reports") {
    return studentReportsView(data);
  }
  if (state.studentView === "contact") {
    return studentContactView(data);
  }
  if (state.studentView === "ownership") {
    return ownershipSection("Student portal");
  }
  if (state.studentView === "about") {
    return studentAboutView(data);
  }
  return studentProfileView(data);
}

function studentProfileView(data) {
  return `
    <section class="student-profile-view">
      <div class="class-incharge-photo">
        <div class="teacher-avatar">${escapeHtml(initialsFor(data.classInfo.classInchargeName))}</div>
        <span>Class incharge photo</span>
      </div>
      <div class="class-incharge-details">
        <p class="school-label">Class Incharge</p>
        <h1>${escapeHtml(data.classInfo.classInchargeName)}</h1>
        <dl>
          <div><dt>Class</dt><dd>${escapeHtml(data.classInfo.name)} ${escapeHtml(data.classInfo.section)}</dd></div>
          <div><dt>Academic year</dt><dd>${escapeHtml(data.classInfo.academicYear)}</dd></div>
          <div><dt>Room</dt><dd>${escapeHtml(data.classInfo.roomNumber)}</dd></div>
          <div><dt>Phone number</dt><dd>${escapeHtml(data.classInfo.teacherPhone)}</dd></div>
        </dl>
      </div>
    </section>
    <section class="student-snapshot">
      ${studentMetric("Attendance", moneylessPercent(attendancePercent(data.attendance)))}
      ${studentMetric("Reports", data.reports.length)}
      ${studentMetric("Subjects", data.subjects.length)}
    </section>
  `;
}

function studentMetric(label, value) {
  return `<div><strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span></div>`;
}

function studentTimetableView(data) {
  return `
    <section class="student-content-panel">
      <div class="student-panel-head">
        <div>
          <p class="school-label">Time table</p>
          <h2>Weekly lectures</h2>
        </div>
      </div>
      <div class="timetable-sheet">
        ${data.schedule.map((entry) => `
          <article class="lecture-card">
            <strong>${escapeHtml(formatDay(entry.dayOfWeek))}</strong>
            <span>${escapeHtml(formatTime(entry.startTime))} - ${escapeHtml(formatTime(entry.endTime))}</span>
            <h3>${escapeHtml(entry.subjectName)}</h3>
            <p>${escapeHtml(entry.teacherName)} - ${escapeHtml(entry.room)}</p>
          </article>
        `).join("") || `<p class="muted">No lectures scheduled.</p>`}
      </div>
    </section>
  `;
}

function studentAttendanceView(data) {
  return `
    <section class="student-content-panel attendance-compact-panel">
      <div class="student-panel-head">
        <div>
          <p class="school-label">Attendance sheet</p>
          <h2>Attendance sheet</h2>
        </div>
        <span class="attendance-score">${escapeHtml(moneylessPercent(attendancePercent(data.attendance)))}</span>
      </div>
      <div class="attendance-compact-summary">
        <div class="attendance-chip"><strong>${escapeHtml(data.attendance.present)}</strong><span>Present</span></div>
        <div class="attendance-chip"><strong>${escapeHtml(data.attendance.absent)}</strong><span>Absent</span></div>
        <div class="attendance-chip"><strong>${escapeHtml(data.attendance.late)}</strong><span>Late</span></div>
        <div class="attendance-chip"><strong>${escapeHtml(data.attendance.excused)}</strong><span>Excused</span></div>
      </div>
      <div class="attendance-compact-list">
        ${data.recentAttendance.map((row) => `
          <article class="attendance-compact-row">
            <strong>${escapeHtml(row.attendanceDate)}</strong>
            ${statusBadge(row.status)}
            <span>${escapeHtml(row.note || "No note")}</span>
          </article>
        `).join("") || `<p class="muted">No attendance records yet.</p>`}
      </div>
    </section>
  `;
}

function studentReportsView(data) {
  const subjects = [...new Set(data.reports.map((report) => report.subjectName))].sort();
  const reports = state.reportFilter === "ALL"
    ? data.reports
    : data.reports.filter((report) => report.subjectName === state.reportFilter);
  return `
    <section class="student-content-panel">
      <div class="student-panel-head">
        <div>
          <p class="school-label">Reports</p>
          <h2>Test performance</h2>
        </div>
        <label class="report-filter">
          Filter
          <select id="reportFilter">
            <option value="ALL">All subjects</option>
            ${subjects.map((subject) => `<option value="${escapeHtml(subject)}" ${state.reportFilter === subject ? "selected" : ""}>${escapeHtml(subject)}</option>`).join("")}
          </select>
        </label>
      </div>
      <div class="student-report-list">
        ${reports.map((report) => {
          const percent = reportPercent(report);
          return `
            <article class="student-report-row">
              <div>
                <strong>${escapeHtml(report.testName)}</strong>
                <span>${escapeHtml(report.subjectName)} - ${escapeHtml(report.testDate)}</span>
              </div>
              <div class="score-pill">${escapeHtml(report.marksObtained)}/${escapeHtml(report.maxMarks)}</div>
              <div class="score-track"><span style="width:${moneylessPercent(percent)}"></span></div>
            </article>
          `;
        }).join("") || `<p class="muted">No reports found.</p>`}
      </div>
    </section>
  `;
}

function studentContactView(data) {
  return contactUsSection(data.classInfo, "Student help desk");
}

function contactUsSection(classInfo, eyebrow) {
  return `
    <section class="student-content-panel">
      <div class="student-panel-head">
        <div>
          <p class="school-label">Contact us</p>
          <h2>${escapeHtml(school.name)}</h2>
        </div>
      </div>
      <div class="contact-grid">
        <div><span>Location</span><strong>${escapeHtml(school.place)}</strong></div>
        <div><span>${escapeHtml(eyebrow)}</span><strong>${escapeHtml(classInfo.classInchargeName)}</strong></div>
        <div><span>Teacher email</span><strong>${escapeHtml(classInfo.teacherEmail)}</strong></div>
        <div><span>Phone number</span><strong>${escapeHtml(classInfo.teacherPhone)}</strong></div>
        <div>
          <span>Official website</span>
          <a class="contact-link" href="${escapeHtml(school.website)}" target="_blank" rel="noopener noreferrer">
            ${escapeHtml(school.website)}
          </a>
        </div>
        <div><span>School type</span><strong>Senior Secondary School</strong></div>
      </div>
    </section>
  `;
}

function ownershipSection(contextLabel) {
  return `
    <section class="student-content-panel ownership-panel">
      <div class="student-panel-head">
        <div>
          <p class="school-label">Ownership</p>
          <h2>School leadership</h2>
        </div>
        <span class="ownership-context">${escapeHtml(contextLabel)}</span>
      </div>
      <div class="ownership-grid">
        ${ownershipCard(
          "assets/director.jpg",
          "SH. SATPAL SINGH",
          "Principal's desk",
          "Maharana Pratap Sr. Sec. School has completed 21 golden years of excellence in education with a focus on discipline, values and all-round development."
        )}
        ${ownershipCard(
          "assets/principal.jpg",
          "SMT. SEEMA CHAUHAN",
          "President desk",
          "The school purpose is to educate students as confident global citizens while keeping trust, respect, innovation and community at the center."
        )}
      </div>
    </section>
  `;
}

function ownershipCard(imagePath, name, role, message) {
  return `
    <article class="ownership-card">
      <img src="${escapeHtml(imagePath)}" alt="${escapeHtml(name)}">
      <div>
        <p class="school-label">${escapeHtml(role)}</p>
        <h3>${escapeHtml(name)}</h3>
        <p>${escapeHtml(message)}</p>
      </div>
    </article>
  `;
}

function studentAboutView(data) {
  return `
    <section class="student-content-panel">
      <div class="student-panel-head">
        <div>
          <p class="school-label">About us</p>
          <h2>Campus and facilities</h2>
        </div>
      </div>
      <div class="about-grid">
        ${data.facilities.slice(0, 6).map((facility) => `
          <article>
            <strong>${escapeHtml(facility.name)}</strong>
            <span>${escapeHtml(facility.category)} - ${escapeHtml(facility.locationLabel)}</span>
            <p>${escapeHtml(facility.description)}</p>
          </article>
        `).join("")}
      </div>
    </section>
  `;
}

function bindStudentPortalEvents() {
  document.querySelectorAll("[data-student-view]").forEach((button) => {
    button.addEventListener("click", () => {
      state.studentView = button.dataset.studentView;
      renderStudentDashboard();
    });
  });

  document.querySelector("#studentLogout").addEventListener("click", async () => {
    await api("/api/logout", { method: "POST", body: "{}" }).catch(() => null);
    state.session = null;
    state.studentView = "profile";
    renderLogin();
  });

  const reportFilter = document.querySelector("#reportFilter");
  if (reportFilter) {
    reportFilter.addEventListener("change", () => {
      state.reportFilter = reportFilter.value;
      renderStudentDashboard();
    });
  }
}

async function boot() {
  try {
    state.session = await api("/api/session");
    if (state.session.authenticated) {
      state.view = state.session.role === "TEACHER" ? "dashboard" : "student";
      state.studentView = "profile";
      renderShell();
    } else {
      renderLogin();
    }
  } catch {
    renderLogin();
  }
}

boot();
