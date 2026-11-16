package com.homeinspect.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HomeInspectApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void homePageRendersWithLayout() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bienvenido a home-inspect")))
                .andExpect(content().string(containsString("/webjars/bootstrap/5.3.9/dist/css/bootstrap.min.css")));
    }

    @Test
    void bootstrapIsServedFromWebJar() throws Exception {
        mvc.perform(get("/webjars/bootstrap/dist/css/bootstrap.min.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bootstrap  v5.3.9")));
    }
}
