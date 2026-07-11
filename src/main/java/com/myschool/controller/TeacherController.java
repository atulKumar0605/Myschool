package com.myschool.controller;

import com.myschool.security.CurrentUserService;
import com.myschool.service.SchoolService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher")
public class TeacherController {
    private final CurrentUserService currentUserService;
    private final SchoolService schoolService;

    public TeacherController(CurrentUserService currentUserService, SchoolService schoolService) {
        this.currentUserService = currentUserService;
        this.schoolService = schoolService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        TeacherContext context = teacherContext();
        addTeacherCommon(model, context);
        model.addAttribute("studentCount", schoolService.countStudents(context.classInfo().id()));
        model.addAttribute("subjectCount", schoolService.countSubjects(context.classInfo().id()));
        model.addAttribute("reportCount", schoolService.countReports(context.classInfo().id()));
        model.addAttribute("attendanceSummary", schoolService.attendanceSummary(context.classInfo().id(), LocalDate.now()));
        model.addAttribute("students", schoolService.listStudents(context.classInfo().id()));
        model.addAttribute("schedule", schoolService.listSchedule(context.classInfo().id()));
        model.addAttribute("reports", schoolService.listReportsForClass(context.classInfo().id()));
        return "teacher/dashboard";
    }

    @GetMapping("/students")
    public String students(Model model) {
        TeacherContext context = teacherContext();
        List<SchoolService.StudentSubjectPerformance> performanceRows =
            schoolService.listSubjectPerformanceForClass(context.classInfo().id());
        Map<Long, List<SchoolService.StudentSubjectPerformance>> performanceByStudent =
            performanceRows.stream().collect(Collectors.groupingBy(SchoolService.StudentSubjectPerformance::studentId));
        addTeacherCommon(model, context);
        model.addAttribute("students", schoolService.listStudents(context.classInfo().id()));
        model.addAttribute("performanceByStudent", performanceByStudent);
        return "teacher/students";
    }

    @PostMapping("/students")
    public String addStudent(@RequestParam String fullName,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
                             @RequestParam String email,
                             @RequestParam String guardianName,
                             @RequestParam String guardianPhone,
                             @RequestParam String address,
                             @RequestParam String bloodGroup,
                             RedirectAttributes redirectAttributes) {
        TeacherContext context = teacherContext();
        try {
            schoolService.addStudent(context.classInfo().id(), new SchoolService.StudentForm(
                fullName, dateOfBirth, email, guardianName, guardianPhone, address, bloodGroup
            ));
            redirectAttributes.addFlashAttribute("success", "Student added with generated roll and admission number.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/teacher/students";
    }

    @PostMapping("/students/{studentId}")
    public String updateStudent(@PathVariable Long studentId,
                                @RequestParam String fullName,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
                                @RequestParam String email,
                                @RequestParam String guardianName,
                                @RequestParam String guardianPhone,
                                @RequestParam String address,
                                @RequestParam String bloodGroup,
                                RedirectAttributes redirectAttributes) {
        TeacherContext context = teacherContext();
        try {
            schoolService.updateStudent(context.classInfo().id(), studentId, new SchoolService.StudentForm(
                fullName, dateOfBirth, email, guardianName, guardianPhone, address, bloodGroup
            ));
            redirectAttributes.addFlashAttribute("success", "Student details updated.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/teacher/students";
    }

    @GetMapping("/attendance")
    public String attendance(@RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             Model model) {
        TeacherContext context = teacherContext();
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        addTeacherCommon(model, context);
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("previousDate", selectedDate.minusDays(1));
        model.addAttribute("nextDate", selectedDate.plusDays(1));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("recentAttendanceDates", schoolService.recentAttendanceDates(context.classInfo().id()));
        model.addAttribute("attendanceRows", schoolService.listAttendance(context.classInfo().id(), selectedDate));
        model.addAttribute("attendanceSummary", schoolService.attendanceSummary(context.classInfo().id(), selectedDate));
        return "teacher/attendance";
    }

    @PostMapping("/attendance")
    public String saveAttendance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
                                 @RequestParam Map<String, String> form,
                                 RedirectAttributes redirectAttributes) {
        TeacherContext context = teacherContext();
        try {
            for (SchoolService.StudentInfo student : schoolService.listStudents(context.classInfo().id())) {
                String suffix = String.valueOf(student.id());
                String status = form.get("status_" + suffix);
                String note = form.get("note_" + suffix);
                if (status != null) {
                    schoolService.upsertAttendance(context.classInfo().id(), student.id(), attendanceDate, status, note);
                }
            }
            redirectAttributes.addFlashAttribute("success", "Attendance saved for " + attendanceDate + ".");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        redirectAttributes.addAttribute("date", attendanceDate.toString());
        return "redirect:/teacher/attendance";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        TeacherContext context = teacherContext();
        addTeacherCommon(model, context);
        model.addAttribute("students", schoolService.listStudents(context.classInfo().id()));
        model.addAttribute("subjects", schoolService.listSubjects(context.classInfo().id()));
        model.addAttribute("reports", schoolService.listReportsForClass(context.classInfo().id()));
        return "teacher/reports";
    }

    @PostMapping("/reports")
    public String addReport(@RequestParam Long studentId,
                            @RequestParam Long subjectId,
                            @RequestParam String testName,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate testDate,
                            @RequestParam int marksObtained,
                            @RequestParam int maxMarks,
                            @RequestParam String teacherRemarks,
                            RedirectAttributes redirectAttributes) {
        TeacherContext context = teacherContext();
        try {
            schoolService.addReport(context.classInfo().id(), new SchoolService.ReportForm(
                studentId, subjectId, testName, testDate, marksObtained, maxMarks, teacherRemarks
            ));
            redirectAttributes.addFlashAttribute("success", "Test report added.");
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/teacher/reports";
    }

    private void addTeacherCommon(Model model, TeacherContext context) {
        model.addAttribute("user", context.user());
        model.addAttribute("classInfo", context.classInfo());
        model.addAttribute("subjects", schoolService.listSubjects(context.classInfo().id()));
        model.addAttribute("facilities", schoolService.listFacilities());
        model.addAttribute("places", schoolService.listCampusPlaces());
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
}
