package com.parvnigam.careerbridge;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:resumereports;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload-dir=target/report-test-uploads","app.mail-enabled=false","app.local-email-testing=false","app.redis-enabled=false"})
class ResumeReportTests {
    @LocalServerPort int port;
    @Autowired Repositories.Resumes resumes;
    @Autowired Repositories.Accounts accounts;
    @Autowired Repositories.Jobs jobs;
    @Autowired Repositories.ResumeReports reports;
    static HttpServer provider;
    static final AtomicInteger requests=new AtomicInteger();
    @DynamicPropertySource static void service(DynamicPropertyRegistry registry) throws Exception {
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        provider.createContext("/",exchange->{
            requests.incrementAndGet();exchange.getRequestBody().readAllBytes();
            byte[] body="{}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
        });provider.start();registry.add("app.ai-url",()->"http://127.0.0.1:"+provider.getAddress().getPort());
    }
    @AfterAll static void stop(){if(provider!=null)provider.stop(0);}
    final JsonMapper json=JsonMapper.builder().build();final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<String> call(String method,String path,String token,Object body)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.header("Content-Type","application/json");
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode tree(HttpResponse<String> result){return json.readTree(result.body());}
    String login(String email)throws Exception {var result=call("POST","/auth/login",null,Map.of("email",email,"password","Demo@12345"));assertEquals(200,result.statusCode());return tree(result).get("token").asText();}
    Model.Resume fixture(String owner,String text) {
        Model.Resume resume=new Model.Resume();resume.userId=accounts.findByEmail(owner).orElseThrow().id;
        resume.filename="fictional-resume.txt";resume.extractedText=text;resume.storageKey=UUID.randomUUID()+".txt";resume.mediaType="text/plain";
        return resumes.save(resume);
    }
    @Test void reportsAndExtractedTextArePrivateAndStudentOnly() throws Exception {
        var resume=fixture("student@careerbridge.dev",ResumeScorerTests.STRONG);String owner=login("student@careerbridge.dev"),other=login("aarav@careerbridge.dev");
        var created=call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of());assertEquals(201,created.statusCode(),created.body());long id=tree(created).get("id").asLong();
        assertEquals(401,call("POST","/student/resumes/"+resume.id+"/reports",null,Map.of()).statusCode());
        assertEquals(403,call("POST","/student/resumes/"+resume.id+"/reports",other,Map.of()).statusCode());
        assertEquals(403,call("GET","/student/resumes/"+resume.id+"/text",other,null).statusCode());
        assertEquals(403,call("GET","/student/resume-reports/"+id,other,null).statusCode());
        assertEquals(403,call("DELETE","/student/resume-reports/"+id,other,null).statusCode());
        assertEquals(403,call("GET","/student/resume-reports",login("recruiter@careerbridge.dev"),null).statusCode());
        assertFalse(call("GET","/student/resume-reports",other,null).body().contains("\"id\":"+id+","));
    }
    @Test void generalAndUnknownTargetsNeverInventMatchScores() throws Exception {
        var resume=fixture("student@careerbridge.dev",ResumeScorerTests.STRONG);String owner=login("student@careerbridge.dev");
        var general=tree(call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of())).get("report");
        assertTrue(general.get("match").get("score").isNull());assertEquals(100,general.get("readiness").get("score").asInt());
        var unknown=tree(call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("jobDescription","A specialist vacancy requiring niche tools and techniques."))).get("report");
        assertTrue(unknown.get("match").get("score").isNull());
    }
    @Test void selectedJobUsesResumeTextAndHistorySurvivesSourceChanges() throws Exception {
        var resume=fixture("student@careerbridge.dev","JavaScript\nSkills\nJavaScript");String owner=login("student@careerbridge.dev");
        Model.Job job=new Model.Job();job.recruiterId=accounts.findByEmail("recruiter@careerbridge.dev").orElseThrow().id;
        job.title="Resume Target";job.description="Develop Java and SQL features";job.requiredSkills=new ArrayList<>(List.of("Java","SQL"));job=jobs.save(job);
        var created=call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("jobId",job.id));assertEquals(201,created.statusCode(),created.body());var original=tree(created);long reportId=original.get("id").asLong();
        assertEquals(0,original.get("report").get("match").get("score").asInt()); // profile Java skills cannot raise the resume score
        job.title="Changed title";job.requiredSkills=List.of("JavaScript");jobs.save(job);resumes.deleteById(resume.id);
        var saved=call("GET","/student/resume-reports/"+reportId,owner,null);assertEquals(200,saved.statusCode());
        assertEquals(original.get("report"),tree(saved).get("report"));assertEquals(original.get("filename"),tree(saved).get("filename"));
        assertEquals(204,call("DELETE","/student/resume-reports/"+reportId,owner,null).statusCode());
        assertEquals(404,call("GET","/student/resume-reports/"+reportId,owner,null).statusCode());
    }
    @Test void invalidTargetsAndForeignCampusAreRejected() throws Exception {
        var resume=fixture("student@careerbridge.dev",ResumeScorerTests.STRONG);String owner=login("student@careerbridge.dev");
        assertEquals(400,call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("jobId",1,"jobDescription","Java developer position with SQL skills")).statusCode());
        assertEquals(400,call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("jobDescription","short")).statusCode());
        assertEquals(400,call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("jobDescription","x".repeat(12001))).statusCode());
        Model.Job campus=new Model.Job();campus.title="Campus target";campus.description="Java developer";
        campus.recruiterId=accounts.findByEmail("recruiter@careerbridge.dev").orElseThrow().id;campus.universityId=accounts.findByEmail("outside@careerbridge.dev").orElseThrow().universityId;
        campus=jobs.save(campus);
        assertEquals(403,call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("jobId",campus.id)).statusCode());
        campus.universityId=null;campus.status="CLOSED";jobs.save(campus);
        assertEquals(400,call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("jobId",campus.id)).statusCode());
        assertEquals(400,call("GET","/student/resume-reports?page=-1",owner,null).statusCode());
    }
    @Test void legacyAiFlagCannotTriggerAnyServiceCall() throws Exception {
        var resume=fixture("student@careerbridge.dev",ResumeScorerTests.STRONG);String owner=login("student@careerbridge.dev");int count=requests.get();
        var local=call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of());assertEquals(201,local.statusCode(),local.body());
        var stale=call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("aiSuggestions",true));assertEquals(201,stale.statusCode(),stale.body());
        assertEquals(count,requests.get());var report=tree(stale).get("report");assertFalse(report.has("ai"));
        assertEquals(tree(local).get("report"),report);assertFalse(report.get("readiness").get("suggestions").isNull());
    }
    @Test void paginationIsStableAndLimitedToTheOwner() throws Exception {
        var owner=accounts.findByEmail("outside@careerbridge.dev").orElseThrow();List<Long> ids=new ArrayList<>();
        for(int i=0;i<21;i++){Model.ResumeReport report=new Model.ResumeReport();report.userId=owner.id;report.filename="page-"+i;report.reportJson="{}";
            report.createdAt=java.time.Instant.parse("2026-01-01T00:00:00Z");ids.add(reports.save(report).id);}
        String token=login("outside@careerbridge.dev");var first=tree(call("GET","/student/resume-reports?page=0",token,null));
        var second=tree(call("GET","/student/resume-reports?page=1",token,null));assertEquals(20,first.size());assertEquals(1,second.size());
        assertEquals(ids.get(20).longValue(),first.get(0).get("id").asLong());assertEquals(ids.get(0).longValue(),second.get(0).get("id").asLong());
        reports.deleteAllById(ids);
    }
    @Test void missingOrUnparseableSourceIsRejectedBeforeAnyProviderCall() throws Exception {
        var resume=fixture("student@careerbridge.dev","   ");String owner=login("student@careerbridge.dev");int count=requests.get();
        assertEquals(400,call("POST","/student/resumes/"+resume.id+"/reports",owner,Map.of("aiSuggestions",true)).statusCode());
        assertEquals(404,call("POST","/student/resumes/9223372036854775807/reports",owner,Map.of("aiSuggestions",true)).statusCode());
        assertEquals(count,requests.get());
    }
}
