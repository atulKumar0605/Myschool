package com.myschool.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

@Component
public class DataSeeder implements CommandLineRunner {
    private static final int CLASS_COUNT = 10;
    private static final int STUDENTS_PER_CLASS = 15;
    private static final String[] TEACHER_NAMES = {
        "Kavita Rao", "Ravi Mehta", "Sunita Verma", "Imran Ali", "Pooja Nair",
        "Arjun Das", "Neha Patel", "Anita Sharma", "Mohit Sinha", "Farah Khan"
    };
    private static final String[] STUDENT_NAMES = {
        "Aarav Singh", "Diya Patel", "Kabir Khan", "Meera Verma", "Ishan Jain",
        "Sara Thomas", "Vivaan Rao", "Anaya Gupta", "Reyansh Yadav", "Myra Nair",
        "Aditya Sinha", "Kiara Das", "Rudra Sharma", "Tara Ali", "Naman Joshi"
    };
    private static final String[] BLOOD_GROUPS = {
        "A+", "B+", "O+", "AB+", "A-", "B-", "O-", "AB-"
    };

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (demoDatasetReady()) {
            seedReports();
            return;
        }

        clearDemoTables();
        seedTeachers();
        seedClasses();
        seedSubjects();
        seedStudents();
        seedUsers();
        seedAttendance();
        seedReports();
        seedSchedule();
        seedFacilities();
        seedCampusMap();
        restartIdentityColumns();
    }

    private boolean demoDatasetReady() {
        Long readyClasses = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM (
                SELECT c.id
                FROM school_classes c
                LEFT JOIN students s ON s.class_id = c.id
                GROUP BY c.id
                HAVING COUNT(s.id) >= ?
            ) ready_classes
            """, Long.class, STUDENTS_PER_CLASS);
        return readyClasses != null && readyClasses >= CLASS_COUNT;
    }

    private void clearDemoTables() {
        jdbc.update("DELETE FROM test_reports");
        jdbc.update("DELETE FROM attendance_records");
        jdbc.update("DELETE FROM schedule_entries");
        jdbc.update("DELETE FROM app_user");
        jdbc.update("DELETE FROM students");
        jdbc.update("DELETE FROM subjects");
        jdbc.update("DELETE FROM school_classes");
        jdbc.update("DELETE FROM teachers");
        jdbc.update("DELETE FROM facilities");
        jdbc.update("DELETE FROM campus_places");
    }

    private void seedTeachers() {
        for (int i = 1; i <= CLASS_COUNT; i++) {
            String name = TEACHER_NAMES[i - 1];
            String email = slug(name) + "@myschool.local";
            jdbc.update("""
                INSERT INTO teachers (id, employee_code, full_name, email, phone, qualification)
                VALUES (?, ?, ?, ?, ?, ?)
                """, (long) i, "TCH-" + String.format(Locale.ROOT, "%03d", i), name, email,
                "+91 98765 " + String.format(Locale.ROOT, "%05d", 10000 + i),
                qualificationFor(i));
        }
    }

    private String qualificationFor(int classNumber) {
        return switch (classNumber % 5) {
            case 0 -> "M.A English, B.Ed";
            case 1 -> "M.Sc Mathematics, B.Ed";
            case 2 -> "M.Sc Science, B.Ed";
            case 3 -> "M.A Social Studies, B.Ed";
            default -> "M.C.A Computer Applications, B.Ed";
        };
    }

    private void seedClasses() {
        for (int classNumber = 1; classNumber <= CLASS_COUNT; classNumber++) {
            jdbc.update("""
                INSERT INTO school_classes (id, name, section, room_number, academic_year, class_incharge_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, (long) classNumber, "Class " + classNumber, "A",
                "Block " + (classNumber <= 5 ? "A" : "B") + "-" + String.format(Locale.ROOT, "%03d", 100 + classNumber),
                "2026-27", (long) classNumber);
        }
    }

    private void seedSubjects() {
        for (int classNumber = 1; classNumber <= CLASS_COUNT; classNumber++) {
            insertSubject(subjectId(classNumber, 1), classNumber, classNumber, "Mathematics", "MATH", 6);
            insertSubject(subjectId(classNumber, 2), classNumber, rotatingTeacher(classNumber, 1), "Science", "SCI", 5);
            insertSubject(subjectId(classNumber, 3), classNumber, rotatingTeacher(classNumber, 2), "English", "ENG", 5);
            insertSubject(subjectId(classNumber, 4), classNumber, rotatingTeacher(classNumber, 3), "Social Studies", "SST", 4);
            insertSubject(subjectId(classNumber, 5), classNumber, rotatingTeacher(classNumber, 4), "Computer Applications", "COMP", 3);
        }
    }

    private void insertSubject(long id, long classId, long teacherId, String name, String code, int weeklyHours) {
        jdbc.update("""
            INSERT INTO subjects (id, class_id, teacher_id, name, code, weekly_hours)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, classId, teacherId, name, code, weeklyHours);
    }

    private long subjectId(int classNumber, int subjectNumber) {
        return classNumber * 100L + subjectNumber;
    }

    private long rotatingTeacher(int classNumber, int offset) {
        return ((classNumber + offset - 1) % CLASS_COUNT) + 1L;
    }

    private void seedStudents() {
        for (int classNumber = 1; classNumber <= CLASS_COUNT; classNumber++) {
            for (int roll = 1; roll <= STUDENTS_PER_CLASS; roll++) {
                String name = STUDENT_NAMES[roll - 1];
                insertStudent(
                    studentId(classNumber, roll),
                    classNumber,
                    admissionNumber(classNumber, roll),
                    roll,
                    name,
                    LocalDate.of(2020 - classNumber, ((roll - 1) % 12) + 1, Math.min(roll + 4, 28)),
                    studentEmail(name, classNumber, roll),
                    guardianName(name, roll),
                    "+91 90000 " + String.format(Locale.ROOT, "%05d", classNumber * 100 + roll),
                    addressFor(classNumber, roll),
                    BLOOD_GROUPS[(classNumber + roll) % BLOOD_GROUPS.length]
                );
            }
        }
    }

    private long studentId(int classNumber, int roll) {
        return classNumber * 100L + roll;
    }

    private String admissionNumber(int classNumber, int roll) {
        return "ADM-2026-" + String.format(Locale.ROOT, "%02dA-%03d", classNumber, roll);
    }

    private String studentEmail(String name, int classNumber, int roll) {
        return slug(name) + ".c" + classNumber + ".r" + roll + "@myschool.local";
    }

    private String guardianName(String studentName, int roll) {
        String firstName = studentName.split("\\s+")[0];
        return (roll % 2 == 0 ? "Mrs. " : "Mr. ") + firstName + " Guardian";
    }

    private String addressFor(int classNumber, int roll) {
        String[] areas = {"Station Road", "New Colony", "Main Market", "Green Park", "Bus Stand Road"};
        return "House " + (classNumber * 10 + roll) + ", " + areas[(classNumber + roll) % areas.length] + ", Chichli";
    }

    private void insertStudent(long id, long classId, String admission, int roll, String name, LocalDate dob,
                               String email, String guardian, String phone, String address, String bloodGroup) {
        jdbc.update("""
            INSERT INTO students (id, class_id, admission_number, roll_number, full_name, date_of_birth,
                                  email, guardian_name, guardian_phone, address, blood_group)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, classId, admission, roll, name, Date.valueOf(dob), email, guardian, phone, address, bloodGroup);
    }

    private void seedUsers() {
        insertUser(1, "teacher@myschool.local", "teacher123", "TEACHER", 8L, null);
        insertUser(2, "teacher2@myschool.local", "teacher123", "TEACHER", 7L, null);
        insertUser(3, "student@myschool.local", "student123", "STUDENT", null, studentId(8, 1));
        insertUser(4, "student2@myschool.local", "student123", "STUDENT", null, studentId(8, 2));
        for (int classNumber = 1; classNumber <= CLASS_COUNT; classNumber++) {
            insertUser(100 + classNumber, "teacher-class" + classNumber + "@myschool.local", "teacher123",
                "TEACHER", (long) classNumber, null);
        }
    }

    private void insertUser(long id, String username, String rawPassword, String role, Long teacherId, Long studentId) {
        jdbc.update("""
            INSERT INTO app_user (id, username, password, role, enabled, teacher_id, student_id)
            VALUES (?, ?, ?, ?, TRUE, ?, ?)
            """, id, username, passwordEncoder.encode(rawPassword), role, teacherId, studentId);
    }

    private void seedAttendance() {
        LocalDate today = LocalDate.now();
        for (int classNumber = 1; classNumber <= CLASS_COUNT; classNumber++) {
            for (int roll = 1; roll <= STUDENTS_PER_CLASS; roll++) {
                for (int day = 0; day < 8; day++) {
                    String status = attendanceStatus(classNumber, roll, day);
                    mark(studentId(classNumber, roll), today.minusDays(day), status, noteFor(status));
                }
            }
        }
    }

    private String attendanceStatus(int classNumber, int roll, int day) {
        int marker = Math.floorMod(classNumber + roll + day, 11);
        if (marker == 0) {
            return "ABSENT";
        }
        if (marker == 1) {
            return "LATE";
        }
        if (marker == 2) {
            return "EXCUSED";
        }
        return "PRESENT";
    }

    private String noteFor(String status) {
        return switch (status) {
            case "ABSENT" -> "Guardian informed";
            case "LATE" -> "Reached after assembly";
            case "EXCUSED" -> "Approved leave";
            default -> "";
        };
    }

    private void mark(long studentId, LocalDate date, String status, String note) {
        jdbc.update("""
            INSERT INTO attendance_records (student_id, attendance_date, status, note)
            VALUES (?, ?, ?, ?)
            """, studentId, Date.valueOf(date), status, note);
    }

    private void seedReports() {
        String[] testNames = {"Unit Test 1", "Lab Quiz", "Reading Check", "Map Work", "Practical Review"};
        String[] remarks = {
            "Good progress in fundamentals.",
            "Participates well in activities.",
            "Keep revising class notes.",
            "Shows steady concept recall.",
            "Confident in practical tasks."
        };
        int[] dayOffsets = {18, 11, 6, 4, 2};
        for (int classNumber = 1; classNumber <= CLASS_COUNT; classNumber++) {
            for (int roll = 1; roll <= STUDENTS_PER_CLASS; roll++) {
                for (int subjectNumber = 1; subjectNumber <= 5; subjectNumber++) {
                    insertReportIfMissing(
                        studentId(classNumber, roll),
                        subjectId(classNumber, subjectNumber),
                        testNames[subjectNumber - 1],
                        LocalDate.now().minusDays(dayOffsets[subjectNumber - 1]),
                        marks(classNumber, roll, subjectNumber - 1),
                        50,
                        remarks[subjectNumber - 1]
                    );
                }
            }
        }
    }

    private int marks(int classNumber, int roll, int offset) {
        return 31 + Math.floorMod(classNumber * 4 + roll * 3 + offset * 5, 18);
    }

    private void insertReport(long studentId, long subjectId, String testName, LocalDate date,
                              int marks, int maxMarks, String remarks) {
        jdbc.update("""
            INSERT INTO test_reports (student_id, subject_id, test_name, test_date,
                                      marks_obtained, max_marks, teacher_remarks)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, studentId, subjectId, testName, Date.valueOf(date), marks, maxMarks, remarks);
    }

    private void insertReportIfMissing(long studentId, long subjectId, String testName, LocalDate date,
                                       int marks, int maxMarks, String remarks) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM test_reports
            WHERE student_id = ? AND subject_id = ? AND test_name = ?
            """, Long.class, studentId, subjectId, testName);
        if (count == null || count == 0) {
            insertReport(studentId, subjectId, testName, date, marks, maxMarks, remarks);
        }
    }

    private void seedSchedule() {
        for (int classNumber = 1; classNumber <= CLASS_COUNT; classNumber++) {
            addWeekSchedule(classNumber);
        }
    }

    private void addWeekSchedule(int classNumber) {
        DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
        long[] subjects = {
            subjectId(classNumber, 1), subjectId(classNumber, 2), subjectId(classNumber, 3),
            subjectId(classNumber, 4), subjectId(classNumber, 5)
        };
        for (int d = 0; d < days.length; d++) {
            insertSchedule(classNumber, subjects[d], days[d], LocalTime.of(8, 30), LocalTime.of(9, 20),
                "Room " + classNumber, "Concept practice");
            insertSchedule(classNumber, subjects[(d + 1) % subjects.length], days[d], LocalTime.of(9, 30), LocalTime.of(10, 20),
                "Room " + classNumber, "Classwork review");
            insertSchedule(classNumber, subjects[(d + 2) % subjects.length], days[d], LocalTime.of(11, 0), LocalTime.of(11, 50),
                "Lab", "Activity period");
        }
    }

    private void insertSchedule(long classId, long subjectId, DayOfWeek day, LocalTime start, LocalTime end,
                                String room, String notes) {
        jdbc.update("""
            INSERT INTO schedule_entries (class_id, subject_id, day_of_week, start_time, end_time, room, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, classId, subjectId, day.name(), Time.valueOf(start), Time.valueOf(end), room, notes);
    }

    private void seedFacilities() {
        insertFacility("Smart Classrooms", "Academic", "Digital boards, projector support, and flexible seating for active lessons.", "Blocks A and B", true);
        insertFacility("Science Laboratory", "Academic", "Physics, chemistry, and biology practical workstations with safety storage.", "Ground Floor, Block B", true);
        insertFacility("Computer Lab", "Academic", "Networked systems for coding, typing practice, and digital assessments.", "First Floor, Block A", true);
        insertFacility("Library", "Learning", "Reference books, newspapers, quiet reading tables, and class-wise issue registers.", "Central Wing", true);
        insertFacility("Health Room", "Student Care", "First-aid desk, rest bed, and emergency guardian contact register.", "Near Reception", true);
        insertFacility("Playground", "Sports", "Morning assembly area, athletics track, volleyball court, and practice zones.", "East Campus", true);
        insertFacility("Transport Desk", "Operations", "Bus route register, pickup points, and daily transport attendance.", "Main Gate", false);
    }

    private void insertFacility(String name, String category, String description, String location, boolean available) {
        jdbc.update("""
            INSERT INTO facilities (name, category, description, location_label, available_to_students)
            VALUES (?, ?, ?, ?, ?)
            """, name, category, description, location, available);
    }

    private void seedCampusMap() {
        insertPlace("Main Gate", "Entry", 10, 70, "Visitor entry, security desk, and transport coordination.");
        insertPlace("Reception", "Office", 24, 52, "Admissions, fee counter, and parent communication.");
        insertPlace("Block A", "Classrooms", 42, 35, "Primary and middle school classrooms.");
        insertPlace("Block B", "Classrooms", 66, 34, "Senior classrooms and science laboratory.");
        insertPlace("Library", "Learning", 45, 60, "Reading room and book issue counter.");
        insertPlace("Playground", "Sports", 76, 72, "Assembly, sports practice, and outdoor activities.");
        insertPlace("Canteen", "Student Care", 26, 78, "Lunch counter and drinking water area.");
    }

    private void insertPlace(String name, String type, int x, int y, String description) {
        jdbc.update("""
            INSERT INTO campus_places (name, place_type, x_position, y_position, description)
            VALUES (?, ?, ?, ?, ?)
            """, name, type, x, y, description);
    }

    private void restartIdentityColumns() {
        jdbc.execute("ALTER TABLE teachers ALTER COLUMN id RESTART WITH 10000");
        jdbc.execute("ALTER TABLE school_classes ALTER COLUMN id RESTART WITH 10000");
        jdbc.execute("ALTER TABLE subjects ALTER COLUMN id RESTART WITH 10000");
        jdbc.execute("ALTER TABLE students ALTER COLUMN id RESTART WITH 10000");
        jdbc.execute("ALTER TABLE app_user ALTER COLUMN id RESTART WITH 10000");
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
    }
}
