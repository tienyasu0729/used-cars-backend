package scu.dn.used_cars_backend;

import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Kiểm tra JDBC tới SQL Server dùng cùng URL/user/password như {@code application.yml}
 * (biến môi trường {@code DB_URL}, {@code DB_USER}, {@code DB_PASSWORD} nếu có).
 * <p>
 * Không dùng {@code @ActiveProfiles("test")} để tránh H2.
 * <p>
 * <b>Cách chạy</b> (PowerShell):
 * <pre>
 * $env:RUN_SQL_SERVER_IT = "true"
 * .\mvnw.cmd test -Dtest=SqlServerConnectionIntegrationTest
 * </pre>
 */
@SpringBootTest(classes = UsedCarsBackendApplication.class)
@TestPropertySource(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=none"
})
@EnabledIfEnvironmentVariable(named = "RUN_SQL_SERVER_IT", matches = "true")
class SqlServerConnectionIntegrationTest {

	private static final String TROUBLESHOOTING_VI = """
			Khong ket noi duoc SQL Server qua JDBC.

			- KHONG dung URL kieu SSMS: HOST\\INSTANCE trong JDBC (gay tim SQL Browser UDP 1434, de timeout).
			  Dung: jdbc:sqlserver://localhost:1433;databaseName=... (hoac may-chu:1433).

			Connection refused / timeout tren port da chon:
			1) Service SQL Server dang Running; TCP/IP bat trong Configuration Manager; IPAll dung dung port (vd. 1433).
			2) Neu bat buoc dung instanceName: bat "SQL Server Browser" hoac chi dinh portNumber trong URL.
			3) Firewall: TCP toi port SQL + (neu dung Browser) UDP 1434.

			Sua DB_URL / application.yml roi chay lai test.
			""";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void dataSource_connects_andReportsSqlServer() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(5)).isTrue();
			assertThat(connection.getMetaData().getDatabaseProductName())
					.containsIgnoringCase("Microsoft SQL Server");
		}
		catch (SQLServerException e) {
			fail(TROUBLESHOOTING_VI + System.lineSeparator() + "Chi tiết: " + e.getMessage(), e);
		}
	}

	@Test
	void jdbcTemplate_selectOne() {
		try {
			Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			assertThat(one).isEqualTo(1);
		}
		catch (CannotGetJdbcConnectionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof SQLServerException) {
				fail(TROUBLESHOOTING_VI + System.lineSeparator() + "Chi tiết: " + cause.getMessage(), cause);
			}
			throw e;
		}
	}
}
