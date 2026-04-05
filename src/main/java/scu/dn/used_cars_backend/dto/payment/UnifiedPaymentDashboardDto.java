package scu.dn.used_cars_backend.dto.payment;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class UnifiedPaymentDashboardDto {

	String totalAmountInPeriod;
	long successCount;
	long pendingCount;
	long failedOrCancelledCount;
	List<DailyMethodBucketDto> last30DaysByMethod;
}
