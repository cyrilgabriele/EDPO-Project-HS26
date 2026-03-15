package ch.unisg.cryptoflow.userservice.adapter.in.worker;

import ch.unisg.cryptoflow.userservice.application.port.in.CreateUserCommand;
import ch.unisg.cryptoflow.userservice.application.port.in.CreateUserUseCase;
import ch.unisg.cryptoflow.userservice.domain.User;
import ch.unisg.cryptoflow.userservice.payload.UserCreationContext;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreationWorker {

    private final CreateUserUseCase createUserUseCase;

    @JobWorker(type="userCreationWorker")
    public void handleCreateUserRequest(JobClient client, ActivatedJob job) {
        log.info("Service Task started via Worker");
        UserCreationContext userCreationContext = UserCreationContext.fromMap(job.getVariablesAsMap());

        String userId = UUID.randomUUID().toString();
        CreateUserCommand command = new CreateUserCommand(userId, userCreationContext.getUserName(), userCreationContext.getPassword());
        User user = createUserUseCase.createUser(command);

        log.info("Persisted user {} with userId {}", user.getUsername(), user.getUserId());
        client.newCompleteCommand(job.getKey()) //
                .variables(Collections.singletonMap("UserId", userId))
                .send().join();
    }
}
