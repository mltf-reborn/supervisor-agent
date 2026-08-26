package com.bagusxmahendra.mltf.supervisor_agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSpannerConfig.class)
class SupervisorAgentApplicationTests {

	@Test
	void contextLoads() {
	}

}
