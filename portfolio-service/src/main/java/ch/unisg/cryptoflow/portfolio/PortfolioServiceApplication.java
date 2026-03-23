package ch.unisg.cryptoflow.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.camunda.zeebe.spring.client.annotation.Deployment;

@SpringBootApplication
// @Deployment(resources = "classpath:userCreation.bpmn")   // comment this out if deployed in cloud
public class PortfolioServiceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PortfolioServiceApplication.class);
        app.setBanner((environment, sourceClass, out) -> out.println(Banner.TEXT));
        app.run(args);
    }
}
