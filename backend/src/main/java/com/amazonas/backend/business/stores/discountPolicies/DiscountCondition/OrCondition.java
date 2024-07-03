package com.amazonas.backend.business.stores.discountPolicies.DiscountCondition;

import com.amazonas.backend.business.stores.discountPolicies.DiscountDTOs.DiscountConditionDTO;
import com.amazonas.backend.business.stores.discountPolicies.DiscountDTOs.MultipleConditionDTO;
import com.amazonas.backend.business.stores.discountPolicies.DiscountDTOs.MultipleConditionType;
import com.amazonas.backend.business.stores.discountPolicies.ProductWithQuantitiy;
import com.amazonas.backend.exceptions.StoreException;

import java.util.LinkedList;
import java.util.List;

public class OrCondition implements Condition {
    final private Condition[] conditions;
    public OrCondition(Condition[] conditions) {
        if (conditions == null || conditions.length == 0) {
            throw new IllegalArgumentException("conditions cannot empty");
        }
        this.conditions = new Condition[conditions.length];
        for (int i = 0; i < conditions.length; i++) {
            if (conditions[i] == null) {
                throw new IllegalArgumentException("conditions cannot be null");
            }
            this.conditions[i] = conditions[i];
        }
    }

    @Override
    public boolean decideCondition(List<ProductWithQuantitiy> products) {
        if (products == null ) {
            throw new IllegalArgumentException("products list cannot be null");
        }
        for (Condition condition : conditions) {
            if (condition.decideCondition(products)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DiscountConditionDTO generateDTO() throws StoreException {
        if (conditions == null || conditions.length == 0) {
            throw new StoreException("cannot generate discount component");
        }
        List<DiscountConditionDTO> discountConditions= new LinkedList<>();
        for (Condition condition : conditions) {
            discountConditions.add(condition.generateDTO());
        }
        return new MultipleConditionDTO(MultipleConditionType.OR, discountConditions);
    }

    @Override
    public String generateCFG() throws StoreException {
        StringBuilder sb = new StringBuilder();
        sb.append("( | ");
        for (Condition condition : conditions) {
            sb.append(" ").append(condition.generateCFG());
        }
        sb.append(" )");
        return sb.toString();
    }
}
