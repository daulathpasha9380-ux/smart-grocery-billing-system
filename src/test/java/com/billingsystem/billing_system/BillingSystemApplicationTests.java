package com.billingsystem.billing_system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:billing-context-tests;DB_CLOSE_DELAY=-1",
	"app.auth.owner-password=TestOwnerPassword#2026",
	"app.auth.cashier-password=TestCashierPassword#2026",
	"app.auth.customer-password=TestCustomerPassword#2026"
})
class BillingSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
