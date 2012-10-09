/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */

/**
 *
 */
package com.evolveum.midpoint.schema;

import static com.evolveum.midpoint.prism.util.PrismAsserts.assertPropertyValue;

import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.util.SchemaTestConstants;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_2.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2.GenericObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2.ProtectedStringType;
import com.evolveum.midpoint.xml.ns._public.common.common_2.UserType;
import com.evolveum.prism.xml.ns._public.types_2.ChangeTypeType;
import com.evolveum.prism.xml.ns._public.types_2.ItemDeltaType;
import com.evolveum.prism.xml.ns._public.types_2.ModificationTypeType;
import com.evolveum.prism.xml.ns._public.types_2.ObjectDeltaType;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.w3._2001._04.xmlenc.EncryptedDataType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author Radovan Semancik
 */
public class TestJaxbParsing {

    private static final String TEST_COMMON_DIR = "src/test/resources/common/";
    private static final String NS_FOO = "http://www.example.com/foo";

    @BeforeSuite
    public void setup() throws SchemaException, SAXException, IOException {
        DebugUtil.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
        PrismTestUtil.resetPrismContext(MidPointPrismContextFactory.FACTORY);
    }

    @Test
    public void testParseUserFromJaxb() throws SchemaException, SAXException, IOException, JAXBException {

        PrismContext prismContext = PrismTestUtil.getPrismContext();

        // Try to use the schema to validate Jack
        UserType userType = PrismTestUtil.unmarshalObject(new File(TEST_COMMON_DIR, "user-jack.xml"), UserType.class);

        // WHEN

        PrismObject<UserType> user = userType.asPrismObject();
        user.revive(prismContext);

        // THEN
        System.out.println("Parsed user:");
        System.out.println(user.dump());

        user.checkConsistence();
        assertPropertyValue(user, SchemaConstants.C_NAME, PrismTestUtil.createPolyString("jack"));
        assertPropertyValue(user, new QName(SchemaConstants.NS_C, "fullName"), new PolyString("Jack Sparrow", "jack sparrow"));
        assertPropertyValue(user, new QName(SchemaConstants.NS_C, "givenName"), new PolyString("Jack", "jack"));
        assertPropertyValue(user, new QName(SchemaConstants.NS_C, "familyName"), new PolyString("Sparrow", "sparrow"));
        assertPropertyValue(user, new QName(SchemaConstants.NS_C, "honorificPrefix"), new PolyString("Cpt.", "cpt"));
        assertPropertyValue(user.findContainer(SchemaConstants.C_EXTENSION),
                new QName(NS_FOO, "bar"), "BAR");
        PrismProperty<ProtectedStringType> password = user.findOrCreateContainer(SchemaConstants.C_EXTENSION).findProperty(new QName(NS_FOO, "password"));
        assertNotNull(password);
        // TODO: check inside
        assertPropertyValue(user.findOrCreateContainer(SchemaConstants.C_EXTENSION),
                new QName(NS_FOO, "num"), 42);
        PrismProperty<?> multi = user.findOrCreateContainer(SchemaConstants.C_EXTENSION).findProperty(new QName(NS_FOO, "multi"));
        assertEquals(3, multi.getValues().size());

        // WHEN

//        Node domNode = user.serializeToDom();
//
//        //THEN
//        System.out.println("\nSerialized user:");
//        System.out.println(DOMUtil.serializeDOMToString(domNode));
//
//        Element userEl = DOMUtil.getFirstChildElement(domNode);
//        assertEquals(SchemaConstants.I_USER, DOMUtil.getQName(userEl));

        // TODO: more asserts
    }

