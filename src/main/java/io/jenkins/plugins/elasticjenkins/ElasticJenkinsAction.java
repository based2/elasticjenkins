package io.jenkins.plugins.elasticjenkins;


import com.google.gson.Gson;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import hudson.XmlFile;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Run;
import hudson.slaves.ComputerLauncher;
import hudson.util.XStream2;
import io.jenkins.plugins.elasticjenkins.entity.GenericBuild;
import io.jenkins.plugins.elasticjenkins.util.ElasticJenkinsUtil;
import io.jenkins.plugins.elasticjenkins.util.ElasticLogHandler;
import io.jenkins.plugins.elasticjenkins.util.ElasticManager;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import org.apache.commons.jelly.XMLOutput;
import org.apache.http.HttpRequest;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ElasticJenkinsAction extends ComputerLauncher implements RunAction2 {
    private AbstractProject<?,?> project;

    private static final Logger LOGGER = Logger.getLogger(ElasticJenkinsAction.class.getName());


    @XStreamOmitField
    protected AbstractBuild<?,?> build;

    ElasticJenkinsAction(AbstractProject<?,?> project) {
        this.project = project;
    }

    public String getIconFileName() {
        return "/plugin/elasticjenkins/24x24/elasticjenkins.png";
    }

    public String getDisplayName() {
        return "Elastic build history";
    }

    public String getUrlName() {
        return "ej1";
    }

    public AbstractProject<?,?> getProject() {
        return project;
    }


    @Override
    public void onAttached(Run<?,?> r) {
        build = (AbstractBuild<?,?>) r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        build = (AbstractBuild<?,?>) r;
    }

    @JavaScriptMethod
    public List<GenericBuild> getPaginatedHistory(@Nonnull String type,
                                                  @Nonnull String viewType,
                                                  @Nonnull Integer paginationSize,@Nonnull String paginationStart) {
        ElasticManager elasticManager = new ElasticManager();
        String index = ElasticJenkinsUtil.getHash(project.getUrl().split("/$")[0]);

       return  elasticManager.getPaginateBuildHistory(index,type,viewType , paginationSize, paginationStart);
    }

    @JavaScriptMethod
    public String getPagninatedHistoryJson(@Nonnull String type,
                                           @Nonnull String viewType,
                                           @Nonnull Integer paginationSize,@Nonnull String paginationStart) {
        ElasticManager elasticManager = new ElasticManager();
        String index = ElasticJenkinsUtil.getHash(project.getUrl().split("/$")[0]);
        Gson gson = new Gson();

        return gson.toJson(elasticManager.getPaginateBuildHistory(index,type,viewType , paginationSize, paginationStart));
    }

    @JavaScriptMethod
    public String getNewResultsJson(@Nonnull String type,@Nonnull String viewType,@Nonnull String lastFetch) {
        ElasticManager elasticManager = new ElasticManager();
        String index = ElasticJenkinsUtil.getHash(project.getUrl().split("/$")[0]);
        Gson gson = new Gson();


        return gson.toJson(elasticManager.getNewResults(index,type,lastFetch,viewType ));
    }

    @JavaScriptMethod
    public String filterResult(@Nonnull String type,@Nonnull String viewType,@Nonnull String filterType, @Nonnull String filterValue) {
        if(filterType.equals("parameters")) {
            return getBuildByParameters(type,viewType,filterValue);
        }

        return null;
    }


    public String getBuildByParameters(@Nonnull String type,@Nonnull String viewType,@Nonnull String parameter) {
        ElasticManager elasticManager = new ElasticManager();
        String index = ElasticJenkinsUtil.getHash(project.getUrl().split("/$")[0]);
        Gson gson = new Gson();
        return gson.toJson(elasticManager.findByParameter(index,type,viewType,parameter));
    }

    @JavaScriptMethod
    public void writeOutput(XMLOutput out,String id) throws IOException, SAXException {
        ElasticManager elasticManager = new ElasticManager();
        //Get log id
        String index = ElasticJenkinsUtil.getHash(project.getUrl().split("/$")[0]);
        String suffix = elasticManager.getLogOutputId(index,"builds",id);
        List<String> list = elasticManager.getLogOutput(URLDecoder.decode(suffix,"UTF-8"));
        for(String row: list) {
            out.write(row+"\n");
        }

        out.flush();

    }

    public void doProgressiveHtml(StaplerRequest req, StaplerResponse rsp) throws IOException {

        ElasticManager elasticManager = new ElasticManager();
        AnnotatedLargeText text = elasticManager.getLog();
        rsp.addHeader("X-More-Data","true");
        text.doProgressiveHtml(req,rsp);

    }

    public void writeLogTo(XMLOutput out) throws IOException {

        Run<?,?> myBuild = (Run<?, ?>) new XStream2().fromXML(new File(Jenkins.getInstance().getRootDir(),"/test_build.xml"));
        //LOGGER.log(Level.INFO,"Root dir:"+myBuild.getRootDir());
        new AnnotatedLargeText<GenericBuild>(new File(Jenkins.getInstance().getRootDir(),"/myfile.txt"),Charset.defaultCharset(),true,new GenericBuild()).writeHtmlTo(0,out.asWriter());

    }

    public HttpResponse doGetLog(StaplerRequest request) {
        return HttpResponses.forwardToView(this,"log_output.jelly");
    }
}
