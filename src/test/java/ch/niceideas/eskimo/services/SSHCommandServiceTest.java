/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.AbstractBaseSSHTest;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.ConnectionManagerServiceTestImpl;
import ch.niceideas.eskimo.test.testwrappers.SetupServiceUnderTest;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.utils.OSDetector;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "setup-under-test", "test-conf", "test-connection-manager", "test-system", "test-operation"})
public class SSHCommandServiceTest extends AbstractBaseSSHTest {

    @Override
    protected CommandFactory getSShSubsystemToUse() {
        return new ProcessShellCommandFactory();
    }

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeTrue(OSDetector.isPosix());
    }

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private SetupServiceUnderTest setupService;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    private String tempPath = null;

    @BeforeEach
    public void setUp() throws Exception {

        tempPath = SystemServiceTest.createTempStoragePath();
        setupService.setConfigStoragePathInternal(tempPath);
        FileUtils.writeFile(new File(tempPath + "/config.json"), "{ \"" + SetupService.SSH_USERNAME_FIELD + "\" : \"test\" }");

        connectionManagerServiceTest.reset();

        connectionManagerServiceTest.setPrivateSShKeyContent(privateKeyRaw);
        connectionManagerServiceTest.setSShPort(getSShPort());

        configurationServiceTest.saveSetupConfig("{ \"" + SetupService.SSH_USERNAME_FIELD + "\" : \"test\" }");
    }

    @AfterEach
    public void tearDown() throws Exception {
        FileUtils.delete(new File (tempPath));
    }

    @Test
    public void testRunSSHCommandArray() throws Exception {
        assertEquals ("1\n", sshCommandService.runSSHCommand(
                connectionManagerServiceTest.getSharedConnection(Node.fromName("localhost")),
                new String[]{"echo", "1"}));
    }

    @Test
    public void testRunSSHCommandWithCon() throws Exception {
        assertEquals ("1\n", sshCommandService.runSSHCommand(
                connectionManagerServiceTest.getSharedConnection(Node.fromName("localhost")),
                "echo 1"));
    }

    @Test
    public void testRunSSHCommandStdOut() throws Exception {
        assertEquals ("1\n", sshCommandService.runSSHCommand(Node.fromName("localhost"), "echo 1"));
    }

    @Test
    public void testRunSSHCommandStdErr() {
        SSHCommandException exp = assertThrows(SSHCommandException.class,
                () -> sshCommandService.runSSHCommand(Node.fromName("localhost"), "/bin/bash -c /bin/tada"));
        assertNotNull (exp);
        assertTrue (
                ("Command exited with return code 127\n" +
                        "/bin/bash: /bin/tada: No such file or directory\n").equals (exp.getMessage())
                        ||
                        ("Command exited with return code 127\n" +
                                "/bin/bash: line 1: /bin/tada: No such file or directory\n").equals (exp.getMessage()));
    }

    @Test
    public void testRunSSHScriptStdOut() throws Exception {
        assertEquals ("1\n" +
                "2\n" +
                "3\n", sshCommandService.runSSHScript(Node.fromName("localhost"), "echo 1; echo 2 && echo 3;"));
    }

    @Test
    public void testRunSSHScriptNewLinesStdOut() throws Exception {
        assertEquals ("1\n" +
                "2\n" +
                "3\n", sshCommandService.runSSHScript(Node.fromName("localhost"), "echo 1\necho 2\necho 3;"));
    }

    @Test
    public void testRunSSHScriptErr() {
        SSHCommandException exp = assertThrows(SSHCommandException.class,
                () -> sshCommandService.runSSHScript(Node.fromName("localhost"), "/bin/tada"));
        assertNotNull(exp);
        assertEquals ("bash: line 1: /bin/tada: No such file or directory\n", exp.getMessage());
    }

    @Test
    public void testRunSSHScriptPath() throws Exception {
        File script = File.createTempFile("test_ssh", ".txt");
        FileUtils.writeFile(script, "#!/bin/bash\n echo 1");
        assertEquals ("1\n", sshCommandService.runSSHScriptPath(Node.fromName("localhost"), script.getAbsolutePath()));
    }

    @Test
    public void testScpFile() throws Exception {
        File source = File.createTempFile("test_ssh", ".txt");
        FileUtils.writeFile(source, "test content");
        try {
            sshCommandService.copySCPFile(Node.fromName("localhost"), source.getAbsolutePath());
            File dest = new File ("/home/" + source.getName());
            assertTrue (dest.exists());
            assertEquals ("test content", FileUtils.readFile(dest));
        } catch (SSHCommandException e) {
            // this can happen if we don't have the rights to write in /home
            if (!e.getMessage().equals("Error during SCP transfer.")) {
                fail (e.getMessage() + " is not expected");
            }
        }
    }
}
