package com.maimai.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MissingResourceTest {
    @RestController
    static class Probe {
        @GetMapping("/missing-image")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET,"private/path/missing.jpg","/uploads/products/missing.jpg");
        }
    }
    @Test void missingMediaIsNotAServerFailureAndDoesNotRevealStoragePath() throws Exception {
        standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/missing-image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("图片或文件暂不可用"));
    }
}
