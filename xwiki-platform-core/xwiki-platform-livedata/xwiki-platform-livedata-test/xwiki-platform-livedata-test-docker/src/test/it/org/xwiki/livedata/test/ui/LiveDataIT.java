/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.livedata.test.ui;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.node.ArrayNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.node.ObjectNode;
import org.xwiki.livedata.test.po.LiveDataElement;
import org.xwiki.livedata.test.po.TableLayoutElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.ClassEditPage;
import org.xwiki.test.ui.po.editor.StaticListClassEditElement;
import org.xwiki.text.StringUtils;

import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the Live Data macro, in view and edit modes.
 *
 * @version $Id$
 * @since 13.4RC1
 */
@UITest
class LiveDataIT
{
    private static final String NAME_COLUMN = "name";

    private static final String CHOICE_COLUMN = "choice";

    private static final String NAME_LYNDA = "Lynda";

    private static final String NAME_ESTHER = "Esther";

    private static final String NAME_CHARLY = "Charly";

    private static final String NAME_NIKOLAY = "Nikolay";

    private static final String CHOICE_A = "value1";

    private static final String CHOICE_B = "value2";

    private static final String CHOICE_C = "value3";

    private static final String CHOICE_D = "value4";

    private static final String BIRTHDAY_COLUMN = "birthday";

    private static final String USER_COLUMN = "user";

    private static final String BIRTHDAY_DATETIME = "11/05/2021 16:00:00";

    private static final String CANCELED_BIRTHDAY_DATETIME = "11/05/2021 16:00:10";

    private static final String DOC_TITLE_COLUMN = "doc.title";

    private static final String FOOTNOTE_COMPUTED_TITLE =
        "(1) Some pages have a computed title. Filtering and sorting by title will not work as expected for these "
            + "pages.";

    private static final String FOOTNOTES_PROPERTY_NOT_VIEWABLE =
        "(*) Displayed when some entries require special rights"
            + " to be viewed and thus fields cannot be extracted from them.";

