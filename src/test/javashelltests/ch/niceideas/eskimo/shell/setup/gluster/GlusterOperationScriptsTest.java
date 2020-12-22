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


package ch.niceideas.eskimo.shell.setup.gluster;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.shell.setup.AbstractSetupShellTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class GlusterOperationScriptsTest extends AbstractSetupShellTest {

    protected static String jailPath = null;

    @BeforeEach
    public void setUp() throws Exception {
        jailPath = setupJail(getServiceName());
        File glusterScriptsFile = new File(jailPath+"/gluster_container_helpers");
        assertTrue (glusterScriptsFile.mkdir());
        handleScript("__replicate-master-blocks.sh");
        handleScript("__delete-local-blocks.sh");
        handleScript("__force-remove-peer.sh");
        handleScript("gluster-address-peer-inconsistency.sh");
        handleScript("gluster-prepare-mount.sh");
        handleScript("gluster-update-peers.sh");

        //handleScript("../commonGlusterFunctions.sh", "commonGlusterFunctions.sh");

        FileUtils.writeFile(new File(jailPath+"/commonGlusterFunctions.sh"), "" +
                "#/bin/bash\n" +
                "\n" +
                "function get_gluster_master() {\n" +
                "   echo '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function get_pool_ips() {\n" +
                "    echo -e '192.168.10.11\n192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function delete_gluster_management_lock_file() {\n" +
                "    echo \" - releasing gluster_management_lock\"\n" +
                "    rm -Rf /var/lib/gluster/gluster_management_lock\n" +
                "}\n");
    }

    void handleScript(String scriptName) throws IOException, FileException {
        handleScript (scriptName, scriptName);
    }

    void handleScript(String source, String dest) throws IOException, FileException {
        FileUtils.copy(
                new File("./services_setup/" + getServiceName() + "/gluster_container_helpers/" + source),
                new File (jailPath + "/" + dest));
        enhanceScript(jailPath, dest);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    @Override
    protected String getJailPath() {
        return jailPath;
    }

    @Override
    protected String getServiceName() {
        return "gluster";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // Don't copy anything here
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[0];
    }

    @Test
    public void testGlusterUpdatePeers_NotInPoolList() throws Exception {

        FileUtils.delete(new File ("/tmp/first_done_flag"));

        File targetPath = new File(getJailPath() + "/gluster");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "if [[ $1 == 'volume' ]]; then \n" +
                "    if [[ $2 == 'list' ]]; then \n" +
                "        echo 'flink_completed_jobs'\n" +
                "        echo 'flink_data'\n" +
                "        echo 'logstash_data'\n" +
                "        echo 'marathon_registry'\n" +
                "        echo 'spark_data'\n" +
                "        echo 'spark_eventlog'\n" +
                "    fi\n" +
                "fi\n" +
                "\n" +
                "echo $@ >> .log_gluster");

        FileUtils.writeFile(new File(jailPath+"/commonGlusterFunctions.sh"), "" +
                "#/bin/bash\n" +
                "\n" +
                "function get_gluster_master() {\n" +
                "   echo '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function get_pool_ips() {\n" +
                "        if [[ -f /tmp/first_done_flag ]]; then \n" +
                "            echo -e '192.168.10.13\n127.0.0.1'\n" +
                "        else\n" +
                "            /bin/touch /tmp/first_done_flag\n" +
                "        fi\n" +
                "        echo -e '127.0.0.1'\n" +
                "}\n" +
                "function get_peer_ips() {\n" +
                "        echo -e '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function delete_gluster_management_lock_file() {\n" +
                "    echo \" - releasing gluster_management_lock\"\n" +
                "    rm -Rf /var/lib/gluster/gluster_management_lock\n" +
                "}\n");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        targetPath = new File(getJailPath() + "/__replicate-master-blocks.sh");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "echo $@ >> .log_replicate-master-blocks.sh");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        // missing argument
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-update-peers.sh"}, false);
        assertEquals("-> gluster-update-peers.sh\n" +
                " Checking and fixing peers for 192.168.10.11 (with master 192.168.10.13)\n" +
                " - Master is not in pool lost. Need to add it\n" +
                " - Attempting to take gluster_management_lock\n" +
                " - Trying : gluster_call_remote.sh 192.168.10.11 peer probe 192.168.10.13\n" +
                " - releasing gluster_management_lock\n", result);

        assertEquals("192.168.10.11 peer probe 192.168.10.13\n", StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_gluster_call_remote.sh")));
    }

    @Test
    public void testGlusterUpdatePeers_AllGoodAlready() throws Exception {
        File targetPath = new File(getJailPath() + "/gluster");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "if [[ $1 == 'volume' ]]; then \n" +
                "    if [[ $2 == 'list' ]]; then \n" +
                "        echo 'flink_completed_jobs'\n" +
                "        echo 'flink_data'\n" +
                "        echo 'logstash_data'\n" +
                "        echo 'marathon_registry'\n" +
                "        echo 'spark_data'\n" +
                "        echo 'spark_eventlog'\n" +
                "    fi\n" +
                "fi\n" +
                "\n" +
                "echo $@ >> .log_gluster");

        FileUtils.writeFile(new File(jailPath+"/commonGlusterFunctions.sh"), "" +
                "#/bin/bash\n" +
                "\n" +
                "function get_gluster_master() {\n" +
                "   echo '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function get_pool_ips() {\n" +
                "    echo -e '192.168.10.13\n127.0.0.1'\n" +
                "}\n" +
                "function get_peer_ips() {\n" +
                "    echo -e '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function delete_gluster_management_lock_file() {\n" +
                "    echo \" - releasing gluster_management_lock\"\n" +
                "    rm -Rf /var/lib/gluster/gluster_management_lock\n" +
                "}\n");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        // missing argument
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-update-peers.sh"}, false);
        assertEquals("-> gluster-update-peers.sh\n" +
                " Checking and fixing peers for 192.168.10.11 (with master 192.168.10.13)\n", result);
    }

    @Test
    public void testGlusterPrepareMount_SingleReplica() throws Exception {

        FileUtils.writeFile(new File(jailPath+"/commonGlusterFunctions.sh"), "" +
                "#/bin/bash\n" +
                "\n" +
                "function get_gluster_master() {\n" +
                "   echo '192.168.10.11'\n" +
                "}\n");

        FileUtils.delete(new File ("/tmp/first_done_flag"));
        FileUtils.delete(new File ("/tmp/first_info_flag"));

        File targetPath = new File(getJailPath() + "/gluster");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "if [[ $1 == 'volume' ]]; then \n" +
                "    if [[ $2 == 'list' ]]; then \n" +
                "        echo 'flink_completed_jobs'\n" +
                "        echo 'flink_data'\n" +
                "        echo 'logstash_data'\n" +
                "        echo 'marathon_registry'\n" +
                "        echo 'spark_data'\n" +
                "        echo 'spark_eventlog'\n" +
                "        if [[ -f /tmp/first_done_flag ]]; then \n" +
                "            echo 'test'\n" +
                "        else\n" +
                "            /bin/touch /tmp/first_done_flag\n" +
                "        fi\n" +
                "    fi\n" +
                "    if [[ $2 == 'info' ]]; then \n" +
                "        if [[ -f /tmp/first_info_flag ]]; then \n" +
                "            echo 'Status: Started'\n" +
                "        else\n" +
                "            /bin/touch /tmp/first_info_flag\n" +
                "        fi\n" +
                "    fi\n" +
                "fi\n" +
                "\n" +
                "echo $@ >> .log_gluster");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        // Testing multiple replicas
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-prepare-mount.sh", "test"}, false);
        assertEquals("-> gluster-prepare-mount.sh\n" +
                " Preparing mount of test\n" +
                " - Searching in volume list for test - attempt 1\n" +
                " - Creating single replica since likely single node in cluster\n", result);

        assertEquals("volume list\n" +
                "volume create test transport tcp 192.168.10.11:/var/lib/gluster/volume_bricks/test\n" +
                "volume list\n" +
                "volume info test\n" +
                "volume start test\n" +
                "volume info test\n", StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_gluster")));
    }

    @Test
    public void testGlusterPrepareMount_MultipleReplicas() throws Exception {

        FileUtils.delete(new File ("/tmp/first_done_flag"));
        FileUtils.delete(new File ("/tmp/first_info_flag"));

        File targetPath = new File(getJailPath() + "/gluster");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "if [[ $1 == 'volume' ]]; then \n" +
                "    if [[ $2 == 'list' ]]; then \n" +
                "        echo 'flink_completed_jobs'\n" +
                "        echo 'flink_data'\n" +
                "        echo 'logstash_data'\n" +
                "        echo 'marathon_registry'\n" +
                "        echo 'spark_data'\n" +
                "        echo 'spark_eventlog'\n" +
                "        if [[ -f /tmp/first_done_flag ]]; then \n" +
                "            echo 'test'\n" +
                "        else\n" +
                "            /bin/touch /tmp/first_done_flag\n" +
                "        fi\n" +
                "    fi\n" +
                "    if [[ $2 == 'info' ]]; then \n" +
                "        if [[ -f /tmp/first_info_flag ]]; then \n" +
                "            echo 'Status: Started'\n" +
                "        else\n" +
                "            /bin/touch /tmp/first_info_flag\n" +
                "        fi\n" +
                "    fi\n" +
                "fi\n" +
                "\n" +
                "echo $@ >> .log_gluster");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        // Testing multiple replicas
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-prepare-mount.sh", "test"}, false);
        assertEquals("-> gluster-prepare-mount.sh\n" +
                " Preparing mount of test\n" +
                " - Searching in volume list for test - attempt 1\n" +
                " - Creating multiple replicas since running on multi-node cluster\n", result);

        assertEquals("volume list\n" +
                "volume create test replica 2 transport tcp 192.168.10.11:/var/lib/gluster/volume_bricks/test 192.168.10.13:/var/lib/gluster/volume_bricks/test force\n" +
                "volume list\n" +
                "volume info test\n" +
                "volume start test\n" +
                "volume info test\n", StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_gluster")));
    }

    @Test
    public void testGlusterPrepareMount_MissingArgument() throws Exception {
        // missing argument
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-prepare-mount.sh"}, false);
        assertEquals("Expecting volume name as first argument\n", result);
    }

    @Test
    public void testGlusterAddressPeerInconsistency_MasterMissesLocal() throws Exception {

        FileUtils.writeFile(new File(jailPath+"/commonGlusterFunctions.sh"), "" +
                "#/bin/bash\n" +
                "\n" +
                "function get_gluster_master() {\n" +
                "   echo '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function get_pool_ips() {\n" +
                "    if [[ \"$1\" != \"\" ]]; then \n" +
                "        echo -e '127.0.0.1'\n" +
                "    else\n" +
                "        echo -e '192.168.10.13\n127.0.0.1'\n" +
                "    fi\n" +
                "}\n" +
                "\n" +
                "function delete_gluster_management_lock_file() {\n" +
                "    echo \" - releasing gluster_management_lock\"\n" +
                "    rm -Rf /var/lib/gluster/gluster_management_lock\n" +
                "}\n");

        createLoggingExecutable("__force-remove-peer.sh", getJailPath());

        // master IP == self IP
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-address-peer-inconsistency.sh"}, false);
        assertEquals("-> gluster-address-peer-inconsistency.sh\n" +
                " - Checking gluster connection between 192.168.10.11 and 192.168.10.13\n" +
                " - Attempting to take gluster_management_lock\n" +
                " - Checking if master is in local pool\n" +
                " - Checking if local in master pool\n" +
                " - Checking consistency \n" +
                " -> gluster cluster is inconsistent. Master doesn't know local but local knows master\n" +
                " - Attempting to remove master from local pool list\n" +
                " - releasing gluster_management_lock\n", result);

        assertEquals("192.168.10.13\n", StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log___force-remove-peer.sh")));
    }

    @Test
    public void testGlusterAddressPeerInconsistency_LocalMissesMaster() throws Exception {

        FileUtils.writeFile(new File(jailPath+"/commonGlusterFunctions.sh"), "" +
                "#/bin/bash\n" +
                "\n" +
                "function get_gluster_master() {\n" +
                "   echo '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function get_pool_ips() {\n" +
                "    if [[ \"$1\" == \"\" ]]; then \n" +
                "        echo -e '127.0.0.1'\n" +
                "    else\n" +
                "        echo -e '192.168.10.11\n127.0.0.1'\n" +
                "    fi\n" +
                "}\n" +
                "\n" +
                "function delete_gluster_management_lock_file() {\n" +
                "    echo \" - releasing gluster_management_lock\"\n" +
                "    rm -Rf /var/lib/gluster/gluster_management_lock\n" +
                "}\n");

        File targetPath = new File(getJailPath() + "/gluster_call_remote.sh");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "echo $@ >> .log_gluster_call_remote");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        createLoggingExecutable("__delete-local-blocks.sh", getJailPath());

        // master IP == self IP
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-address-peer-inconsistency.sh"}, false);
        assertEquals("-> gluster-address-peer-inconsistency.sh\n" +
                " - Checking gluster connection between 192.168.10.11 and 192.168.10.13\n" +
                " - Attempting to take gluster_management_lock\n" +
                " - Checking if master is in local pool\n" +
                " - Checking if local in master pool\n" +
                " - Checking consistency \n" +
                " -> gluster cluster is inconsistent. Local doesn't know master but master knows local\n" +
                " - Attempting to remove local from master pool list\n" +
                " - Deleting corresponding local blocks\n" +
                " - releasing gluster_management_lock\n", result);

        assertEquals("192.168.10.13\n", StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log___delete-local-blocks.sh")));

        assertEquals("192.168.10.13 force-remove-peer now 192.168.10.11\n", StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_gluster_call_remote")));
    }

    @Test
    public void testGlusterAddressPeerInconsistency_ClusterConsistent() throws Exception {

        FileUtils.writeFile(new File(jailPath+"/commonGlusterFunctions.sh"), "" +
                "#/bin/bash\n" +
                "\n" +
                "function get_gluster_master() {\n" +
                "   echo '192.168.10.13'\n" +
                "}\n" +
                "\n" +
                "function get_pool_ips() {\n" +
                "    if [[ \"$1\" == \"\" ]]; then \n" +
                "        echo -e '192.168.10.13\n127.0.0.1'\n" +
                "    else\n" +
                "        echo -e '192.168.10.11\n127.0.0.1'\n" +
                "    fi\n" +
                "}\n" +
                "\n" +
                "function delete_gluster_management_lock_file() {\n" +
                "    echo \" - releasing gluster_management_lock\"\n" +
                "    rm -Rf /var/lib/gluster/gluster_management_lock\n" +
                "}\n");

        // master IP == self IP
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/gluster-address-peer-inconsistency.sh"}, false);
        assertEquals("-> gluster-address-peer-inconsistency.sh\n" +
                " - Checking gluster connection between 192.168.10.11 and 192.168.10.13\n" +
                " - Attempting to take gluster_management_lock\n" +
                " - Checking if master is in local pool\n" +
                " - Checking if local in master pool\n" +
                " - Checking consistency \n" +
                " -> gluster cluster is consistent. both local and master know each others\n" +
                " - releasing gluster_management_lock\n", result);
    }

    @Test
    public void testForceRemovePeer() throws Exception {
        File targetPath = new File(getJailPath() + "/gluster");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "if [[ $1 == 'volume' ]]; then \n" +
                "    if [[ $2 == 'list' ]]; then \n" +
                "        echo 'flink_completed_jobs'\n" +
                "        echo 'flink_data'\n" +
                "        echo 'logstash_data'\n" +
                "        echo 'marathon_registry'\n" +
                "        echo 'spark_data'\n" +
                "        echo 'spark_eventlog'\n" +
                "    fi\n" +
                "    if [[ $2 == 'info' ]]; then\n" +
                "        if [[ $3 == 'spark_eventlog' ]]; then\n" +
                "            echo 'Bricks:'\n" +
                "            echo 'Brick1: 192.168.10.11:/var/lib/gluster/volume_bricks/spark_eventlog'\n" +
                "            echo 'Number of Bricks: 1'\n" +
                "        else\n" +
                "            echo 'Bricks:'\n" +
                "            echo \"Brick1: 192.168.10.11:/var/lib/gluster/volume_bricks/$3\"\n" +
                "            echo \"Brick1: 192.168.10.13:/var/lib/gluster/volume_bricks/$3\"\n" +
                "            echo 'Number of Bricks: 1 x 2 = 2'\n" +
                "        fi\n" +
                "    fi\n" +
                "fi\n" +
                "\n" +
                "echo $@ >> .log_gluster");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        // missing argument
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/__force-remove-peer.sh"}, false);
        assertEquals("Expecting Gluster Shadow (vanished !) IP address as first argument\n", result);

        // simulating shadow remote bricks
        result = ProcessHelper.exec(new String[]{"bash", jailPath + "/__force-remove-peer.sh", "192.168.10.13"}, false);
        assertEquals("-> __force-remove-peers.sh\n" +
                " - Forcing removal of 192.168.10.13 from local (192.168.10.11) peer list\n" +
                "    + Listing local volumes\n" +
                "    + Removing all bricks from 192.168.10.13\n" +
                "    + Listing volume bricks for flink_completed_jobs\n" +
                "    + Removing brick 192.168.10.13:/var/lib/gluster/volume_bricks/flink_completed_jobs\n" +
                "    + Listing volume bricks for flink_data\n" +
                "    + Removing brick 192.168.10.13:/var/lib/gluster/volume_bricks/flink_data\n" +
                "    + Listing volume bricks for logstash_data\n" +
                "    + Removing brick 192.168.10.13:/var/lib/gluster/volume_bricks/logstash_data\n" +
                "    + Listing volume bricks for marathon_registry\n" +
                "    + Removing brick 192.168.10.13:/var/lib/gluster/volume_bricks/marathon_registry\n" +
                "    + Listing volume bricks for spark_data\n" +
                "    + Removing brick 192.168.10.13:/var/lib/gluster/volume_bricks/spark_data\n" +
                "    + Listing volume bricks for spark_eventlog\n" +
                "    + Detaching peer 192.168.10.13\n", result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_gluster"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            assertEquals("volume list\n" +
                    "volume info flink_completed_jobs\n" +
                    "volume remove-brick flink_completed_jobs replica 1 192.168.10.13:/var/lib/gluster/volume_bricks/flink_completed_jobs force\n" +
                    "volume info flink_data\n" +
                    "volume remove-brick flink_data replica 1 192.168.10.13:/var/lib/gluster/volume_bricks/flink_data force\n" +
                    "volume info logstash_data\n" +
                    "volume remove-brick logstash_data replica 1 192.168.10.13:/var/lib/gluster/volume_bricks/logstash_data force\n" +
                    "volume info marathon_registry\n" +
                    "volume remove-brick marathon_registry replica 1 192.168.10.13:/var/lib/gluster/volume_bricks/marathon_registry force\n" +
                    "volume info spark_data\n" +
                    "volume remove-brick spark_data replica 1 192.168.10.13:/var/lib/gluster/volume_bricks/spark_data force\n" +
                    "volume info spark_eventlog\n" +
                    "peer detach 192.168.10.13\n", dockerLogs);


        } else {
            fail ("No docker manipulations found");
        }
    }

        @Test
    public void testDeleteLocalBlocks() throws Exception {

        // can only do a simple test
        File targetPath = new File (getJailPath() + "/gluster_call_remote.sh");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "if [[ $1 == 'volume' ]]; then \n" +
                "    if [[ $2 == 'list' ]]; then \n" +
                "        echo 'flink_completed_jobs'\n" +
                "        echo 'flink_data'\n" +
                "        echo 'logstash_data'\n" +
                "        echo 'marathon_registry'\n" +
                "        echo 'spark_data'\n" +
                "        echo 'spark_eventlog'\n" +
                "    fi\n" +
                "fi\n");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        // missing argument
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/__delete-local-blocks.sh"}, false);
        assertEquals("Expecting Gluster master IP address as first argument\n", result);

        result = ProcessHelper.exec(new String[]{"bash", jailPath + "/__delete-local-blocks.sh", "192.168.10.13"}, false);
        assertEquals("" +
                "-> __delete-local-blocks.sh\n" +
                " - Removing de-synchronized local blocks\n", result);

    }

    @Test
    public void testReplicateMasterBlocks() throws Exception {
        File targetPath = new File (getJailPath() + "/gluster");
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "if [[ $1 == 'volume' ]]; then \n" +
                "    if [[ $2 == 'list' ]]; then \n" +
                "        echo 'flink_completed_jobs'\n" +
                "        echo 'flink_data'\n" +
                "        echo 'logstash_data'\n" +
                "        echo 'marathon_registry'\n" +
                "        echo 'spark_data'\n" +
                "        echo 'spark_eventlog'\n" +
                "    fi\n" +
                "    if [[ $2 == 'info' ]]; then\n" +
                "        if [[ $3 == 'spark_eventlog' ]]; then\n" +
                "            echo 'Bricks:'\n" +
                "            echo 'Brick1: 192.168.10.11:/var/lib/gluster/volume_bricks/spark_eventlog'\n" +
                "            echo 'Number of Bricks: 1'\n" +
                "        else\n" +
                "            echo 'Bricks:'\n" +
                "            echo \"Brick1: 192.168.10.11:/var/lib/gluster/volume_bricks/$3\"\n" +
                "            echo \"Brick1: 192.168.10.13:/var/lib/gluster/volume_bricks/$3\"\n" +
                "            echo 'Number of Bricks: 1 x 2 = 2'\n" +
                "        fi\n" +
                "    fi\n" +
                "fi\n");

        ProcessHelper.exec("chmod 755 " + targetPath, true);

        // missing argument
        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/__replicate-master-blocks.sh"}, false);
        assertEquals("Expecting Gluster master IP address as first argument\n", result);

        // brick on 192.168.10.13 for spark_eventlog needs synchronization
        result = ProcessHelper.exec(new String[]{"bash", jailPath + "/__replicate-master-blocks.sh", "192.168.10.13"}, false);
        assertEquals("" +
                "-> __replicate-master-blocks.sh\n" +
                " - Replicating single blocks to 192.168.10.13\n" +
                " - Analyzing volume flink_completed_jobs\n" +
                "    + Volume flink_completed_jobs has a local brick on 192.168.10.11\n" +
                " - Analyzing volume flink_data\n" +
                "    + Volume flink_data has a local brick on 192.168.10.11\n" +
                " - Analyzing volume logstash_data\n" +
                "    + Volume logstash_data has a local brick on 192.168.10.11\n" +
                " - Analyzing volume marathon_registry\n" +
                "    + Volume marathon_registry has a local brick on 192.168.10.11\n" +
                " - Analyzing volume spark_data\n" +
                "    + Volume spark_data has a local brick on 192.168.10.11\n" +
                " - Analyzing volume spark_eventlog\n" +
                "    + Volume spark_eventlog has a local brick on 192.168.10.11\n" +
                "    + Volume spark_eventlog has only 1 brick, need to replicate it\n", result);
    }

}
