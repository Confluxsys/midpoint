/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.wf.impl.processors.primary.user;

import com.confluxsys.idmp.common.model.EntityInfo;
import com.confluxsys.idmp.connector.pset.lookup.AttributeLookupService;
import com.confluxsys.idmp.model.AttributeLookupValueInfo;
import com.confluxsys.idmp.model.PlatformObjectMetadataInfo;
import com.confluxsys.idmp.platformobject.*;

import com.confluxsys.idmp.platformobject.MetadataType;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.ModelElementContext;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.xnode.PrimitiveXNode;
import com.evolveum.midpoint.prism.xnode.XNode;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;

import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.impl.processes.addrole.AddRoleVariableNames;
import com.evolveum.midpoint.wf.impl.processes.common.SpringApplicationContextHolder;
import com.evolveum.midpoint.wf.impl.processes.itemApproval.ApprovalRequest;
import com.evolveum.midpoint.wf.impl.processes.itemApproval.ApprovalRequestImpl;
import com.evolveum.midpoint.wf.impl.processes.itemApproval.ItemApprovalProcessInterface;
import com.evolveum.midpoint.wf.impl.processes.itemApproval.ProcessVariableNames;
import com.evolveum.midpoint.wf.impl.processors.primary.PcpChildJobCreationInstruction;
import com.evolveum.midpoint.wf.impl.processors.primary.PrimaryChangeProcessor;
import com.evolveum.midpoint.wf.impl.processors.primary.aspect.BasePrimaryChangeAspect;
import com.evolveum.midpoint.wf.impl.util.MiscDataUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.midpoint.xml.ns.model.workflow.common_forms_3.AccountApprovalFormType;
import com.evolveum.midpoint.xml.ns.model.workflow.common_forms_3.QuestionFormType;

import com.evolveum.prism.xml.ns._public.types_3.RawType;

import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.XMLGregorianCalendar;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Dharmendra on 1/20/2015.
 */
@Component
public class AddAccountAssignmentAspect extends BasePrimaryChangeAspect {

    ChangeTypeEnum changeType = ChangeTypeEnum.MODIFY;

    private enum ChangeTypeEnum {
        ADD("creation"), DELETE("deletion"), MODIFY("modification");
        private String change;

        private ChangeTypeEnum(String s) {
            this.change = s;
        }

        public String getChangeTypeString() {
            return change;
        }
    }


    private static final Trace LOGGER = TraceManager.getTrace(AddAccountAssignmentAspect.class);

    @Autowired
    private PrismContext prismContext;

    @Autowired
    private ItemApprovalProcessInterface itemApprovalProcessInterface;

    //region ------------------------------------------------------------ Things that execute on request arrival

    @Override
    public List<PcpChildJobCreationInstruction> prepareJobCreationInstructions(ModelContext<?> modelContext, ObjectDelta<? extends ObjectType> change, Task taskFromModel, OperationResult result) throws SchemaException {

        List<ApprovalRequest<AssignmentType>> approvalRequestList = getAssignmentToApproveList(change, modelContext, result, taskFromModel);
        if (approvalRequestList == null || approvalRequestList.isEmpty()) {
            return null;
        } else {
            return prepareJobCreateInstructions(modelContext, taskFromModel, result, approvalRequestList);
        }
    }

