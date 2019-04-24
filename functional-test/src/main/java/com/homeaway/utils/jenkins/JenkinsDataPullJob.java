package com.homeaway.utils.jenkins;


public class JenkinsDataPullJob {
   /*
    static final Logger logger = Logger.getLogger(JenkinsDataPullJob.class);

    private String baseUri = "jenkins_job_baseUri";
    private String pipelineName;
    private int buildNumber;

    private String jenkinsJobDetailsUrl ;
    private String userName = "";
    private String password = "";
    private String jenkinsConsoleUrl;
    public JenkinsDataPullJob(String pipelineName){
        this.pipelineName = "emr-"+pipelineName+"-pipeline";
        jenkinsJobDetailsUrl = baseUri+this.pipelineName+"/job/test/api/json?pretty=true";
        buildNumber = getCurrentBuildNumber();

    }

    public boolean waitForBuildToComplete() throws InterruptedException {
        int maxCountCheck = 10;
        int nextBuild = buildNumber +3;
        logger.info("Waiting for the build# "+nextBuild+" to start.");
        jenkinsConsoleUrl = getJenkinsConsoleUrl(  nextBuild);
        if(!waitForActualBuildToStart(jenkinsConsoleUrl)){
            logger.info("Build didn't start.So terminating the test.");
            return false;
        }

        Response response = httpGetCall(jenkinsConsoleUrl,userName,password);
        boolean isPending= new Boolean(response.headers.getOrDefault("X-More-Data", "false"));
        if(isPending){
            logger.info("Build has started. Waiting for 7 minutes to complete the build.");
            Thread.sleep(7*60*1000);
        }else {
            logger.info("Build didn't start. waiting for 1 more min to start the build.");
            Thread.sleep(60*1000);
            response = httpGetCall(jenkinsConsoleUrl,userName,password);
            isPending= new Boolean(response.headers.getOrDefault("X-More-Data", "false"));
            if(BooleanUtils.isFalse(isPending)){
                logger.error("Build is not starting.");
                return false;
            }
        }
        int i =0;
        while(isPending && i< maxCountCheck ){
            logger.info("Build didn't complete yet. waiting for 1 more min to complete the build.");
            Thread.sleep(60*1000);
            response = httpGetCall(jenkinsConsoleUrl,userName,password);
            isPending= new Boolean(response.headers.getOrDefault("X-More-Data", "false"));
            i++;
        }
        if(i==10)
            return false;
        return true;
    }

    public String getClusterID()  {
       String consoleLog = httpGetCall(jenkinsConsoleUrl,userName, password).responseBody;
       Matcher m = Pattern.compile(".*clusterid=.*").matcher(consoleLog);
       if(m.find()){
           String line = m.group();
           return line.substring(line.indexOf("=")+1).trim();
       }
       return "";
    }

    public int getCurrentBuildNumber(){
        int buildNum = 0;
        String responseBody = httpGetCall(jenkinsJobDetailsUrl, userName, password).responseBody;
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(responseBody);
            buildNum = node.path("nextBuildNumber").asInt() -1;
            logger.info("Jenkins current build number is "+buildNum);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buildNum;

    }

    public Response httpGetCall(String urlString, String username, String password) {
        URI uri = URI.create(urlString);
        HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), new UsernamePasswordCredentials(username, password));
        CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        HttpGet httpGet = new HttpGet(uri);
        HttpResponse response = null;
        Response customResponse = new Response();
        try {
            response = httpClient.execute(host, httpGet, getClientContext(urlString));
            customResponse.responseBody = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
        }
        customResponse.statusCode = response.getStatusLine().getStatusCode();
        Map<String, String>  headers = new HashMap<>();
        for(Header header : response.getAllHeaders()){
            headers.put(header.getName(),header.getValue());
        }
        customResponse.headers = headers;

        return customResponse;
    }

    public HttpClientContext getClientContext(String urlString){
        URI uri = URI.create(urlString);
        HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        AuthCache authCache = new BasicAuthCache();
        // Generate BASIC scheme object and add it to the local auth cache
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(host, basicAuth);
        // Add AuthCache to the execution context
        HttpClientContext localContext = HttpClientContext.create();
        localContext.setAuthCache(authCache);
        return localContext;
    }

    public boolean waitForActualBuildToStart(String url) throws InterruptedException {
        int statusCode = httpGetCall(url, userName,  password).statusCode;
        int maxCount = 5;
        int i =0;
        while(statusCode == 404 && i< maxCount){
            Thread.sleep(60*1000);
            logger.info("Build didn't start yet. Waiting for 1 more minute.");
            statusCode = httpGetCall(url, userName,  password).statusCode;
            i++;
        }
        return  statusCode == 200;
    }

    private String getJenkinsConsoleUrl(int buildNumber){
        return jenkinsConsoleUrl = baseUri+this.pipelineName+"/job/test/"+buildNumber+"/logText/progressiveText?start=93000";
    }



*/



}
