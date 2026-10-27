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
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmController.class)
@Import(SecurityConfiguration.class)
class FarmControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FarmService farms;

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
        mvc.perform(get("/farms").with(jwt().authorities(can("farm:farm:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalCapacity").value(40))
                .andExpect(jsonPath("$[0].barns[0].code").value("B-1"));
    }

    @Test
    void domainRuleBecomesProblemDetail422() throws Exception {
        given(farms.addBarn(any(), any(), any(), anyInt()))
                .willThrow(new FarmRuleViolation("SF-FARM-007", "Mã chuồng B-1 đã có trong farm"));

        mvc.perform(post("/farms/f1/barns").with(jwt().authorities(can("farm:farm:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"B-1\",\"name\":\"x\",\"capacity\":1}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SF-FARM-007"));
    }
}
