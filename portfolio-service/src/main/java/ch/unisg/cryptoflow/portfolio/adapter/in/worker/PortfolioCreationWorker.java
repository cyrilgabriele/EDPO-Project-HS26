package ch.unisg.cryptoflow.portfolio.adapter.in.worker;

import ch.unisg.cryptoflow.portfolio.application.PortfolioCreationResult;
import ch.unisg.cryptoflow.portfolio.application.PortfolioService;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioCreationWorker {

    private final PortfolioService portfolioService;

    @JobWorker(type = "portfolioCreationWorker")
    public void createPortfolio(JobClient client, ActivatedJob job) {
        var variables = job.getVariablesAsMap();
        Object userIdValue = variables.get("userId");
        if (userIdValue == null) {
            throw new IllegalStateException("userId variable missing for portfolio creation job " + job.getKey());
        }

        Object userNameValue = variables.get("userName");
        if (userNameValue == null) {
            throw new IllegalStateException("userName variable missing for portfolio creation job " + job.getKey());
        }

        String userId = userIdValue.toString();
        String userName = userNameValue.toString();
        PortfolioCreationResult result = portfolioService.createPortfolioForUser(userId, userName);
        log.info("Ensured portfolio {} for user {} (created={})", result.portfolioId(), userId, result.created());

        client.newCompleteCommand(job.getKey())
            .variables(Map.of(
                "portfolioId", result.portfolioId(),
                "portfolioCreated", result.created()
            ))
            .send()
            .join();
    }
}
