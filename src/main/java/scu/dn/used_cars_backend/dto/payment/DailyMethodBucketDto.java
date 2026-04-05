package scu.dn.used_cars_backend.dto.payment;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DailyMethodBucketDto {

	String day;
	long vnpayAmount;
	long zalopayAmount;
	long cashAmount;
}
