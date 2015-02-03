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

package com.evolveum.midpoint.web.component.assignment;

import com.confluxsys.idmp.common.logging.LogManager;
import com.confluxsys.idmp.common.logging.Logger;
import com.confluxsys.idmp.platformobject.MetadataType;
import com.confluxsys.idmp.platformobject.PlatformObjectMetadataManager;
import com.confluxsys.idmp.platformservice.impl.PermissionInfoService;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.xml.XsdTypeMapper;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.web.component.input.*;
import com.evolveum.midpoint.web.component.prism.InputPanel;
import com.evolveum.midpoint.web.component.util.SimplePanel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import org.apache.commons.lang.ClassUtils;
import org.apache.wicket.Application;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.*;

/**
 * @author lazyman
 */
public class ACAttributeValuePanel extends SimplePanel<ACValueConstructionDto> {

    private static final String ID_INPUT = "input";
    private static final String ID_ADD = "add";
    private static final String ID_REMOVE = "remove";

    private Map<String, String> applicationMap;
    private Map<String, String> riskMap;
    private Map<String, String> groupMap;

    public ACAttributeValuePanel(String id, IModel<ACValueConstructionDto> iModel, Form form) {
        super(id, iModel);

        initPanel(form);
    }

    private void initPanel(Form form) {
        ACValueConstructionDto dto = getModel().getObject();
        PrismPropertyDefinition definition = dto.getAttribute().getDefinition();
        boolean required = definition.getMinOccurs() > 0;

        InputPanel input = createTypedInputComponent(ID_INPUT, definition);
        for (FormComponent comp : input.getFormComponents()) {
            comp.setLabel(new PropertyModel(dto.getAttribute(), ACAttributeDto.F_NAME));
            comp.setRequired(required);

            comp.add(new AjaxFormComponentUpdatingBehavior("onBlur") {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                }
            });
        }

        add(input);