    private List<ApprovalRequest<AssignmentType>> getAssignmentToApproveList(ObjectDelta<? extends ObjectType> change, ModelContext<?> modelContext, OperationResult result, Task taskFromModel) {

        List<ApprovalRequest<AssignmentType>> approvalRequestList = new ArrayList<ApprovalRequest<AssignmentType>>();

        /*
         * We either add a user; then the list of roles to be added is given by the assignment property,
         * or we modify a user; then the list of roles is given by the assignment property modification.
         */

        if (change.getChangeType() == ChangeType.ADD) {

            PrismObject<?> prismToAdd = change.getObjectToAdd();
            UserType user = (UserType) prismToAdd.asObjectable();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Role-related assignments in user add delta (" + user.getAssignment().size() + "): ");
            }

            // in the following we look for assignments of roles that should be approved

            Iterator<AssignmentType> assignmentTypeIterator = user.getAssignment().iterator();
            while (assignmentTypeIterator.hasNext()) {
                AssignmentType a = assignmentTypeIterator.next();

                ObjectType objectType = primaryChangeAspectHelper.resolveAccountObjectRef(a, result);
                if (objectType != null && objectType instanceof ResourceType) {
                    ResourceType resource = (ResourceType) objectType;
                    boolean approvalRequired = shouldResourceBeApproved(resource, ChangeType.ADD, modelContext, taskFromModel, result);
                    if (approvalRequired) {
                        AttributeLookupValueInfo applicationInfo = this.getApplicationInfo(a);
                        AssignmentType aCopy = a.clone();
                        PrismContainerValue.copyDefinition(aCopy, a);
                        aCopy.setTarget(resource);
                        approvalRequestList.add(createApprovalRequest(aCopy, resource, applicationInfo));
                        changeType = ChangeTypeEnum.ADD;
                        assignmentTypeIterator.remove();
                    }
                }
            }

        } else if (change.getChangeType() == ChangeType.MODIFY) {

            Iterator<? extends ItemDelta> deltaIterator = change.getModifications().iterator();

            // are any 'sensitive' roles assigned?

            while (deltaIterator.hasNext()) {
                ItemDelta delta = deltaIterator.next();
                if (UserType.F_ASSIGNMENT.equals(delta.getElementName()) && ((delta.getValuesToAdd() != null && !delta.getValuesToAdd().isEmpty()) || (delta.getValuesToDelete() != null && !delta.getValuesToDelete().isEmpty()))) {          // todo: what if assignments are modified?
                    if (delta.getValuesToAdd() != null && !delta.getValuesToAdd().isEmpty()) {
                        Iterator valueIterator = delta.getValuesToAdd().iterator();
                        while (valueIterator.hasNext()) {
                            Object o = valueIterator.next();
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Assignment to add = " + ((PrismContainerValue) o).debugDump());
                            }
                            PrismContainerValue<AssignmentType> at = (PrismContainerValue<AssignmentType>) o;
                            AssignmentType a = at.asContainerable();


                            ObjectType objectType = primaryChangeAspectHelper.resolveAccountObjectRef(a, result);
                            if (objectType != null && objectType instanceof ResourceType) {
                                ResourceType resource = (ResourceType) objectType;
                                boolean approvalRequired = shouldResourceBeApproved(resource, ChangeType.ADD, modelContext, taskFromModel, result);
                                if (approvalRequired) {
                                    AttributeLookupValueInfo applicationInfo = this.getApplicationInfo(a);
                                    AssignmentType aCopy = a.clone();
                                    PrismContainerValue.copyDefinition(aCopy, a);
                                    aCopy.setTarget(resource);
                                    approvalRequestList.add(createApprovalRequest(aCopy, resource, applicationInfo));
                                    changeType = ChangeTypeEnum.ADD;
                                    valueIterator.remove();
                                }
                            }
                        }
                        if (delta.getValuesToAdd().isEmpty()) {         // empty set of values to add is an illegal state
                            delta.resetValuesToAdd();
                            if (delta.getValuesToReplace() == null && delta.getValuesToDelete() == null) {
                                deltaIterator.remove();
                            }
                        }
                    } else if (delta.getValuesToDelete() != null && !delta.getValuesToDelete().isEmpty()) {
                        Iterator valueIterator = delta.getValuesToDelete().iterator();
                        while (valueIterator.hasNext()) {
                            Object o = valueIterator.next();
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Assignment to delete = " + ((PrismContainerValue) o).debugDump());
                            }
                            PrismContainerValue<AssignmentType> at = (PrismContainerValue<AssignmentType>) o;
                            AssignmentType a = at.asContainerable();

                            ObjectType objectType = primaryChangeAspectHelper.resolveAccountObjectRef(a, result);
                            if (objectType != null && objectType instanceof ResourceType) {
                                ResourceType resource = (ResourceType) objectType;
                                boolean approvalRequired = shouldResourceBeApproved(resource, ChangeType.DELETE, modelContext, taskFromModel, result);
                                if (approvalRequired) {
//                                    ApplicationInfo applicationInfo = this.getApplicationInfo(a);
                                    AssignmentType aCopy = a.clone();
                                    PrismContainerValue.copyDefinition(aCopy, a);
                                    aCopy.setTarget(resource);
                                    approvalRequestList.add(createAccountDeleteApprovalRequest(aCopy, resource, modelContext));
                                    changeType = ChangeTypeEnum.DELETE;
                                    valueIterator.remove();
                                }
                            }
                        }
                        if (delta.getValuesToDelete().isEmpty()) {
                            if (delta.getValuesToReplace() == null && delta.getValuesToAdd() == null) {
                                deltaIterator.remove();
                            }
                        }
                    }

                }
            }
        } else {
            return null;
        }
        return approvalRequestList;
    }

    public ApprovalRequest<AssignmentType> createAccountDeleteApprovalRequest(AssignmentType assignment, ResourceType resource, ModelContext<?> modelContext) {

        ModelElementContext<? extends ObjectType> fc = modelContext.getFocusContext();
        PrismObject<? extends ObjectType> prism = fc.getObjectNew() != null ? fc.getObjectNew() : fc.getObjectOld();
        if (prism == null) {
            throw new IllegalStateException("No object (new or old) in model context");
        }
        ObjectType object = prism.asObjectable();
        String beneficiaryOid = object.getOid();

        ObjectReferenceType approverRefObj = new ObjectReferenceType();
        approverRefObj.setDescription("Approval by role members");
        approverRefObj.setType(UserType.COMPLEX_TYPE);
        approverRefObj.setOid(beneficiaryOid);
        List<ObjectReferenceType> approverRefs = new ArrayList<ObjectReferenceType>();
        approverRefs.add(approverRefObj);

        return new ApprovalRequestImpl(assignment, null, approverRefs, null, null, prismContext);

    }

    private AttributeLookupValueInfo getApplicationInfo(AssignmentType a) {

        if (a == null) {
            throw new IllegalArgumentException("Invalid parameter: " + a);
        }
        ConstructionType construction = a.getConstruction();
        List<ResourceAttributeDefinitionType> attrs = construction.getAttribute();
        for (ResourceAttributeDefinitionType attr : attrs) {
            String attrName = attr.getRef().getLocalPart();
            if (attrName.equals("psetApplication")) {
                List<JAXBElement<?>> values = attr.getOutbound().getExpression().getExpressionEvaluator();

                JAXBElement<?> firstElement = values.iterator().next();
                RawType val = (RawType) firstElement.getValue();
                XNode xnode = val.getXnode();
                if (xnode instanceof PrimitiveXNode) {
                    PrimitiveXNode pxNode = (PrimitiveXNode) xnode;
                    if (pxNode.getValue() != null) {
                        Object applicationId = pxNode.getValue();
                        LOGGER.info("Application id found: " + applicationId);

                        ApplicationContext idmpApplicationContext = SpringApplicationContextHolder.getApplicationContext();
                        AttributeLookupService metadataManager = (AttributeLookupService) idmpApplicationContext.getBean("attributeLookupService");
                        AttributeLookupValueInfo appInfo = metadataManager.getLoookupValueInfo("UnixPermissionSet",MetadataType.Application.getMetaString(), new Integer(String.valueOf(applicationId)));

  /*                      EntityInfo info = appInfo.getOwner().getOwnerInfo();
                        LOGGER.info("App Owner: " + info.getIdentifier());*/
                        return appInfo;
                    }
                }
            }
        }

        return null;
    }

    private boolean shouldResourceBeApproved(ResourceType resource, ChangeType changeType, ModelContext<?> modelContext, Task taskFromModel, OperationResult result) {
        //TODO get permission set application oid from configuration hardcoded for now.
        if (resource.getOid().equals("PermissionSet-resource") || resource.getOid().equals("Windows-PermissionSet-resource")) {
            if (changeType.equals(ChangeType.ADD)) {
                return true;
            } else if (changeType.equals(ChangeType.DELETE)) {
                PrismObject<UserType> requester = primaryChangeAspectHelper.getRequester(taskFromModel, result);
                UserType requesterUser = requester.asObjectable();
                String beneficiary = MiscDataUtil.getFocusObjectName(modelContext);

                if (!beneficiary.equals(requesterUser.getName().getOrig())) {
                    return true;
                }
            }
        }
        return false;
    }

    // creates an approval request for a given resource  assignment
    private ApprovalRequest<AssignmentType> createApprovalRequest(AssignmentType a, ResourceType resource, AttributeLookupValueInfo appInfo) {

        ApprovalSchemaType appSchema = new ApprovalSchemaType();
        appSchema.setName("Approval Schema for Permission Set Create");
        appSchema.setDescription("Two level approval: First by application owner then Security Audit Role");
        //set first level : application owner
        ApprovalLevelType appOwnerLevel = new ApprovalLevelType();
        appSchema.getLevel().add(appOwnerLevel);
        appOwnerLevel.setName("Approval By Application Owner");
        appOwnerLevel.setDescription("At this level Application owner will approve the permission set change request");
        //set owner reference
        ObjectReferenceType appOwnerRef = new ObjectReferenceType();
        appOwnerLevel.getApproverRef().add(appOwnerRef);
        appOwnerRef.setDescription("Approval By Application Owner");
        EntityInfo ownerInfo = appInfo.getOwner().getOwnerInfo();
        if (ownerInfo.getEntityType().equals("User")) {
            appOwnerRef.setType(UserType.COMPLEX_TYPE);
        } else if (ownerInfo.getEntityType().equals("Role")) {
            appOwnerRef.setType(RoleType.COMPLEX_TYPE);
        } else {
            throw new IllegalStateException("Invalid owner entity type: " + ownerInfo.getEntityType());
        }
        appOwnerRef.setOid(ownerInfo.getIdentifier().toString());


        ApprovalLevelType auditTeamLevel = new ApprovalLevelType();
        appSchema.getLevel().add(auditTeamLevel);
        auditTeamLevel.setName("Approval By Security Audit Team");
        auditTeamLevel.setDescription("At this level Member of Security Audit Team will approve the changes in Permission set");


        ObjectReferenceType auditRoleRef = new ObjectReferenceType();
        auditTeamLevel.getApproverRef().add(auditRoleRef);
        auditRoleRef.setDescription("Approval By Security Audit Role Members");
        auditRoleRef.setType(RoleType.COMPLEX_TYPE);
        auditRoleRef.setOid("9df8e4f6-0f70-4fa1-ba0e-6a04463bf391");//TODO get security audit role detail  from config .hardcoded for now

        return new ApprovalRequestImpl(a, appSchema, null, null, null, prismContext);
    }

    // approvalRequestList should contain de-referenced roles and approvalRequests that have prismContext set
    private List<PcpChildJobCreationInstruction> prepareJobCreateInstructions(ModelContext<?> modelContext, Task taskFromModel, OperationResult result, List<ApprovalRequest<AssignmentType>> approvalRequestList) throws SchemaException {
        List<PcpChildJobCreationInstruction> instructions = new ArrayList<>();

        String beneficiaryUsername = MiscDataUtil.getFocusObjectName(modelContext);

        for (ApprovalRequest<AssignmentType> approvalRequest : approvalRequestList) {

            assert (approvalRequest.getPrismContext() != null);

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Approval request = " + approvalRequest);
            }

            AssignmentType assignmentType = approvalRequest.getItemToApprove();
            String permissionSetDisplayName = this.getPermissionSetDisplayName(assignmentType);
            ResourceType res = (ResourceType) assignmentType.getTarget();
            LOGGER.info("kind: " + assignmentType.getConstruction().getKind());
            if (assignmentType.getConstruction().getKind() == null) {
                assignmentType.getConstruction().setKind(ShadowKindType.ACCOUNT);
            }
            assignmentType.setTarget(null);         // we must remove the target object  in order to avoid problems with deserialization
            assignmentType.setTargetRef(null);// we must remove targetRef as account assignment does not supports it
            ((ApprovalRequestImpl<AssignmentType>) approvalRequest).setItemToApprove(assignmentType);   // set the modified value back

            Validate.notNull(res);
            Validate.notNull(res.getName());
            String resourceName = res.getName().getOrig();

            String objectOid = primaryChangeAspectHelper.getObjectOid(modelContext);
            PrismObject<UserType> requester = primaryChangeAspectHelper.getRequester(taskFromModel, result);

            // create a JobCreateInstruction for a given change processor (primaryChangeProcessor in this case)
            PcpChildJobCreationInstruction instruction =
                    PcpChildJobCreationInstruction.createInstruction(getChangeProcessor());

            // set some common task/process attributes
            instruction.prepareCommonAttributes(this, modelContext, objectOid, requester);

            // prepare and set the delta that has to be approved
            ObjectDelta<? extends ObjectType> delta = assignmentToDelta(modelContext, approvalRequest, objectOid);
            instruction.setDeltaProcessAndTaskVariables(delta);

            String ownerStr = changeType.equals(ChangeTypeEnum.ADD) ? " to be owned by " + beneficiaryUsername : "";
            String taskName = "Approve " + changeType.getChangeTypeString() + " of permission set " + permissionSetDisplayName + ownerStr;
            instruction.setTaskName(taskName);
            instruction.setProcessInstanceName("Approval request for " + changeType.getChangeTypeString() + " of permission set");

            // setup general item approval process
            itemApprovalProcessInterface.prepareStartInstruction(instruction, approvalRequest, taskName);

            // set some aspect-specific variables
            instruction.addProcessVariable(AddRoleVariableNames.USER_NAME, beneficiaryUsername);

            instructions.add(instruction);
        }
        return instructions;
    }

    private String getPermissionSetDisplayName(AssignmentType a) {

        if (a == null) {
            throw new IllegalArgumentException("Invalid parameter: " + a);
        }
        ConstructionType construction = a.getConstruction();
        List<ResourceAttributeDefinitionType> attrs = construction.getAttribute();
        for (ResourceAttributeDefinitionType attr : attrs) {
            String attrName = attr.getRef().getLocalPart();
            if (attrName.equals("psetDisplayName")) {
                List<JAXBElement<?>> values = attr.getOutbound().getExpression().getExpressionEvaluator();

                JAXBElement<?> firstElement = values.iterator().next();
                RawType val = (RawType) firstElement.getValue();
                XNode xnode = val.getXnode();
                PrimitiveXNode pxNode = (PrimitiveXNode) xnode;
                return String.valueOf(pxNode.getValue());
            } else {
                continue;
            }
        }
        return null;
    }

    private ObjectDelta<? extends ObjectType> assignmentToDelta(ModelContext<?> modelContext, ApprovalRequest<AssignmentType> approvalRequest, String objectOid) {
        PrismObject<UserType> user = (PrismObject<UserType>) modelContext.getFocusContext().getObjectNew();
        PrismContainerDefinition<AssignmentType> prismContainerDefinition = user.getDefinition().findContainerDefinition(UserType.F_ASSIGNMENT);

        ItemDelta<PrismContainerValue<AssignmentType>> accountDelta = new ContainerDelta<>(new ItemPath(), UserType.F_ASSIGNMENT, prismContainerDefinition, prismContext);
        PrismContainerValue<AssignmentType> assignmentValue = approvalRequest.getItemToApprove().asPrismContainerValue().clone();
        if (this.changeType.equals(ChangeTypeEnum.DELETE)) {
            accountDelta.addValuesToDelete(assignmentValue);
        } else {
            accountDelta.addValueToAdd(assignmentValue);
        }


        return ObjectDelta.createModifyDelta(objectOid != null ? objectOid : PrimaryChangeProcessor.UNKNOWN_OID, accountDelta, UserType.class, ((LensContext) modelContext).getPrismContext());
    }