    /**
     * Test the view and edition of the cells of a live data in table layout with a liveTable source. Creates an XClass
     * and two XObjects, then edit the XObjects properties from the live data.
     */
    @Test
    @Order(1)
    void livedataLivetableTableLayout(TestUtils testUtils, TestReference testReference) throws Exception
    {
        // Login as super admin because guest user cannot remove pages.
        testUtils.loginAsSuperAdmin();
        testUtils.createUser("U1", "U1", null);
        testUtils.createUser("U2", "U2", null);
        // Wipes the test space.
        testUtils.deletePage(testReference, true);

        initLocalization(testUtils, testReference);

        String className = testUtils.serializeReference(testReference);

        // Initializes the page content.
        createClassNameLiveDataPage(testUtils, testReference, className);

        // Creates the XClass.
        createXClass(testUtils, testReference);

        // Creates corresponding XObjects.
        addXObject(testUtils, new DocumentReference("O1", (SpaceReference) testReference.getParent()), className,
            NAME_LYNDA, CHOICE_A, "U1");
        DocumentReference o2 = new DocumentReference("O2", (SpaceReference) testReference.getParent());
        // Make 02 not viewable by guests to test the footnotes.
        testUtils.setRights(o2, null, "XWiki.XWikiGuest", "view", false);
        addXObject(testUtils, o2, className,
            NAME_ESTHER, CHOICE_B, "U2");
        DocumentReference o3 = new DocumentReference("O3", (SpaceReference) testReference.getParent());
        // Set a localized title on O3 to test the footnotes.
        testUtils.createPage(o3, "", "$services.localization.render('computedTitle')");
        addXObject(testUtils, o3, className, NAME_NIKOLAY, "", null);

        testUtils.gotoPage(testReference);

        LiveDataElement liveDataElement = new LiveDataElement("test");
        TableLayoutElement tableLayout = liveDataElement.getTableLayout();
        assertEquals(3, tableLayout.countRows());
        tableLayout.assertRow(DOC_TITLE_COLUMN, "O1");
        tableLayout.assertRow(DOC_TITLE_COLUMN, "O2");
        tableLayout.assertRow(DOC_TITLE_COLUMN, "O3 1");
        tableLayout.assertRow(NAME_COLUMN, NAME_LYNDA);
        tableLayout.assertRow(NAME_COLUMN, NAME_ESTHER);
        tableLayout.assertRow(CHOICE_COLUMN, CHOICE_A);
        tableLayout.assertRow(CHOICE_COLUMN, CHOICE_B);
        tableLayout.editCell(NAME_COLUMN, 1, NAME_COLUMN, NAME_CHARLY);
        tableLayout.editCell(CHOICE_COLUMN, 2, CHOICE_COLUMN, CHOICE_C);
        tableLayout.editCell(BIRTHDAY_COLUMN, 1, BIRTHDAY_COLUMN, BIRTHDAY_DATETIME);
        tableLayout.editAndCancel(BIRTHDAY_COLUMN, 2, BIRTHDAY_COLUMN, CANCELED_BIRTHDAY_DATETIME);
        // Edits the choice column of Nikolay, to assert that an cell with an empty content can be edited. 
        tableLayout.editCell(CHOICE_COLUMN, 3, CHOICE_COLUMN, CHOICE_D);
        assertEquals(3, tableLayout.countRows());
        tableLayout.assertRow(NAME_COLUMN, NAME_CHARLY);
        tableLayout.assertRow(NAME_COLUMN, NAME_LYNDA);
        tableLayout.assertRow(CHOICE_COLUMN, CHOICE_B);
        tableLayout.assertRow(CHOICE_COLUMN, CHOICE_C);
        tableLayout.assertRow(BIRTHDAY_COLUMN, BIRTHDAY_DATETIME);
        // The canceled birthday date shouldn't appear on the table since it has been canceled. 
        tableLayout
            .assertRow(BIRTHDAY_COLUMN, not(hasItem(tableLayout.getWebElementTextMatcher(CANCELED_BIRTHDAY_DATETIME))));
        tableLayout.assertRow(CHOICE_COLUMN, CHOICE_D);
        tableLayout
            .assertCellWithLink(USER_COLUMN, "U1", testUtils.getURL(new DocumentReference("xwiki", "XWiki", "U1")));
        tableLayout
            .assertCellWithLink(USER_COLUMN, "U2", testUtils.getURL(new DocumentReference("xwiki", "XWiki", "U2")));
        tableLayout.assertRow(USER_COLUMN, "");
        assertEquals(1, liveDataElement.countFootnotes());
        assertEquals(FOOTNOTE_COMPUTED_TITLE, liveDataElement.getFootnotesText().get(0));
        tableLayout.filterColumn(USER_COLUMN, "U1");
        assertEquals(1, tableLayout.countRows());
        // The footnotes are supposed to disappear after the filter because the related entries are not displayed.
        assertEquals(0, liveDataElement.countFootnotes());
        tableLayout
            .assertCellWithLink(USER_COLUMN, "U1", testUtils.getURL(new DocumentReference("xwiki", "XWiki", "U1")));

        // Become guest because the tests does not need specific rights. 
        testUtils.forceGuestUser();

        testUtils.gotoPage(testReference);

        liveDataElement = new LiveDataElement("test");
        tableLayout = liveDataElement.getTableLayout();
        tableLayout.assertRow(NAME_COLUMN, NAME_LYNDA);
        tableLayout.assertRow(NAME_COLUMN, "N/A*");
        tableLayout.assertRow(NAME_COLUMN, NAME_NIKOLAY);
        assertEquals(2, liveDataElement.countFootnotes());
        assertThat(liveDataElement.getFootnotesText(),
            containsInAnyOrder(FOOTNOTES_PROPERTY_NOT_VIEWABLE, FOOTNOTE_COMPUTED_TITLE));
    }

    /**
     * @since 12.10.9
     */
    @Test
    @Order(2)
    void livedataLivetableTableLayoutResultPage(TestUtils testUtils, TestReference testReference) throws Exception
    {
        // Login as super admin because guest user cannot remove pages.
        testUtils.loginAsSuperAdmin();
        // Wipes the test space.
        testUtils.deletePage(testReference, true);

        DocumentReference resultPageDocumentReference =
            new DocumentReference("resultPage", testReference.getLastSpaceReference());

        initResultPage(testUtils, resultPageDocumentReference);

        String resultPage = testUtils.serializeReference(resultPageDocumentReference).replaceFirst("xwiki:", "");

        createResultPageLiveDataPage(testUtils, testReference, resultPage);

        testUtils.gotoPage(testReference);
        TableLayoutElement tableLayout = new LiveDataElement("test").getTableLayout();
        assertEquals(1, tableLayout.countRows());
        tableLayout.assertRow("count", "1");
        tableLayout.assertRow("label", "first result");
    }

