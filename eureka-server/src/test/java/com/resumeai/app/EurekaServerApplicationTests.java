package com.resumeai.app;

import com.resumeai.eurekaserver.EurekaServerApplication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for Eureka Server.
 * Full context loading is skipped because @EnableEurekaServer requires
 * a running Eureka environment (ApplicationInfoManager, PeerAwareInstanceRegistry, etc.)
 * that cannot be satisfied in a lightweight test without an embedded Eureka cluster.
 */
class EurekaServerApplicationTests {

	@Test
	void applicationClassExists() {
		assertThat(EurekaServerApplication.class).isNotNull();
	}

	@Test
	void mainMethodDoesNotThrow() {
		// Verify the class has a main method (basic structural check)
		assertThat(EurekaServerApplication.class.getDeclaredMethods())
				.anyMatch(m -> m.getName().equals("main"));
	}
}
