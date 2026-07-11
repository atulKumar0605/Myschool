package com.myschool.controller;

import com.myschool.security.CurrentUserService;
import com.myschool.service.SchoolService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/student")
public class StudentController {
    private final CurrentUserService currentUserService;
    private final SchoolService schoolService;

    public StudentController(CurrentUserService currentUserService, SchoolService schoolService) {
        this.currentUserService = currentUserService;
        this.schoolService = schoolService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        SchoolService.SessionUser user = currentUserService.currentUser();
        if (user.studentId() == null) {
            throw new IllegalStateException("Student profile is missing.");
        }
        SchoolService.StudentInfo student = schoolService.studentById(user.studentId())
            .orElseThrow(() -> new IllegalStateException("Student record is missing."));
        SchoolService.ClassInfo classInfo = schoolService.classForStudent(student.id())
            .orElseThrow(() -> new IllegalStateException("Class assignment is missing."));

        model.addAttribute("user", user);
        model.addAttribute("student", student);
        model.addAttribute("classInfo", classInfo);
        model.addAttribute("subjects", schoolService.listSubjects(classInfo.id()));
        model.addAttribute("schedule", schoolService.listSchedule(classInfo.id()));
        model.addAttribute("reports", schoolService.listReportsForStudent(student.id()));
        model.addAttribute("attendance", schoolService.attendanceOverview(student.id()));
        model.addAttribute("recentAttendance", schoolService.recentAttendance(student.id()));
        model.addAttribute("facilities", schoolService.listFacilities());
        model.addAttribute("places", schoolService.listCampusPlaces());
        return "student/dashboard";
    }
}
