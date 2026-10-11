package com.parvnigam.careerbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories=true)
@EnableAsync
public class CareerBridgeApplication {

	@Bean
	PersistenceManagedTypes persistenceManagedTypes() {
		// Register nested model classes explicitly, without relying on classpath scanning.
		return PersistenceManagedTypes.of(
			Model.Base.class.getName(),
			Model.Account.class.getName(),
			Model.University.class.getName(),
			Model.Profile.class.getName(),
			Model.Academics.class.getName(),
			Model.AcademicDocument.class.getName(),
			Model.AcademicSubmission.class.getName(),
			Model.Project.class.getName(),
			Model.Education.class.getName(),
			Model.Experience.class.getName(),
			Model.Company.class.getName(),
			Model.Job.class.getName(),
			Model.Application.class.getName(),
			Model.ApplicationEvent.class.getName(),
			Model.SavedJob.class.getName(),
			Model.Resume.class.getName(),
			Model.Notice.class.getName(),
			Model.Interview.class.getName(),
			Model.MockSession.class.getName(),
			Model.Audit.class.getName()
		);
	}

	public static void main(String[] args) {
		SpringApplication.run(CareerBridgeApplication.class, args);
	}

}
