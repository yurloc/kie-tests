package org.kie.remote.tests.base;

import static org.kie.remote.tests.base.RestUtil.*;
import static org.junit.Assert.*;
import static org.kie.remote.tests.base.RestUtil.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.guvnor.rest.client.CreateOrCloneRepositoryRequest;
import org.guvnor.rest.client.CreateOrganizationalUnitRequest;
import org.guvnor.rest.client.JobRequest;
import org.guvnor.rest.client.JobResult;
import org.guvnor.rest.client.JobStatus;
import org.guvnor.rest.client.OrganizationalUnit;
import org.guvnor.rest.client.RemoveRepositoryRequest;
import org.guvnor.rest.client.RepositoryRequest;
import org.guvnor.rest.client.RepositoryResponse;
import org.junit.Ignore;
import org.junit.Test;

@Ignore // add Junit "Ping Succeed or Ignore" rule
public class RestUtilTest {

    private static final String contentType = MediaType.APPLICATION_JSON;
    private static final String user = "mary";
    private static final String password = "mary123@";
    
    private static URL deploymentUrl;
    static {
        try {
            deploymentUrl = new URL("http://localhost:8080/test/");
        } catch( MalformedURLException e ) {
            // no-op
        }
    }
    
    @Test
    public void restMethods() throws Exception { 
       
        Collection<RepositoryResponse> repoList = 
                get(deploymentUrl, "rest/repositories", 
                        contentType, 200, user, password,
                        Collection.class, RepositoryResponse.class);
        
        assertNotNull("Null repo list", repoList);
        
        OrganizationalUnit orgUnit = new OrganizationalUnit();
        orgUnit.setRepositories(new ArrayList<String>());
        for (RepositoryResponse repo : repoList ) { 
            orgUnit.getRepositories().add(repo.getName());
        }
        orgUnit.setName(UUID.randomUUID().toString());
        orgUnit.setOwner(this.getClass().getName());
        CreateOrganizationalUnitRequest createOrgUnitRequest =
                postEntity(deploymentUrl, "rest/organizationalunits/",
                        contentType, 202,  user, password, 0.5,
                        orgUnit, CreateOrganizationalUnitRequest.class);
        assertNotNull("CreateOrganizationalUnitRequest", createOrgUnitRequest); 
        waitForJobsToFinish(7, createOrgUnitRequest);
    
        String repoUrl = "https://github.com/droolsjbpm/jbpm-playground.git";
        String repositoryName = UUID.randomUUID().toString();
        String project = "integration-tests";
       
        RepositoryRequest repoRequest = new RepositoryRequest();
        repoRequest.setName(repositoryName);
        repoRequest.setRequestType("clone");
        repoRequest.setGitURL(repoUrl);
        repoRequest.setOrganizationalUnitName(orgUnit.getName());
        
        CreateOrCloneRepositoryRequest createRepoRequest = postEntity(deploymentUrl,
                "rest/repositories", contentType, 
                202, user, password, 
                repoRequest,
                CreateOrCloneRepositoryRequest.class);
        assertNotNull("CreateOrCloneRepositoryRequest", createRepoRequest);
        waitForJobsToFinish(7, createRepoRequest);
        
        RemoveRepositoryRequest removeRepoRequest = delete(deploymentUrl, 
                "repositories/" + repoRequest.getName(), contentType, 
                202, user, password, 
                RemoveRepositoryRequest.class);
        assertNotNull("RemoveRepositoryRequest", removeRepoRequest);
        waitForJobsToFinish(7, removeRepoRequest);
    }
    
    private <R,S> void waitForJobsToFinish(int sleepSecs, JobRequest ...requests ) { 
        Map<String, JobStatus> requestStatusMap = new HashMap<String, JobStatus>();
      
        int totalTries = 10;
        int tryCount = 0;
        List<JobRequest> checkRequests = new ArrayList<JobRequest>(Arrays.asList(requests));
        while( ! checkRequests.isEmpty() && tryCount < totalTries ) { 
            List<JobRequest> done = new ArrayList<JobRequest>(checkRequests.size());
            for( JobRequest request : requests ) { 
                String jobId = request.getJobId();
                JobStatus jobStatus  = requestStatusMap.get(jobId);
                if( JobStatus.SUCCESS.equals(jobStatus) ) { 
                   done.add(request);
                   continue;
                }
                if( JobStatus.FAIL.equals(jobStatus) ) { 
                    fail( "Job " + jobId + " failed!");
                }
                JobResult jobResult = get( deploymentUrl,
                        "rest/jobs/" + jobId, contentType, 
                        200, user, password, 
                        JobResult.class);
                requestStatusMap.put(jobId, jobResult.getStatus());
            }
            checkRequests.removeAll(done);
            if( checkRequests.isEmpty()) { 
                break;
            }
            ++tryCount;
            try { 
                Thread.sleep(sleepSecs*1000);
            } catch( Exception e ) { 
                // no op
            }
        }
     }
}
