/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.deliveries.hooks;

import static com.qcadoo.mes.deliveries.constants.OrderedProductFields.DELIVERY;
import static com.qcadoo.mes.deliveries.constants.OrderedProductFields.PRODUCT;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.deliveries.DeliveriesService;
import com.qcadoo.mes.deliveries.constants.OrderedProductFields;
import com.qcadoo.mes.deliveries.constants.OrderedProductReservationFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityList;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchRestrictions;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class OrderedProductHooks {

    @Autowired
    private DeliveriesService deliveriesService;

    @Autowired
    private NumberService numberService;

    public void onSave(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        calculateOrderedProductPricePerUnit(orderedProductDD, orderedProduct);
        calculateReservationQuantities(orderedProductDD, orderedProduct);
    }

    public void calculateOrderedProductPricePerUnit(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        deliveriesService.calculatePricePerUnit(orderedProduct, OrderedProductFields.ORDERED_QUANTITY);
    }

    public boolean checkIfOrderedProductAlreadyExists(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        SearchCriteriaBuilder searchCriteriaBuilder = orderedProductDD.find()
                .add(SearchRestrictions.belongsTo(DELIVERY, orderedProduct.getBelongsToField(DELIVERY)))
                .add(SearchRestrictions.belongsTo(PRODUCT, orderedProduct.getBelongsToField(PRODUCT)));

        if (orderedProduct.getId() != null) {
            searchCriteriaBuilder.add(SearchRestrictions.ne("id", orderedProduct.getId()));
        }

        Entity orderedProductFromDB = searchCriteriaBuilder.setMaxResults(1).uniqueResult();

        if (orderedProductFromDB == null) {
            return true;
        } else {
            orderedProduct.addError(orderedProductDD.getField(PRODUCT), "deliveries.orderedProduct.error.productAlreadyExists");

            return false;
        }
    }

    private void calculateReservationQuantities(final DataDefinition orderedProductDD, final Entity orderedProduct) {
        EntityList reservations = orderedProduct.getHasManyField(OrderedProductFields.RESERVATIONS);
        if (reservations != null) {
            BigDecimal conversion = orderedProduct.getDecimalField(OrderedProductFields.CONVERSION);
            for (Entity reservation : reservations) {
                BigDecimal orderedQuantity = reservation.getDecimalField(OrderedProductReservationFields.ORDERED_QUANTITY);
                BigDecimal newAdditionalQuantity = orderedQuantity.multiply(conversion, numberService.getMathContext());
                newAdditionalQuantity = newAdditionalQuantity.setScale(NumberService.DEFAULT_MAX_FRACTION_DIGITS_IN_DECIMAL, RoundingMode.HALF_UP);
                reservation.setField(OrderedProductReservationFields.ADDITIONAL_QUANTITY, newAdditionalQuantity);
            }
        }
    }

}
