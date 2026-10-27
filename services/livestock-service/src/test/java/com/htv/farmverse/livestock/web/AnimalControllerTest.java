package com.htv.farmverse.livestock.web;

import java.util.List;

import com.htv.farmverse.livestock.animal.AnimalCommandService;
import com.htv.farmverse.livestock.animal.AnimalEntity;
import com.htv.farmverse.livestock.config.SecurityConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Token Relay tới đây: kiểm quyền theo claim {@code perms} / {@code roles}, tenant lấy từ token. */
@WebMvcTest(AnimalController.class)
@Import(SecurityConfiguration.class)
class AnimalControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AnimalCommandService animals;

    static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor user(String... perms) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject("user-1").claim("tenant_id", "tenant-a").claim("perms", List.of(perms)))
                .authorities(java.util.Arrays.stream(perms)
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }

    @Test
    void noTokenIsRejected() throws Exception {
        mvc.perform(get("/animals").param("farmId", "farm-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingPermissionIsForbidden() throws Exception {
        mvc.perform(get("/animals").param("farmId", "farm-1").with(user("livestock:task:read")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(animals);
    }

    @Test
    void readerListsAnimalsOfOwnTenantOnly() throws Exception {
        given(animals.list(eq("tenant-a"), eq("farm-1"), isNull(), anyInt())).willReturn(List.of(
                new AnimalEntity("a-1", "tenant-a", "farm-1", null, null, "TAG-1", "cow", null,
                        "ANIMAL_STATUS_ACTIVE", 1L)));

        mvc.perform(get("/animals").param("farmId", "farm-1").with(user("livestock:animal:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tagCode").value("TAG-1"));
    }

    @Test
    void writerRegistersAnimal() throws Exception {
        given(animals.register(eq("tenant-a"), eq("user-1"), any())).willReturn(
                new AnimalEntity("a-2", "tenant-a", "farm-1", null, null, "TAG-2", null, null,
                        "ANIMAL_STATUS_ACTIVE", 1L));

        mvc.perform(post("/animals").with(user("livestock:animal:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"farmId\":\"farm-1\",\"tagCode\":\"TAG-2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("a-2"));
    }

    @Test
    void invalidBodyReturnsProblemDetail() throws Exception {
        mvc.perform(post("/animals").with(user("livestock:animal:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"farmId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
