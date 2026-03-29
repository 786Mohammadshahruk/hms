package com.hms.billing.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BillItemResponse {
    private final Long       id;
    private final String     description;
    private final BigDecimal unitPrice;
    private final Integer    quantity;
    private final BigDecimal totalPrice;
}
