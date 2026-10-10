package com.parvnigam.careerbridge;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:workflows;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","app.upload-dir=target/test-resumes","app.mail-enabled=false","app.local-email-testing=true","app.local-inbox-port=0","app.ai-url=","app.redis-enabled=false"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowTests {
    @org.springframework.beans.factory.annotation.Autowired VerificationMail mail;
    @LocalServerPort int port;
    final HttpClient http=HttpClient.newHttpClient();final JsonMapper json=JsonMapper.builder().build();
    HttpResponse<String> request(String method,String path,String token,Object body)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).timeout(Duration.ofSeconds(15));if(token!=null)b.header("Authorization","Bearer "+token);if(body!=null)b.header("Content-Type","application/json");return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());}
    JsonNode tree(HttpResponse<String>r){return json.readTree(r.body());}
    String login(String email)throws Exception{var res=request("POST","/auth/login",null,Map.of("email",email,"password","Demo@12345"));assertEquals(200,res.statusCode(),res.body());return tree(res).get("token").asText();}
    String student()throws Exception{return login("student@careerbridge.dev");}String recruiter()throws Exception{return login("recruiter@careerbridge.dev");}
    Map<String,Object> jobInput(Long campus){Map<String,Object>d=new LinkedHashMap<>(Map.of("title","Integration Test Internship","description","Build tested Java features and SQL queries.","location","Remote","employmentType","INTERNSHIP","workMode","REMOTE","requiredSkills",List.of("Java","SQL"),"minCgpa",0,"minExperience",0,"openings",1));d.put("universityId",campus);return d;}
    static Long createdJob;static Long appId;static Long resumeId;
    @Test @Order(1) void authenticationAndRoleEscalationAreBlocked()throws Exception{
        assertEquals(200,http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401,request("GET","/auth/me",null,null).statusCode());assertEquals(403,request("GET","/admin/users",student(),null).statusCode());
        assertEquals(403,request("POST","/auth/register",null,Map.of("name","Bad Admin","email","bad@example.com","password","Strong@12345","role","ADMIN")).statusCode());
        assertEquals(400,request("POST","/auth/register",null,Map.of("name","Weak Password","email","weak@example.com","password","short","role","RECRUITER")).statusCode());
    }
    @Test @Order(2) void signupCreatesProfileWithoutLeakingPassword()throws Exception{
        var schools=tree(request("GET","/public/universities",null,null));var result=request("POST","/auth/register",null,Map.of("name","New Student","email","new-student@example.com","password","Strong@12345","role","STUDENT","universityId",schools.get(0).get("id").asLong(),"graduationYear",2028));
        assertEquals(201,result.statusCode(),result.body());assertFalse(result.body().contains("passwordHash"));assertFalse(tree(result).has("token"));String link=mail.messages.get(mail.messages.size()-1).link();assertEquals(200,request("POST","/auth/verify-email",null,Map.of("token",link.substring(link.indexOf("#token=")+7))).statusCode());String token=tree(request("POST","/auth/login",null,Map.of("email","new-student@example.com","password","Strong@12345"))).get("token").asText();assertEquals(200,request("GET","/student/profile",token,null).statusCode());
    }
    @Test @Order(3) void recruiterCreatesOpeningAndOwnershipIsEnforced()throws Exception{
        var created=request("POST","/recruiter/jobs",recruiter(),jobInput(null));assertEquals(201,created.statusCode(),created.body());createdJob=tree(created).get("id").asLong();
        assertEquals(403,request("PUT","/recruiter/jobs/"+createdJob,login("other-recruiter@careerbridge.dev"),jobInput(null)).statusCode());
        assertEquals(403,request("POST","/recruiter/jobs",student(),jobInput(null)).statusCode());
    }
    @Test @Order(4) void universityScopeAndAcademicEligibilityAreEnforced()throws Exception{
        long campus=tree(request("GET","/public/universities",null,null)).get(0).get("id").asLong();long jid=tree(request("POST","/recruiter/jobs",recruiter(),jobInput(campus))).get("id").asLong();
        assertEquals(403,request("POST","/student/applications",login("outside@careerbridge.dev"),Map.of("jobId",jid)).statusCode());
        var d=jobInput(null);d.put("minCgpa",10);long strict=tree(request("POST","/recruiter/jobs",recruiter(),d)).get("id").asLong();assertEquals(400,request("POST","/student/applications",student(),Map.of("jobId",strict)).statusCode());
    }
    HttpResponse<String> upload(String token,String filename,byte[] bytes)throws Exception{String boundary="CareerBridge"+UUID.randomUUID();byte[] head=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] tail=("\r\n--"+boundary+"--\r\n").getBytes();var out=new java.io.ByteArrayOutputStream();out.write(head);out.write(bytes);out.write(tail);return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/student/resumes")).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());}
    @Test @Order(5) void resumeVersionsArePrivateAndSkillsAreExtracted()throws Exception{
        var first=upload(student(),"resume.txt","Java SQL Spring Boot Python".getBytes());assertEquals(200,first.statusCode(),first.body());assertTrue(first.body().contains("Java"));
        var second=upload(student(),"resume-v2.txt","Java SQL React Docker".getBytes());assertEquals(200,second.statusCode());resumeId=tree(second).get("id").asLong();
        assertEquals(2,tree(request("GET","/student/resumes",student(),null)).size());assertEquals(403,request("GET","/resumes/"+resumeId+"/download",login("aarav@careerbridge.dev"),null).statusCode());assertEquals(400,upload(student(),"script.exe","evil".getBytes()).statusCode());
    }
    @Test @Order(6) void realPdfAndDocxParsingWork()throws Exception{
        byte[] pdf;try(var document=new org.apache.pdfbox.pdmodel.PDDocument()){var page=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(page);try(var content=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)){content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(40,700);content.showText("Java SQL Spring Boot");content.endText();}var out=new java.io.ByteArrayOutputStream();document.save(out);pdf=out.toByteArray();}
        var parsed=upload(login("aarav@careerbridge.dev"),"actual.pdf",pdf);assertEquals(200,parsed.statusCode(),parsed.body());assertTrue(parsed.body().contains("Spring Boot"));
        var bytes=new java.io.ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(bytes)){zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));zip.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Java SQL Docker</w:t></w:r></w:p></w:body></w:document>".getBytes());zip.closeEntry();}var docx=upload(login("aarav@careerbridge.dev"),"actual.docx",bytes.toByteArray());assertEquals(200,docx.statusCode(),docx.body());assertTrue(docx.body().contains("Docker"));
    }
    @Test @Order(7) void applicationsAreUniqueAndCaptureSubmittedResume()throws Exception{
        var response=request("POST","/student/applications",student(),Map.of("jobId",createdJob,"coverLetter","I can contribute Java and SQL skills."));assertEquals(201,response.statusCode(),response.body());appId=tree(response).get("id").asLong();assertEquals(resumeId.longValue(),tree(response).get("resumeId").asLong());
        assertEquals(409,request("POST","/student/applications",student(),Map.of("jobId",createdJob)).statusCode());assertEquals(409,request("DELETE","/student/resumes/"+resumeId,student(),null).statusCode());assertEquals(200,request("GET","/resumes/"+resumeId+"/download",recruiter(),null).statusCode());assertEquals(403,request("GET","/resumes/"+resumeId+"/download",login("other-recruiter@careerbridge.dev"),null).statusCode());
    }
    @Test @Order(8) void pipelineTransitionsAndInterviewConflictsAreChecked()throws Exception{
        assertEquals(400,request("PATCH","/recruiter/applications/"+appId,recruiter(),Map.of("status","HIRED")).statusCode());assertEquals(200,request("PATCH","/recruiter/applications/"+appId,recruiter(),Map.of("status","SHORTLISTED")).statusCode());
        Map<String,Object> schedule=Map.of("scheduledAt",Instant.now().plusSeconds(86400).toString(),"durationMinutes",30,"meetingUrl","https://example.com/meeting","instructions","Prepare a project walkthrough");
        var result=request("POST","/recruiter/applications/"+appId+"/interview",recruiter(),schedule);assertEquals(200,result.statusCode(),result.body());assertEquals(409,request("POST","/recruiter/applications/"+appId+"/interview",recruiter(),schedule).statusCode());assertEquals(1,tree(request("GET","/interviews",student(),null)).size());
        assertEquals(200,request("PATCH","/recruiter/applications/"+appId,recruiter(),Map.of("status","OFFERED")).statusCode());assertEquals(200,request("PATCH","/recruiter/applications/"+appId,recruiter(),Map.of("status","HIRED")).statusCode());
    }
    @Test @Order(9) void placementAnalyticsAreScopedAndCountDistinctHires()throws Exception{
        var result=request("GET","/placement/analytics",login("placement@careerbridge.dev"),null);assertEquals(200,result.statusCode(),result.body());assertFalse(result.body().contains("Dev Mehta"));assertEquals(2,tree(result).get("placedStudents").asInt());
        assertEquals(200,request("GET","/placement/export",login("placement@careerbridge.dev"),null).statusCode());assertEquals(403,request("GET","/placement/analytics",student(),null).statusCode());
    }
    @Test @Order(10) void notificationsAndMockSessionsArePrivate()throws Exception{
        var notices=tree(request("GET","/notifications",student(),null));assertTrue(notices.size()>0);long nid=notices.get(0).get("id").asLong();assertEquals(403,request("PATCH","/notifications/"+nid,login("aarav@careerbridge.dev"),null).statusCode());
        var practice=request("POST","/student/practice",student(),Map.of("targetRole","Java developer","skill","Java"));assertEquals(200,practice.statusCode(),practice.body());long mid=tree(practice).get("id").asLong();var answers=Map.of("answers",Collections.nCopies(5,"Dependency injection uses a constructor and a container to provide dependencies with validation, services, authentication and authorization."));
        assertEquals(403,request("POST","/student/practice/"+mid+"/submit",login("aarav@careerbridge.dev"),answers).statusCode());assertEquals(200,request("POST","/student/practice/"+mid+"/submit",student(),answers).statusCode());assertEquals(409,request("POST","/student/practice/"+mid+"/submit",student(),answers).statusCode());
    }
    @Test @Order(11) void savedJobsAndRecommendationsWork()throws Exception{assertEquals(200,request("POST","/student/saved/"+createdJob,student(),null).statusCode());assertTrue(request("GET","/student/saved",student(),null).body().contains("Integration Test Internship"));assertEquals(200,request("GET","/student/recommendations",student(),null).statusCode());assertTrue(request("GET","/student/learning",student(),null).body().contains("gaps"));}
    @Test @Order(12) void logoutRevokesOldSession()throws Exception{String token=student();assertEquals(200,request("POST","/auth/logout",token,null).statusCode());assertEquals(401,request("GET","/auth/me",token,null).statusCode());assertEquals(200,request("GET","/auth/me",student(),null).statusCode());}
    @Test @Order(13) void invitationsAnnouncementsAndInterviewUpdatesNotifyStudents()throws Exception{
        long studentId=tree(request("GET","/auth/me",student(),null)).get("id").asLong();
        assertEquals(200,request("POST","/recruiter/candidates/"+studentId+"/invite",recruiter(),Map.of("jobId",createdJob)).statusCode());
        var interviews=tree(request("GET","/interviews",recruiter(),null));long interviewId=interviews.get(0).get("id").asLong();
        assertEquals(200,request("PATCH","/recruiter/interviews/"+interviewId,recruiter(),Map.of("status","COMPLETED")).statusCode());
        assertEquals(200,request("POST","/placement/announcements",login("placement@careerbridge.dev"),Map.of("title","Campus workshop","message","Bring your project portfolio.")).statusCode());
        assertTrue(request("GET","/notifications",student(),null).body().contains("Campus workshop"));
        assertFalse(request("GET","/notifications",login("outside@careerbridge.dev"),null).body().contains("Campus workshop"));
    }
    @Test @Order(14) void administratorsProvisionStaffAndDisableAccounts()throws Exception{
        String admin=login("admin@careerbridge.dev");long campus=tree(request("GET","/public/universities",null,null)).get(0).get("id").asLong();
        var created=request("POST","/admin/users",admin,Map.of("name","New Officer","email","officer-test@example.com","password","Strong@12345","role","PLACEMENT_OFFICER","universityId",campus));
        assertEquals(201,created.statusCode(),created.body());assertFalse(created.body().contains("passwordHash"));
        long id=tree(created).get("id").asLong();var session=request("POST","/auth/login",null,Map.of("email","officer-test@example.com","password","Strong@12345"));String token=tree(session).get("token").asText();
        assertEquals(200,request("PATCH","/admin/users/"+id,admin,Map.of("enabled",false)).statusCode());
        assertEquals(401,request("GET","/auth/me",token,null).statusCode());assertEquals(401,request("POST","/auth/login",null,Map.of("email","officer-test@example.com","password","Strong@12345")).statusCode());
        assertEquals(200,request("GET","/admin/system",admin,null).statusCode());
    }
}
