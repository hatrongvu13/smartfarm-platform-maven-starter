package com.htv.smartfarm.livestock.grpc;

import com.htv.smartfarm.livestock.animal.AnimalCommandService;
import com.htv.smartfarm.livestock.animal.AnimalEntity;
import com.htv.smartfarm.livestock.schedule.ScheduleCommandService;
import com.htv.smartfarm.livestock.schedule.ScheduleEntity;
import com.htv.smartfarm.livestock.task.TaskCommandService;
import com.htv.smartfarm.livestock.task.TaskEntity;
import com.htv.smartfarm.livestock.task.TaskStore;
import com.htv.smartfarm.proto.livestock.v1.*;
import com.htv.smartfarm.proto.common.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LivestockGrpcService extends LivestockTaskServiceGrpc.LivestockTaskServiceImplBase {
    private final TaskCommandService commands;
    private final AnimalCommandService animals;
    private final ScheduleCommandService schedules;

    public LivestockGrpcService(TaskCommandService commands, AnimalCommandService animals, ScheduleCommandService schedules) {
        this.commands = commands;
        this.animals = animals;
        this.schedules = schedules;
    }

    private static String tenant() {
        String t = GrpcSecurityContext.TENANT.get();
        if (t == null || t.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return t;
    }

    private static String actor() {
        return GrpcSecurityContext.SUBJECT.get();
    }

    /** Common error mapping for lifecycle RPCs. */
    private static <T> void run(StreamObserver<T> observer, java.util.function.Supplier<T> action) {
        try {
            observer.onNext(action.get());
            observer.onCompleted();
        } catch (SecurityException ex) {
            observer.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
        } catch (TaskCommandService.NoSuchTaskException ex) {
            observer.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException ex) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
            observer.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException ex) {
            observer.onError(ex);
        } catch (RuntimeException ex) {
            observer.onError(Status.INTERNAL.withDescription("task operation failed").asRuntimeException());
        }
    }

    @Override
    public void createTask(CreateTaskRequest req, StreamObserver<CreateTaskResponse> observer) {
        try {
            TaskStore.Task task = commands.create(GrpcSecurityContext.TENANT.get(), GrpcSecurityContext.SUBJECT.get(), req);
            observer.onNext(CreateTaskResponse.newBuilder().setTaskId(task.id()).setStatus(task.status()).build());
            observer.onCompleted();
        } catch (SecurityException ex) {
            observer.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException ex) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
            observer.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            observer.onError(Status.INTERNAL.withDescription("Task creation failed").asRuntimeException());
        }
    }

    @Override
    public void getTask(GetTaskRequest request, StreamObserver<TaskResponse> observer) {
        String tenantId = GrpcSecurityContext.TENANT.get();
        if (tenantId == null || tenantId.isBlank()) {
            observer.onError(Status.UNAUTHENTICATED.asRuntimeException());
            return;
        }
        if (request.hasContext() && !request.getContext().getTenantId().isBlank()
                && !tenantId.equals(request.getContext().getTenantId())) {
            observer.onError(Status.PERMISSION_DENIED.asRuntimeException());
            return;
        }
        var task = commands.entityView(tenantId, request.getTaskId());
        if (task == null) {
            observer.onError(Status.NOT_FOUND.withDescription("Task not found").asRuntimeException());
            return;
        }
        observer.onNext(TaskResponse.newBuilder().setTask(toProto(task)).build());
        observer.onCompleted();
    }

    @Override
    public void assignTask(AssignTaskRequest req, StreamObserver<TaskResponse> observer) {
        run(observer, () -> TaskResponse.newBuilder().setTask(toProto(commands.assign(tenant(), actor(), req))).build());
    }

    @Override
    public void acceptTask(AcceptTaskRequest req, StreamObserver<TaskResponse> observer) {
        run(observer, () -> TaskResponse.newBuilder()
                .setTask(toProto(commands.accept(tenant(), actor(), req.getTaskId(), req.getContext().getCorrelationId()))).build());
    }

    @Override
    public void completeTask(CompleteTaskRequest req, StreamObserver<TaskResponse> observer) {
        run(observer, () -> TaskResponse.newBuilder()
                .setTask(toProto(commands.complete(tenant(), actor(), req.getTaskId(), req.getCompletionNote(), req.getContext().getCorrelationId()))).build());
    }

    @Override
    public void cancelTask(CancelTaskRequest req, StreamObserver<TaskResponse> observer) {
        run(observer, () -> TaskResponse.newBuilder()
                .setTask(toProto(commands.cancel(tenant(), actor(), req.getTaskId(), req.getReason(), req.getContext().getCorrelationId()))).build());
    }

    @Override
    public void listTasks(ListTasksRequest req, StreamObserver<ListTasksResponse> observer) {
        run(observer, () -> {
            int limit = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 50;
            String statusFilter = req.getStatus() == TaskStatus.TASK_STATUS_UNSPECIFIED ? null : req.getStatus().name();
            List<TaskEntity> tasks = commands.list(tenant(), req.getFarmId(), statusFilter, req.getAssigneeId(), limit);
            var b = ListTasksResponse.newBuilder();
            for (TaskEntity t : tasks) b.addTasks(toProto(t));
            return b.build();
        });
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> observer) {
        observer.onNext(PingResponse.newBuilder().setService("livestock").setStatus("UP").setVersion("0.1.0").build());
        observer.onCompleted();
    }

    // ---- animal registry ----

    @Override
    public void registerAnimal(RegisterAnimalRequest req, StreamObserver<AnimalResponse> observer) {
        run(observer, () -> AnimalResponse.newBuilder().setAnimal(toProto(animals.register(tenant(), actor(), req))).build());
    }

    @Override
    public void getAnimal(GetAnimalRequest req, StreamObserver<AnimalResponse> observer) {
        run(observer, () -> {
            var a = animals.get(tenant(), req.getAnimalId());
            if (a == null) throw Status.NOT_FOUND.withDescription("animal not found").asRuntimeException();
            return AnimalResponse.newBuilder().setAnimal(toProto(a)).build();
        });
    }

    @Override
    public void listAnimals(ListAnimalsRequest req, StreamObserver<ListAnimalsResponse> observer) {
        run(observer, () -> {
            int limit = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 50;
            var list = animals.list(tenant(), req.getFarmId(), req.getBatchId(), limit);
            var b = ListAnimalsResponse.newBuilder();
            for (AnimalEntity a : list) b.addAnimals(toProto(a));
            return b.build();
        });
    }

    // ---- recurring schedules ----

    @Override
    public void createSchedule(CreateScheduleRequest req, StreamObserver<ScheduleResponse> observer) {
        run(observer, () -> ScheduleResponse.newBuilder().setSchedule(toProto(schedules.create(tenant(), actor(), req))).build());
    }

    @Override
    public void listSchedules(ListSchedulesRequest req, StreamObserver<ListSchedulesResponse> observer) {
        run(observer, () -> {
            int limit = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 50;
            var list = schedules.list(tenant(), req.getFarmId(), limit);
            var b = ListSchedulesResponse.newBuilder();
            for (ScheduleEntity s : list) b.addSchedules(toProto(s));
            return b.build();
        });
    }

    private static AnimalStatus animalStatus(String name) {
        if (name == null) return AnimalStatus.ANIMAL_STATUS_UNSPECIFIED;
        try {
            return AnimalStatus.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return AnimalStatus.ANIMAL_STATUS_UNSPECIFIED;
        }
    }

    private static Animal toProto(AnimalEntity a) {
        var b = Animal.newBuilder()
                .setAnimalId(a.getId()).setFarmId(a.getFarmId()).setTagCode(a.getTagCode())
                .setStatus(animalStatus(a.getStatus()));
        if (a.getBarnId() != null) b.setBarnId(a.getBarnId());
        if (a.getBatchId() != null) b.setBatchId(a.getBatchId());
        if (a.getSpecies() != null) b.setSpecies(a.getSpecies());
        if (a.getBirthDate() != null) b.setBirthDate(ts(a.getBirthDate()));
        return b.build();
    }

    private static TaskSchedule toProto(ScheduleEntity s) {
        var b = TaskSchedule.newBuilder()
                .setScheduleId(s.getId()).setFarmId(s.getFarmId()).setTitle(s.getTitle())
                .setType(type(s.getTaskType())).setCronExpression(s.getCronExpression())
                .setTimeZone(s.getTimeZone()).setEnabled(s.isEnabled());
        if (s.getAssigneeId() != null) b.setAssigneeId(s.getAssigneeId());
        if (s.getNextRunAt() != null) b.setNextRunAt(ts(s.getNextRunAt()));
        return b.build();
    }

    // ---- proto mapping ----

    private static Timestamp ts(Long epochMillis) {
        long m = epochMillis == null ? 0 : epochMillis;
        return Timestamp.newBuilder().setSeconds(Math.floorDiv(m, 1000))
                .setNanos((int) Math.floorMod(m, 1000) * 1_000_000).build();
    }

    private static TaskStatus status(String name) {
        if (name == null) return TaskStatus.TASK_STATUS_UNSPECIFIED;
        // Tolerate legacy short names ("CREATED") alongside full enum names.
        String full = name.startsWith("TASK_STATUS_") ? name : "TASK_STATUS_" + name;
        try {
            return TaskStatus.valueOf(full);
        } catch (IllegalArgumentException ex) {
            return TaskStatus.TASK_STATUS_UNSPECIFIED;
        }
    }

    private static TaskType type(String name) {
        if (name == null) return TaskType.TASK_TYPE_UNSPECIFIED;
        try {
            return TaskType.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return TaskType.TASK_TYPE_UNSPECIFIED;
        }
    }

    private static LivestockTask toProto(TaskEntity t) {
        var b = LivestockTask.newBuilder()
                .setTaskId(t.getId())
                .setFarmId(t.getFarmId())
                .setTitle(t.getTitle())
                .setType(type(t.getTaskType()))
                .setStatus(status(t.getStatus()))
                .setAssigneeId(t.getAssigneeId() == null ? "" : t.getAssigneeId());
        if (t.getDueAt() != null) b.setDueAt(ts(t.getDueAt()));
        if (t.getAssignedAt() != null) b.setAssignedAt(ts(t.getAssignedAt()));
        if (t.getAcceptDeadlineAt() != null) b.setAcceptDeadlineAt(ts(t.getAcceptDeadlineAt()));
        if (t.getAcceptedAt() != null) b.setAcceptedAt(ts(t.getAcceptedAt()));
        if (t.getReportDueAt() != null) b.setReportDueAt(ts(t.getReportDueAt()));
        if (t.getReportedAt() != null) b.setReportedAt(ts(t.getReportedAt()));
        if (t.getCompletedAt() != null) b.setCompletedAt(ts(t.getCompletedAt()));
        return b.build();
    }
}