        AjaxLink addLink = new AjaxLink(ID_ADD) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                addPerformed(target);
            }
        };
        add(addLink);
        addLink.add(new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                return isAddVisible();
            }
        });

        AjaxLink removeLink = new AjaxLink(ID_REMOVE) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                removePerformed(target);
            }
        };
        add(removeLink);
        removeLink.add(new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                return isRemoveVisible();
            }
        });
    }

    private InputPanel createTypedInputComponent(String id, PrismPropertyDefinition definition) {
        QName valueType = definition.getTypeName();

        final String attributeName = definition.getName().getLocalPart();

        final MetadataType attrMetaName = MetadataType.fromValue(attributeName);

        final String baseExpression = ACValueConstructionDto.F_VALUE;

        InputPanel panel;
        if (DOMUtil.XSD_DATETIME.equals(valueType)) {
            panel = new DatePanel(id, new PropertyModel<XMLGregorianCalendar>(getModel(), baseExpression));
        } else if (attrMetaName != null && attrMetaName.equals(MetadataType.Application)) {
            if (applicationMap == null || applicationMap.isEmpty()) {
                initializeApplicationMap();
            }
            panel = new DropDownChoicePanel(id, new PropertyModel<String>(getModel(), baseExpression), new AbstractReadOnlyModel<List<String>>() {
                @Override
                public List<String> getObject() {

                    return getApplicationIds();
                }
            }, new IChoiceRenderer<String>() {

                @Override
                public Object getDisplayValue(String object) {
                    return getDispValue(object);
                }

                @Override
                public String getIdValue(String object, int index) {
                    return getValue(object);
                }
            }, true);

        }
//        else if(attributeName.equals("psetRiskLevel")){
//            if (riskMap == null || riskMap.isEmpty()){
//                initializeRiskMap();
//            }
//            panel = new DropDownChoicePanel(id, new PropertyModel<String>(getModel(), baseExpression), new AbstractReadOnlyModel<List<String>>() {
//                @Override
//                public List<String> getObject() {
//                    return getRiskIds();
//                }
//            }, new IChoiceRenderer<String>() {
//
//                @Override
//                public Object getDisplayValue(String object) {
//                    return getRiskDisplayValue(object);
//                }
//
//                @Override
//                public String getIdValue(String object, int index) {
//                    return getRiskValue(object);
//                }
//            }, true);
//        }
        else if (attributeName.equals("psetAppAccess")) {
            MidPointApplication midpointApplication = (MidPointApplication) Application.get();
            final PermissionInfoService permissionInfoService = midpointApplication.getPermissionInfoService();
            final List<String> psetAppAccess = new ArrayList<>();
            psetAppAccess.add(null);
            psetAppAccess.addAll(Arrays.asList(permissionInfoService.getServiceNames()));
            panel = new DropDownChoicePanel(id, new PropertyModel<String>(getModel(), baseExpression),
                    new AbstractReadOnlyModel<List<String>>() {
                        @Override
                        public List<String> getObject() {
                            return psetAppAccess;
                        }
                    }, true);

            if (ObjectType.F_NAME.equals(definition.getName())) {
                panel.getBaseFormComponent().setRequired(true);
            }
        } else if (attributeName.equals("psetGroups")) {

            if (groupMap == null || groupMap.isEmpty()) {
                initializeGroupMap();
            }
            panel = new DropDownChoicePanel(id, new PropertyModel<String>(getModel(), baseExpression), new AbstractReadOnlyModel<List<String>>() {
                @Override
                public List<String> getObject() {
                    return getGroupDnList();
                }
            }, new IChoiceRenderer<String>() {

                @Override
                public Object getDisplayValue(String object) {
                    return getGroupCn(object);
                }

                @Override
                public String getIdValue(String object, int index) {
                    return getGroupCn(object);
                }
            }, true);
        } else if (attrMetaName != null && Arrays.asList(MetadataType.values()).contains(attrMetaName)) {
            panel = new DropDownChoicePanel(id, new PropertyModel<String>(getModel(), baseExpression),
                    new AbstractReadOnlyModel<List<Object>>() {
                        @Override
                        public List<Object> getObject() {
                            Set<Object> attrValues = getAttributeValueList(attrMetaName).keySet();
                            List<Object> list = new ArrayList<Object>();
                            list.add(null);
                            list.addAll(Arrays.asList(attrValues.toArray()));
//                            List<String> list = dropDownListAttributeMap.get(attributeName);
                            return list;
                        }
                    }, true);

            if (ObjectType.F_NAME.equals(definition.getName())) {
                panel.getBaseFormComponent().setRequired(true);
            }
        } else if (ProtectedStringType.COMPLEX_TYPE.equals(valueType)) {
            panel = new PasswordPanel(id, new PropertyModel<String>(getModel(), baseExpression + ".clearValue"));
        } else if (DOMUtil.XSD_BOOLEAN.equals(valueType)) {
            panel = new TriStateComboPanel(id, new PropertyModel<Boolean>(getModel(), baseExpression));
        } else if (SchemaConstants.T_POLY_STRING_TYPE.equals(valueType)) {
            panel = new TextPanel<String>(id, new PropertyModel<String>(getModel(), baseExpression + ".orig"), String.class);
        } else {
            Class type = XsdTypeMapper.getXsdToJavaMapping(valueType);
            if (type != null && type.isPrimitive()) {
                type = ClassUtils.primitiveToWrapper(type);
            }
            panel = new TextPanel<String>(id, new PropertyModel<String>(getModel(), baseExpression),
                    type);

            if (ObjectType.F_NAME.equals(definition.getName())) {
                panel.getBaseFormComponent().setRequired(true);
            }
        }

        return panel;
    }

    private Map<Object, String> getAttributeValueList(MetadataType attrVal) {
        Logger logger = LogManager.getLogger(this.getClass());
        MidPointApplication midpointApplication = (MidPointApplication) Application.get();
        logger.info("attrVal: " + attrVal);
        PlatformObjectMetadataManager metadataManager = midpointApplication.getPlatformObjectMetadataManager();

        Map<Object, String> attMap = metadataManager.getIDNameMap(attrVal);

        logger.info("AttrMap: " + attMap);


        return attMap;
    }


    private void initializeGroupMap() {
        MidPointApplication midpointApplication = (MidPointApplication) Application.get();
        final PermissionInfoService permissionInfoService = midpointApplication.getPermissionInfoService();
        groupMap = new HashMap<String, String>();
        try {
            List<String[]> groupList = permissionInfoService.getPlatformGroupNames("*");
            for (String[] groupArr : groupList) {
                groupMap.put(groupArr[0], groupArr[1]);
            }
        } catch (Exception e) {
            LogManager.getLogger(this.getClass()).error("Error getting group map: " , e);
        }
    }

    private List<String> getGroupDnList() {
        List<String> groupDnList = new ArrayList<>();
        for (String obj : groupMap.keySet()) {
            groupDnList.add(obj);
        }
        return groupDnList;
    }

    private String getGroupCn(String groupDn) {
        String dispValue = groupMap.get(groupDn);
        return dispValue;
    }

    private void initializeApplicationMap() {
        applicationMap = new HashMap<String, String>();

        MidPointApplication midpointApplication = (MidPointApplication) Application.get();
        PlatformObjectMetadataManager metadataManager = midpointApplication.getPlatformObjectMetadataManager();

        Map<Object, String> attMap = metadataManager.getIDNameMap(MetadataType.Application);
        if(attMap!=null) {
            for (Object obj : attMap.keySet()) {
                applicationMap.put(String.valueOf(obj), attMap.get(obj));
            }
        }
    }

    private List<String> getApplicationIds() {
        List<String> applicationIds = new ArrayList<>();
/*        if(applicationMap == null || applicationMap.isEmpty()){
            initializeApplicationMap();
        }*/
        for (String obj : applicationMap.keySet()) {
            applicationIds.add(obj);
        }

        return applicationIds;
    }

    private String getDispValue(String applicationId) {
        if (applicationMap == null || applicationMap.isEmpty()) {
            initializeApplicationMap();
        }
        String dispValue = applicationMap.get(applicationId);
        if (dispValue == null) {
            dispValue = "Choose Atleast One";
        }
        return dispValue;
    }

    private String getValue(String applicationId) {

        if (applicationMap == null || applicationMap.isEmpty()) {
            initializeApplicationMap();
        }
        String value = applicationMap.get(applicationId);
        if (value == null) {
            value = "Choose Atleast One";
        }
        return value;
    }

    private void initializeRiskMap() {
        riskMap = new HashMap<String, String>();

        riskMap.put("1", "Low");
        riskMap.put("5", "Medium");
        riskMap.put("10", "High");
    }

    private List<String> getRiskIds() {
        List<String> riskIds = new ArrayList<>();
        if (riskMap == null || riskMap.isEmpty()) {
            initializeRiskMap();
        }
        for (Object obj : riskMap.keySet()) {
            riskIds.add(String.valueOf(obj));
        }

        return riskIds;
    }

    private String getRiskDisplayValue(String riskId) {
        if (riskMap == null || riskMap.isEmpty()) {
            initializeRiskMap();
        }
        String dispValue = riskMap.get(riskId);

        return dispValue;
    }

    private String getRiskValue(String riskId) {

        if (riskMap == null || riskMap.isEmpty()) {
            initializeRiskMap();
        }
        String value = riskMap.get(riskId);
        return value;
    }

    private boolean isAddVisible() {
        ACValueConstructionDto dto = getModel().getObject();
        ACAttributeDto attributeDto = dto.getAttribute();
        PrismPropertyDefinition def = attributeDto.getDefinition();

        List<ACValueConstructionDto> values = attributeDto.getValues();
        if (def.getMaxOccurs() != -1 && values.size() >= def.getMaxOccurs()) {
            return false;
        }

        //we want to show add on last item only
        if (values.indexOf(dto) + 1 != values.size()) {
            return false;
        }

        return true;
    }

    private boolean isRemoveVisible() {
        ACValueConstructionDto dto = getModel().getObject();
        ACAttributeDto attributeDto = dto.getAttribute();
        PrismPropertyDefinition def = attributeDto.getDefinition();

        List<ACValueConstructionDto> values = attributeDto.getValues();
        if (values.size() <= 1) {
            return false;
        }

        if (values.size() <= def.getMinOccurs()) {
            return false;
        }

        return true;
    }

    private void addPerformed(AjaxRequestTarget target) {
        ACValueConstructionDto dto = getModel().getObject();
        ACAttributeDto attributeDto = dto.getAttribute();
        attributeDto.getValues().add(new ACValueConstructionDto(attributeDto, null));

        target.add(findParent(ACAttributePanel.class).getParent());

        //todo implement add to account construction
    }

    private void removePerformed(AjaxRequestTarget target) {
        ACValueConstructionDto dto = getModel().getObject();
        ACAttributeDto attributeDto = dto.getAttribute();
        attributeDto.getValues().remove(dto);
        //todo implement remove from acctount construction

        target.add(findParent(ACAttributePanel.class).getParent());
    }
}
