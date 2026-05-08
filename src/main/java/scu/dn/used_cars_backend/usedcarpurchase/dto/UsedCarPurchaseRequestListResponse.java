package scu.dn.used_cars_backend.usedcarpurchase.dto;

import lombok.Data;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;

import java.util.ArrayList;
import java.util.List;

@Data
public class UsedCarPurchaseRequestListResponse {
	private List<UsedCarPurchaseRequestResponse> items = new ArrayList<>();
	private PageMetaDto meta;
}
