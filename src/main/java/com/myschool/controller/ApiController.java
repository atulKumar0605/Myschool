package com.myschool.controller;

import com.myschool.security.CurrentUserService;
import com.myschool.service.SchoolService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthenticationManager authenticationManager;
    private final CurrentUserService currentUserService;
    private final SchoolService schoolService;

    public ApiController(AuthenticationManager authenticationManager,
                         CurrentUserService currentUserService,
                         SchoolService schoolService) {
        this.authenticationManager = authenticationManager;
        this.currentUserService = currentUserService;
        this.schoolService = schoolService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "myschool");
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication) {
        return sessionResponse(authentication);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password())
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true)
                .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return ResponseEntity.ok(sessionResponse(authentication));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponse("Invalid username or password."));
        }
    }

    @PostMapping("/logout")
    public MessageResponse logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return new MessageResponse("Logged out.");
    }

    @GetMapping("/teacher/dashboard")
    public TeacherDashboardResponse teacherDashboard() {
        TeacherContext context = teacherContext();
        Long classId = context.classInfo().id();
        return new TeacherDashboardResponse(
            context.user(),
            context.classInfo(),
            schoolService.countStudents(classId),
            schoolService.countSubjects(classId),
            schoolService.countReports(classId),
            schoolService.attendanceSummary(classId, LocalDate.now()),
            schoolService.listStudents(classId),
            schoolService.listSubjects(classId),
            schoolService.listSchedule(classId),
            schoolService.listReportsForClass(classId),
            schoolService.listFacilities(),
            schoolService.listCampusPlaces()
        );
    }

    @GetMapping("/teacher/students")
    public TeacherStudentsResponse teacherStudents() {
        TeacherContext context = teacherContext();
        List<SchoolService.StudentSubjectPerformance> performanceRows =
            schoolService.listSubjectPerformanceForClass(context.classInfo().id());
        Map<Long, List<SchoolService.StudentSubjectPerformance>> performanceByStudent =
            performanceRows.stream().collect(Collectors.groupingBy(SchoolService.StudentSubjectPerformance::studentId));
        return new TeacherStudentsResponse(
            context.user(),
            context.classInfo(),
            schoolService.listStudents(context.classInfo().id()),
            schoolService.listSubjects(context.classInfo().id()),
            performanceByStudent
        );
    }

    @PostMapping("/teacher/students")
    public MessageResponse addStudent(@RequestBody SchoolService.StudentForm form) {
        TeacherContext context = teacherContext();
        schoolService.addStudent(context.classInfo().id(), form);
        return new MessageResponse("Student added.");
    }

    @PutMapping("/teacher/students/{studentId}")
    public MessageResponse updateStudent(@PathVariable Long studentId,
                                         @RequestBody SchoolService.StudentForm form) {
        TeacherContext context = teacherContext();
        schoolService.updateStudent(context.classInfo().id(), studentId, form);
        return new MessageResponse("Student updated.");
    }

    @GetMapping("/teacher/attendance")
    public TeacherAttendanceResponse teacherAttendance(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        TeacherContext context = teacherContext();
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        Long classId = context.classInfo().id();
        return new TeacherAttendanceResponse(
            context.user(),
            context.classInfo(),
            selectedDate,
            selectedDate.minusDays(1),
            selectedDate.plusDays(1),
            LocalDate.now(),
            schoolService.recentAttendanceDates(classId),
            schoolService.listAttendance(classId, selectedDate),
            schoolService.attendanceSummary(classId, selectedDate)
        );
    }

    @PostMapping("/teacher/attendance")
    public MessageResponse saveAttendance(@RequestBody AttendanceSaveRequest request) {
        TeacherContext context = teacherContext();
        for (AttendanceUpdate update : request.entries()) {
            schoolService.upsertAttendance(
                context.classInfo().id(),
                update.studentId(),
                request.attendanceDate(),
                update.status(),
                update.note()
            );
        }
        return new MessageResponse("Attendance saved.");
    }

    @GetMapping("/teacher/reports")
    public TeacherReportsResponse teacherReports() {
        TeacherContext context = teacherContext();
        Long classId = context.classInfo().id();
        return new TeacherReportsResponse(
            context.user(),
            context.classInfo(),
            schoolService.listStudents(classId),
            schoolService.listSubjects(classId),
            schoolService.listReportsForClass(classId)
        );
    }

    @PostMapping("/teacher/reports")
    public MessageResponse addReport(@RequestBody SchoolService.ReportForm form) {
        TeacherContext context = teacherContext();
        schoolService.addReport(context.classInfo().id(), form);
        return new MessageResponse("Report added.");
    }

    @GetMapping("/teacher/migration")
    public TeacherMigrationResponse teacherMigration() {
        TeacherContext context = teacherContext();
        return new TeacherMigrationResponse(
            context.user(),
            context.classInfo(),
            schoolService.migrationPreview(context.classInfo().id())
        );
    }

    @PostMapping("/teacher/migration/promote")
    public TeacherMigrationPromoteResponse promoteClassStudents() {
        TeacherContext context = teacherContext();
        SchoolService.ClassMigrationResult result =
            schoolService.promoteClassStudents(context.classInfo().id());
        return new TeacherMigrationPromoteResponse(
            "Promoted " + result.migratedStudents() + " students to "
                + result.toClass().name() + " " + result.toClass().section() + ".",
            result
        );
    }

    @GetMapping("/student/dashboard")
    public StudentDashboardResponse studentDashboard() {
        SchoolService.SessionUser user = currentUserService.currentUser();
        if (user.studentId() == null) {
            throw new IllegalStateException("Student profile is missing.");
        }
        SchoolService.StudentInfo student = schoolService.studentById(user.studentId())
            .orElseThrow(() -> new IllegalStateException("Student record is missing."));
        SchoolService.ClassInfo classInfo = schoolService.classForStudent(student.id())
            .orElseThrow(() -> new IllegalStateException("Class assignment is missing."));
        return new StudentDashboardResponse(
            user,
            student,
            classInfo,
            schoolService.listSubjects(classInfo.id()),
            schoolService.listSchedule(classInfo.id()),
            schoolService.listReportsForStudent(student.id()),
            schoolService.attendanceOverview(student.id()),
            schoolService.recentAttendance(student.id()),
            schoolService.listFacilities(),
            schoolService.listCampusPlaces()
        );
    }

    private SessionResponse sessionResponse(Authentication authentication) {
        if (!isApplicationUser(authentication)) {
            return new SessionResponse(false, null, null, null, null);
        }
        SchoolService.SessionUser user = schoolService.findSessionUser(authentication.getName()).orElse(null);
        if (user == null) {
            return new SessionResponse(false, null, null, null, null);
        }
        String role = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .findFirst()
            .orElse(user.role());
        String landingPath = "TEACHER".equals(role) ? "/teacher" : "/student";
        return new SessionResponse(true, user.username(), user.displayName(), role, landingPath);
    }

    private boolean isApplicationUser(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch("ROLE_ANONYMOUS"::equals);
    }

    private TeacherContext teacherContext() {
        SchoolService.SessionUser user = currentUserService.currentUser();
        if (user.teacherId() == null) {
            throw new IllegalStateException("Teacher profile is missing.");
        }
        SchoolService.ClassInfo classInfo = schoolService.classForTeacher(user.teacherId())
            .orElseThrow(() -> new IllegalStateException("No class has been assigned to this teacher."));
        return new TeacherContext(user, classInfo);
    }

    private record TeacherContext(SchoolService.SessionUser user, SchoolService.ClassInfo classInfo) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record MessageResponse(String message) {
    }

    public record SessionResponse(boolean authenticated, String username, String displayName,
                                  String role, String landingPath) {
    }

    public record AttendanceUpdate(Long studentId, String status, String note) {
    }

    public record AttendanceSaveRequest(LocalDate attendanceDate, List<AttendanceUpdate> entries) {
    }

    public record TeacherDashboardResponse(SchoolService.SessionUser user,
                                           SchoolService.ClassInfo classInfo,
                                           long studentCount,
                                           long subjectCount,
                                           long reportCount,
                                           SchoolService.AttendanceSummary attendanceSummary,
                                           List<SchoolService.StudentInfo> students,
                                           List<SchoolService.SubjectInfo> subjects,
                                           List<SchoolService.ScheduleEntry> schedule,
                                           List<SchoolService.TestReportRow> reports,
                                           List<SchoolService.FacilityRow> facilities,
                                           List<SchoolService.CampusPlaceRow> places) {
    }

    public record TeacherStudentsResponse(SchoolService.SessionUser user,
                                          SchoolService.ClassInfo classInfo,
                                          List<SchoolService.StudentInfo> students,
                                          List<SchoolService.SubjectInfo> subjects,
                                          Map<Long, List<SchoolService.StudentSubjectPerformance>> performanceByStudent) {
    }

    public record TeacherAttendanceResponse(SchoolService.SessionUser user,
                                            SchoolService.ClassInfo classInfo,
                                            LocalDate selectedDate,
                                            LocalDate previousDate,
                                            LocalDate nextDate,
                                            LocalDate today,
                                            List<LocalDate> recentAttendanceDates,
                                            List<SchoolService.AttendanceRow> attendanceRows,
                                            SchoolService.AttendanceSummary attendanceSummary) {
    }

    public record TeacherReportsResponse(SchoolService.SessionUser user,
                                         SchoolService.ClassInfo classInfo,
                                         List<SchoolService.StudentInfo> students,
                                         List<SchoolService.SubjectInfo> subjects,
                                         List<SchoolService.TestReportRow> reports) {
    }

    public record TeacherMigrationResponse(SchoolService.SessionUser user,
                                           SchoolService.ClassInfo classInfo,
                                           SchoolService.ClassMigrationPreview migration) {
    }

    public record TeacherMigrationPromoteResponse(String message,
                                                  SchoolService.ClassMigrationResult result) {
    }

    public record StudentDashboardResponse(SchoolService.SessionUser user,
                                           SchoolService.StudentInfo student,
                                           SchoolService.ClassInfo classInfo,
                                           List<SchoolService.SubjectInfo> subjects,
                                           List<SchoolService.ScheduleEntry> schedule,
                                           List<SchoolService.TestReportRow> reports,
                                           SchoolService.StudentAttendanceOverview attendance,
                                           List<SchoolService.AttendanceHistoryRow> recentAttendance,
                                           List<SchoolService.FacilityRow> facilities,
                                           List<SchoolService.CampusPlaceRow> places) {
    }
}
