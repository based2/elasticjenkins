package io.jenkins.plugins.elasticjenkins.util;

import hudson.model.*;
import io.jenkins.plugins.elasticjenkins.entity.GenericBuild;
import jenkins.model.Jenkins;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jenkinsci.plugins.mocksecurityrealm.MockSecurityRealm;
import org.junit.*;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class ElasticManagerTest  {

    private static Logger LOGGER = Logger.getLogger(ElasticManagerTest.class.getName());

    @Rule public JenkinsRule j = new JenkinsRule();

    private static String testFolder = "elasticjenkins";

    public static String root = (System.getProperty("java.io.tmpdir"))+testFolder;
    public static File testFile = new File(root);



    public static String master = "MASTERNAME";
    public String uniqueId = "20170428";
    public String title = "PROJECT_NAME";
    public String logIndex = "jenkins_logs";

    public static String url = "http://localhost:9200";

    public Computer computer;
    public Node node;


    public void deleleTest(String index, String type,String id) throws IOException {
        HttpDelete httpDelete = new HttpDelete(url+"/"+index+"/"+type+"/"+id);
        httpDelete.setHeader("Accept","application/json");
        HttpClientBuilder builder = HttpClientBuilder.create();
        CloseableHttpClient client = builder.build();
        CloseableHttpResponse response = client.execute(httpDelete);
    }


    public Run<?,?> generateBuild(String id) throws IOException {
        computer = j.jenkins.createComputer();
        node = computer.getNode();
        j.jenkins.setNumExecutors(2);

        Long queueId = new Long(1);
        String url = "/"+title+"/"+id;

        Run build = Mockito.mock(Run.class);

        StringParameterValue p1 = new StringParameterValue("parameter1","value1");
        StringParameterValue p2 = new StringParameterValue("parameter2","value2");
        ParametersAction pa =  new ParametersAction(p1,p2);


        List<ParametersAction> list = new ArrayList<ParametersAction>();
        list.add(pa);
        //Create Mock Job
        Job job = Mockito.mock(Job.class);
        job.setDisplayName(title);

        Mockito.when(build.getUrl()).thenReturn(url);
        Mockito.when(build.getId()).thenReturn(id);
        Mockito.when(build.getActions(ParametersAction.class)).thenReturn(list);
        Mockito.when(build.getParent()).thenReturn(job);
        Mockito.when(build.getQueueId()).thenReturn(queueId);
        Mockito.when(build.getDisplayName()).thenReturn(title);

        return build;
    }

    @BeforeClass
    public static void initialize() throws IOException {
        if(! testFile.exists()) {
            if(!testFile.mkdirs())
                throw new IOException("Can not execute since the directory is not writtable: "+root);
        }
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        ElasticJenkinsUtil.writeProperties(master,url,"UTF-16",logIndex);

    }

    @Test
    public void allTests() throws IOException, InterruptedException {
        //Add build
        Run<?,?> build = generateBuild("1");
        ElasticManager em = new ElasticManager();
        String index = "jenkins_test";
        String type = "builds";
        String idElastic = em.addBuild(index,type,build);
        assertTrue(idElastic.equals("1_"+master));
        deleleTest(index,type,"1_"+master);
        //Thread.sleep(10000);
        //Update build
        String fileName = "testUpdate.log";
        String idElastic2 = em.addBuild(index,type,build);
        List<String> output = new ArrayList<>();
        File file = new File(root+"/"+fileName);
        if(file.exists()) file.delete();
        BufferedWriter writer = new BufferedWriter(new FileWriter(file.getPath(),true));
        writer.append("Line1");
        writer.append("Line2");
        writer.close();
        PowerMockito.when(build.getLogFile()).thenReturn(file);
        Thread.sleep(3000);
        List<String> logs = new ArrayList<>();
        logs.add("Line1");
        logs.add("Line2");
        String idUpdated = em.updateBuild(index,type,build,idElastic2,"COMPLETED",logs);
        assertEquals("1_"+master,idUpdated);
        deleleTest(index,type,"1_"+master);
        if(file.exists()) file.delete();
        //Search by id
        //Thread.sleep(10000);
        String fileName2 = "testUpdate2.log";
        String idElastic3 = em.addBuild(index,type,build);
        assertTrue(idElastic.equals("1_"+master));

        List<String> output2 = new ArrayList<>();

        File file2 = new File(root+"/"+fileName2);

        if(file2.exists()) file2.delete();

        String line1 ="[[ \"test\" == \"${TEST}\" ]] && echo 'OK';";
        String line2 = "#Javascript";
        String line3 = "if('OK' == 'OK'){alert('OK');}";
        List<String> logs2 = new ArrayList<>();
        logs2.add(line1);
        logs2.add(line2);
        logs2.add(line3);
        BufferedWriter writer2 = new BufferedWriter(new FileWriter(file.getPath(),true));
        writer2.append(line1+"\n");
        writer2.append(line2+"\n");
        writer2.append(line3);
        writer2.flush();
        writer2.close();
        PowerMockito.when(build.getLogFile()).thenReturn(file);
        String idUpdated2 = em.updateBuild(index,type,build,idElastic3,"COMPLETED",logs2);
        assertEquals(idElastic3,idUpdated2);
        GenericBuild genericBuild = em.searchById(index,type,idElastic3);
        List<String> outputList = em.getLogOutput(genericBuild.getLogOutput());
        int ind = 1;
        for(String row : outputList) {
            switch (ind) {
                case 1:
                    assertEquals(line1,URLDecoder.decode(row,"UTF-16"));
                    break;
                case 2:
                    assertEquals(line2,URLDecoder.decode(row,"UTF-16"));
                    break;
                case 3:
                    assertEquals(line3,URLDecoder.decode(row,"UTF-16"));
                    break;
                default:
                    LOGGER.log(Level.SEVERE,"Unknown value:"+ind);
                    fail();
                    break;
            }
            ind++;
        }
        deleleTest(index,type,"1_"+master);
        if(file.exists()) file.delete();
        //Get pagination
        for(int i=1;i<10;i++) {
            em.addBuild(index,type,generateBuild(Integer.toString(i)));
        }
        //Let elasticsearch save the entries correctly
        Thread.sleep(2000);
        List<GenericBuild> list = em.getPaginateBuildHistory(index,type, 2, "5");
        assertEquals("4",list.get(0).getId());
        assertEquals("3",list.get(1).getId());
        for(int i=1;i<10;i++) {
            deleleTest(index,type,Integer.toString(i)+"_"+master);
        }
    }

    @Ignore
    @Test
    public void addBuild() throws IOException, InterruptedException {

        Run<?,?> build = generateBuild("1");
        ElasticManager em = new ElasticManager();
        String index = "jenkins_test";
        String type = "builds";
        String idElastic = em.addBuild(index,type,build);
        assertTrue(idElastic.equals("1_"+master));
        deleleTest(index,type,"1_"+master);
    }

    @Ignore
    @Test
    public void updateBuild() throws IOException, InterruptedException {
        Thread.sleep(10000);
        Run<?,?> build = generateBuild("1");
        ElasticManager em = new ElasticManager();
        String index = "jenkins_test";
        String type = "builds";
        String fileName = "testUpdate.log";
        String idElastic = em.addBuild(index,type,build);
        List<String> output = new ArrayList<>();
        File file = new File(root+"/"+fileName);
        if(file.exists()) file.delete();
        BufferedWriter writer = new BufferedWriter(new FileWriter(file.getPath(),true));
        writer.append("Line1");
        writer.append("Line2");
        writer.close();
        PowerMockito.when(build.getLogFile()).thenReturn(file);
        //String idUpdated = em.updateBuild(index,type,build,idElastic);
        //assertEquals("1_"+master,idUpdated);
        deleleTest(index,type,"1_"+master);
        if(file.exists()) file.delete();

    }

    @Ignore
    @Test
    public void searchById() throws IOException, InterruptedException {
        Thread.sleep(10000);
        Run<?,?> build = generateBuild("1");
        ElasticManager em = new ElasticManager();
        String index = "jenkins_test";
        String type = "builds";
        String fileName = "testUpdate2.log";
        String idElastic = em.addBuild(index,type,build);
        assertTrue(idElastic.equals("1_"+master));

        List<String> output = new ArrayList<>();

        File file = new File(root+"/"+fileName);

        if(file.exists()) file.delete();

        String line1 ="[[ \"test\" == \"${TEST}\" ]] && echo 'OK';";
        String line2 = "#Javascript";
        String line3 = "if('OK' == 'OK'){alert('OK');}";
        BufferedWriter writer = new BufferedWriter(new FileWriter(file.getPath(),true));
        writer.append(line1+"\n");
        writer.append(line2+"\n");
        writer.append(line3);
        writer.flush();
        writer.close();
        PowerMockito.when(build.getLogFile()).thenReturn(file);
        //String idUpdated = em.updateBuild(index,type,build,idElastic);
        //assertEquals(idElastic,idUpdated);
        GenericBuild genericBuild = em.searchById(index,type,idElastic);
        List<String> outputList = em.getLogOutput(genericBuild.getLogOutput());
        int ind = 1;
        for(String row : outputList) {
            switch (ind) {
                case 1:
                    assertEquals(line1,URLDecoder.decode(row,"UTF-16"));
                    break;
                case 2:
                    assertEquals(line2,URLDecoder.decode(row,"UTF-16"));
                    break;
                case 3:
                    assertEquals(line3,URLDecoder.decode(row,"UTF-16"));
                    break;
                default:
                    LOGGER.log(Level.SEVERE,"Unknown value:"+ind);
                    fail();
                    break;
            }
            ind++;
        }
        deleleTest(index,type,"1_"+master);
        if(file.exists()) file.delete();
    }

    @Ignore
    @Test
    public void testGetPaginateBuildHistory() throws IOException, InterruptedException {
        ElasticManager em = new ElasticManager();
        String index = "jenkins_test";
        String type = "builds";
        for(int i=1;i<10;i++) {
            em.addBuild(index,type,generateBuild(Integer.toString(i)));
        }
        //Let elasticsearch save the entries correctly
        Thread.sleep(2000);
        List<GenericBuild> list = em.getPaginateBuildHistory(index,type, 2, "5");
        assertEquals("4",list.get(0).getId());
        assertEquals("3",list.get(1).getId());
        for(int i=1;i<10;i++) {
            deleleTest(index,type,Integer.toString(i)+"_"+master);
        }
    }
}