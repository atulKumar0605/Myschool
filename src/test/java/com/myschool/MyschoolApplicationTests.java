package com.myschool;

import com.myschool.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void apiHealthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.application").value("myschool"));
    }

    @Test
    void apiLoginCreatesSessionForSplitFrontend() throws Exception {
        mockMvc.perform(get("/api/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));

        MvcResult login = mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "teacher@myschool.local",
                      "password": "teacher123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.role").value("TEACHER"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/teacher/dashboard").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.classInfo.name").value("Class 8"))
            .andExpect(jsonPath("$.studentCount").isNumber());
    }

    @Test
    void apiDashboardsReturnRoleSpecificData() throws Exception {
        mockMvc.perform(get("/api/teacher/dashboard")
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.classInfo.name").value("Class 8"))
            .andExpect(jsonPath("$.studentCount").isNumber())
            .andExpect(jsonPath("$.students[0].fullName").value("Aarav Singh"));

        mockMvc.perform(get("/api/student/dashboard")
                .with(user("student@myschool.local").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.student.fullName").value("Aarav Singh"))
            .andExpect(jsonPath("$.classInfo.name").value("Class 8"));
    }

    @Test
    void apiStudentAndReportGalleriesReturnJson() throws Exception {
        mockMvc.perform(get("/api/teacher/students")
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.students[0].fullName").value("Aarav Singh"))
            .andExpect(jsonPath("$.students[0].admissionNumber").value("ADM-2026-08A-001"))
            .andExpect(jsonPath("$.performanceByStudent.801").isArray());

        mockMvc.perform(get("/api/teacher/reports")
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reports[0].testName").exists());
    }

    @Test
    void teacherCanRegisterStudentThroughApi() throws Exception {
        mockMvc.perform(post("/api/teacher/students")
                .with(user("teacher@myschool.local").roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fullName": "New Demo Student",
                      "dateOfBirth": "2012-04-16",
                      "email": "new.demo.student@myschool.local",
                      "guardianName": "Mr. Demo Guardian",
                      "guardianPhone": "+91 90000 00999",
                      "address": "House 99, Demo Road, Chichli",
                      "bloodGroup": "O+"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Student added."));

        assertThat(schoolService.listStudents(8L))
            .anySatisfy(student -> {
                assertThat(student.fullName()).isEqualTo("New Demo Student");
                assertThat(student.rollNumber()).isEqualTo(16);
                assertThat(student.admissionNumber()).isEqualTo("ADM-2026-08A-016");
            });
    }

    @Test
    void teacherCanEditAttendanceForPreviousDateThroughApi() throws Exception {
        LocalDate previousDate = LocalDate.now().minusDays(21);

        mockMvc.perform(get("/api/teacher/attendance")
                .param("date", previousDate.toString())
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.selectedDate").value(previousDate.toString()))
            .andExpect(jsonPath("$.attendanceRows[0].studentId").value(801));

        mockMvc.perform(post("/api/teacher/attendance")
                .with(user("teacher@myschool.local").roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "attendanceDate": "%s",
                      "entries": [
                        {
                          "studentId": 801,
                          "status": "ABSENT",
                          "note": "Updated previous date"
                        }
                      ]
                    }
                    """.formatted(previousDate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Attendance saved."));

        assertThat(schoolService.listAttendance(8L, previousDate))
            .filteredOn(row -> row.studentId().equals(801L))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.status()).isEqualTo("ABSENT");
                assertThat(row.note()).isEqualTo("Updated previous date");
            });
    }

    @Test
    void oldServerRenderedRoutesAreRemoved() throws Exception {
        mockMvc.perform(get("/teacher/dashboard")
                .with(user("teacher@myschool.local").roles("TEACHER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/student/dashboard")
                .with(user("student@myschool.local").roles("STUDENT")))
            .andExpect(status().isForbidden());
    }
}
