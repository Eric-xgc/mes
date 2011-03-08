/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 0.3.0
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

package com.qcadoo.mes.qualityControls;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.api.DataDefinitionService;
import com.qcadoo.mes.api.Entity;
import com.qcadoo.mes.api.NumberGeneratorService;
import com.qcadoo.mes.api.SecurityService;
import com.qcadoo.mes.api.TranslationService;
import com.qcadoo.mes.internal.DefaultEntity;
import com.qcadoo.mes.internal.EntityTree;
import com.qcadoo.mes.model.DataDefinition;
import com.qcadoo.mes.model.FieldDefinition;
import com.qcadoo.mes.model.search.CustomRestriction;
import com.qcadoo.mes.model.search.RestrictionOperator;
import com.qcadoo.mes.model.search.Restrictions;
import com.qcadoo.mes.model.search.SearchCriteriaBuilder;
import com.qcadoo.mes.model.search.SearchResult;
import com.qcadoo.mes.model.types.internal.DateType;
import com.qcadoo.mes.view.ComponentState;
import com.qcadoo.mes.view.ComponentState.MessageType;
import com.qcadoo.mes.view.ViewDefinitionState;
import com.qcadoo.mes.view.components.FieldComponentState;
import com.qcadoo.mes.view.components.form.FormComponentState;
import com.qcadoo.mes.view.components.grid.GridComponentState;
import com.qcadoo.mes.view.components.lookup.LookupComponentState;
import com.qcadoo.mes.view.components.select.SelectComponentState;

@Service
public class QualityControlService {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private NumberGeneratorService numberGeneratorService;

    public void checkIfCommentIsRequiredBasedOnResult(final ViewDefinitionState state, final Locale locale) {
        FieldComponentState comment = (FieldComponentState) state.getComponentByReference("comment");

        FieldComponentState controlResult = (FieldComponentState) state.getComponentByReference("controlResult");

        if (controlResult != null && controlResult.getFieldValue() != null && "03objection".equals(controlResult.getFieldValue())) {
            comment.setRequired(true);
            comment.requestComponentUpdateState();
        } else {
            comment.setRequired(false);
        }

    }

    public void checkIfCommentIsRequiredBasedOnDefects(final ViewDefinitionState state, final Locale locale) {
        FieldComponentState comment = (FieldComponentState) state.getComponentByReference("comment");

        FieldComponentState acceptedDefectsQuantity = (FieldComponentState) state
                .getComponentByReference("acceptedDefectsQuantity");

        if (acceptedDefectsQuantity.getFieldValue() != null
                && !acceptedDefectsQuantity.getFieldValue().toString().isEmpty()
                && (new BigDecimal(acceptedDefectsQuantity.getFieldValue().toString().replace(",", "."))
                        .compareTo(BigDecimal.ZERO) > 0)) {
            comment.setRequired(true);
            comment.requestComponentUpdateState();
        } else {
            comment.setRequired(false);
        }

    }

    public boolean checkIfCommentForResultOrQuantityIsReq(final DataDefinition dataDefinition, final Entity entity) {
        String qualityControlType = (String) entity.getField("qualityControlType");

        if (hasControlResult(qualityControlType)) {
            return checkIfCommentForResultIsReq(dataDefinition, entity);
        } else {
            return checkIfCommentForQuantityIsReq(dataDefinition, entity);
        }

    }

    public boolean checkIfCommentForResultIsReq(final DataDefinition dataDefinition, final Entity entity) {
        String resultType = (String) entity.getField("controlResult");

        if (resultType != null && "03objection".equals(resultType)) {

            String comment = (String) entity.getField("comment");
            if (comment == null || comment.isEmpty()) {
                entity.addGlobalError("core.validate.global.error.custom");
                entity.addError(dataDefinition.getField("comment"),
                        "qualityControls.quality.control.validate.global.error.comment");
                return false;
            }
        }
        return true;

    }

