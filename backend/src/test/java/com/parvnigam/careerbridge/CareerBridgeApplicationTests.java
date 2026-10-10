package com.parvnigam.careerbridge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:context;MODE=PostgreSQL", "spring.jpa.hibernate.ddl-auto=create-drop"})
class CareerBridgeApplicationTests {

	@Test
	void contextLoads() {
	}

}
