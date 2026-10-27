package com.htv.farmverse.farm.adapter.in.rest;

import java.util.List;

import com.htv.farmverse.farm.application.FarmService;
import com.htv.farmverse.farm.domain.Barn;
import com.htv.farmverse.farm.domain.Farm;
import com.htv.farmverse.farm.domain.FarmRuleViolation;
import com.htv.farmverse.service.web.ApiProblem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Adapter vào (REST). Không thấy tenant ở đây: Hibernate lọc theo token. */
@RestController
@RequestMapping("/farms")
public class FarmController {

    static final String CAN_READ = "hasAuthority('farm:farm:read') or hasRole('SUPER_ADMIN')";
    static final String CAN_WRITE = "hasAuthority('farm:farm:write') or hasRole('SUPER_ADMIN')";

    private final FarmService farms;

    public FarmController(FarmService farms) {
        this.farms = farms;
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public List<FarmView> list() {
        return farms.list().stream().map(FarmView::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public FarmView get(@PathVariable String id) {
        return FarmView.of(farms.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_WRITE)
    public FarmView create(@Valid @RequestBody NewFarm body) {
        return FarmView.of(farms.create(body.code(), body.name(), body.address()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_WRITE)
    public FarmView rename(@PathVariable String id, @Valid @RequestBody FarmDetails body) {
        return FarmView.of(farms.rename(id, body.name(), body.address()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(CAN_WRITE)
    public FarmView archive(@PathVariable String id) {
        return FarmView.of(farms.archive(id));
    }

    @PostMapping("/{id}/barns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_WRITE)
    public FarmView addBarn(@PathVariable String id, @Valid @RequestBody NewBarn body) {
        return FarmView.of(farms.addBarn(id, body.code(), body.name(), body.capacity()));
    }

    @DeleteMapping("/{id}/barns/{code}")
    @PreAuthorize(CAN_WRITE)
    public FarmView removeBarn(@PathVariable String id, @PathVariable String code) {
        return FarmView.of(farms.removeBarn(id, code));
    }

    @ExceptionHandler(FarmRuleViolation.class)
    ApiProblem rule(FarmRuleViolation e) {
        return new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), e.getMessage());
    }

    @ExceptionHandler(FarmService.FarmNotFound.class)
    ApiProblem notFound(FarmService.FarmNotFound e) {
        return ApiProblem.notFound("SF-FARM-404", e.getMessage());
    }

    public record NewFarm(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 200) String name,
                          @Size(max = 500) String address) {
    }

    public record FarmDetails(@NotBlank @Size(max = 200) String name, @Size(max = 500) String address) {
    }

    public record NewBarn(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 120) String name,
                          @Positive int capacity) {
    }

    public record FarmView(String id, String code, String name, String address, String status,
                           int totalCapacity, List<Barn> barns) {
        static FarmView of(Farm f) {
            return new FarmView(f.id(), f.code(), f.name(), f.address(), f.status().name(), f.totalCapacity(), f.barns());
        }
    }
}
