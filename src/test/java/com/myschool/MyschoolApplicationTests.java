package com.myschool;

import com.myschool.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:myschool-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class MyschoolApplicationTests {
    @Autowired
    private SchoolService schoolService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsWithSeededTeacherAndStudentAccounts() {
        assertThat(schoolService.findAuthUser("teacher@myschool.local")).isPresent();
        assertThat(schoolService.findAuthUser("student@myschool.local")).isPresent();
        assertThat(schoolService.countStudents(8L)).isGreaterThanOrEqualTo(15);
        assertThat(schoolService.countReports(8L)).isGreaterThanOrEqualTo(75);
        assertThat(schoolService.listSubjectPerformanceForClass(8L))
            .filteredOn(performance -> performance.studentId().equals(801L))
            .hasSize(5);
    }

    @Test
    void teacherLoginRedirectsToTeacherDashboard() throws Exception {
        mockMvc.perform(formLogin("/login")
                .user("teacher@myschool.local")
                .password("teacher123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/teacher/dashboard"));
    }

    @Test
    void teacherAndStudentDashboardsRenderForTheirRoles() throws Exception {
        mockMvc.perform(get("/teacher/dashboard")
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Teacher Workspace")))
            .andExpect(content().string(containsString("Class 8")));

        mockMvc.perform(get("/student/dashboard")
                .with(user("student@myschool.local").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Student Read-Only Portal")))
            .andExpect(content().string(containsString("Aarav Singh")));
    }

    @Test
    void teacherStudentAndReportGalleriesRender() throws Exception {
        mockMvc.perform(get("/teacher/students")
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Student Gallery")))
            .andExpect(content().string(containsString("Subject Performance")))
            .andExpect(content().string(containsString("Aarav Singh")))
            .andExpect(content().string(containsString("ADM-2026-08A-001")));

        mockMvc.perform(get("/teacher/reports")
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Report Gallery")))
            .andExpect(content().string(containsString("Unit Test 1")));
    }

    @Test
    void teacherCanRegisterStudentWithGeneratedRollAndAdmissionNumber() throws Exception {
        mockMvc.perform(post("/teacher/students")
                .with(user("teacher@myschool.local").roles("TEACHER"))
                .with(csrf())
                .param("fullName", "New Demo Student")
                .param("dateOfBirth", "2012-04-16")
                .param("email", "new.demo.student@myschool.local")
                .param("guardianName", "Mr. Demo Guardian")
                .param("guardianPhone", "+91 90000 00999")
                .param("address", "House 99, Demo Road, Chichli")
                .param("bloodGroup", "O+"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/teacher/students"));

        assertThat(schoolService.listStudents(8L))
            .anySatisfy(student -> {
                assertThat(student.fullName()).isEqualTo("New Demo Student");
                assertThat(student.rollNumber()).isEqualTo(16);
                assertThat(student.admissionNumber()).isEqualTo("ADM-2026-08A-016");
            });
    }

    @Test
    void teacherCanEditAttendanceForPreviousDate() throws Exception {
        LocalDate previousDate = LocalDate.now().minusDays(21);

        mockMvc.perform(get("/teacher/attendance")
                .param("date", previousDate.toString())
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Editing attendance for: " + previousDate)))
            .andExpect(content().string(containsString("Attendance Date")));

        mockMvc.perform(post("/teacher/attendance")
                .with(user("teacher@myschool.local").roles("TEACHER"))
                .with(csrf())
                .param("attendanceDate", previousDate.toString())
                .param("status_801", "ABSENT")
                .param("note_801", "Updated previous date"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/teacher/attendance?date=" + previousDate));

        assertThat(schoolService.listAttendance(8L, previousDate))
            .filteredOn(row -> row.studentId().equals(801L))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.status()).isEqualTo("ABSENT");
                assertThat(row.note()).isEqualTo("Updated previous date");
            });
    }
}