    public boolean checkIfCommentForQuantityIsReq(final DataDefinition dataDefinition, final Entity entity) {
        BigDecimal acceptedDefectsQuantity = (BigDecimal) entity.getField("acceptedDefectsQuantity");

        if (acceptedDefectsQuantity != null) {
            String comment = (String) entity.getField("comment");

            if ((comment == null || comment.isEmpty()) && acceptedDefectsQuantity.compareTo(BigDecimal.ZERO) > 0) {
                entity.addGlobalError("core.validate.global.error.custom");
                entity.addError(dataDefinition.getField("comment"),
                        "qualityControls.quality.control.validate.global.error.comment");
                return false;
            }
        }
        return true;

    }

    public void checkQualityControlResult(final ViewDefinitionState viewDefinitionState, final ComponentState state,
            final String[] args) {
        if (!(state instanceof SelectComponentState)) {
            throw new IllegalStateException("component is not select");
        }

        SelectComponentState resultType = (SelectComponentState) state;

        FieldComponentState comment = (FieldComponentState) viewDefinitionState.getComponentByReference("comment");

        if (resultType.getFieldValue() != null && "03objection".equals(resultType.getFieldValue())) {
            comment.setRequired(true);
        } else {
            comment.setRequired(false);
        }
    }

    public void closeQualityControl(final ViewDefinitionState viewDefinitionState, final ComponentState state, final String[] args) {
        if (state.getFieldValue() != null) {
            if (state instanceof FormComponentState) {
                FieldComponentState controlResult = (FieldComponentState) viewDefinitionState
                        .getComponentByReference("controlResult");

                String qualityControlType = ((FieldComponentState) viewDefinitionState
                        .getComponentByReference("qualityControlType")).getFieldValue().toString();

                if (hasControlResult(qualityControlType) && controlResult != null
                        && (controlResult.getFieldValue() == null || ((String) controlResult.getFieldValue()).isEmpty())) {
                    controlResult.addMessage(
                            translationService.translate("qualityControls.quality.control.result.missing", state.getLocale()),
                            MessageType.FAILURE);
                    state.addMessage(
                            translationService.translate("qualityControls.quality.control.result.missing", state.getLocale()),
                            MessageType.FAILURE);
                    return;
                } else if (!hasControlResult(qualityControlType)
                        || (controlResult != null && ((controlResult.getFieldValue() != null) || !((String) controlResult
                                .getFieldValue()).isEmpty()))) {

                    FieldComponentState closed = (FieldComponentState) viewDefinitionState.getComponentByReference("closed");
                    FieldComponentState staff = (FieldComponentState) viewDefinitionState.getComponentByReference("staff");
                    FieldComponentState date = (FieldComponentState) viewDefinitionState.getComponentByReference("date");

                    staff.setFieldValue(securityService.getCurrentUserName());
                    date.setFieldValue(new SimpleDateFormat(DateType.DATE_FORMAT).format(new Date()));

                    closed.setFieldValue(true);

                    ((FormComponentState) state).performEvent(viewDefinitionState, "save", new String[0]);
                }

            } else if (state instanceof GridComponentState) {
                DataDefinition qualityControlDD = dataDefinitionService.get("qualityControls", "qualityControl");
                Entity qualityControl = qualityControlDD.get((Long) state.getFieldValue());

                FieldDefinition controlResultField = qualityControlDD.getField("controlResult");

                Object controlResult = qualityControl.getField("controlResult");
                String qualityControlType = (String) qualityControl.getField("qualityControlType");

                if (hasControlResult(qualityControlType) && controlResultField != null
                        && (controlResult == null || controlResult.toString().isEmpty())) {
                    state.addMessage(
                            translationService.translate("qualityControls.quality.control.result.missing", state.getLocale()),
                            MessageType.FAILURE);
                    return;
                } else if (!hasControlResult(qualityControlType)
                        || (controlResultField == null || (controlResult != null && !controlResult.toString().isEmpty()))) {

                    qualityControl.setField("staff", securityService.getCurrentUserName());
                    qualityControl.setField("date", new Date());
                    qualityControl.setField("closed", true);
                    qualityControlDD.save(qualityControl);

                    ((GridComponentState) state).performEvent(viewDefinitionState, "refresh", new String[0]);
                }
            }
            state.addMessage(translationService.translate("qualityControls.quality.control.closed.success", state.getLocale()),
                    MessageType.SUCCESS);
        } else {
            if (state instanceof FormComponentState) {
                state.addMessage(translationService.translate("core.form.entityWithoutIdentifier", state.getLocale()),
                        MessageType.FAILURE);
            } else {
                state.addMessage(translationService.translate("core.grid.noRowSelectedError", state.getLocale()),
                        MessageType.FAILURE);
            }
        }
    }

