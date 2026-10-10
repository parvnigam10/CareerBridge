package com.parvnigam.careerbridge;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:eligibility;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.upload-dir=target/eligibility-test-resumes",
    "app.mail-enabled=false","app.ai-url=","app.redis-enabled=false"})
class EligibilityTests {
    @LocalServerPort int port;
    final HttpClient http=HttpClient.newHttpClient();
    final JsonMapper json=JsonMapper.builder().build();
    HttpResponse<String> call(String method,String path,String token,Object body)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body!=null)request.header("Content-Type","application/json");
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode tree(HttpResponse<String> response){return json.readTree(response.body());}
    String login(String email)throws Exception {
        var response=call("POST","/auth/login",null,Map.of("email",email,"password","Demo@12345"));
        assertEquals(200,response.statusCode(),response.body());return tree(response).get("token").asText();
    }
    String student()throws Exception {
        long university=tree(call("GET","/public/universities",null,null)).get(0).get("id").asLong();
        var response=call("POST","/auth/register",null,Map.of("name","Eligibility Test Student","email",UUID.randomUUID()+"@example.com","password","Strong@12345","role","STUDENT","universityId",university,"department","Computer Science","graduationYear",2028));
        assertEquals(201,response.statusCode(),response.body());return tree(response).get("token").asText();
    }
    Map<String,Object> academicInput() {
        return new LinkedHashMap<>(Map.of("tenthScoreType","PERCENTAGE","tenthScore",60.0,"secondaryQualification","TWELFTH","secondaryScoreType","PERCENTAGE","secondaryScore",60.0,"cgpa",7.0,"department","Computer Science","graduationYear",2028,"activeBacklogs",0));
    }
    Map<String,Object> jobInput(boolean strict) {
        var input=new LinkedHashMap<String,Object>();
        input.put("title","Eligibility Test Opening");input.put("description","A Java internship with transparent eligibility.");input.put("location","Remote");input.put("employmentType","INTERNSHIP");input.put("workMode","REMOTE");input.put("requiredSkills",List.of("Java"));input.put("minCgpa",strict?7:0);
        if(strict){input.put("minTenthPercentage",60);input.put("minSecondaryPercentage",60);input.put("allowedBranches",List.of("Computer Science"));input.put("allowedGraduationYears",List.of(2028));input.put("maxActiveBacklogs",0);}
        return input;
    }
    long createJob(Map<String,Object> input)throws Exception {
        var response=call("POST","/recruiter/jobs",login("recruiter@careerbridge.dev"),input);assertEquals(201,response.statusCode(),response.body());return tree(response).get("id").asLong();
    }
    JsonNode report(long job,String token)throws Exception {
        var response=call("GET","/jobs/"+job,token,null);assertEquals(200,response.statusCode(),response.body());return tree(response).get("match").get("eligibility");
    }
    JsonNode check(JsonNode report,String key) {
        for(JsonNode check:report.get("checks"))if(check.get("key").asText().equals(key))return check;
        fail("Missing check: "+key);return null;
    }
    void academics(String token,Map<String,Object> input)throws Exception {
        var response=call("PUT","/student/academics",token,input);assertEquals(200,response.statusCode(),response.body());
    }

    @Test void exactCutoffsAreInclusiveAndBranchMatchingIgnoresCaseAndExtraSpaces()throws Exception {
        String token=student();var academic=academicInput();academic.put("department"," computer   SCIENCE ");academics(token,academic);
        var input=jobInput(true);input.put("allowedBranches",List.of("Computer  Science","computer science"));input.put("allowedGraduationYears",List.of(2028,2028));long job=createJob(input);
        var saved=tree(call("GET","/jobs/"+job,token,null));assertEquals(1,saved.get("allowedBranches").size());assertEquals(1,saved.get("allowedGraduationYears").size());
        assertTrue(saved.get("match").get("eligible").asBoolean());assertEquals("ELIGIBLE",report(job,token).get("status").asText());
        for(JsonNode check:report(job,token).get("checks"))assertEquals("MET",check.get("status").asText());
        // Required skills contribute to compatibility, not academic application eligibility.
        assertTrue(saved.get("match").get("missingSkills").size()>0);
        var applied=call("POST","/student/applications",token,Map.of("jobId",job));assertEquals(201,applied.statusCode(),applied.body());
        assertEquals(409,call("POST","/student/applications",token,Map.of("jobId",job)).statusCode());
    }
    @Test void missingAcademicDataNeverCountsAsZeroBacklogsOrPassingMarks()throws Exception {
        String token=student();long job=createJob(jobInput(true));JsonNode report=report(job,token);
        assertFalse(report.get("eligible").asBoolean());assertEquals("DETAILS_REQUIRED",report.get("status").asText());
        for(String key:List.of("tenthPercentage","secondaryPercentage","cgpa","activeBacklogs"))assertEquals("MISSING",check(report,key).get("status").asText());
        var blocked=call("POST","/student/applications",token,Map.of("jobId",job,"eligible",true,"cgpa",10));assertEquals(400,blocked.statusCode());assertTrue(blocked.body().contains("active backlog count"));
        long studentId=tree(call("GET","/auth/me",token,null)).get("id").asLong();
        assertEquals(400,call("POST","/recruiter/candidates/"+studentId+"/invite",login("recruiter@careerbridge.dev"),Map.of("jobId",job)).statusCode());
        assertEquals(0,tree(call("GET","/student/applications",token,null)).size());
    }
    @Test void eachAcademicRequirementIndependentlyBlocksApplicationSubmission()throws Exception {
        String token=student();long job=createJob(jobInput(true));
        Object[][] failures={{"tenthScore",59.99,"tenthPercentage"},{"secondaryScore",59.99,"secondaryPercentage"},{"cgpa",6.99,"cgpa"},{"department","Mechanical Engineering","branch"},{"graduationYear",2027,"graduationYear"},{"activeBacklogs",1,"activeBacklogs"}};
        for(Object[] failure:failures){var academic=academicInput();academic.put((String)failure[0],failure[1]);academics(token,academic);JsonNode report=report(job,token);assertEquals("NOT_ELIGIBLE",report.get("status").asText());assertEquals("NOT_MET",check(report,(String)failure[2]).get("status").asText());assertEquals(400,call("POST","/student/applications",token,Map.of("jobId",job)).statusCode());}
        academics(token,academicInput());assertEquals(201,call("POST","/student/applications",token,Map.of("jobId",job)).statusCode());
    }
    @Test void diplomaPercentagesAreAcceptedButCgpaAndLetterGradesAreNotGuessed()throws Exception {
        String token=student();long job=createJob(jobInput(true));var academic=academicInput();academic.put("secondaryQualification","DIPLOMA");academics(token,academic);assertTrue(report(job,token).get("eligible").asBoolean());
        academic.put("secondaryScoreType","CGPA");academic.put("secondaryScore",3.8);academic.put("secondaryScale",4);academics(token,academic);
        assertEquals("DETAILS_REQUIRED",report(job,token).get("status").asText());assertEquals("REVIEW_REQUIRED",check(report(job,token),"secondaryPercentage").get("status").asText());assertEquals(400,call("POST","/student/applications",token,Map.of("jobId",job)).statusCode());
        academic=academicInput();academic.remove("tenthScore");academic.put("tenthScoreType","GRADE");academic.put("tenthGrade","A1");academics(token,academic);
        assertEquals("REVIEW_REQUIRED",check(report(job,token),"tenthPercentage").get("status").asText());assertEquals(400,call("POST","/student/applications",token,Map.of("jobId",job)).statusCode());
    }
    @Test void existingJobsRemainUnrestrictedAndRecruitersCanClearOptionalRules()throws Exception {
        String token=student();long job=createJob(jobInput(false));assertTrue(report(job,token).get("eligible").asBoolean());assertEquals(0,report(job,token).get("checks").size());
        long strict=createJob(jobInput(true));assertFalse(report(strict,token).get("eligible").asBoolean());
        var edited=call("PUT","/recruiter/jobs/"+strict,login("recruiter@careerbridge.dev"),jobInput(false));assertEquals(200,edited.statusCode(),edited.body());assertTrue(tree(edited).get("minTenthPercentage").isNull());assertEquals(0,tree(edited).get("allowedBranches").size());assertTrue(report(strict,token).get("eligible").asBoolean());
        assertEquals(201,call("POST","/student/applications",token,Map.of("jobId",strict)).statusCode());
        var tightened=jobInput(true);tightened.put("minTenthPercentage",100);assertEquals(200,call("PUT","/recruiter/jobs/"+strict,login("recruiter@careerbridge.dev"),tightened).statusCode());
        var applications=tree(call("GET","/student/applications",token,null));assertEquals(1,applications.size());assertEquals("APPLIED",applications.get(0).get("status").asText());assertFalse(applications.get(0).get("eligibility").get("eligible").asBoolean());
    }
    @Test void eligibilitySettingsAreValidatedAndJobOwnershipIsStillEnforced()throws Exception {
        for(Map<String,Object> invalid:List.<Map<String,Object>>of(Map.of("minTenthPercentage",101),Map.of("minSecondaryPercentage",-1),Map.of("maxActiveBacklogs",-1),Map.of("maxActiveBacklogs",51),Map.of("allowedGraduationYears",List.of(2101)),Map.of("allowedBranches",List.of("  ")),Map.of("allowedBranches",List.of("x".repeat(101))))){var input=jobInput(false);input.putAll(invalid);assertEquals(400,call("POST","/recruiter/jobs",login("recruiter@careerbridge.dev"),input).statusCode());}
        long job=createJob(jobInput(true));assertEquals(403,call("PUT","/recruiter/jobs/"+job,login("other-recruiter@careerbridge.dev"),jobInput(false)).statusCode());assertEquals(403,call("POST","/recruiter/jobs",student(),jobInput(true)).statusCode());
    }
}
