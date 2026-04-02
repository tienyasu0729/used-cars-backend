package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Giờ mở/đóng theo ngày trong tuần — map BranchWorkingHours (day_of_week 0–6, is_closed).
@Getter
@Setter
@Entity
@Table(name = "BranchWorkingHours")
public class BranchWorkingHours {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	/** DB thường dùng TINYINT (0–6); map rõ để schema validation khớp với cột thật. */
	@Column(name = "day_of_week", nullable = false)
	@JdbcTypeCode(SqlTypes.TINYINT)
	private Integer dayOfWeek;

	@Column(name = "open_time", nullable = false)
	@JdbcTypeCode(SqlTypes.TIME)
	private LocalTime openTime;

	@Column(name = "close_time", nullable = false)
	@JdbcTypeCode(SqlTypes.TIME)
	private LocalTime closeTime;

	@Column(name = "is_closed", nullable = false)
	private boolean closed;
}
