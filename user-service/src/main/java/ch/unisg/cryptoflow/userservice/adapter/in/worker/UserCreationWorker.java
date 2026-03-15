package ch.unisg.cryptoflow.userservice.adapter.in.worker;

import ch.unisg.cryptoflow.userservice.domain.User;
import ch.unisg.cryptoflow.userservice.payload.UserCreationContext;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;


@Slf4j
@Component
public class UserCreationWorker {

    @JobWorker(type="userCreationWorker")
    public void handleCreateUserRequest(JobClient client, ActivatedJob job) {
        log.info("Service Task started via Worker");
        UserCreationContext userCreationContext = UserCreationContext.fromMap(job.getVariablesAsMap());
        User user = new User(userCreationContext.getUserName(), userCreationContext.getPassword());

        String correlationId = UUID.randomUUID().toString();

        log.info("Creating user with name {} and password {} with correlationId {}", user.getUsername(), user.getPassword(), correlationId);
        client.newCompleteCommand(job.getKey()) //
                .variables(Collections.singletonMap("UserId", correlationId))
                .send().join();
    }
}
