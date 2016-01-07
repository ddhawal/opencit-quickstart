/*
 * Copyright (C) 2015 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.deployment.task;

import com.intel.dcsg.cpg.crypto.RandomUtil;
import com.intel.mtwilson.Folders;
import com.intel.mtwilson.deployment.SSHClientWrapper;
import com.intel.mtwilson.deployment.descriptor.SSH;
import com.intel.mtwilson.deployment.jaxrs.faults.Connection;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.UUID;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * This is an integration task: prior to installing trust agent, a user must
 * be created in attestation service for trust agent to access those APIs.
 *
 * @author jbuhacoff
 */
public class CreateTrustAgentUserInAttestationService extends AbstractPostconfigureTask {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CreateTrustAgentUserInAttestationService.class);
    private SSH remote;

    /**
     * The remote server to pass here is the Attestation Service,  where we 
     * will ssh to create the trustagent user. 
     * @param remote 
     */
    public CreateTrustAgentUserInAttestationService(SSH remote) {
        this.remote = remote;
    }

    @Override
    public void execute() {

        String trustagentUsername = setting("trustagent.mtwilson.username"); // TODO:  generate random uuid after it, like kmsproxy does... and this should really be moved to director setup as a registration request w/ mtwilson, so all we should have to do here is get that username from director and then go to mtwilson to approve it.
        if (trustagentUsername.isEmpty()) {
            trustagentUsername = "trustagent-" + UUID.randomUUID().toString();
            setting("trustagent.mtwilson.username", trustagentUsername);
        }
        String trustagentPassword = setting("trustagent.mtwilson.password");
        if (trustagentPassword.isEmpty()) {
            int lengthBytes = 16;
            trustagentPassword = RandomUtil.randomBase64String(lengthBytes).replace("=", "");
            setting("trustagent.mtwilson.password", trustagentPassword);
        }

        // command to execute on attestation service to create the trust director user;  TODO:  if we can just call an API, that would be better than ssh+command.
        // TODO:  escape the director username and password
        String cmdCreateTrustAgentUser = "/opt/mtwilson/bin/mtwilson login-password " + trustagentUsername + " " + trustagentPassword + " --permissions *:*";

        try (SSHClientWrapper client = new SSHClientWrapper(remote)) {
            client.connect();
            try (Session session = client.session()) {
                Session.Command command = session.exec(cmdCreateTrustAgentUser);
                InputStream stdout = command.getInputStream();
                InputStream stderr = command.getErrorStream();
                String stdoutText = IOUtils.toString(stdout, "UTF-8");

                // we don't need the output when successfull
                // REST OF THIS SECTION IS JUST TO RECORD THE OUTPUT FOR DEBUGGING

                String stderrText = IOUtils.toString(stderr, "UTF-8");
                log.debug("result: {}", stdoutText);

                // ensure output directory exists
                File outputDirectory = new File(Folders.repository("tasks") + File.separator + getId());
                outputDirectory.mkdirs();
                log.debug("Output directory: {}", outputDirectory.getAbsolutePath());

                // store the stdout into a file
                File stdoutFile = new File(Folders.repository("tasks") + File.separator + getId() + File.separator + "stdout.log");
                FileUtils.writeStringToFile(stdoutFile, stdoutText, Charset.forName("UTF-8"));

                // store the stderr into a file
                File stderrFile = new File(Folders.repository("tasks") + File.separator + getId() + File.separator + "stderr.log");
                FileUtils.writeStringToFile(stderrFile, stderrText, Charset.forName("UTF-8"));

            }
            client.disconnect();
        } catch (Exception e) {
            log.error("Connection failed", e);
            fault(new Connection(remote.getHost()));
        }

        // postcondition:
        // DIRECTOR_MTWILSON_USERNAME and DIRECTOR_MTWILSON_PASSWORD must be set (and corresponding user actually created in mtwilson) 

    }
}