    public void autoGenerateQualityControl(final ViewDefinitionState viewDefinitionState, final ComponentState state,
            final String[] args) {
        boolean inProgressState = Boolean.parseBoolean(args[0]);
        if (inProgressState && isQualityControlAutoGenEnabled()) {
            generateQualityControl(viewDefinitionState, state, args);
        }
    }

    public void generateOnSaveQualityControl(final DataDefinition dataDefinition, final Entity entity) {
        Entity order = entity.getBelongsToField("order");
        Entity technology = order.getBelongsToField("technology");

        Object qualityControl = technology.getField("qualityControlType");

        if (qualityControl != null) {
            boolean qualityControlType = "01forBatch".equals(technology.getField("qualityControlType").toString());

            if (isQualityControlAutoGenEnabled() || qualityControlType) {
                createAndSaveControlForSingleBatch(order, entity);
            }
        }
    }

    public void generateQualityControl(final ViewDefinitionState viewDefinitionState, final ComponentState state,
            final String[] args) {
        if (state.getFieldValue() != null) {
            DataDefinition orderDataDefinition = dataDefinitionService.get("products", "order");
            Entity order = orderDataDefinition.get((Long) state.getFieldValue());

            Entity technology = (Entity) order.getField("technology");

            if (technology == null) {
                return;
            }

            if (technology.getField("qualityControlType") != null) {

                String qualityControlType = technology.getField("qualityControlType").toString();

                generateQualityControlForGivenType(qualityControlType, technology, order);

                state.addMessage(
                        translationService.translate("qualityControls.qualityControls.generated.success", state.getLocale()),
                        MessageType.SUCCESS);

                state.performEvent(viewDefinitionState, "refresh", new String[0]);
            } else {
                state.addMessage(
                        translationService.translate("qualityControls.qualityControls.qualityType.missing", state.getLocale()),
                        MessageType.FAILURE);
            }

        } else {
            if (state instanceof FormComponentState) {
                state.addMessage(translationService.translate("core.form.entityWithoutIdentifier", state.getLocale()),
                        MessageType.FAILURE);
            } else {
                state.addMessage(translationService.translate("core.grid.noRowSelectedError", state.getLocale()),
                        MessageType.FAILURE);
            }
        }
    }

    public void checkAcceptedDefectsQuantity(final ViewDefinitionState viewDefinitionState, final ComponentState state,
            final String[] args) {
        if (!(state instanceof FieldComponentState)) {
            throw new IllegalStateException("component is not input");
        }

        FieldComponentState acceptedDefectsQuantity = (FieldComponentState) state;

        FieldComponentState comment = (FieldComponentState) viewDefinitionState.getComponentByReference("comment");

        if (acceptedDefectsQuantity.getFieldValue() != null) {
            if (isNumber(acceptedDefectsQuantity.getFieldValue().toString())
                    && (new BigDecimal(acceptedDefectsQuantity.getFieldValue().toString())).compareTo(BigDecimal.ZERO) > 0) {
                comment.setRequired(true);
            } else {
                comment.setRequired(false);
            }
        }
    }

