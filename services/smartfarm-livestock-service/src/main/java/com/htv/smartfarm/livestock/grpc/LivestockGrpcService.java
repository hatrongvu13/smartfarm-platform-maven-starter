package com.htv.smartfarm.livestock.grpc;

import com.htv.smartfarm.livestock.task.TaskCommandService;
import com.htv.smartfarm.livestock.task.TaskStore;
import com.htv.smartfarm.proto.livestock.v1.*;
import com.htv.smartfarm.proto.common.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LivestockGrpcService extends LivestockTaskServiceGrpc.LivestockTaskServiceImplBase {
    private final TaskCommandService commands;

    public LivestockGrpcService(TaskCommandService commands) {
        this.commands = commands;
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
    public void getTask(
            GetTaskRequest request,
            StreamObserver<TaskResponse> observer
    ) {
        String tenantId = GrpcSecurityContext.TENANT.get();

        if (tenantId == null || tenantId.isBlank()) {
            observer.onError(Status.UNAUTHENTICATED.asRuntimeException());
            return;
        }

        if (request.hasContext()
                && !request.getContext().getTenantId().isBlank()
                && !tenantId.equals(request.getContext().getTenantId())) {
            observer.onError(Status.PERMISSION_DENIED.asRuntimeException());
            return;
        }

        var task = commands.get(tenantId, request.getTaskId());

        if (task == null) {
            observer.onError(Status.NOT_FOUND
                    .withDescription("Task not found")
                    .asRuntimeException());
            return;
        }

        LivestockTask responseTask = LivestockTask.newBuilder()
                .setTaskId(task.id())
                .setFarmId(task.farm())
                .setTitle(task.title())
                .setAssigneeId(task.assignee())
                .setStatus(TaskStatus.TASK_STATUS_CREATED)
                .build();

        observer.onNext(TaskResponse.newBuilder()
                .setTask(responseTask)
                .build());
        observer.onCompleted();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> observer) {
        observer.onNext(PingResponse.newBuilder().setService("livestock").setStatus("UP").setVersion("0.1.0").build());
        observer.onCompleted();
    }
}
