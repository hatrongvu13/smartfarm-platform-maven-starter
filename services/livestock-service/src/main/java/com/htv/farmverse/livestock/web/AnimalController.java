package com.htv.farmverse.livestock.web;

import java.util.List;

import com.htv.farmverse.livestock.animal.AnimalCommandService;
import com.htv.farmverse.livestock.animal.AnimalEntity;
import com.htv.farmverse.proto.livestock.v1.Animal;
import com.htv.farmverse.proto.livestock.v1.RegisterAnimalRequest;
import com.htv.farmverse.service.security.FarmVerseClaims;
import com.htv.farmverse.service.web.ApiProblem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sổ vật nuôi. Tenant luôn lấy từ token, không lấy từ request. */
@Validated
@RestController
@RequestMapping("/animals")
public class AnimalController {

    static final String CAN_READ = "hasAuthority('livestock:animal:read') or hasRole('SUPER_ADMIN')";
    static final String CAN_WRITE = "hasAuthority('livestock:animal:write') or hasRole('SUPER_ADMIN')";

    private final AnimalCommandService animals;

    public AnimalController(AnimalCommandService animals) {
        this.animals = animals;
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public List<AnimalView> list(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam @NotBlank String farmId,
                                 @RequestParam(required = false) String batchId,
                                 @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return animals.list(tenant(jwt), farmId, batchId, limit).stream().map(AnimalView::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public AnimalView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        AnimalEntity animal = animals.get(tenant(jwt), id);
        if (animal == null) {
            throw ApiProblem.notFound("SF-LIVESTOCK-001", "Không có vật nuôi " + id);
        }
        return AnimalView.of(animal);
    }

    /** Đăng ký trùng (cùng farm + mã tai) trả lại con đã có. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_WRITE)
    public AnimalView register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewAnimal body) {
        Animal.Builder animal = Animal.newBuilder().setFarmId(body.farmId()).setTagCode(body.tagCode());
        if (body.species() != null) animal.setSpecies(body.species());
        if (body.barnId() != null) animal.setBarnId(body.barnId());
        if (body.batchId() != null) animal.setBatchId(body.batchId());
        RegisterAnimalRequest request = RegisterAnimalRequest.newBuilder().setAnimal(animal).build();
        return AnimalView.of(animals.register(tenant(jwt), jwt.getSubject(), request));
    }

    private static String tenant(Jwt jwt) {
        return jwt.getClaimAsString(FarmVerseClaims.TENANT_ID);
    }

    public record NewAnimal(@NotBlank @Size(max = 100) String farmId,
                            @NotBlank @Size(max = 100) String tagCode,
                            @Size(max = 80) String species,
                            @Size(max = 100) String barnId,
                            @Size(max = 100) String batchId) {
    }

    public record AnimalView(String id, String farmId, String barnId, String batchId, String tagCode,
                             String species, String status, long createdAt) {
        static AnimalView of(AnimalEntity a) {
            return new AnimalView(a.getId(), a.getFarmId(), a.getBarnId(), a.getBatchId(), a.getTagCode(),
                    a.getSpecies(), a.getStatus(), a.getCreatedAt());
        }
    }
}
