/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.html;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class EskimoServicesSelectionTest extends AbstractWebTest {

    private String jsonServices = null;

    @Before
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/jquery-3.3.1.js')");
        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoServicesSelection.js')");

        // redefine constructor
        page.executeJavaScript("eskimo.ServicesSelection.initialize = function() {};");

        page.executeJavaScript("$('#services-selection-modal-wrapper').html('" +
                "<div id=\"services-selection-modal\" class=\"modal fade\" role=\"dialog\">" +
                "    <div class=\"modal-dialog\" style=\"width: 800px; max-width: 100%;\">" +
                "        <div class=\"modal-content\">" +
                "            <div class=\"modal-header\">" +
                "                <button type=\"button\" class=\"close\" onclick=\"javascript:eskimoServicesSelection.cancelServicesSelection();\">&times;</button>" +
                "                <h4 class=\"modal-title\">Select Services</h4>" +
                "            </div>" +
                "            <div id=\"services-selection-body\" class=\"modal-body\">" +
                "                <p>Some text in the modal.</p>" +
                "            </div>" +
                "            <div class=\"modal-footer\">" +
                "                <button id=\"select-all-services-button\" type=\"button\" class=\"btn btn-default\" onclick=\"javascript:eskimoServicesSelection.servicesSelectionSelectAll();\">SelectAll</button>" +
                "                <button type=\"button\" class=\"btn btn-default\" onclick=\"javascript:eskimoServicesSelection.cancelServicesSelection();\">Cancel</button>" +
                "                <button type=\"button\" class=\"btn btn-default\" onclick=\"javascript:eskimoServicesSelection.validateServicesSelection();\">OK</button>" +
                "            </div>" +
                "        </div>" +
                "    </div>" +
                "</div>')");

        // instantiate test object
        page.executeJavaScript("eskimoServicesSelection = new eskimo.ServicesSelection();");

        URL testPage = ResourceUtils.getURL("classpath:emptyPage.html");

        page.executeJavaScript("SERVICES_CONFIGURATION = " + jsonServices + ";");

        page.executeJavaScript("eskimoServicesSelection.setServicesConfigForTest(SERVICES_CONFIGURATION);");
    }

    @Test
    public void testGetService() throws Exception {

        assertEquals ("spark-history-server", page.executeJavaScript("eskimoServicesSelection.getService(1, 1).name").getJavaScriptResult().toString());
        assertEquals ("elasticsearch", page.executeJavaScript("eskimoServicesSelection.getService(3, 3).name").getJavaScriptResult().toString());

    }

    @Test
    public void testInitModalServicesConfig() throws Exception {

        page.executeJavaScript("eskimoServicesSelection.initModalServicesConfig()");

        assertEquals("1.0", page.executeJavaScript("$('#cerebro-choice').length").getJavaScriptResult().toString());
        assertEquals("1.0", page.executeJavaScript("$('#kibana-choice').length").getJavaScriptResult().toString());
        assertEquals("1.0", page.executeJavaScript("$('#grafana-choice').length").getJavaScriptResult().toString());

    }

}
