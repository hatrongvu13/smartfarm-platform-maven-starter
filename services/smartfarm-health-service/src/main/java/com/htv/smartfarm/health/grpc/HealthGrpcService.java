package com.htv.smartfarm.health.grpc;

import com.htv.smartfarm.health.domain.HealthCommands;
import com.htv.smartfarm.proto.health.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class HealthGrpcService extends AnimalHealthServiceGrpc.AnimalHealthServiceImplBase {
    private final HealthCommands commands;

    public HealthGrpcService(HealthCommands commands) {
        this.commands = commands;
    }

    private String tenant() {
        String t = GrpcSecurityContext.TENANT.get();
        if (t == null || t.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return t;
    }

    private static <T> void respond(StreamObserver<T> observer, Supplier<T> action) {
        try {
            T value = action.get();
            observer.onNext(value);
            observer.onCompleted();
        } catch (StatusRuntimeException e) {
            observer.onError(e);
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (DuplicateKeyException e) {
            observer.onError(Status.ALREADY_EXISTS.withDescription("duplicate idempotency key").asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL.withDescription("health operation failed").asRuntimeException());
        }
    }

    @Override
    public void recordObservation(RecordObservationRequest req, StreamObserver<ObservationResponse> observer) {
        respond(observer, () -> ObservationResponse.newBuilder().setObservation(commands.recordObservation(tenant(), req)).build());
    }

    @Override
    public void listObservations(ListObservationsRequest req, StreamObserver<ListObservationsResponse> observer) {
        respond(observer, () -> ListObservationsResponse.newBuilder().addAllObservations(commands.observations(tenant(), req)).build());
    }

    @Override
    public void recordVaccination(RecordVaccinationRequest req, StreamObserver<VaccinationResponse> observer) {
        respond(observer, () -> VaccinationResponse.newBuilder().setVaccination(commands.recordVaccination(tenant(), req)).build());
    }

    @Override
    public void listVaccinations(ListVaccinationsRequest req, StreamObserver<ListVaccinationsResponse> observer) {
        respond(observer, () -> ListVaccinationsResponse.newBuilder().addAllVaccinations(commands.vaccinations(tenant(), req)).build());
    }
}