/*    private boolean shouldRoleBeApproved(AbstractRoleType role) {
        return !role.getApproverRef().isEmpty() || !role.getApproverExpression().isEmpty() || role.getApprovalSchema() != null;
    }*/
    //endregion

    //region ------------------------------------------------------------ Things that execute when item is being approved

    @Override
    public PrismObject<? extends QuestionFormType> prepareQuestionForm(org.activiti.engine.task.Task task, Map<String, Object> variables, OperationResult result) throws SchemaException, ObjectNotFoundException {

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("getRequestSpecific starting: execution id " + task.getExecutionId() + ", pid " + task.getProcessInstanceId() + ", variables = " + variables);
        }

        PrismObjectDefinition<AccountApprovalFormType> formDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByType(AccountApprovalFormType.COMPLEX_TYPE);
        PrismObject<AccountApprovalFormType> formPrism = formDefinition.instantiate();
        AccountApprovalFormType form = formPrism.asObjectable();

        form.setUser((String) variables.get(AddRoleVariableNames.USER_NAME));

        // todo check type compatibility
        ApprovalRequest request = (ApprovalRequest) variables.get(ProcessVariableNames.APPROVAL_REQUEST);
        request.setPrismContext(prismContext);
        Validate.notNull(request, "Approval request is not present among process variables");

        AssignmentType assignment = (AssignmentType) request.getItemToApprove();
        Validate.notNull(assignment, "Approval request does not contain as assignment");

        ObjectReferenceType resRef = assignment.getConstruction().getResourceRef();


        Validate.notNull(resRef, "Approval request does not contain resource reference");
        String resOid = resRef.getOid();
        Validate.notNull(resOid, "Approval request does not contain resource OID");

        ResourceType resource;
        try {
            resource = repositoryService.getObject(ResourceType.class, resOid, null, result).asObjectable();
        } catch (ObjectNotFoundException e) {
            throw new ObjectNotFoundException("Role with OID " + resOid + " does not exist anymore.");
        } catch (SchemaException e) {
            throw new SchemaException("Couldn't get role with OID " + resOid + " because of schema exception.");
        }

        form.setResource(resource.getName() == null ? resource.getOid() : resource.getName().getOrig());        // ==null should not occur
        form.setRequesterComment(assignment.getDescription());
        form.setTimeInterval(formatTimeIntervalBrief(assignment));

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Resulting prism object instance = " + formPrism.debugDump());
        }
        return formPrism;
    }

    public static String formatTimeIntervalBrief(AssignmentType assignment) {
        StringBuilder sb = new StringBuilder();
        if (assignment != null && assignment.getActivation() != null &&
                (assignment.getActivation().getValidFrom() != null || assignment.getActivation().getValidTo() != null)) {
            if (assignment.getActivation().getValidFrom() != null && assignment.getActivation().getValidTo() != null) {
                sb.append(formatTime(assignment.getActivation().getValidFrom()));
                sb.append("-");
                sb.append(formatTime(assignment.getActivation().getValidTo()));
            } else if (assignment.getActivation().getValidFrom() != null) {
                sb.append("from ");
                sb.append(formatTime(assignment.getActivation().getValidFrom()));
            } else {
                sb.append("to ");
                sb.append(formatTime(assignment.getActivation().getValidTo()));
            }
        }
        return sb.toString();
    }

    private static String formatTime(XMLGregorianCalendar time) {
        DateFormat formatter = DateFormat.getDateInstance();
        return formatter.format(time.toGregorianCalendar().getTime());
    }

    @Override
    public PrismObject<? extends ObjectType> prepareRelatedObject(org.activiti.engine.task.Task task, Map<String, Object> variables, OperationResult result) throws SchemaException, ObjectNotFoundException {

        ApprovalRequest<AssignmentType> approvalRequest = (ApprovalRequest<AssignmentType>) variables.get(ProcessVariableNames.APPROVAL_REQUEST);
        approvalRequest.setPrismContext(prismContext);
        if (approvalRequest == null) {
            throw new IllegalStateException("No approval request in activiti task " + task);
        }
        String oid = approvalRequest.getItemToApprove().getConstruction().getResourceRef().getOid();
        PrismObject object = repositoryService.getObject(ResourceType.class, oid, null, result);
        ResourceType res = (ResourceType) object.asObjectable();
        ConnectorType connector = repositoryService.getObject(ConnectorType.class, res.getConnectorRef().getOid(), null, result).asObjectable();
        res.setConnector(connector);
        return object;
    }


    //endregion
}