    public void setQualityControlInstruction(final ViewDefinitionState viewDefinitionState, final ComponentState state,
            final String[] args) {
        if (!(state instanceof LookupComponentState)) {
            return;
        }

        LookupComponentState order = (LookupComponentState) state;
        FieldComponentState controlInstruction = (FieldComponentState) viewDefinitionState
                .getComponentByReference("controlInstruction");

        if (controlInstruction != null) {
            controlInstruction.setFieldValue("");
        } else {
            return;
        }

        if (order.getFieldValue() != null) {
            String qualityControlInstruction = getInstructionForOrder(order.getFieldValue());
            if (qualityControlInstruction != null) {
                controlInstruction.setFieldValue(qualityControlInstruction);
            }
        }
    }

    public void enableCalendarsOnRender(final ViewDefinitionState state, final Locale locale) {
        FieldComponentState dateFrom = (FieldComponentState) state.getComponentByReference("dateFrom");
        FieldComponentState dateTo = (FieldComponentState) state.getComponentByReference("dateTo");

        dateFrom.setEnabled(true);
        dateTo.setEnabled(true);
    }

    public void setQuantitiesToDefaulIfEmpty(final ViewDefinitionState state, final Locale locale) {
        FieldComponentState takenForControlQuantity = (FieldComponentState) state
                .getComponentByReference("takenForControlQuantity");
        FieldComponentState rejectedQuantity = (FieldComponentState) state.getComponentByReference("rejectedQuantity");
        FieldComponentState acceptedDefectsQuantity = (FieldComponentState) state
                .getComponentByReference("acceptedDefectsQuantity");

        if (takenForControlQuantity.getFieldValue() == null || takenForControlQuantity.getFieldValue().toString().isEmpty()) {
            takenForControlQuantity.setFieldValue(BigDecimal.ONE);
        }

        if (rejectedQuantity.getFieldValue() == null || rejectedQuantity.getFieldValue().toString().isEmpty()) {
            rejectedQuantity.setFieldValue(BigDecimal.ZERO);
        }

        if (acceptedDefectsQuantity.getFieldValue() == null || acceptedDefectsQuantity.getFieldValue().toString().isEmpty()) {
            acceptedDefectsQuantity.setFieldValue(BigDecimal.ZERO);
        }

    }

    public void addRestrictionToQualityControlGrid(final ViewDefinitionState viewDefinitionState, final Locale locale) {
        final GridComponentState qualityControlsGrid = (GridComponentState) viewDefinitionState.getComponentByReference("grid");
        final String qualityControlType = qualityControlsGrid.getName();

        qualityControlsGrid.setCustomRestriction(new CustomRestriction() {

            @Override
            public void addRestriction(final SearchCriteriaBuilder searchCriteriaBuilder) {
                searchCriteriaBuilder.restrictedWith(Restrictions.eq("qualityControlType", qualityControlType));
            }

        });
    }

    // TODO mina test
    public void addRestrictionToTestGrids(final ViewDefinitionState viewDefinitionState, final Locale locale) {
        final GridComponentState qualityControlsForOrderGrid = (GridComponentState) viewDefinitionState
                .getComponentByReference("forOrderGrid");
        final String qualityControlForOrderType = qualityControlsForOrderGrid.getName();

        qualityControlsForOrderGrid.setCustomRestriction(new CustomRestriction() {

            @Override
            public void addRestriction(final SearchCriteriaBuilder searchCriteriaBuilder) {
                searchCriteriaBuilder.restrictedWith(Restrictions.eq("qualityControlType", qualityControlForOrderType));
            }

        });

        final GridComponentState qualityControlsForUnitGrid = (GridComponentState) viewDefinitionState
                .getComponentByReference("forUnitGrid");
        final String qualityControlForUnitType = qualityControlsForUnitGrid.getName();

        qualityControlsForUnitGrid.setCustomRestriction(new CustomRestriction() {

            @Override
            public void addRestriction(final SearchCriteriaBuilder searchCriteriaBuilder) {
                searchCriteriaBuilder.restrictedWith(Restrictions.eq("qualityControlType", qualityControlForUnitType));
            }

        });
    }

