package com.htv.farmverse.farm.adapter.in.rest;

import java.util.List;

import com.htv.farmverse.farm.application.FarmService;
import com.htv.farmverse.farm.config.SecurityConfiguration;
import com.htv.farmverse.farm.domain.Farm;
import com.htv.farmverse.farm.domain.FarmRuleViolation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmController.class)
@Import(SecurityConfiguration.class)
class FarmControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FarmService farms;

    /** Mọi token gateway gửi xuống đều có scope farmverse.api; thiếu scope thì 403 trước cả @PreAuthorize. */
    static final GrantedAuthority API = new SimpleGrantedAuthority("SCOPE_farmverse.api");

    static GrantedAuthority can(String permission) {
        return new SimpleGrantedAuthority(permission);
    }

    @Test
    void readRequiresPermission() throws Exception {
        mvc.perform(get("/farms")).andExpect(status().isUnauthorized());
        mvc.perform(get("/farms").with(jwt())).andExpect(status().isForbidden());

        Farm farm = Farm.create("valley", "Valley", null);
        farm.addBarn("B-1", "Chuồng bò", 40);
        given(farms.list()).willReturn(List.of(farm));
        mvc.perform(get("/farms").with(jwt().authorities(API, can("farm:farm:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalCapacity").value(40))
                .andExpect(jsonPath("$[0].barns[0].code").value("B-1"));
    }

    @Test
    void securityErrorsAreProblemDetailWithCode() throws Exception {
        mvc.perform(get("/farms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SF-AUTH-001"));
        mvc.perform(get("/farms").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SF-AUTH-002"));
    }

    @Test
    void invalidBodyIsProblemDetail400WithFieldErrors() throws Exception {
        mvc.perform(post("/farms").with(jwt().authorities(API, can("farm:farm:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SF-VALIDATION-001"))
                .andExpect(jsonPath("$.errors[?(@.field=='code')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    @Test
    void concurrentUpdateBecomesProblemDetail409() throws Exception {
        given(farms.archive(any()))
                .willThrow(new ObjectOptimisticLockingFailureException("FarmEntity", "f1"));

        mvc.perform(post("/farms/f1/archive").with(jwt().authorities(API, can("farm:farm:write"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SF-FARM-010"));
    }

    @Test
    void duplicateKeyRaceBecomesProblemDetail409WithoutLeakingDbMessage() throws Exception {
        given(farms.create(any(), any(), any()))
                .willThrow(new DataIntegrityViolationException("duplicate key uk_farm_tenant_code"));

        mvc.perform(post("/farms").with(jwt().authorities(API, can("farm:farm:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"valley\",\"name\":\"Valley\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SF-FARM-011"))
                .andExpect(content().string(not(containsString("uk_farm_tenant_code"))));
    }

    @Test
    void domainRuleBecomesProblemDetail422() throws Exception {
        given(farms.addBarn(any(), any(), any(), anyInt()))
                .willThrow(new FarmRuleViolation("SF-FARM-007", "Mã chuồng B-1 đã có trong farm"));

        mvc.perform(post("/farms/f1/barns").with(jwt().authorities(API, can("farm:farm:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"B-1\",\"name\":\"x\",\"capacity\":1}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SF-FARM-007"));
    }

    @Test
    void assignManagerCallsTheUseCaseAndReturnsTheManagerId() throws Exception {
        Farm farm = Farm.create("valley", "Valley", null);
        farm.assignManager("u1");
        given(farms.assignManager("f1", "u1")).willReturn(farm);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/farms/f1/manager")
                        .with(jwt().authorities(API, can("farm:farm:write")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerUserId").value("u1"));
    }

    /** P3: identity không gọi được khi gán → 503 SF-FARM-503, ghi không được thực hiện. */
    @Test
    void assignManagerWhenDirectoryUnavailableIsProblemDetail503() throws Exception {
        given(farms.assignManager(any(), any()))
                .willThrow(new com.htv.farmverse.farm.application.MemberDirectory.DirectoryUnavailable(new RuntimeException()));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/farms/f1/manager")
                        .with(jwt().authorities(API, can("farm:farm:write")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u1\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SF-FARM-503"));
    }

    @Test
    void assignManagerNeedsWritePermission() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/farms/f1/manager")
                        .with(jwt().authorities(API, can("farm:farm:read")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"u1\"}"))
                .andExpect(status().isForbidden());
    }
}
