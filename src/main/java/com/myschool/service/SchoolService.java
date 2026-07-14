package com.myschool.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class SchoolService {
    private final JdbcTemplate jdbc;

    public SchoolService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthUser> findAuthUser(String username) {
        return optional("""
            SELECT username, password, role, enabled
            FROM app_user
            WHERE username = ?
            """, authUserMapper(), username);
    }

    public Optional<SessionUser> findSessionUser(String username) {
        return optional("""
            SELECT u.id, u.username, u.role, u.teacher_id, u.student_id,
                   COALESCE(t.full_name, s.full_name, u.username) AS display_name
            FROM app_user u
            LEFT JOIN teachers t ON t.id = u.teacher_id
            LEFT JOIN students s ON s.id = u.student_id
            WHERE u.username = ?
            """, sessionUserMapper(), username);
    }

    public Optional<ClassInfo> classForTeacher(Long teacherId) {
        return optional("""
            SELECT c.id, c.name, c.section, c.room_number, c.academic_year,
                   t.full_name AS class_incharge_name, t.email AS teacher_email, t.phone AS teacher_phone
            FROM school_classes c
            JOIN teachers t ON t.id = c.class_incharge_id
            WHERE c.class_incharge_id = ?
            """, classInfoMapper(), teacherId);
    }

    public Optional<ClassInfo> classForStudent(Long studentId) {
        return optional("""
            SELECT c.id, c.name, c.section, c.room_number, c.academic_year,
                   t.full_name AS class_incharge_name, t.email AS teacher_email, t.phone AS teacher_phone
            FROM students s
            JOIN school_classes c ON c.id = s.class_id
            JOIN teachers t ON t.id = c.class_incharge_id
            WHERE s.id = ?
            """, classInfoMapper(), studentId);
    }

    public Optional<ClassInfo> classById(Long classId) {
        return optional("""
            SELECT c.id, c.name, c.section, c.room_number, c.academic_year,
                   t.full_name AS class_incharge_name, t.email AS teacher_email, t.phone AS teacher_phone
            FROM school_classes c
            JOIN teachers t ON t.id = c.class_incharge_id
            WHERE c.id = ?
            """, classInfoMapper(), classId);
    }

    public List<ClassInfo> listClasses() {
        return jdbc.query("""
            SELECT c.id, c.name, c.section, c.room_number, c.academic_year,
                   t.full_name AS class_incharge_name, t.email AS teacher_email, t.phone AS teacher_phone
            FROM school_classes c
            JOIN teachers t ON t.id = c.class_incharge_id
            ORDER BY c.id
            """, classInfoMapper());
    }

    public Optional<ClassInfo> nextClassFor(Long classId) {
        ClassInfo current = classById(classId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found."));
        Optional<Integer> currentNumber = classNumber(current);
        if (currentNumber.isEmpty()) {
            return Optional.empty();
        }
        return listClasses().stream()
            .filter(candidate -> candidate.section().equalsIgnoreCase(current.section()))
            .filter(candidate -> classNumber(candidate)
                .map(number -> number == currentNumber.get() + 1)
                .orElse(false))
            .findFirst();
    }

    public Optional<StudentInfo> studentById(Long studentId) {
        return optional("""
            SELECT id, class_id, admission_number, roll_number, full_name, date_of_birth,
                   email, guardian_name, guardian_phone, address, blood_group
            FROM students
            WHERE id = ?
            """, studentInfoMapper(), studentId);
    }

    public List<StudentInfo> listStudents(Long classId) {
        return jdbc.query("""
            SELECT id, class_id, admission_number, roll_number, full_name, date_of_birth,
                   email, guardian_name, guardian_phone, address, blood_group
            FROM students
            WHERE class_id = ?
            ORDER BY roll_number, full_name
            """, studentInfoMapper(), classId);
    }

    public List<SubjectInfo> listSubjects(Long classId) {
        return jdbc.query("""
            SELECT s.id, s.name, s.code, s.weekly_hours, t.full_name AS teacher_name
            FROM subjects s
            JOIN teachers t ON t.id = s.teacher_id
            WHERE s.class_id = ?
            ORDER BY s.name
            """, subjectInfoMapper(), classId);
    }

    public List<ScheduleEntry> listSchedule(Long classId) {
        return jdbc.query("""
            SELECT se.id, se.day_of_week, se.start_time, se.end_time, se.room, se.notes,
                   su.name AS subject_name, su.code AS subject_code, t.full_name AS teacher_name
            FROM schedule_entries se
            JOIN subjects su ON su.id = se.subject_id
            JOIN teachers t ON t.id = su.teacher_id
            WHERE se.class_id = ?
            ORDER BY CASE se.day_of_week
                WHEN 'MONDAY' THEN 1
                WHEN 'TUESDAY' THEN 2
                WHEN 'WEDNESDAY' THEN 3
                WHEN 'THURSDAY' THEN 4
                WHEN 'FRIDAY' THEN 5
                WHEN 'SATURDAY' THEN 6
                ELSE 7
            END, se.start_time
            """, scheduleEntryMapper(), classId);
    }

    public List<AttendanceRow> listAttendance(Long classId, LocalDate date) {
        return jdbc.query("""
            SELECT s.id AS student_id, s.roll_number, s.full_name,
                   COALESCE(a.status, 'UNMARKED') AS status,
                   COALESCE(a.note, '') AS note
            FROM students s
            LEFT JOIN attendance_records a
                ON a.student_id = s.id AND a.attendance_date = ?
            WHERE s.class_id = ?
            ORDER BY s.roll_number, s.full_name
            """, attendanceRowMapper(), Date.valueOf(date), classId);
    }

    public AttendanceSummary attendanceSummary(Long classId, LocalDate date) {
        List<AttendanceRow> rows = listAttendance(classId, date);
        long present = rows.stream().filter(row -> "PRESENT".equals(row.status())).count();
        long absent = rows.stream().filter(row -> "ABSENT".equals(row.status())).count();
        long late = rows.stream().filter(row -> "LATE".equals(row.status())).count();
        long excused = rows.stream().filter(row -> "EXCUSED".equals(row.status())).count();
        long unmarked = rows.stream().filter(row -> "UNMARKED".equals(row.status())).count();
        return new AttendanceSummary(rows.size(), present, absent, late, excused, unmarked);
    }

    public List<LocalDate> recentAttendanceDates(Long classId) {
        return jdbc.query("""
            SELECT DISTINCT a.attendance_date
            FROM attendance_records a
            JOIN students s ON s.id = a.student_id
            WHERE s.class_id = ?
            ORDER BY a.attendance_date DESC
            LIMIT 10
            """, (rs, rowNum) -> rs.getDate("attendance_date").toLocalDate(), classId);
    }

    @Transactional
    public void upsertAttendance(Long classId, Long studentId, LocalDate date, String status, String note) {
        if (!studentBelongsToClass(studentId, classId)) {
            throw new IllegalArgumentException("Student is not assigned to your class.");
        }
        jdbc.update("""
            MERGE INTO attendance_records (student_id, attendance_date, status, note)
            KEY (student_id, attendance_date)
            VALUES (?, ?, ?, ?)
            """, studentId, Date.valueOf(date), normalizeStatus(status), clean(note));
    }

    @Transactional
    public void addStudent(Long classId, StudentForm form) {
        int rollNumber = nextRollNumber(classId);
        String admissionNumber = generateAdmissionNumber(classId, rollNumber);
        jdbc.update("""
            INSERT INTO students (class_id, admission_number, roll_number, full_name, date_of_birth,
                                  email, guardian_name, guardian_phone, address, blood_group)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            classId,
            admissionNumber,
            rollNumber,
            clean(form.fullName()),
            Date.valueOf(form.dateOfBirth()),
            clean(form.email()),
            clean(form.guardianName()),
            clean(form.guardianPhone()),
            clean(form.address()),
            clean(form.bloodGroup())
        );
    }

    @Transactional
    public void updateStudent(Long classId, Long studentId, StudentForm form) {
        int rows = jdbc.update("""
            UPDATE students
            SET full_name = ?, date_of_birth = ?, email = ?, guardian_name = ?,
                guardian_phone = ?, address = ?, blood_group = ?
            WHERE id = ? AND class_id = ?
            """,
            clean(form.fullName()),
            Date.valueOf(form.dateOfBirth()),
            clean(form.email()),
            clean(form.guardianName()),
            clean(form.guardianPhone()),
            clean(form.address()),
            clean(form.bloodGroup()),
            studentId,
            classId
        );
        if (rows == 0) {
            throw new IllegalArgumentException("Student is not assigned to your class.");
        }
    }

    public List<TestReportRow> listReportsForClass(Long classId) {
        return jdbc.query("""
            SELECT tr.id, tr.test_name, tr.test_date, tr.marks_obtained, tr.max_marks,
                   tr.teacher_remarks, st.full_name AS student_name, st.roll_number,
                   su.name AS subject_name
            FROM test_reports tr
            JOIN students st ON st.id = tr.student_id
            JOIN subjects su ON su.id = tr.subject_id
            WHERE st.class_id = ?
            ORDER BY tr.test_date DESC, st.roll_number
            """, testReportMapper(), classId);
    }

    public List<TestReportRow> listReportsForStudent(Long studentId) {
        return jdbc.query("""
            SELECT tr.id, tr.test_name, tr.test_date, tr.marks_obtained, tr.max_marks,
                   tr.teacher_remarks, st.full_name AS student_name, st.roll_number,
                   su.name AS subject_name
            FROM test_reports tr
            JOIN students st ON st.id = tr.student_id
            JOIN subjects su ON su.id = tr.subject_id
            WHERE st.id = ?
            ORDER BY tr.test_date DESC
            """, testReportMapper(), studentId);
    }

    public List<StudentSubjectPerformance> listSubjectPerformanceForClass(Long classId) {
        return jdbc.query("""
            SELECT s.id AS student_id, su.name AS subject_name, su.code AS subject_code,
                   CAST(ROUND(COALESCE(AVG(
                       CASE
                           WHEN tr.max_marks > 0 THEN tr.marks_obtained * 100.0 / tr.max_marks
                           ELSE 0
                       END
                   ), 0), 0) AS INT) AS percentage,
                   COUNT(tr.id) AS test_count
            FROM students s
            JOIN subjects su ON su.class_id = s.class_id
            LEFT JOIN test_reports tr ON tr.student_id = s.id AND tr.subject_id = su.id
            WHERE s.class_id = ?
            GROUP BY s.id, s.roll_number, su.id, su.name, su.code
            ORDER BY s.roll_number, su.name
            """, studentSubjectPerformanceMapper(), classId);
    }

    @Transactional
    public void addReport(Long classId, ReportForm form) {
        if (!studentBelongsToClass(form.studentId(), classId)) {
            throw new IllegalArgumentException("Student is not assigned to your class.");
        }
        if (!subjectBelongsToClass(form.subjectId(), classId)) {
            throw new IllegalArgumentException("Subject is not assigned to your class.");
        }
        if (form.marksObtained() > form.maxMarks()) {
            throw new IllegalArgumentException("Marks obtained cannot be greater than maximum marks.");
        }
        jdbc.update("""
            INSERT INTO test_reports (student_id, subject_id, test_name, test_date,
                                      marks_obtained, max_marks, teacher_remarks)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            form.studentId(),
            form.subjectId(),
            clean(form.testName()),
            Date.valueOf(form.testDate()),
            form.marksObtained(),
            form.maxMarks(),
            clean(form.teacherRemarks())
        );
    }

    public ClassMigrationPreview migrationPreview(Long fromClassId) {
        ClassInfo fromClass = classById(fromClassId)
            .orElseThrow(() -> new IllegalArgumentException("Class not found."));
        ClassInfo toClass = nextClassFor(fromClassId).orElse(null);
        long fromCount = countStudents(fromClassId);
        long toCount = toClass == null ? 0 : countStudents(toClass.id());
        if (toClass == null) {
            return new ClassMigrationPreview(
                fromClass,
                null,
                fromCount,
                toCount,
                false,
                "This is the final class. Keep these records as outgoing/alumni records."
            );
        }
        if (fromCount == 0) {
            return new ClassMigrationPreview(
                fromClass,
                toClass,
                fromCount,
                toCount,
                false,
                "No students are available in this class for migration."
            );
        }
        return new ClassMigrationPreview(
            fromClass,
            toClass,
            fromCount,
            toCount,
            true,
            "Students will move to the next class and roll numbers will continue after the target class last roll."
        );
    }

    @Transactional
    public ClassMigrationResult promoteClassStudents(Long fromClassId) {
        ClassMigrationPreview preview = migrationPreview(fromClassId);
        if (!preview.available() || preview.toClass() == null) {
            throw new IllegalArgumentException(preview.message());
        }
        List<StudentInfo> students = listStudents(fromClassId);
        int startRoll = nextRollNumber(preview.toClass().id());
        int nextRoll = startRoll;
        for (StudentInfo student : students) {
            jdbc.update("""
                UPDATE students
                SET class_id = ?, roll_number = ?
                WHERE id = ? AND class_id = ?
                """, preview.toClass().id(), nextRoll, student.id(), fromClassId);
            nextRoll++;
        }
        return new ClassMigrationResult(
            preview.fromClass(),
            preview.toClass(),
            students.size(),
            startRoll,
            nextRoll - 1,
            countStudents(fromClassId),
            countStudents(preview.toClass().id())
        );
    }

    public StudentAttendanceOverview attendanceOverview(Long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT status, COUNT(*) AS total
            FROM attendance_records
            WHERE student_id = ?
            GROUP BY status
            """, studentId);
        long total = 0;
        long present = 0;
        long absent = 0;
        long late = 0;
        long excused = 0;
        for (Map<String, Object> row : rows) {
            long value = ((Number) row.get("total")).longValue();
            String status = String.valueOf(row.get("status"));
            total += value;
            if ("PRESENT".equals(status)) {
                present = value;
            } else if ("ABSENT".equals(status)) {
                absent = value;
            } else if ("LATE".equals(status)) {
                late = value;
            } else if ("EXCUSED".equals(status)) {
                excused = value;
            }
        }
        return new StudentAttendanceOverview(total, present, absent, late, excused);
    }

    public List<AttendanceHistoryRow> recentAttendance(Long studentId) {
        return jdbc.query("""
            SELECT attendance_date, status, COALESCE(note, '') AS note
            FROM attendance_records
            WHERE student_id = ?
            ORDER BY attendance_date DESC
            LIMIT 12
            """, attendanceHistoryMapper(), studentId);
    }

    public List<FacilityRow> listFacilities() {
        return jdbc.query("""
            SELECT id, name, category, description, location_label, available_to_students
            FROM facilities
            ORDER BY category, name
            """, facilityMapper());
    }

    public List<CampusPlaceRow> listCampusPlaces() {
        return jdbc.query("""
            SELECT id, name, place_type, x_position, y_position, description
            FROM campus_places
            ORDER BY name
            """, campusPlaceMapper());
    }

    public long countStudents(Long classId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE class_id = ?", Long.class, classId);
        return count == null ? 0 : count;
    }

    public long countReports(Long classId) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM test_reports tr
            JOIN students s ON s.id = tr.student_id
            WHERE s.class_id = ?
            """, Long.class, classId);
        return count == null ? 0 : count;
    }

    public long countSubjects(Long classId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM subjects WHERE class_id = ?", Long.class, classId);
        return count == null ? 0 : count;
    }

    private boolean studentBelongsToClass(Long studentId, Long classId) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM students
            WHERE id = ? AND class_id = ?
            """, Long.class, studentId, classId);
        return count != null && count > 0;
    }

    private int nextRollNumber(Long classId) {
        Integer current = jdbc.queryForObject("""
            SELECT COALESCE(MAX(roll_number), 0)
            FROM students
            WHERE class_id = ?
            """, Integer.class, classId);
        return (current == null ? 0 : current) + 1;
    }

    private String generateAdmissionNumber(Long classId, int rollNumber) {
        ClassInfo classInfo = optional("""
            SELECT c.id, c.name, c.section, c.room_number, c.academic_year,
                   t.full_name AS class_incharge_name, t.email AS teacher_email, t.phone AS teacher_phone
            FROM school_classes c
            JOIN teachers t ON t.id = c.class_incharge_id
            WHERE c.id = ?
            """, classInfoMapper(), classId).orElseThrow(() -> new IllegalArgumentException("Class not found."));
        String classNumber = classInfo.name().replaceAll("\\D+", "");
        if (classNumber.isBlank()) {
            classNumber = String.valueOf(classId);
        } else {
            classNumber = String.format(Locale.ROOT, "%02d", Integer.parseInt(classNumber));
        }
        String academicYear = classInfo.academicYear().length() >= 4
            ? classInfo.academicYear().substring(0, 4)
            : String.valueOf(LocalDate.now().getYear());
        String prefix = "ADM-" + academicYear + "-"
            + classNumber + classInfo.section().toUpperCase(Locale.ROOT) + "-";
        String admissionNumber = prefix + String.format(Locale.ROOT, "%03d", rollNumber);
        int attempt = rollNumber;
        while (admissionExists(admissionNumber)) {
            attempt++;
            admissionNumber = prefix + String.format(Locale.ROOT, "%03d", attempt);
        }
        return admissionNumber;
    }

    private boolean admissionExists(String admissionNumber) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM students
            WHERE admission_number = ?
            """, Long.class, admissionNumber);
        return count != null && count > 0;
    }

    private boolean subjectBelongsToClass(Long subjectId, Long classId) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM subjects
            WHERE id = ? AND class_id = ?
            """, Long.class, subjectId, classId);
        return count != null && count > 0;
    }

    private Optional<Integer> classNumber(ClassInfo classInfo) {
        String digits = classInfo.name().replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(digits));
    }

    private String normalizeStatus(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        if (List.of("PRESENT", "ABSENT", "LATE", "EXCUSED").contains(normalized)) {
            return normalized;
        }
        return "PRESENT";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> Optional<T> optional(String sql, RowMapper<T> mapper, Object... args) {
        return jdbc.query(sql, mapper, args).stream().findFirst();
    }

    private RowMapper<AuthUser> authUserMapper() {
        return (rs, rowNum) -> new AuthUser(
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("role"),
            rs.getBoolean("enabled")
        );
    }

    private RowMapper<SessionUser> sessionUserMapper() {
        return (rs, rowNum) -> new SessionUser(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("role"),
            nullableLong(rs, "teacher_id"),
            nullableLong(rs, "student_id"),
            rs.getString("display_name")
        );
    }

    private RowMapper<ClassInfo> classInfoMapper() {
        return (rs, rowNum) -> new ClassInfo(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("section"),
            rs.getString("room_number"),
            rs.getString("academic_year"),
            rs.getString("class_incharge_name"),
            rs.getString("teacher_email"),
            rs.getString("teacher_phone")
        );
    }

    private RowMapper<StudentInfo> studentInfoMapper() {
        return (rs, rowNum) -> new StudentInfo(
            rs.getLong("id"),
            rs.getLong("class_id"),
            rs.getString("admission_number"),
            rs.getInt("roll_number"),
            rs.getString("full_name"),
            rs.getDate("date_of_birth").toLocalDate(),
            rs.getString("email"),
            rs.getString("guardian_name"),
            rs.getString("guardian_phone"),
            rs.getString("address"),
            rs.getString("blood_group")
        );
    }

    private RowMapper<SubjectInfo> subjectInfoMapper() {
        return (rs, rowNum) -> new SubjectInfo(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("code"),
            rs.getInt("weekly_hours"),
            rs.getString("teacher_name")
        );
    }

    private RowMapper<ScheduleEntry> scheduleEntryMapper() {
        return (rs, rowNum) -> new ScheduleEntry(
            rs.getLong("id"),
            DayOfWeek.valueOf(rs.getString("day_of_week")),
            rs.getTime("start_time").toLocalTime(),
            rs.getTime("end_time").toLocalTime(),
            rs.getString("subject_name"),
            rs.getString("subject_code"),
            rs.getString("teacher_name"),
            rs.getString("room"),
            rs.getString("notes")
        );
    }

    private RowMapper<AttendanceRow> attendanceRowMapper() {
        return (rs, rowNum) -> new AttendanceRow(
            rs.getLong("student_id"),
            rs.getInt("roll_number"),
            rs.getString("full_name"),
            rs.getString("status"),
            rs.getString("note")
        );
    }

    private RowMapper<AttendanceHistoryRow> attendanceHistoryMapper() {
        return (rs, rowNum) -> new AttendanceHistoryRow(
            rs.getDate("attendance_date").toLocalDate(),
            rs.getString("status"),
            rs.getString("note")
        );
    }

    private RowMapper<TestReportRow> testReportMapper() {
        return (rs, rowNum) -> new TestReportRow(
            rs.getLong("id"),
            rs.getString("student_name"),
            rs.getInt("roll_number"),
            rs.getString("subject_name"),
            rs.getString("test_name"),
            rs.getDate("test_date").toLocalDate(),
            rs.getInt("marks_obtained"),
            rs.getInt("max_marks"),
            rs.getString("teacher_remarks")
        );
    }

    private RowMapper<StudentSubjectPerformance> studentSubjectPerformanceMapper() {
        return (rs, rowNum) -> new StudentSubjectPerformance(
            rs.getLong("student_id"),
            rs.getString("subject_name"),
            rs.getString("subject_code"),
            Math.max(0, Math.min(100, rs.getInt("percentage"))),
            rs.getInt("test_count")
        );
    }

    private RowMapper<FacilityRow> facilityMapper() {
        return (rs, rowNum) -> new FacilityRow(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("location_label"),
            rs.getBoolean("available_to_students")
        );
    }

    private RowMapper<CampusPlaceRow> campusPlaceMapper() {
        return (rs, rowNum) -> new CampusPlaceRow(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("place_type"),
            rs.getInt("x_position"),
            rs.getInt("y_position"),
            rs.getString("description")
        );
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public record AuthUser(String username, String password, String role, boolean enabled) {
    }

    public record SessionUser(Long id, String username, String role, Long teacherId, Long studentId,
                              String displayName) {
    }

    public record ClassInfo(Long id, String name, String section, String roomNumber, String academicYear,
                            String classInchargeName, String teacherEmail, String teacherPhone) {
        public String label() {
            return name + " - " + section;
        }
    }

    public record StudentInfo(Long id, Long classId, String admissionNumber, int rollNumber, String fullName,
                              LocalDate dateOfBirth, String email, String guardianName, String guardianPhone,
                              String address, String bloodGroup) {
        public String initials() {
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
            }
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
        }

        public String avatarTone() {
            return "tone-" + Math.floorMod(fullName.hashCode(), 6);
        }
    }

    public record SubjectInfo(Long id, String name, String code, int weeklyHours, String teacherName) {
    }

    public record ScheduleEntry(Long id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                String subjectName, String subjectCode, String teacherName, String room,
                                String notes) {
        public String dayLabel() {
            String text = dayOfWeek.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
        }
    }

    public record AttendanceRow(Long studentId, int rollNumber, String fullName, String status, String note) {
    }

    public record AttendanceHistoryRow(LocalDate attendanceDate, String status, String note) {
    }

    public record AttendanceSummary(long total, long present, long absent, long late, long excused, long unmarked) {
    }

    public record StudentAttendanceOverview(long total, long present, long absent, long late, long excused) {
        public int attendancePercent() {
            if (total == 0) {
                return 0;
            }
            return (int) Math.round((present * 100.0) / total);
        }
    }

    public record TestReportRow(Long id, String studentName, int rollNumber, String subjectName, String testName,
                                LocalDate testDate, int marksObtained, int maxMarks, String teacherRemarks) {
        public int percentage() {
            if (maxMarks == 0) {
                return 0;
            }
            return (int) Math.round((marksObtained * 100.0) / maxMarks);
        }
    }

    public record StudentSubjectPerformance(Long studentId, String subjectName, String subjectCode, int percentage,
                                            int testCount) {
    }

    public record ClassMigrationPreview(ClassInfo fromClass, ClassInfo toClass, long fromStudentCount,
                                        long toStudentCount, boolean available, String message) {
    }

    public record ClassMigrationResult(ClassInfo fromClass, ClassInfo toClass, long migratedStudents,
                                       int firstAssignedRoll, int lastAssignedRoll, long remainingInSource,
                                       long totalInTarget) {
    }

    public record FacilityRow(Long id, String name, String category, String description, String locationLabel,
                              boolean availableToStudents) {
    }

    public record CampusPlaceRow(Long id, String name, String placeType, int xPosition, int yPosition,
                                 String description) {
    }

    public record StudentForm(String fullName, LocalDate dateOfBirth, String email, String guardianName,
                              String guardianPhone, String address, String bloodGroup) {
    }

    public record ReportForm(Long studentId, Long subjectId, String testName, LocalDate testDate, int marksObtained,
                             int maxMarks, String teacherRemarks) {
    }
}