    public void setQualityControlTypeHiddenField(final ViewDefinitionState viewDefinitionState, final Locale locale) {
        FormComponentState qualityControlsForm = (FormComponentState) viewDefinitionState.getComponentByReference("form");
        String qualityControlTypeString = qualityControlsForm.getName().replace("Control", "Controls");
        FieldComponentState qualityControlType = (FieldComponentState) viewDefinitionState
                .getComponentByReference("qualityControlType");

        qualityControlType.setFieldValue(qualityControlTypeString);
    }

    public void setOperationAsRequired(final ViewDefinitionState state, final Locale locale) {
        LookupComponentState operation = (LookupComponentState) state.getComponentByReference("operation");
        operation.setRequired(true);
    }

    public boolean checkIfOperationIsRequired(final DataDefinition dataDefinition, final Entity entity) {
        String qualityControlType = (String) entity.getField("qualityControlType");

        if ("qualityControlsForOperation".equals(qualityControlType)) {
            Object operation = entity.getField("operation");

            if (operation == null) {
                entity.addGlobalError("core.validate.global.error.custom");
                entity.addError(dataDefinition.getField("operation"),
                        "qualityControls.quality.control.validate.global.error.operation");
                return false;
            }
        }

        return true;
    }

    public boolean checkIfQuantitiesAreCorrect(final DataDefinition dataDefinition, final Entity entity) {
        String qualityControlType = (String) entity.getField("qualityControlType");

        if (hasQuantitiesToBeChecked(qualityControlType)) {
            BigDecimal controlledQuantity = (BigDecimal) entity.getField("controlledQuantity");
            BigDecimal takenForControlQuantity = (BigDecimal) entity.getField("takenForControlQuantity");
            BigDecimal rejectedQuantity = (BigDecimal) entity.getField("rejectedQuantity");
            BigDecimal acceptedDefectsQuantity = (BigDecimal) entity.getField("acceptedDefectsQuantity");

            if (rejectedQuantity != null && rejectedQuantity.compareTo(takenForControlQuantity) > 0) {
                entity.addGlobalError("core.validate.global.error.custom");
                entity.addError(dataDefinition.getField("rejectedQuantity"),
                        "qualityControls.quality.control.validate.global.error.rejectedQuantity.tooLarge");
                return false;
            }

            if (acceptedDefectsQuantity != null && takenForControlQuantity != null
                    && acceptedDefectsQuantity.compareTo(takenForControlQuantity.subtract(rejectedQuantity)) > 0) {
                entity.addGlobalError("core.validate.global.error.custom");
                entity.addError(dataDefinition.getField("acceptedDefectsQuantity"),
                        "qualityControls.quality.control.validate.global.error.acceptedDefectsQuantity.tooLarge");
                return false;
            }

            entity.setField("controlledQuantity", controlledQuantity == null ? BigDecimal.ZERO : controlledQuantity);
            entity.setField("takenForControlQuantity", takenForControlQuantity == null ? BigDecimal.ZERO
                    : takenForControlQuantity);
            entity.setField("rejectedQuantity", rejectedQuantity == null ? BigDecimal.ZERO : rejectedQuantity);
            entity.setField("acceptedDefectsQuantity", acceptedDefectsQuantity == null ? BigDecimal.ZERO
                    : acceptedDefectsQuantity);
        }

        return true;
    }

    public void disableFormForClosedControl(final ViewDefinitionState state, final Locale locale) {
        FormComponentState qualityControl = (FormComponentState) state.getComponentByReference("form");
        boolean disabled = false;

        if (qualityControl.getEntityId() != null) {
            Entity entity = dataDefinitionService.get("qualityControls", "qualityControl").get(qualityControl.getEntityId());

            if (entity != null && (Boolean) entity.getField("closed") && qualityControl.isValid()) {
                disabled = true;
            }
        }

        qualityControl.setEnabledWithChildren(!disabled);
    }

