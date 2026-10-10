package com.parvnigam.careerbridge;

import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:academics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.upload-dir=target/academic-test-resumes",
    "app.mail-enabled=false","app.local-email-testing=true","app.local-inbox-port=0","app.ai-url=","app.redis-enabled=false"})
class AcademicTests {
    @org.springframework.beans.factory.annotation.Autowired VerificationMail mail;
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
        var response=call("POST","/auth/register",null,Map.of("name","Academic Test Student","email",UUID.randomUUID()+"@example.com","password","Strong@12345","role","STUDENT","universityId",university));
        assertEquals(201,response.statusCode(),response.body());var message=mail.messages.get(mail.messages.size()-1);assertEquals(200,call("POST","/auth/verify-email",null,Map.of("token",message.link().substring(message.link().indexOf("#token=")+7))).statusCode());return tree(call("POST","/auth/login",null,Map.of("email",message.to(),"password","Strong@12345"))).get("token").asText();
    }
    Map<String,Object> academicInput() {
        var d=new LinkedHashMap<String,Object>();
        d.put("tenthInstitution","School, Main Campus");d.put("tenthBoard","ICSE");d.put("tenthYear",2022);d.put("tenthScoreType","PERCENTAGE");d.put("tenthScore",97.2);
        d.put("secondaryQualification","TWELFTH");d.put("secondaryInstitution","=HYPERLINK(\"test\")");d.put("secondaryBoard","ISC");d.put("secondaryYear",2024);d.put("secondaryScoreType","PERCENTAGE");d.put("secondaryScore",95.75);
        d.put("collegeInstitution","Test Institute");d.put("degree","B.Tech");d.put("department","Computer Science");d.put("graduationYear",2028);d.put("currentSemester",5);d.put("cgpa",9.23);d.put("activeBacklogs",0);d.put("clearedBacklogs",1);d.put("gapYears",0);
        return d;
    }
    @Test void savesAcademicRecordsAndSharesThemThroughAuthorizedViews()throws Exception {
        String token=student();var input=academicInput();
        var saved=call("PUT","/student/academics",token,input);assertEquals(200,saved.statusCode(),saved.body());
        long id=tree(saved).get("user").get("id").asLong();
        var profile=tree(call("GET","/student/profile",token,null));
        assertEquals(97.2,profile.get("profile").get("academics").get("tenthScore").asDouble());
        assertEquals(9.23,profile.get("profile").get("cgpa").asDouble());
        assertEquals(2028,profile.get("user").get("graduationYear").asInt());
        var candidates=tree(call("GET","/recruiter/candidates",login("recruiter@careerbridge.dev"),null));
        boolean found=false;for(JsonNode candidate:candidates)if(candidate.get("user").get("id").asLong()==id){found=true;assertEquals(95.75,candidate.get("profile").get("academics").get("secondaryScore").asDouble());}
        assertTrue(found);
        var placement=call("GET","/placement/analytics",login("placement@careerbridge.dev"),null);assertEquals(200,placement.statusCode());assertTrue(placement.body().contains("Test Institute"));assertFalse(placement.body().contains("Dev Mehta"));
        var export=call("GET","/placement/export",login("placement@careerbridge.dev"),null);assertTrue(export.body().contains("10th Result"));assertTrue(export.body().contains("97.2%"));assertTrue(export.body().contains("\"School, Main Campus\""));assertTrue(export.body().contains("\"'=HYPERLINK(\"\"test\"\")\""));
        var basics=Map.of("headline","New headline","cgpa",9.23,"skills",List.of("Java"),"visibleToRecruiters",false);
        assertEquals(200,call("PUT","/student/profile",token,basics).statusCode());
        assertEquals(97.2,tree(call("GET","/student/profile",token,null)).get("profile").get("academics").get("tenthScore").asDouble());
        var hidden=tree(call("GET","/recruiter/candidates",login("recruiter@careerbridge.dev"),null));
        for(JsonNode candidate:hidden)assertNotEquals(id,candidate.get("user").get("id").asLong());
        assertTrue(call("GET","/placement/export",login("placement@careerbridge.dev"),null).body().contains("97.2%"));
    }
    @Test void allowsIncompleteRecordsDiplomasAndLetterGradesAndClearsScores()throws Exception {
        String token=student();assertTrue(tree(call("GET","/student/profile",token,null)).get("profile").get("cgpa").isNull());
        assertEquals(200,call("GET","/student/recommendations",token,null).statusCode());
        var partial=Map.of("tenthInstitution","Pending School");assertEquals(200,call("PUT","/student/academics",token,partial).statusCode());
        assertTrue(tree(call("GET","/student/profile",token,null)).get("profile").get("academics").get("tenthScore").isNull());
        var diploma=Map.of("tenthScoreType","GRADE","tenthGrade","A1","secondaryQualification","DIPLOMA","secondaryScoreType","CGPA","secondaryScore",3.8,"secondaryScale",4);
        var saved=call("PUT","/student/academics",token,diploma);assertEquals(200,saved.statusCode(),saved.body());
        assertEquals("DIPLOMA",tree(saved).get("profile").get("academics").get("secondaryQualification").asText());
        assertEquals(200,call("PUT","/student/academics",token,Map.of()).statusCode());
        var cleared=tree(call("GET","/student/profile",token,null)).get("profile");assertTrue(cleared.get("academics").isNull()||cleared.get("academics").get("secondaryScore").isNull());
        assertTrue(cleared.get("cgpa").isNull());
    }
    @Test void rejectsInvalidMarksScalesAndAcademicChronologyWithoutSaving()throws Exception {
        String token=student();assertEquals(200,call("PUT","/student/academics",token,academicInput()).statusCode());
        List<Map<String,Object>> invalid=List.of(
            Map.of("tenthScoreType","PERCENTAGE","tenthScore",100.1),Map.of("tenthScoreType","PERCENTAGE","tenthScore",-1),
            Map.of("tenthScoreType","CGPA","tenthScore",9.5,"tenthScale",4),Map.of("tenthScoreType","CGPA","tenthScore",9.5),
            Map.of("tenthScoreType","CGPA","tenthScale",0),Map.of("tenthScoreType","GRADE","tenthScore",90),
            Map.of("tenthScore",80),Map.of("secondaryScoreType","PERCENTAGE"),Map.of("tenthYear",LocalDate.now().getYear()+1),
            Map.of("tenthYear",2024,"secondaryQualification","TWELFTH","secondaryYear",2022),
            Map.of("secondaryQualification","DIPLOMA","secondaryYear",2024,"graduationYear",2023),
            Map.of("tenthYear",2022,"graduationYear",2020),
            Map.of("cgpa",10.1),Map.of("activeBacklogs",-1),Map.of("currentSemester",0),Map.of("gapYears",21),
            Map.of("secondaryQualification","OTHER"),Map.of("tenthInstitution","x".repeat(201)));
        for(var input:invalid){var response=call("PUT","/student/academics",token,input);assertEquals(400,response.statusCode(),input+" -> "+response.body());}
        assertEquals(97.2,tree(call("GET","/student/profile",token,null)).get("profile").get("academics").get("tenthScore").asDouble());
    }
    @Test void onlyStudentsCanUpdateTheirOwnAcademicRecord()throws Exception {
        assertEquals(401,call("PUT","/student/academics",null,academicInput()).statusCode());
        assertEquals(403,call("PUT","/student/academics",login("recruiter@careerbridge.dev"),academicInput()).statusCode());
        assertEquals(403,call("PUT","/student/academics",login("placement@careerbridge.dev"),academicInput()).statusCode());
        String token=student();long other=tree(call("GET","/auth/me",login("aarav@careerbridge.dev"),null)).get("id").asLong();
        var input=academicInput();input.put("userId",other);var response=call("PUT","/student/academics",token,input);assertEquals(200,response.statusCode(),response.body());
        assertNotEquals(other,tree(response).get("user").get("id").asLong());
        var untouched=tree(call("GET","/student/profile",login("aarav@careerbridge.dev"),null)).get("profile");assertTrue(untouched.get("academics").isNull());
    }
}