    private void initLocalization(TestUtils testUtils, TestReference testReference) throws Exception
    {
        DocumentReference translationDocumentReference =
            new DocumentReference("Translation", testReference.getLastSpaceReference());
        testUtils.addObject(translationDocumentReference, "XWiki.TranslationDocumentClass",
            singletonMap("scope", "WIKI"));
        testUtils.rest().savePage(translationDocumentReference, "emptyvalue=\ncomputedTitle=O3", "translation");
    }

    /**
     * Creates an XObject of type {@code className} and stores it in {@code documentReference}.
     *
     * @param testUtils the {@link TestUtils} instance of the test
     * @param documentReference the reference of the document storing the created XObject
     * @param className the type of the created XObject
     * @param name the value of the name field
     * @param choice the value of the choice field
     * @param username the username of the user field (e.g., {@code "U1"})
     */
    private void addXObject(TestUtils testUtils, DocumentReference documentReference, String className, String name,
        String choice, String username)
    {
        Map<String, Object> properties = new HashMap<>();
        properties.put(NAME_COLUMN, name);
        properties.put(CHOICE_COLUMN, choice);
        if (username != null) {
            properties.put(USER_COLUMN, "XWiki." + username);
        }
        testUtils.addObject(documentReference, className, properties);
    }

    private void createXClass(TestUtils testUtils, TestReference testReference)
    {
        testUtils.addClassProperty(testReference, NAME_COLUMN, "String");
        testUtils.addClassProperty(testReference, CHOICE_COLUMN, "StaticList");
        ClassEditPage classEditPage = new ClassEditPage();
        StaticListClassEditElement propertyList = classEditPage.getStaticListClassEditElement(CHOICE_COLUMN);
        propertyList.setValues(StringUtils.joinWith("|", CHOICE_A, CHOICE_B, CHOICE_C, CHOICE_D));
        classEditPage.clickSaveAndView();
        testUtils.addClassProperty(testReference, BIRTHDAY_COLUMN, "Date");
        testUtils.addClassProperty(testReference, USER_COLUMN, "Users");
    }

    private void createClassNameLiveDataPage(TestUtils testUtils, TestReference testReference, String className)
        throws Exception
    {
        TestUtils.RestTestUtils rest = testUtils.rest();
        Page page = rest.page(testReference);
        String properties =
            StringUtils.joinWith(",", NAME_COLUMN, CHOICE_COLUMN, BIRTHDAY_COLUMN, USER_COLUMN, DOC_TITLE_COLUMN);
        page.setContent("{{velocity}}\n"
            + "{{liveData\n"
            + "  id=\"test\"\n"
            + "  properties=\"" + properties + "\"\n"
            + "  source=\"liveTable\"\n"
            + "  sourceParameters=\"translationPrefix=&className=" + className.replace("xwiki:", "") + "\"\n"
            + "}}{{/liveData}}\n"
            + "{{/velocity}}");
        rest.save(page);
    }

    private void createResultPageLiveDataPage(TestUtils testUtils, TestReference testReference, String resultPage)
        throws Exception
    {
        TestUtils.RestTestUtils rest = testUtils.rest();
        Page page = rest.page(testReference);
        page.setContent("{{velocity}}\n"
            + "#set ($liveDataConfig={'meta': {'propertyDescriptors':[{'id': 'count', 'type': 'Number'}]}})\n"
            + "\n"
            + "{{liveData\n"
            + "  id=\"test\"\n"
            + "  properties=\"count,label\"\n"
            + "  source=\"liveTable\"\n"
            + "  sourceParameters=\"translationPrefix=&resultPage=" + resultPage + "\"\n"
            + "}}$jsontool.serialize($liveDataConfig){{/liveData}}\n"
            + "{{/velocity}}");
        rest.save(page);
    }

    private void initResultPage(TestUtils testUtils, DocumentReference resultPageDocumentReference)
        throws JsonProcessingException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("totalrows", 1);
        objectNode.put("returnedrows", 1);
        objectNode.put("offset", 1);
        objectNode.put("reqNo", 1);
        ArrayNode rows = objectNode.putArray("rows");
        ObjectNode jsonNodes = rows.addObject();
        jsonNodes.put("count", 1);
        jsonNodes.put("label", "first result");
        testUtils.createPage(resultPageDocumentReference, objectMapper.writeValueAsString(objectNode));
    }
}