    public boolean clearQualityControlOnCopy(final DataDefinition dataDefinition, final Entity entity) {
        entity.setField("closed", "0");
        entity.setField("controlResult", null);
        return true;
    }

    private boolean hasQuantitiesToBeChecked(String qualityControlType) {
        if ("qualityControlsForUnit".equals(qualityControlType) || "qualityControlsForBatch".equals(qualityControlType)) {
            return true;
        }

        return false;
    }

    private boolean hasControlResult(String qualityControlType) {
        if ("qualityControlsForOrder".equals(qualityControlType) || "qualityControlsForOperation".equals(qualityControlType)) {
            return true;
        }

        return false;
    }

    private String getInstructionForOrder(final Long fieldValue) {
        DataDefinition orderDD = dataDefinitionService.get("products", "order");

        SearchCriteriaBuilder searchCriteria = orderDD.find().withMaxResults(1)
                .restrictedWith(Restrictions.idRestriction(fieldValue, RestrictionOperator.EQ));

        return (String) searchCriteria.list().getEntities().get(0).getBelongsToField("technology")
                .getField("qualityControlInstruction");
    }

    private boolean isNumber(final String value) {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }

    private boolean isQualityControlAutoGenEnabled() {
        SearchResult searchResult = dataDefinitionService.get("basic", "parameter").find().withMaxResults(1).list();

        Entity parameter = null;
        if (searchResult.getEntities().size() > 0) {
            parameter = searchResult.getEntities().get(0);
        }

        if (parameter != null) {
            return parameter.getField("autoGenerateQualityControl") == null ? false : (Boolean) parameter
                    .getField("autoGenerateQualityControl");
        } else {
            return false;
        }
    }

    private void generateQualityControlForGivenType(final String qualityControlType, final Entity technology, final Entity order) {
        if ("01forBatch".equals(qualityControlType)) {
            List<Entity> genealogies = getGenealogiesForOrder(order.getId());

            for (Entity genealogy : genealogies) {
                createAndSaveControlForSingleBatch(order, genealogy);
            }

        } else if ("02forUnit".equals(qualityControlType)) {
            BigDecimal sampling = (BigDecimal) technology.getField("unitSamplingNr");

            BigDecimal doneQuantity = (BigDecimal) order.getField("doneQuantity");
            BigDecimal plannedQuantity = (BigDecimal) order.getField("plannedQuantity");
            BigDecimal numberOfControls = doneQuantity != null ? doneQuantity.divide(sampling, RoundingMode.HALF_UP)
                    : plannedQuantity.divide(sampling, RoundingMode.HALF_UP);

            for (int i = 0; i <= numberOfControls.intValue(); i++) {
                DataDefinition qualityForUnitDataDefinition = dataDefinitionService.get("qualityControls", "qualityControl");

                Entity forUnit = new DefaultEntity("qualityControls", "qualityControl");
                forUnit.setField("order", order);
                forUnit.setField("number", numberGeneratorService.generateNumber("qualityControls", "qualityControl"));
                forUnit.setField("closed", false);
                forUnit.setField("qualityControlType", "qualityControlsForUnit");
                forUnit.setField("takenForControlQuantity", BigDecimal.ONE);
                forUnit.setField("rejectedQuantity", BigDecimal.ZERO);
                forUnit.setField("acceptedDefectsQuantity", BigDecimal.ZERO);

                if (i < numberOfControls.intValue()) {
                    forUnit.setField("controlledQuantity", sampling);
                } else {
                    BigDecimal numberOfRemainders = doneQuantity != null ? doneQuantity.divideAndRemainder(sampling)[1]
                            : plannedQuantity.divideAndRemainder(sampling)[1];
                    forUnit.setField("controlledQuantity", numberOfRemainders);

                    if (numberOfRemainders.compareTo(new BigDecimal("0")) < 1) {
                        return;
                    }
                }

                setControlInstruction(order, forUnit);

                qualityForUnitDataDefinition.save(forUnit);
            }
        } else if ("03forOrder".equals(qualityControlType)) {
            createAndSaveControlForSingleOrder(order);
        } else if ("04forOperation".equals(qualityControlType)) {
            EntityTree tree = technology.getTreeField("operationComponents");
            for (Entity entity : tree) {
                if (entity.getField("qualityControlRequired") != null && (Boolean) entity.getField("qualityControlRequired")) {
                    createAndSaveControlForOperation(order, entity);
                }

            }
        }

    }

