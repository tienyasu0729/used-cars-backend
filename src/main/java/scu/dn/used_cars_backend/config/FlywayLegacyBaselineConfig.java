package scu.dn.used_cars_backend.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Baselines legacy databases that were created before Flyway was enabled,
 * so only pending migrations (e.g. V7) run instead of replaying V1–V6.
 */
@Configuration
public class FlywayLegacyBaselineConfig {

	private static final Logger log = LoggerFactory.getLogger(FlywayLegacyBaselineConfig.class);

	@Bean
	public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
		return flyway -> {
			MigrationInfoService info = flyway.info();
			if (info.current() == null && legacySchemaPresent(dataSource)) {
				log.info("Legacy schema detected without Flyway history; baselining before migrate");
				resetEmptyFlywayHistory(dataSource);
				flyway.baseline();
			}
			flyway.repair();
			flyway.migrate();
		};
	}

	private boolean legacySchemaPresent(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("""
						SELECT TOP 1 1
						FROM INFORMATION_SCHEMA.TABLES
						WHERE TABLE_NAME IN ('InstallmentApplications', 'Users', 'device_keys')
						""")) {
			return rs.next();
		} catch (SQLException ex) {
			log.warn("Could not inspect schema for Flyway baseline decision", ex);
			return false;
		}
	}

	private void resetEmptyFlywayHistory(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("""
					IF OBJECT_ID(N'dbo.flyway_schema_history', N'U') IS NOT NULL
					   AND NOT EXISTS (SELECT 1 FROM dbo.flyway_schema_history)
					DROP TABLE dbo.flyway_schema_history
					""");
		} catch (SQLException ex) {
			log.warn("Could not reset empty Flyway history table", ex);
		}
	}
}