    @Test
    public void testParseAccountFromJaxb() throws SchemaException, SAXException, IOException, JAXBException {

        PrismContext prismContext = PrismTestUtil.getPrismContext();

        // Try to use the schema to validate Jack
        AccountShadowType accType = PrismTestUtil.unmarshalObject(new File(TEST_COMMON_DIR, "account-jack.xml"), AccountShadowType.class);

        PrismObject<AccountShadowType> account = accType.asPrismObject();
        account.revive(prismContext);

        System.out.println("Parsed account:");
        System.out.println(account.dump());

        account.checkConsistence(); 
        assertPropertyValue(account, SchemaConstants.C_NAME, PrismTestUtil.createPolyString("jack"));
        assertPropertyValue(account, AccountShadowType.F_OBJECT_CLASS, 
        		new QName("http://midpoint.evolveum.com/xml/ns/public/resource/instance/ef2bc95b-76e0-59e2-86d6-3d4f02d3ffff", "AccountObjectClass"));
        assertPropertyValue(account, AccountShadowType.F_INTENT, "default");

        // TODO: more asserts
    }

    @Test
    public void testParseGenericObjectFromJaxb() throws Exception {
        System.out.println("\n\n ===[ testParseGenericObjectFromJaxb ]===\n");

        PrismContext prismContext = PrismTestUtil.getPrismContext();

        GenericObjectType object = PrismTestUtil.unmarshalObject(new File(TEST_COMMON_DIR, "generic-sample-configuration.xml"),
                GenericObjectType.class);

        PrismObject<GenericObjectType> prism = object.asPrismObject();
        prism.revive(prismContext);

        prism.checkConsistence(); 
        assertPropertyValue(prism, GenericObjectType.F_NAME, PrismTestUtil.createPolyString("My Sample Config Object"));
        assertPropertyValue(prism, GenericObjectType.F_DESCRIPTION, "Sample description");
        assertPropertyValue(prism, GenericObjectType.F_OBJECT_TYPE, "http://midpoint.evolveum.com/xml/ns/test/extension#SampleConfigType");
        //assert extension
        PrismContainer<?> extension = prism.findContainer(GenericObjectType.F_EXTENSION);
        assertNotNull(extension);
        
        PrismAsserts.assertPropertyValue(extension, SchemaTestConstants.EXTENSION_STRING_TYPE_ELEMENT, "X marks the spot");
		PrismAsserts.assertPropertyValue(extension, SchemaTestConstants.EXTENSION_INT_TYPE_ELEMENT, 1234);
		PrismAsserts.assertPropertyValue(extension, SchemaTestConstants.EXTENSION_DOUBLE_TYPE_ELEMENT, 456.789D);
		PrismAsserts.assertPropertyValue(extension, SchemaTestConstants.EXTENSION_LONG_TYPE_ELEMENT, 567890L);
		XMLGregorianCalendar calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar("2002-05-30T09:10:11");
		PrismAsserts.assertPropertyValue(extension, SchemaTestConstants.EXTENSION_DATE_TYPE_ELEMENT, calendar);
        
        //todo locations ????? how to test DOM ??????
    }

    @Test
    public void testMarshallObjectDeltaType() throws Exception {
        ObjectDeltaType delta = new ObjectDeltaType();
        delta.setOid("07b32c14-0c18-460b-bd4a-99b96699f952");
        delta.setChangeType(ChangeTypeType.MODIFY);

        ItemDeltaType item1 = new ItemDeltaType();
        delta.getModification().add(item1);
        item1.setModificationType(ModificationTypeType.REPLACE);
        Document document = DOMUtil.getDocument();
        Element path = document.createElementNS(SchemaConstantsGenerated.NS_TYPES, "path");
        path.setTextContent("c:credentials/c:password");
        item1.setPath(path);
        ProtectedStringType protectedString = new ProtectedStringType();
        protectedString.setEncryptedData(new EncryptedDataType());
        ItemDeltaType.Value value = new ItemDeltaType.Value();
        value.getAny().add(new JAXBElement(new QName(SchemaConstants.NS_C, "protectedString"), ProtectedStringType.class, protectedString));
        item1.setValue(value);

        //fix marshalling somehow, or change the way how to create XML from ObjectDeltaType
        PrismContext prismContext = PrismTestUtil.getPrismContext();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(Marshaller.JAXB_FORMATTED_OUTPUT, false);
        String xml = prismContext.getPrismJaxbProcessor().marshalElementToString(
                new JAXBElement<Object>(new QName("http://www.example.com", "custom"), Object.class, delta), properties);
        assertNotNull(xml);
    }
}