    private void createAndSaveControlForOperation(final Entity order, final Entity entity) {
        DataDefinition qualityForOperationDataDefinition = dataDefinitionService.get("qualityControls", "qualityControl");

        Entity forOperation = new DefaultEntity("qualityControls", "qualityControl");
        forOperation.setField("order", order);
        forOperation.setField("number", numberGeneratorService.generateNumber("qualityControls", "qualityControl"));
        forOperation.setField("operation", entity.getBelongsToField("operation"));
        forOperation.setField("closed", false);
        forOperation.setField("qualityControlType", "qualityControlsForOperation");

        setControlInstruction(order, forOperation);

        qualityForOperationDataDefinition.save(forOperation);
    }

    private void createAndSaveControlForSingleOrder(final Entity order) {
        DataDefinition qualityForOrderDataDefinition = dataDefinitionService.get("qualityControls", "qualityControl");

        Entity forOrder = new DefaultEntity("qualityControls", "qualityControl");
        forOrder.setField("order", order);
        forOrder.setField("number", numberGeneratorService.generateNumber("qualityControls", "qualityControl"));
        forOrder.setField("closed", false);
        forOrder.setField("qualityControlType", "qualityControlsForOrder");

        setControlInstruction(order, forOrder);

        qualityForOrderDataDefinition.save(forOrder);
    }

    private void createAndSaveControlForSingleBatch(final Entity order, final Entity genealogy) {
        DataDefinition qualityForBatchDataDefinition = dataDefinitionService.get("qualityControls", "qualityControl");

        Entity forBatch = new DefaultEntity("qualityControls", "qualityControl");
        forBatch.setField("order", order);
        forBatch.setField("number", numberGeneratorService.generateNumber("qualityControls", "qualityControl"));
        forBatch.setField("batchNr", genealogy.getField("batch"));
        forBatch.setField("closed", false);
        forBatch.setField("qualityControlType", "qualityControlsForBatch");

        if (getGenealogiesForOrder(order.getId()).size() == 1) {
            BigDecimal doneQuantity = (BigDecimal) order.getField("doneQuantity");
            BigDecimal plannedQuantity = (BigDecimal) order.getField("plannedQuantity");

            if (doneQuantity != null) {
                forBatch.setField("controlledQuantity", doneQuantity);
            } else if (plannedQuantity != null) {
                forBatch.setField("controlledQuantity", plannedQuantity);
            }
        } else {
            forBatch.setField("controlledQuantity", BigDecimal.ZERO);
        }

        forBatch.setField("takenForControlQuantity", BigDecimal.ONE);
        forBatch.setField("rejectedQuantity", BigDecimal.ZERO);
        forBatch.setField("acceptedDefectsQuantity", BigDecimal.ZERO);

        setControlInstruction(order, forBatch);

        qualityForBatchDataDefinition.save(forBatch);
    }

    private void setControlInstruction(final Entity order, final Entity qualityControl) {
        String qualityControlInstruction = (String) order.getBelongsToField("technology").getField("qualityControlInstruction");

        if (qualityControlInstruction != null) {
            qualityControl.setField("controlInstruction", qualityControlInstruction);
        }
    }

    private List<Entity> getGenealogiesForOrder(final Long id) {
        DataDefinition genealogyDD = dataDefinitionService.get("genealogies", "genealogy");

        SearchCriteriaBuilder searchCriteria = genealogyDD.find().restrictedWith(Restrictions.eq("order.id", id));

        return searchCriteria.list().getEntities();
    }

}
